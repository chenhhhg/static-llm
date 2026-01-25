package bupt.staticllm.web.controller;

import bupt.staticllm.common.response.Result;
import bupt.staticllm.web.model.Knowledge;
import bupt.staticllm.web.service.KnowledgeService;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeController {

    @Autowired
    private KnowledgeService knowledgeService;

    @GetMapping("/list")
    public Result<List<Knowledge>> list() {
        return Result.success(knowledgeService.list());
    }

    @GetMapping("/{id}")
    public Result<Knowledge> get(@PathVariable Long id) {
        return Result.success(knowledgeService.getById(id));
    }

    @PostMapping("/add")
    public Result<Void> addKnowledge(@RequestBody KnowledgeRequest request) {
        if (request.getContent() == null || request.getContent().isEmpty()) {
            return Result.fail("文档内容不能为空");
        }
        
        try {
            knowledgeService.addKnowledge(request.getTitle(), request.getContent());
            return Result.success();
        } catch (Exception e) {
            log.error("添加知识失败", e);
            return Result.fail("添加知识失败: " + e.getMessage());
        }
    }

    @PutMapping("/update/{id}")
    public Result<Void> updateKnowledge(@PathVariable Long id, @RequestBody KnowledgeRequest request) {
        try {
            knowledgeService.updateKnowledge(id, request.getTitle(), request.getContent());
            return Result.success();
        } catch (Exception e) {
            log.error("更新知识失败", e);
            return Result.fail("更新知识失败: " + e.getMessage());
        }
    }

    @DeleteMapping("/delete/{id}")
    public Result<Void> deleteKnowledge(@PathVariable Long id) {
        try {
            knowledgeService.deleteKnowledge(id);
            return Result.success();
        } catch (Exception e) {
            log.error("删除知识失败", e);
            return Result.fail("删除知识失败: " + e.getMessage());
        }
    }

    @Data
    public static class KnowledgeRequest {
        private String title;
        private String content;
    }
}
