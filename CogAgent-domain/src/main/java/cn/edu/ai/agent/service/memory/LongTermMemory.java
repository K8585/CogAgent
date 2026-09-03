package cn.edu.ai.agent.service.memory;

import cn.edu.ai.infrastructure.cache.RedisCacheService;
import cn.edu.ai.infrastructure.vectordb.MilvusService;
import io.milvus.grpc.SearchResults;
import io.milvus.response.SearchResultsWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;

/**
 * 长期记忆
 * 将对话摘要向量化后存入 Milvus
 */
@Slf4j
@Component
public class LongTermMemory {

    private static final String ARCHIVE_HASH_KEY_PREFIX = "memory:long:archive-hash:";
    private static final String SUMMARY_KEY_PREFIX = "memory:long:summary:";

    private final MilvusService milvusService;
    private final RedisCacheService redisCacheService;
    private final OpenAiChatModel chatModel;
    private final EmbeddingModel embeddingModel;

    public LongTermMemory(
            MilvusService milvusService,
            RedisCacheService redisCacheService,
            @Qualifier("fallbackChatModel") OpenAiChatModel chatModel,
            @Qualifier("qwenEmbeddingModel") EmbeddingModel embeddingModel
    ){
        this.milvusService = milvusService;
        this.redisCacheService = redisCacheService;
        this.chatModel = chatModel;
        this.embeddingModel = embeddingModel;
    }

    /**
     * 长期记忆归档
     *
     * @param conversationId 会话 ID
     * @param history        会话历史
     * @param summary        摘要文本
     */
    public void archive(String conversationId, List<String> history, String summary) {
        if (summary == null || summary.isBlank()) return;

        // 如果该对话内容已被归档，则返回 false
        String historyHash = sha256(String.join("\n", history));
        Optional<String> previousHash = redisCacheService.get(ARCHIVE_HASH_KEY_PREFIX + conversationId, String.class);
        if (previousHash.filter(historyHash::equals).isPresent()) return;

        String memoryId = "memory-" + UUID.randomUUID().toString().replace("-", "");
        float[] vector = embeddingModel.embed(summary);
        if (vector == null || vector.length == 0) {
            throw new IllegalStateException("长期记忆向量化结果为空");
        }
        milvusService.insertMemoryVectors(List.of(memoryId), List.of(conversationId),
                List.of("long-memory"), List.of(toFloatList(vector)), List.of(summary));

        redisCacheService.set(SUMMARY_KEY_PREFIX + conversationId, summary, Duration.ofDays(30));
        redisCacheService.set(ARCHIVE_HASH_KEY_PREFIX + conversationId, historyHash, Duration.ofDays(30));
        log.info("长期记忆归档成功: conversationId={}", conversationId);
    }

    /**
     * 将对话摘要存入长期记忆
     *
     * @param conversationId 会话 ID
     * @param summary        摘要文本
     * @param embedding      摘要的向量表示
     */
    public void store(String conversationId, String summary, List<Float> embedding) {
        if (summary == null || summary.isBlank() || embedding == null || embedding.isEmpty()) return;

        String memoryId = "memory-" + UUID.randomUUID().toString().replace("-", "");
        milvusService.insertMemoryVectors(List.of(memoryId), List.of(conversationId),
                List.of("long-memory"), List.of(embedding), List.of(summary));

        redisCacheService.set(
            SUMMARY_KEY_PREFIX + conversationId,
                Map.of("summary", summary, "conversationId", conversationId),
                Duration.ofDays(30)
        );

        log.info("长期记忆存储成功: conversationId={}", conversationId);
    }

    /**
     * 根据查询内容检索相关的长期记忆
     *
     * @param queryEmbedding 查询向量
     * @param topK           返回数量
     * @param conversationId 会话 ID
     * @return 搜索结果
     */
    public SearchResults recall(List<Float> queryEmbedding, int topK, String conversationId) {
        log.debug("检索长期记忆: conversationId={}, topK={}", conversationId, topK);
        return milvusService.searchMemorySimilar(queryEmbedding, topK, conversationId);
    }

    /**
     * 根据查询内容召回相关的长期记忆
     *
     * @param conversationId 会话 ID
     * @param query          查询文本
     * @param topK           返回数量
     * @return 召回的长期记忆内容列表
     */
    public List<String> recallByQuery(String conversationId, String query, int topK) {
        if (conversationId == null || conversationId.isBlank() || query == null || query.isBlank()) {
            return List.of();
        }

        float[] values = embeddingModel.embed(query);
        List<Float> vector = new ArrayList<>(values.length);
        for (float value : values) {
            vector.add(value);
        }

        return parseContents(recall(vector, topK, conversationId));
    }

    /**
     * 解析 Milvus 搜索结果，提取 content 字段
     */
    private List<String> parseContents(SearchResults raw) {
        if (raw == null || raw.getResults() == null) return List.of();

        SearchResultsWrapper wrapper = new SearchResultsWrapper(raw.getResults());
        List<String> contents = new ArrayList<>();
        if (wrapper.getRowRecords(0) == null || wrapper.getRowRecords(0).isEmpty()) return contents;

        for (int i = 0; i < wrapper.getRowRecords(0).size(); i++) {
            Map<String, Object> fields = wrapper.getRowRecords(0).get(i).getFieldValues();
            Object content = fields.get("content");
            if (content != null && !content.toString().isBlank()) {
                contents.add(content.toString());
            }
        }
        return contents;
    }

    /**
     * 生成对话摘要（通过 LLM 提取关键信息）
     *
     * @param conversationHistory 对话历史
     * @return 摘要文本
     */
    public String generateSummary(List<String> conversationHistory) {
        String historyText = String.join("\n", conversationHistory);

        String promptText = String.format("""
                请将以下对话历史总结为简短的摘要，提取关键信息和用户偏好：
                
                对话历史：
                %s
                
                请输出摘要（不超过200字）：
                """, historyText);

        try{
            String summary = chatModel.call(new Prompt(promptText))
                    .getResult()
                    .getOutput()
                    .getText();
            log.info("生成对话摘要成功，长度: {}", summary.length());
            return summary;
        } catch (Exception e) {
            log.error("生成对话摘要失败", e);
            return "对话摘要生成失败";
        }

    }

    /**
     * 计算文本的 SHA-256 哈希值，并以十六进制字符串的形式返回
     */
    private String sha256(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("对话历史转化失败", e);
        }
    }

    private List<Float> toFloatList(float[] vector) {
        List<Float> values = new ArrayList<>(vector.length);
        for (float value : vector) {
            values.add(value);
        }
        return values;
    }
}
