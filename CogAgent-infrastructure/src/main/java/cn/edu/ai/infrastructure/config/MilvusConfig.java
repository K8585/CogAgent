package cn.edu.ai.infrastructure.config;

import io.milvus.client.MilvusServiceClient;
import io.milvus.grpc.DataType;
import io.milvus.param.*;
import io.milvus.param.collection.CreateCollectionParam;
import io.milvus.param.collection.FieldType;
import io.milvus.param.collection.HasCollectionParam;
import io.milvus.param.collection.LoadCollectionParam;
import io.milvus.param.index.CreateIndexParam;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Milvus 配置类
 * 在首次启动时创建文档集合、记忆集合和向量索引
 */
@Slf4j
@Getter
@Configuration
@ConditionalOnProperty(name = "milvus.enabled", havingValue = "true")
public class MilvusConfig {

    @Value("${milvus.host:localhost}")
    private String host;
    @Value("${milvus.port:19530}")
    private int port;
    @Value("${milvus.documents-collection-name:documents}")
    private String documentsCollectionName;
    @Value("${milvus.memories-collection-name:memories}")
    private String memoriesCollectionName;
    @Value("${milvus.dimension:1024}")
    private int dimension;

    @Bean
    public MilvusServiceClient milvusServiceClient() {
        return new MilvusServiceClient(ConnectParam.newBuilder()
                .withHost(host)
                .withPort(port)
                .withConnectTimeout(10, TimeUnit.SECONDS)
                .withKeepAliveTime(55, TimeUnit.SECONDS)
                .withKeepAliveTimeout(20, TimeUnit.SECONDS).build());
    }

    /**
     * 在首次启动时创建文档集合、记忆集合和向量索引
     */
    @Bean
    public Boolean agentMilvusCollections(MilvusServiceClient client) {
        ensureCollection(client, documentsCollectionName, "Agent 文档分块", false);
        ensureCollection(client, memoriesCollectionName, "Agent 长期记忆", true);
        return Boolean.TRUE;
    }

    private void ensureCollection(MilvusServiceClient client, String collectionName, String description, boolean memoryCollection) {
        R<Boolean> exists = client.hasCollection(HasCollectionParam.newBuilder()
                .withCollectionName(collectionName)
                .build());
        if (exists.getStatus() != R.Status.Success.getCode()) {
            throw new IllegalStateException("Milvus 服务异常，无法检查集合: " + collectionName);
        }

        // 如果集合不存在，创建集合和索引
        if (!Boolean.TRUE.equals(exists.getData())) {
            // 创建集合
            R<RpcStatus> created = client.createCollection(CreateCollectionParam.newBuilder()
                    .withCollectionName(collectionName)
                    .withDescription(description)
                    .withShardsNum(2)
                    .withFieldTypes(buildFieldTypes(memoryCollection))
                    .build());
            if (created.getStatus() != R.Status.Success.getCode()) {
                throw new IllegalStateException("无法创建 Milvus 集合, status=" + created.getStatus());
            }

            // 创建索引
            try {
                R<RpcStatus> indexed = client.createIndex(CreateIndexParam.newBuilder()
                        .withCollectionName(collectionName)
                        .withFieldName("embedding")
                        .withIndexType(IndexType.IVF_FLAT)  // 索引类型：IVF_FLAT(倒排索引，先聚类再暴力搜索)
                        .withMetricType(MetricType.COSINE)  // 距离度量：余弦相似度
                        .withExtraParam("{\"nlist\":1024}") // 聚类中心数量
                        .build());
                if (indexed.getStatus() != R.Status.Success.getCode()) {
                    log.warn("Milvus索引创建返回状态 {}; 继续使用已有索引", indexed.getStatus());
                }
            } catch (RuntimeException ex) {
                log.warn("Milvus向量索引可能已存在; 跳过重复创建: {}", ex.toString());
            }
        }

        // 加载集合到内存
        R<RpcStatus> loaded = client.loadCollection(
                LoadCollectionParam.newBuilder()
                        .withCollectionName(collectionName)
                        .build()
        );
        if (loaded.getStatus() != R.Status.Success.getCode()) {
            // 如果已经加载，再次加载会报错
            log.warn("加载 Milvus 集合失败（可能已加载）: {}", loaded.getStatus());
        } else {
            log.info("Milvus 集合 {} 已加载到内存", collectionName);
        }
    }

    private List<FieldType> buildFieldTypes(boolean memoryCollection) {
        List<FieldType> fields = new ArrayList<>(List.of(
                FieldType.newBuilder().withName("chunk_id").withDataType(DataType.VarChar)
                        .withMaxLength(64).withPrimaryKey(true).withAutoID(false).build(),
                FieldType.newBuilder().withName("doc_id").withDataType(DataType.VarChar)
                        .withMaxLength(64).build(),
                FieldType.newBuilder().withName("source").withDataType(DataType.VarChar)
                        .withMaxLength(512).build(),
                FieldType.newBuilder().withName("content").withDataType(DataType.VarChar)
                        .withMaxLength(65535).build()));
        if (memoryCollection) {
            fields.add(FieldType.newBuilder().withName("conversation_id").withDataType(DataType.VarChar)
                    .withMaxLength(128).build());
        }
        fields.add(FieldType.newBuilder().withName("embedding").withDataType(DataType.FloatVector)
                .withDimension(dimension).build());
        return fields;
    }
}
