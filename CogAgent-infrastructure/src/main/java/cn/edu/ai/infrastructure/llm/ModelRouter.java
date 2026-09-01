package cn.edu.ai.infrastructure.llm;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

/**
 * 模型路由器
 * 职责：
 *   根据任务复杂度选择合适的模型（主力模型 / 快速模型）
 *   结合熔断器实现自动降级
 */
@Slf4j
@Component
public class ModelRouter {

    @Qualifier("primaryChatModel")  // 指定要注入的 Bean
    private final OpenAiChatModel primaryModel;
    @Qualifier("fallbackChatModel")
    private final OpenAiChatModel fallbackModel;

    private final CircuitBreaker circuitBreaker;

    public ModelRouter(
            @Qualifier("primaryChatModel") OpenAiChatModel primaryModel,
            @Qualifier("fallbackChatModel") OpenAiChatModel fallbackModel,
            CircuitBreaker circuitBreaker) {
        this.primaryModel = primaryModel;
        this.fallbackModel = fallbackModel;
        this.circuitBreaker = circuitBreaker;
    }

    /**
     * 调用模型，自带熔断降级
     */
    public ChatResponse call(Prompt prompt, String forceModel){
        ChatModel chatModel = selectModel(forceModel);
        try {
            ChatResponse response = chatModel.call(prompt);
            circuitBreaker.recordSuccess();
            log.debug("模型调用成功，使用模型: {}", getModelName(chatModel));
            return response;
        } catch (Exception e) {
            circuitBreaker.recordFailure();
            log.warn("主模型调用失败，尝试降级: {}", e.getMessage());
            return fallbackModel.call(prompt);
        }
    }

    /**
     * 流式调用模型，自带熔断降级
     */
    public Flux<ChatResponse> stream(Prompt prompt, String forceModel) {
        ChatModel selectedModel = selectModel(forceModel);

        return Flux.defer(() -> {   // 外部开始请求数据时再执行内部代码，避免不必要的计算，支持每个请求独立执行(并发安全)
            try {
                return selectedModel.stream(prompt)
                        .doOnNext(r -> circuitBreaker.recordSuccess())
                        .onErrorResume(e -> {
                            circuitBreaker.recordFailure();
                            log.warn("流式调用主模型失败，降级到备用模型: {}", e.getMessage());
                            return fallbackModel.stream(prompt);
                        });
            } catch (Exception e) {
                circuitBreaker.recordFailure();
                log.warn("流式调用启动失败，降级到备用模型: {}", e.getMessage());
                return fallbackModel.stream(prompt);
            }
        });
    }

    /**
     * 根据条件选择模型
     */
    private ChatModel selectModel(String forceModel) {

        if (!circuitBreaker.allowRequest()) {
            log.info("熔断器开启，降级到备用模型");
            return fallbackModel;
        }

        if (forceModel != null) {
            if (forceModel.contains("flash")) {
                return fallbackModel;
            }
            return primaryModel;
        }

        return primaryModel;
    }

    private String getModelName(ChatModel model) {
        if (model == primaryModel) {
            return "primary (deepseek-v4-pro)";
        }
        return "fallback (deepseek-v4-flash)";
    }
}
