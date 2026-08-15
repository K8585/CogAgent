package cn.edu.ai.agent.service.rag;

import cn.edu.ai.infrastructure.vectordb.InMemoryRagStore;
import cn.edu.ai.infrastructure.vectordb.MilvusService;
import io.milvus.grpc.SearchResults;
import io.milvus.response.SearchResultsWrapper;
import lombok.Builder;
import lombok.Data;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 多路检索引擎
 * 支持多种检索策略并行执行，合并结果后统一排序
 *   向量语义检索：通过 Milvus 或本地内存进行余弦相似度搜索
 *   关键词检索：基于 BM25 或简单关键词匹配
 *   缓存层：高频查询结果缓存，避免重复检索
 */
@Service
public class MultiRetriever {

    private final InMemoryRagStore memoryRagStore;
    private final ObjectProvider<MilvusService> milvusProvider;
    private final EmbeddingModel embeddingModel;
    private final int topK;
    private final double threshold;
    private final boolean milvusEnabled;

    public MultiRetriever(InMemoryRagStore memoryRagStore, ObjectProvider<MilvusService> milvusProvider,
                          @Qualifier("qwenEmbeddingModel") EmbeddingModel embeddingModel,
                          @Value("${agent.rag.top-k:5}") int topK,
                          @Value("${agent.rag.similarity-threshold:0.35}") double threshold,
                          @Value("${milvus.enabled:false}") boolean milvusEnabled) {
        this.memoryRagStore = memoryRagStore;
        this.milvusProvider = milvusProvider;
        this.embeddingModel = embeddingModel;
        this.topK = topK;
        this.threshold = threshold;
        this.milvusEnabled = milvusEnabled;
    }

    public List<RetrievalResult> retrieve(String query){
        if(query == null) return List.of();
        if(milvusEnabled) return retrieveFromMilvus(query);
        return memoryRagStore.search(query, topK, threshold).stream()
                .map(r -> RetrievalResult.builder()
                        .documentId(r.getDocumentId())
                        .content(r.getContent())
                        .score(r.getScore())
                        .source("qwen-memory")
                        .build())
                .toList();
    }

    /**
     * Milvus 执行多路检索或内存执行检索
     */
    private List<RetrievalResult> retrieveFromMilvus(String query) {
        MilvusService milvus = milvusProvider.getIfAvailable();
        if (milvus == null) return List.of();

        float[] values = embeddingModel.embed(query);
        List<Float> vector = new ArrayList<>(values.length);
        for(float value : values) {
            vector.add(value);
        }
        SearchResults raw = milvus.searchSimilar(vector, topK);

        // 包装 Milvus 返回的原始结果
        SearchResultsWrapper wrapper = new SearchResultsWrapper(raw.getResults());
        List<RetrievalResult> results = new ArrayList<>();
        // 0 代表第 1 个查询向量(这里就是指query), 返回其查询结果列表
        if(wrapper.getRowRecords(0) == null) {
            return results;
        }

        for(int i = 0; i < wrapper.getRowRecords(0).size(); i++) {
            // 获取第 i 个结果的 ID 和分数
            SearchResultsWrapper.IDScore score = wrapper.getIDScore(0).get(i);
            // 获取第 i 个结果的所有字段值
            Map<String, Object> fields = wrapper.getRowRecords(0).get(i).getFieldValues();
            if(score.getScore() < threshold) continue;
            // 构建 RetrievalResult 对象
            results.add(RetrievalResult.builder()
                    .documentId(String.valueOf(fields.getOrDefault("doc_id", "")))
                    .content(String.valueOf(fields.getOrDefault("content","")))
                    .score(score.getScore())
                    .source("qwen-milvus")
                    .build());
        }
        return results;
    }

    @Data
    @Builder
    public static class RetrievalResult {
        private String documentId;
        private String content;
        private double score;
        private String source;
    }


}
