package bupt.staticllm.web.service;

public interface RagService {

    /**
     * 检索相关知识
     * @param query 查询语句
     * @return 相关文档片段
     */
    String retrieve(String query);

    /**
     * 添加文档到知识库
     * @param content 文档内容
     * @param title 文档标题 (作为元数据)
     */
    void addDocument(String content, String title);

    /**
     * 添加文档到知识库 (带ID)
     * @param content 文档内容
     * @param knowledgeId 知识ID
     */
    void addDocument(String content, Long knowledgeId);

    /**
     * 从知识库移除文档
     * @param knowledgeId 知识ID
     */
    void removeDocument(Long knowledgeId);
}
