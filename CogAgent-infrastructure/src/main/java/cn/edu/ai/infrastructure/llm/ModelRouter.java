package cn.edu.ai.infrastructure.llm;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

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

    public ModelRouter(
            @Qualifier("primaryChatModel") OpenAiChatModel primaryModel,
            @Qualifier("fallbackChatModel") OpenAiChatModel fallbackModel) {
        this.primaryModel = primaryModel;
        this.fallbackModel = fallbackModel;
    }

    /**
     * 调用模型
     */
    public ChatResponse call(Prompt prompt, String forceModel){
        ChatModel chatModel = selectModel(forceModel);
        ChatResponse response = chatModel.call(prompt);
        return response;
    }

    /**
     * 根据条件选择模型
     */
    private ChatModel selectModel(String forceModel) {
        if (forceModel != null) {
            if (forceModel.contains("flash")) {
                return fallbackModel;
            }
            return primaryModel;
        }

        return primaryModel;
    }
}
