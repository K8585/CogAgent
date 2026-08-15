package cn.edu.ai.infrastructure.vectordb;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 本地内存实现的向量数据库服务
 * 封装向量的插入、搜索、删除等操作
 */
@Service
public class InMemoryRagStore {

    private final EmbeddingModel qwenEmbeddingModel;
    private final Map<String, Entry> entries = new ConcurrentHashMap<>();

    public InMemoryRagStore(@Qualifier("qwenEmbeddingModel") EmbeddingModel qwenEmbeddingModel) {
        this.qwenEmbeddingModel = qwenEmbeddingModel;
    }

    /**
     * 将集合加载进内存
     */
    public void upsert(String id, String documentId, String source, String content) {
        if(content == null || content.isEmpty()) return;
        entries.put(id, new Entry(id, documentId, source, content, qwenEmbeddingModel.embed(content)));
    }

    public List<SearchResult> search(String query, int topK, double threshold) {
        if(query == null || query.isEmpty() || entries.isEmpty()) return List.of();
        float[] vector = qwenEmbeddingModel.embed(query);
        return entries.values().stream()
                .map(e -> new SearchResult(e.getId(),e.getDocumentId(),e.getSource(),e.getContent(),cosine(vector,e.getVector())))
                .filter(r -> r.getScore() >= threshold)
                .sorted(Comparator.comparingDouble(SearchResult::getScore).reversed())
                .limit(topK)
                .collect(Collectors.toList());
    }

    // 求余弦相似度 cos(θ) = (A · B) / (||A|| × ||B||)
    private double cosine(float[] a, float[] b) {
        if (a == null || b == null || a.length == 0 || a.length != b.length) return 0;
        double dot = 0, na = 0, nb = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];     // 用坐标公式求点积
            na += a[i] * a[i];
            nb += b[i] * b[i];
        }
        return na == 0 || nb == 0 ? 0 : dot / (Math.sqrt(na) * Math.sqrt(nb));
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Entry {
        private String id;
        private String documentId;
        private String source;
        private String content;
        private float[] vector;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SearchResult {
        private String id;
        private String documentId;
        private String source;
        private String content;
        private double score;
    }
}
