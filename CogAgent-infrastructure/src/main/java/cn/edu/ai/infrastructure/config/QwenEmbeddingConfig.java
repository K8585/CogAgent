package cn.edu.ai.infrastructure.config;


import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 通过 DashScope 提供的 OpenAI 兼容接口来使用 Qwen 的 Embedding 模型
 */
@Configuration
public class QwenEmbeddingConfig {

    @Value("${spring.ai.qwen.embedding.api-key:${DASH_SCOPE_API_KEY:}}")
    private String apiKey;
    @Value("${spring.ai.qwen.embedding.base-url:https://dashscope.aliyuncs.com/compatible-mode}")
    private String baseUrl;
    @Value("${spring.ai.qwen.embedding.model:text-embedding-v3}")
    private String model;

    @Bean(name = "qwenEmbeddingModel")
    public OpenAiEmbeddingModel qwenEmbeddingModel() {
        OpenAiApi api = OpenAiApi.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .build();

        return new OpenAiEmbeddingModel(
                api,
                MetadataMode.EMBED,
                OpenAiEmbeddingOptions.builder().model(model).build()
        );
    }
}