package cn.edu.ai.infrastructure.config;

import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAI 模型配置类, 提供多模型实例
 */
@Configuration
public class OpenAIConfig {

    @Value("${spring.ai.openai.api-key}")
    private String apiKey;

    @Value("${spring.ai.openai.base-url}")
    private String baseUrl;

    /**
     * 主力模型 - deepseek-v4-pro，用于复杂推理任务
     */
    @Bean(name = "primaryChatModel")
    public OpenAiChatModel primaryChatModel() {
        OpenAiApi api = OpenAiApi.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .build();
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model("deepseek-v4-pro")
                .temperature(0.7)
                .maxTokens(4096)
                .build();
        return OpenAiChatModel.builder()
                .openAiApi(api)
                .defaultOptions(options)
                .build();
    }

    /**
     * 快速模型 - deepseek-v4-flash，用于简单任务和降级场景
     */
    @Bean(name = "fallbackChatModel")
    public OpenAiChatModel fallbackChatModel() {
        OpenAiApi api = OpenAiApi.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .build();
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model("deepseek-v4-flash")
                .temperature(0.7)
                .maxTokens(2048)
                .build();
        return OpenAiChatModel.builder()
                .openAiApi(api)
                .defaultOptions(options)
                .build();
    }
}
