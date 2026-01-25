package bupt.staticllm.web.service;

import bupt.staticllm.web.model.Knowledge;
import com.baomidou.mybatisplus.extension.service.IService;

public interface KnowledgeService extends IService<Knowledge> {
    
    /**
     * 添加知识
     * @param title 标题
     * @param content 内容
     */
    void addKnowledge(String title, String content);

    /**
     * 更新知识
     * @param id ID
     * @param title 标题
     * @param content 内容
     */
    void updateKnowledge(Long id, String title, String content);

    /**
     * 删除知识
     * @param id ID
     */
    void deleteKnowledge(Long id);
}
