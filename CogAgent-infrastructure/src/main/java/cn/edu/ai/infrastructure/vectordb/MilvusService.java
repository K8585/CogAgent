package cn.edu.ai.infrastructure.vectordb;

import cn.edu.ai.infrastructure.config.MilvusConfig;
import io.milvus.client.MilvusServiceClient;
import io.milvus.grpc.MutationResult;
import io.milvus.grpc.SearchResults;
import io.milvus.param.R;
import io.milvus.param.dml.InsertParam;
import io.milvus.param.dml.SearchParam;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Milvus 向量数据库服务
 * 封装向量的插入、搜索、删除等操作
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "milvus.enabled", havingValue = "true")
@DependsOn("agentDocumentsCollection")
public class MilvusService {

    private final MilvusServiceClient milvusClient;
    private final MilvusConfig milvusConfig;

    /**
     * 插入向量数据
     *
     * @param ids        文档 ID 列表
     * @param vectors    向量数据列表
     * @param contents   原始文本列表
     * @return 插入数量
     */
    public long insertVectors(List<String>ids, List<List<Float>> vectors, List<String> contents) {
        return insertVectors(ids, ids, Collections.nCopies(ids.size(), "upload"), vectors, contents);
    }

    public long insertVectors(List<String> chunkIds, List<String> documentIds,
                              List<String> sources, List<List<Float>> vectors, List<String> contents) {
        List<InsertParam.Field> fields = new ArrayList<>();
        fields.add(new InsertParam.Field("chunk_id", chunkIds));
        fields.add(new InsertParam.Field("doc_id", documentIds));
        fields.add(new InsertParam.Field("source", sources));
        fields.add(new InsertParam.Field("content", contents));
        fields.add(new InsertParam.Field("embedding", vectors));

        InsertParam insertParam = InsertParam.newBuilder()
                .withCollectionName(milvusConfig.getCollectionName())
                .withFields(fields)
                .build();

        R<MutationResult> response = milvusClient.insert(insertParam);

        if (response.getStatus() != R.Status.Success.getCode()) {
            log.error("Milvus 向量插入失败: {}", response.getMessage());
            throw new RuntimeException("向量插入失败: " + response.getMessage());
        }

        long count = response.getData().getInsertCnt();
        log.info("成功插入 {} 条向量到集合 {}", count, milvusConfig.getCollectionName());
        return count;
    }

    /**
     * 向量相似度搜索
     *
     * @param queryVector 查询向量
     * @param topK        返回结果数量
     * @return 搜索结果
     */
    public SearchResults searchSimilar(List<Float> queryVector, int topK) {
        SearchParam searchParam = SearchParam.newBuilder()
                .withCollectionName(milvusConfig.getCollectionName())
                .withMetricType(io.milvus.param.MetricType.COSINE)
                .withOutFields(List.of("doc_id", "content"))    // 除了返回的向量本身，附带的两个返回值
                .withTopK(topK)
                .withVectors(Collections.singletonList(queryVector))
                .withVectorFieldName("embedding")               // 指定 Collection 中存储向量的字段名
                .withParams("{\"nprobe\": 16}")                 // 值越大召回率越高，但速度越慢
                .build();

        R<SearchResults> response = milvusClient.search(searchParam);

        if (response.getStatus() != R.Status.Success.getCode()) {
            log.error("Milvus 向量搜索失败: {}", response.getMessage());
            throw new RuntimeException("向量搜索失败: " + response.getMessage());
        }

        log.debug("向量搜索完成，返回 {} 条结果", topK);
        return response.getData();
    }



}
