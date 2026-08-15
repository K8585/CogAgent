package cn.edu.ai.agent.service.document;

import cn.edu.ai.api.dto.DocumentUploadResponse;
import cn.edu.ai.infrastructure.vectordb.InMemoryRagStore;
import cn.edu.ai.infrastructure.vectordb.MilvusService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * ETL 数据管道
 * 文档处理完整流程：
 *   Extract（提取）：使用 DocumentParser 从文件中提取文本
 *   Transform（转换）：使用 DocumentChunker 切分文本为 Chunk
 *   Load（加载）：向量化后存入内存或 Milvus
 */
@Slf4j
@Service
public class ETLPipeline {

    private final DocumentParser documentParser;
    private final DocumentChunker documentChunker;
    private final InMemoryRagStore ragStore;
    private final ObjectProvider<MilvusService> milvusProvider; // 可选依赖: 调用时才获取 MilvusService 这个 Bean
    private final EmbeddingModel embeddingModel;
    private final boolean milvusEnabled;

    public ETLPipeline(DocumentParser documentParser, DocumentChunker documentChunker,
                       InMemoryRagStore ragStore, ObjectProvider<MilvusService> milvusProvider,
                       @Qualifier("qwenEmbeddingModel") EmbeddingModel embeddingModel,
                       @Value("${milvus.enabled:false}") boolean milvusEnabled) {
        this.documentParser = documentParser;
        this.documentChunker = documentChunker;
        this.ragStore = ragStore;
        this.milvusProvider = milvusProvider;
        this.embeddingModel = embeddingModel;
        this.milvusEnabled = milvusEnabled;
    }

    /**
     * 执行完整的 ETL 流程
     *
     * @param file 上传的文件
     * @return 处理结果
     */
    public DocumentUploadResponse process(MultipartFile file) {
        String documentId = UUID.randomUUID().toString().replace("-", "");
        String source = file.getOriginalFilename() == null ? "upload" : file.getOriginalFilename();
        // 提取文本
        String text = documentParser.parse(file);
        if(text.isEmpty()) throw new IllegalArgumentException("文档内容为空，无法处理");
        // 切分为 Chunk
        List<DocumentChunker.Chunk> chunks = documentChunker.chunk(text, documentId);
        if (chunks.isEmpty()) throw new IllegalArgumentException("文档切片结果为空");
        // 向量化, 存入 Milvus 或内存中
        if(milvusEnabled) {
            MilvusService milvus = milvusProvider.getIfAvailable();
            if(milvus == null) throw new IllegalStateException("Milvus 已启用但服务未装配");

            List<String> ids = new ArrayList<>();
            List<String> documentIds = new ArrayList<>();
            List<String> sources = new ArrayList<>();
            List<String> contents = new ArrayList<>();
            List<List<Float>> vectors = new ArrayList<>();

            for(DocumentChunker.Chunk chunk : chunks) {
                ids.add(chunk.getId());
                documentIds.add(chunk.getDocumentId());
                sources.add(source);
                contents.add(chunk.getContent());
                vectors.add(toFloatList(embeddingModel.embed(chunk.getContent())));
            }

            milvus.insertVectors(ids, documentIds, sources, vectors, contents);
        } else {
            chunks.forEach(c -> ragStore.upsert(c.getId(), c.getDocumentId(), source, c.getContent()));
        }

        return DocumentUploadResponse.builder()
                .documentId(documentId)
                .fileName(source)
                .chunkCount(chunks.size())
                .status("completed")
                .build();
    }

    private List<Float> toFloatList(float[] vector) {
        if(vector == null || vector.length == 0) {
            throw new IllegalStateException("Embedding 模型返回空向量");
        }
        List<Float> result = new ArrayList<Float>(vector.length);
        for(float value : vector) {
            result.add(value);
        }
        return result;
    }
}
