package bupt.staticllm.web.service.impl;

import bupt.staticllm.web.mapper.KnowledgeMapper;
import bupt.staticllm.web.model.Knowledge;
import bupt.staticllm.web.service.KnowledgeService;
import bupt.staticllm.web.service.RagService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class KnowledgeServiceImpl extends ServiceImpl<KnowledgeMapper, Knowledge> implements KnowledgeService {

    @Autowired
    private RagService ragService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addKnowledge(String title, String content) {
        // 1. 保存到数据库
        Knowledge knowledge = new Knowledge();
        knowledge.setTitle(title);
        knowledge.setContent(content);
        this.save(knowledge);

        // 2. 同步到向量库
        ragService.addDocument(content, knowledge.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateKnowledge(Long id, String title, String content) {
        // 1. 更新数据库
        Knowledge knowledge = this.getById(id);
        if (knowledge == null) {
            throw new RuntimeException("Knowledge not found: " + id);
        }
        knowledge.setTitle(title);
        knowledge.setContent(content);
        this.updateById(knowledge);

        // 2. 更新向量库 (先删后加)
        ragService.removeDocument(id);
        ragService.addDocument(content, id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteKnowledge(Long id) {
        // 1. 删除数据库
        this.removeById(id);

        // 2. 删除向量库
        ragService.removeDocument(id);
    }
}
