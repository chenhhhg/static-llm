package bupt.staticllm.web.service.impl;

import bupt.staticllm.web.service.RagService;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.query.Query;
import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class SimpleRagServiceImpl implements RagService {

    private EmbeddingStore<TextSegment> embeddingStore;
    private EmbeddingModel embeddingModel;
    private ContentRetriever retriever;

    @PostConstruct
    public void init() {
        // 1. 初始化 Embedding 模型 (会自动下载模型文件到本地缓存)
        this.embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();
        
        // 2. 初始化 内存向量库
        this.embeddingStore = new InMemoryEmbeddingStore<>();
        
        // 4. 构建检索器
        this.retriever = EmbeddingStoreContentRetriever.builder()
                .embeddingStore(embeddingStore)
                .embeddingModel(embeddingModel)
                .maxResults(2) // 每次返回最相关的2条
                .minScore(0.6)
                .build();
    }

    @Override
    public String retrieve(String query) {
        try {
            List<Content> contents = retriever.retrieve(Query.from(query));
            if (contents.isEmpty()) return "";
            
            return contents.stream()
                    .map(c -> c.textSegment().text())
                    .collect(Collectors.joining("\n---\n"));
        } catch (Exception e) {
            log.error("RAG 检索失败", e);
            return "";
        }
    }

    @Override
    public void addDocument(String content, String title) {
        Document document = Document.from(content);
        document.metadata().put("title", title);
        DocumentSplitter splitter = DocumentSplitters.recursive(300, 0);
        EmbeddingStoreIngestor ingestor = EmbeddingStoreIngestor.builder()
                .documentSplitter(splitter)
                .embeddingModel(embeddingModel)
                .embeddingStore(embeddingStore)
                .build();
        ingestor.ingest(document);
    }

    @Override
    public void addDocument(String content, Long knowledgeId) {
        Document document = Document.from(content);
        document.metadata().put("knowledge_id", String.valueOf(knowledgeId));
        DocumentSplitter splitter = DocumentSplitters.recursive(300, 0);
        EmbeddingStoreIngestor ingestor = EmbeddingStoreIngestor.builder()
                .documentSplitter(splitter)
                .embeddingModel(embeddingModel)
                .embeddingStore(embeddingStore)
                .build();
        ingestor.ingest(document);
    }

    @Override
    public void removeDocument(Long knowledgeId) {
        embeddingStore.removeAll(metadataKey("knowledge_id").isEqualTo(String.valueOf(knowledgeId)));
    }
}
