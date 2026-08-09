package cn.edu.ai.agent.service.agent;

import cn.edu.ai.api.dto.ChatResponse;
import cn.edu.ai.infrastructure.llm.ModelRouter;
import cn.edu.ai.infrastructure.trace.TraceService;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.math.raw.Mod;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 规划 Agent
 * 采用 Plan-and-Execute 模式：
 *   先让 LLM 制定完整的执行计划
 *   按计划逐步执行每个子任务
 *   汇总所有步骤的结果，生成最终回答
 * 适用于复杂的多步骤任务
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlannerAgent {

    private final TraceService traceService;
    private final ModelRouter modelRouter;

    private static final String PLAN_PROMPT = """
            你是一个任务规划专家。请将用户的复杂问题分解为可执行的步骤计划。
            
            可用工具：
            %s
            
            用户问题：
            %s
            
            请按以下格式输出执行计划：
            Step 1: [步骤描述] | Tool: [工具名称或none] | Args: [参数JSON或none]
            Step 2: [步骤描述] | Tool: [工具名称或none] | Args: [参数JSON或none]
            ...
            Step N: 综合以上结果，生成最终回答
            
            注意：
            1. 步骤要合理且有序
            2. 每步尽量只完成一个明确的可执行操作
            3. 如果有 Args，则 Args 必须是可以直接执行的JSON参数，避免模糊描述
            4. 如问题需用户补充信息，在步骤中明确标注待补充项
            5. 最后一步必须是综合回答
            """;
    private static final String SYNTHESIZE_PROMPT = """
            请根据以下执行计划的各步骤结果，综合给出最终回答。
            
            用户问题：
            %s
            
            执行结果：
            %s
            
            给出完整、准确的最终回答：
            """;

    // 提取信息：步骤描述、调用的工具名称、工具参数
    private static final Pattern STEP_PATTERN = Pattern.compile(
            "Step \\d+:\\s*(.+?)\\s*\\|\\s*Tool:\\s*(.+?)\\s*\\|\\s*Args:\\s*(.+?)\\s*$",
            Pattern.MULTILINE);

    /**
     * 执行 Plan-and-Execute
     */
    public PlanResult execute(String query, String context, List<String> availableTools, String traceId) {
        log.info("Planner Agent 开始执行: query={}", query);

        String toolDescriptions = null;

        traceService.addSpan(traceId, "planner_planning", Map.of("query", query));
        List<PlanStep> plan = generatePlan(query, toolDescriptions);
        log.info("生成执行计划，共 {} 个步骤", plan.size());

        List<ChatResponse.ThinkingStep> thinkingSteps = new ArrayList<>();
        List<String> usedTools = new ArrayList<>();
        StringBuilder results = new StringBuilder();

        for (int i = 0; i < plan.size(); i++) {
            PlanStep step = plan.get(i);
            log.info("执行步骤 {}/{}: {}", i + 1, plan.size(), step.getDescription());
            traceService.addSpan(traceId, "planner_step_" + (i + 1),
                    Map.of("description", step.getDescription(), "tool", step.getToolName()));

            String toolResult;
            if (!"none".equalsIgnoreCase(step.getToolName())) {
                toolResult = null;
            } else {
                toolResult = "该步骤不需要工具调用";
            }

            results.append("步骤 ").append(i + 1).append(": ")
                    .append(step.getDescription()).append("\n结果: ").append(toolResult).append("\n\n");

            thinkingSteps.add(ChatResponse.ThinkingStep.builder()
                    .step(i + 1)
                    .thought(step.getDescription())
                    .action(step.getToolName())
                    .stepResult(toolResult)
                    .build());
        }

        String finalAnswer = synthesize(query, results.toString());

        log.info("Planner Agent 执行完成，共 {} 个步骤", plan.size());

        return PlanResult.builder()
                .finalAnswer(finalAnswer)
                .plan(plan)
                .thinkingSteps(thinkingSteps)
                .usedTools(usedTools)
                .build();
    }

    private List<PlanStep> generatePlan(String query, String toolDescriptions) {
        String promptText = String.format(PLAN_PROMPT,toolDescriptions,query);
        String llmOutput = modelRouter.call(new Prompt(promptText), null).getResult().getOutput().getText();

        List<PlanStep> steps = new ArrayList<>();
        Matcher matcher = STEP_PATTERN.matcher(llmOutput);
        while (matcher.find()) {
            steps.add(PlanStep.builder()
                    .description(matcher.group(1).trim())
                    .toolName(matcher.group(2).trim())
                    .args(matcher.group(3).trim())
                    .build());
        }

        if (steps.isEmpty()) {
            steps.add(PlanStep.builder()
                    .description("直接回答用户问题")
                    .toolName("none")
                    .args("none")
                    .build());
        }

        return steps;
    }

    private String synthesize(String query, String results) {
        String promptText = String.format(SYNTHESIZE_PROMPT, query, results);
        return modelRouter.call(new Prompt(promptText), null).getResult().getOutput().getText();
    }


    @Data
    @Builder
    public static class PlanStep {
        private String description;
        private String toolName;
        private String args;
    }

    @Data
    @Builder
    public static class PlanResult {
        private String finalAnswer;
        private List<PlanStep> plan;
        private List<ChatResponse.ThinkingStep> thinkingSteps;
        private List<String> usedTools;
    }
}
