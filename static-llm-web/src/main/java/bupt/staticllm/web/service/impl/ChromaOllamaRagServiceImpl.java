package bupt.staticllm.web.service.impl;

import bupt.staticllm.web.service.RagService;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import dev.langchain4j.store.embedding.chroma.ChromaEmbeddingStore;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.query.Query;
import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.context.annotation.Primary;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@Primary // 标记为首选实现，替代 SimpleRagServiceImpl
public class ChromaOllamaRagServiceImpl implements RagService {

    @Value("${rag.chroma.base-url}")
    private String chromaBaseUrl;

    @Value("${rag.chroma.collection-name}")
    private String collectionName;

    @Value("${rag.ollama.base-url}")
    private String ollamaBaseUrl;

    @Value("${rag.ollama.embedding-model}")
    private String ollamaModelName;

    private EmbeddingStore<TextSegment> embeddingStore;
    private EmbeddingModel embeddingModel;
    private ContentRetriever retriever;

    @PostConstruct
    public void init() {
        log.info("初始化 RAG 组件: Chroma@{}, Ollama@{}", chromaBaseUrl, ollamaBaseUrl);

        try {
            // 1. 初始化 Embedding 模型 (调用 Ollama)
            this.embeddingModel = OllamaEmbeddingModel.builder()
                    .baseUrl(ollamaBaseUrl)
                    .modelName(ollamaModelName)
                    .timeout(Duration.ofSeconds(60))
                    .build();

            // 2. 初始化 Chroma 向量库
            this.embeddingStore = ChromaEmbeddingStore.builder()
                    .baseUrl(chromaBaseUrl)
                    .collectionName(collectionName)
                    .logRequests(true)
                    .logResponses(true)
                    .build();

            // 3. 构建检索器
            this.retriever = EmbeddingStoreContentRetriever.builder()
                    .embeddingStore(embeddingStore)
                    .embeddingModel(embeddingModel)
                    .maxResults(3) // 稍微增加返回数量，给大模型更多参考
                    .minScore(0.5) // 调整阈值
                    .build();
            
        } catch (Exception e) {
            log.error("RAG 组件初始化失败，请检查 Chroma 和 Ollama 是否已启动", e);
        }
    }

    @Override
    public String retrieve(String query) {
        if (retriever == null) {
            log.warn("RAG 检索器未初始化");
            return "";
        }
        
        try {
            log.info("RAG 检索 Query: {}", query);
            List<Content> contents = retriever.retrieve(Query.from(query));
            
            if (contents.isEmpty()) {
                log.info("未检索到相关知识");
                return "";
            }
            
            String result = contents.stream()
                    .map(c -> c.textSegment().text())
                    .collect(Collectors.joining("\n---\n"));
            
            log.info("检索到 {} 条相关知识", contents.size());
            return result;
        } catch (Exception e) {
            log.error("RAG 检索失败", e);
            return ""; // 降级处理
        }
    }

    @Override
    public void addDocument(String content, String title) {
        try {
            log.info("正在添加文档: {}", title);
            Document document = Document.from(content);
            if (title != null && !title.isEmpty()) {
                document.metadata().put("title", title);
            }

            // 使用 Token 切分器 (Recursive)
            // 500 tokens chunk size, 50 tokens overlap
            DocumentSplitter splitter = DocumentSplitters.recursive(500, 50);

            EmbeddingStoreIngestor ingestor = EmbeddingStoreIngestor.builder()
                    .documentSplitter(splitter)
                    .embeddingModel(embeddingModel)
                    .embeddingStore(embeddingStore)
                    .build();

            ingestor.ingest(document);
            log.info("文档添加成功: {}", title);
        } catch (Exception e) {
            log.error("添加文档失败", e);
            throw new RuntimeException("添加文档失败: " + e.getMessage());
        }
    }

    @Override
    public void addDocument(String content, Long knowledgeId) {
        try {
            log.info("正在添加文档 ID: {}", knowledgeId);
            Document document = Document.from(content);
            document.metadata().put("knowledge_id", String.valueOf(knowledgeId));

            // 使用 Token 切分器 (Recursive)
            DocumentSplitter splitter = DocumentSplitters.recursive(500, 50);

            EmbeddingStoreIngestor ingestor = EmbeddingStoreIngestor.builder()
                    .documentSplitter(splitter)
                    .embeddingModel(embeddingModel)
                    .embeddingStore(embeddingStore)
                    .build();

            ingestor.ingest(document);
            log.info("文档添加成功 ID: {}", knowledgeId);
        } catch (Exception e) {
            log.error("添加文档失败", e);
            throw new RuntimeException("添加文档失败: " + e.getMessage());
        }
    }

    @Override
    public void removeDocument(Long knowledgeId) {
        try {
            log.info("正在移除文档 ID: {}", knowledgeId);
            embeddingStore.removeAll(metadataKey("knowledge_id").isEqualTo(String.valueOf(knowledgeId)));
            log.info("文档移除成功 ID: {}", knowledgeId);
        } catch (Exception e) {
            log.error("移除文档失败", e);
            throw new RuntimeException("移除文档失败: " + e.getMessage());
        }
    }
}