package cn.edu.ai.agent.service.agent;

import cn.edu.ai.api.dto.ChatResponse;
import cn.edu.ai.infrastructure.llm.ModelRouter;
import cn.edu.ai.infrastructure.trace.TraceService;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 反思 Agent
 * 采用 Self-Reflection 模式：
 *   初次生成回答
 *   自我审视回答的质量、准确性、完整性
 *   根据反思结果改进回答
 *   重复直到满意度达标或达到最大轮次
 * 适用于对准确性要求高的场景
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReflectionAgent {

    private final ModelRouter modelRouter;
    private final TraceService traceService;
    private static final int MAX_REFLECTIONS = 3;

    private static final String INITIAL_PROMPT = """
            请根据上下文信息和用户的问题，给出详细、准确的回答。
            上下文信息：
            %s
            
            用户问题：
            %s
            """;
    private static final String REFLECTION_PROMPT = """
            请审视以下回答，从以下维度进行评估：
            
            1. 准确性：信息是否正确？
            2. 完整性：是否遗漏了重要方面？
            3. 逻辑性：推理过程是否合理？
            4. 实用性：对用户是否有帮助？
            
            用户问题：
            %s
            
            当前回答：
            %s
            
            请按以下格式输出：
            评分: [1-10的分数]
            不足之处: [列出具体问题]
            改进建议: [具体的改进方向]
            """;
    private static final String IMPROVEMENT_PROMPT = """
            请根据以下反思意见，改进你的回答。
            
            用户问题：
            %s
            
            原始回答：
            %s
            
            反思意见：
            %s
            
            请给出改进后的回答：
            """;

    /**
     * 执行反思循环
     */
    public ReflectionResult execute(String query, String context, String traceId) {
        log.info("Reflection Agent 开始执行: query={}", query);

        List<ChatResponse.ThinkingStep> thinkingSteps = new ArrayList<>();
        String contextStr = "";
        if(context != null && !context.isBlank()) {
            contextStr =  "参考信息:\n" + context;
        }

        traceService.addSpan(traceId, "reflection_initial", Map.of("query", query));
        String prompt = String.format(INITIAL_PROMPT, contextStr, query);
        String currentAnswer = modelRouter.call(new Prompt(prompt), null).getResult().getOutput().getText();
        thinkingSteps.add(ChatResponse.ThinkingStep.builder()
                .step(1)
                .thought("生成初始回答")
                .action("initial_generation")
                .stepResult(truncate(currentAnswer, 200))
                .build());

        int reflectionCount = 0;
        for(int i = 0; i < MAX_REFLECTIONS; i++) {
            traceService.addSpan(traceId, "reflection_round_" + (i + 1), Map.of());

            String promptReflect = String.format(REFLECTION_PROMPT, query, currentAnswer);
            String reflection = modelRouter.call(new Prompt(promptReflect), null).getResult().getOutput().getText();
            log.info("第 {} 轮反思结果: {}", i + 1, truncate(reflection, 200));

            int score = extractScore(reflection);
            thinkingSteps.add(ChatResponse.ThinkingStep.builder()
                    .step(i + 2)
                    .thought("反思第 " + (i + 1) + " 轮，评分: " + score)
                    .action("self_reflection")
                    .stepResult(reflection)
                    .build());
            if(score >= 8){
                log.info("反思评分 {} ≥ 8，回答质量达标，停止反思", score);
                break;
            }

            String promptImprove = String.format(IMPROVEMENT_PROMPT, query, currentAnswer, reflection);
            currentAnswer = modelRouter.call(new Prompt(promptImprove), null).getResult().getOutput().getText();
            reflectionCount ++;
            thinkingSteps.add(ChatResponse.ThinkingStep.builder()
                    .step(i + 3)
                    .thought("根据反思改进回答")
                    .action("improvement")
                    .stepResult(truncate(currentAnswer,200))
                    .build());
        }
        log.info("Reflection Agent 完成，共 {} 轮反思", reflectionCount);

        return ReflectionResult.builder()
                .finalAnswer(currentAnswer)
                .reflectionCount(reflectionCount)
                .thinkingSteps(thinkingSteps)
                .build();

    }

    private String truncate(String text, int maxLen) {
        return text.length() <= maxLen ? text : text.substring(0, maxLen) + "...";
    }

    private int extractScore(String reflection) {
        try {
            Matcher matcher = Pattern.compile("评分[：:]\\s*(\\d+)").matcher(reflection);
            if(matcher.find()) {
                return Integer.parseInt(matcher.group(1));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return 5;
    }

    @Data
    @Builder
    public static class ReflectionResult {
        private String finalAnswer;
        private List<ChatResponse.ThinkingStep> thinkingSteps;
        private int reflectionCount;
    }
}
