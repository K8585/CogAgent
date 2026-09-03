package cn.edu.ai.agent.service.agent;

import cn.edu.ai.agent.service.agent.intent.IntentRecognizer;
import cn.edu.ai.agent.service.memory.MemoryManager;
import cn.edu.ai.agent.service.rag.MultiRetriever;
import cn.edu.ai.agent.service.rag.Reranker;
import cn.edu.ai.api.dto.ChatRequest;
import cn.edu.ai.api.dto.ChatResponse;
import cn.edu.ai.api.dto.enums.AgentMode;
import cn.edu.ai.infrastructure.llm.ModelRouter;
import cn.edu.ai.infrastructure.trace.TraceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Agent 编排器
 * 系统的核心调度中枢，负责：
 *   意图识别 → 选择 Agent 模式
 *   RAG 检索 → 提供上下文
 *   Agent 执行 → ReAct / Planner / Reflection
 *   记忆管理 → 保存对话历史
 *   全链路追踪 → 记录执行过程
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentOrchestrator {
    
    private final IntentRecognizer intentRecognizer;
    private final MemoryManager memoryManager;
    private final MultiRetriever multiRetriever;
    private final Reranker reranker;
    private final ReActAgent reActAgent;
    private final PlannerAgent plannerAgent;
    private final ReflectionAgent reflectionAgent;
    private final ModelRouter modelRouter;
    private final TraceService traceService;

    /**
     * 处理对话请求（同步）
     *
     * @param request 对话请求
     * @return 对话响应
     */
    public ChatResponse chat(ChatRequest request) {
        // 追踪完整链路
        String traceId = traceService.startTrace("chat");
        try{
            // 召回记忆上下文
            String conversationId = generateConversationId(request.getConversationId());
            traceService.addSpan(traceId, "memory_recall", Map.of("message", request.getMessage()));
            String memoryContext = memoryManager.recallMemoryContext(conversationId, request.getMessage());

            // 保存对话历史
            memoryManager.saveUserMessage(conversationId, request.getMessage());

            traceService.addSpan(traceId, "intent_recognition", Map.of("message", request.getMessage()));
            IntentRecognizer.IntentResult intent = intentRecognizer.recognize(request.getMessage());

            AgentMode mode = request.getMode() != null ? request.getMode() : intent.getAgentMode();
            log.info("选定 Agent 模式: {}, 意图: {}, 置信度: {}", mode, intent.getIntentType(), intent.getConfidence());

            String ragContext = "";
            List<ChatResponse.SourceDocument> sources = new ArrayList<>();
            if (request.isEnableRag() && intent.isNeedsRag()) {
                traceService.addSpan(traceId, "rag_retrieval", Map.of());
                ragContext = performRAG(request.getMessage(), sources, traceId);
            }

            // 合并上下文
            String context = mergeContext(memoryContext, ragContext);

            traceService.addSpan(traceId, "agent_execution", Map.of("mode", mode.name(), "intent", intent.getIntentType()));
            ChatResponse response = dispatchToAgent(mode, request, context, traceId);
            response.setConversationId(conversationId);
            response.setSources(sources.isEmpty() ? null : sources);
            response.setTraceId(traceId);

            memoryManager.saveAssistantMessage(conversationId, response.getReply());
            // 触发长期记忆归档的判断
            memoryManager.archiveIfNeeded(conversationId);

            return response;
        } catch (Exception e) {
            log.error("对话处理异常", e);
            return ChatResponse.builder()
                    .reply("抱歉，处理您的请求时发生了错误: " + e.getMessage())
                    .traceId(traceId)
                    .build();
        } finally {
            traceService.endTrace(traceId);
        }
    }

    /**
     * 处理对话请求（流式 SSE）
     */
    public Flux<String> chatStream(ChatRequest request) {
        // 追踪一次完整的请求链路
        String traceId = traceService.startTrace("chat_stream");
        try {
            // 召回记忆上下文
            String conversationId = generateConversationId(request.getConversationId());
            traceService.addSpan(traceId, "memory_recall", Map.of("message", request.getMessage()));
            String memoryContext = memoryManager.recallMemoryContext(conversationId, request.getMessage());

            // 保存对话历史
            memoryManager.saveUserMessage(conversationId, request.getMessage());

            traceService.addSpan(traceId, "intent_recognition", Map.of("message", request.getMessage()));
            IntentRecognizer.IntentResult intent = intentRecognizer.recognize(request.getMessage());

            AgentMode mode = request.getMode() != null ? request.getMode() : intent.getAgentMode();
            log.info("选定 Agent 模式: {}, 意图: {}, 置信度: {}", mode, intent.getIntentType(), intent.getConfidence());

            String ragContext = "";
            List<ChatResponse.SourceDocument> sources = new ArrayList<>();
            if (request.isEnableRag() && intent.isNeedsRag()) {
                traceService.addSpan(traceId, "rag_retrieval", Map.of());
                ragContext = performRAG(request.getMessage(), sources, traceId);
            }

            // 合并上下文
            String context = mergeContext(memoryContext, ragContext);

            traceService.addSpan(traceId, "agent_stream_execution", Map.of("mode", mode.name(), "intent", intent.getIntentType()));
            String promptText = buildDirectPrompt(request.getMessage(), context);
            String forceModel = request.getModelOptions() != null ? request.getModelOptions().getModel() : null;

            return modelRouter.stream(new Prompt(promptText), forceModel)
                    .map(chatResponse -> {
                        if (chatResponse.getResult() != null && chatResponse.getResult().getOutput() != null) {
                            String text = chatResponse.getResult().getOutput().getText();
                            return text != null ? text : "";
                        }
                        return "";
                    })
                    .filter(text -> !text.isEmpty())
                    .doOnComplete(() -> {
                        traceService.endTrace(traceId);
                        log.info("流式对话完成: traceId={}, conversationId={}", traceId, conversationId);
                    })
                    .doOnError(e -> {
                        log.error("流式对话异常: traceId={}, conversationId={}", traceId, conversationId, e);
                        traceService.endTrace(traceId);
                    });
        } catch (Exception e) {
            log.error("流式对话异常", e);
            traceService.endTrace(traceId);
            // 返回错误消息的流
            return Flux.just("抱歉，处理您的请求时发生了错误: " + e.getMessage());
        }
    }

    /**
     * 直接对话模式（不经过 Agent 编排）
     */
    private String directChat(String message, String context) {
        String promptText = buildDirectPrompt(message, context);
        return modelRouter.call(new Prompt(promptText), null).getResult().getOutput().getText();
    }

    /**
     * 如果没有对话 ID，生成
     */
    private String generateConversationId(String conversationId) {
        if(conversationId == null || conversationId.isBlank()) {
            return UUID.randomUUID().toString().replace("-", "");
        }
        return conversationId;
    }

    /**
     * 执行 RAG 检索，获取相关上下文
     */
    private String performRAG(String query, List<ChatResponse.SourceDocument> sources, String traceId) {
        try {
            List<MultiRetriever.RetrievalResult> results = multiRetriever.retrieve(query);
            if(!results.isEmpty()) {
                results = reranker.rerank(query, results);
            }

            for(MultiRetriever.RetrievalResult result : results) {
                sources.add(ChatResponse.SourceDocument.builder()
                        .documentId(result.getDocumentId())
                        .content(result.getContent())
                        .score(result.getScore())
                        .build());
            }
            traceService.addSpan(traceId, "rag_results", Map.of("count", results.size()));

            return results.stream()
                    .map(MultiRetriever.RetrievalResult::getContent)
                    .collect(Collectors.joining("\n\n---\n\n"));
        } catch (Exception e) {
            log.warn("RAG 检索失败，降级为无上下文模式: {}", e.getMessage());
            return "";
        }
    }

    /**
     * 根据模式分发到对应的 Agent
     */
    private ChatResponse dispatchToAgent(AgentMode mode, ChatRequest request, String context, String traceId) {
        if(mode.equals(AgentMode.REACT)) {
            ReActAgent.ReActResult result = reActAgent.execute(request.getMessage(), context, request.getTools(), traceId);
            return ChatResponse.builder()
                    .reply(result.getFinalAnswer())
                    .thinkingSteps(result.getThinkingSteps())
                    .usedTools(result.getUsedTools())
                    .build();
        } else if(mode.equals(AgentMode.PLANNER)) {
            PlannerAgent.PlanResult result = plannerAgent.execute(request.getMessage(), context, request.getTools(), traceId);
            return ChatResponse.builder()
                    .reply(result.getFinalAnswer())
                    .thinkingSteps(result.getThinkingSteps())
                    .usedTools(result.getUsedTools())
                    .build();
        } else if(mode.equals(AgentMode.REFLECTION)) {
            ReflectionAgent.ReflectionResult result = reflectionAgent.execute(request.getMessage(), context, traceId);
            return ChatResponse.builder()
                    .reply(result.getFinalAnswer())
                    .thinkingSteps(result.getThinkingSteps())
                    .build();
        } else if(mode.equals(AgentMode.DIRECT)) {
            String reply = directChat(request.getMessage(), context);
            return ChatResponse.builder()
                    .reply(reply)
                    .build();
        }

        return ChatResponse.builder()
                .reply("")
                .build();
    }

    /**
     * 合并记忆召回上下文与 RAG 检索上下文
     */
    private String mergeContext(String memoryContext, String ragContext) {
        StringBuilder merged = new StringBuilder();
        if (memoryContext != null && !memoryContext.isBlank()) {
            merged.append("对话历史 (供参考)：\n");
            merged.append(memoryContext);
        }
        if (ragContext != null && !ragContext.isBlank()) {
            if (merged.length() > 0) {
                merged.append("\n\n");
            }
            merged.append("知识库检索结果 (权威知识源)：\n");
            merged.append(ragContext);
        }
        return merged.toString();
    }

    private String buildDirectPrompt(String message, String context) {
        if (context != null && !context.isBlank()) {
            return String.format("""
                    你是一个专业的 AI 助手。请基于以下参考信息回答用户的问题。
                    
                    参考信息：
                    %s
                    
                    用户问题：%s
                    """, context, message);
        }
        return "你是一个专业的 AI 助手。请回答用户的问题。\n\n用户问题：" + message;
    }
}
