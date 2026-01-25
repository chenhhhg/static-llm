package bupt.staticllm.web.service.impl;

import bupt.staticllm.adapter.analysis.StaticAnalysisService;
import bupt.staticllm.adapter.llm.LlmClient;
import bupt.staticllm.common.enums.TaskStatus;
import bupt.staticllm.common.model.AnalysisTask;
import bupt.staticllm.common.model.UnifiedIssue;
import bupt.staticllm.core.extractor.ContextExtractor;
import bupt.staticllm.core.normalizer.SpotBugsNormalizer;
import bupt.staticllm.web.mapper.AnalysisTaskMapper;
import bupt.staticllm.web.model.request.FileAnalysisRequest;
import bupt.staticllm.web.service.AnalysisTaskService;
import bupt.staticllm.web.service.RagService;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class AnalysisTaskServiceImpl extends ServiceImpl<AnalysisTaskMapper, AnalysisTask> implements AnalysisTaskService {

    @Autowired
    private StaticAnalysisService spotBugsAdapter;

    @Autowired
    private SpotBugsNormalizer normalizer;
    
    @Autowired
    private ContextExtractor contextExtractor;
    
    @Autowired
    private RagService ragService; // 注入 RAG 服务

    @Autowired
    private LlmClient llmClient;

    @Override
    public Long submitTask(FileAnalysisRequest request) {
        AnalysisTask task = new AnalysisTask();
        // 将 Request 对象转换为 Map 存储
        Map<String, Object> params = JSON.parseObject(JSON.toJSONString(request), new TypeReference<Map<String, Object>>(){});
        task.setTaskParams(params);
        task.setToolName("SpotBugs");
        task.setLlmModel("DeepSeek");
        task.setStatus(TaskStatus.SUBMITTED);
        
        task.setCreatedTime(LocalDateTime.now());
        task.setUpdatedTime(LocalDateTime.now());
        
        this.save(task);
        
        processTaskAsync(task);
        
        return task.getId();
    }

    @Override
    public boolean cancelTask(Long taskId) {
        AnalysisTask task = this.getById(taskId);
        if (task == null) {
            return false;
        }
        if (task.getStatus().getCode() < TaskStatus.COMPLETED.getCode()) {
            task.setStatus(TaskStatus.CANCELLED);
            this.updateById(task);
            return true;
        }
        return false;
    }

    @Async
    @Override
    public void processTaskAsync(AnalysisTask task) {
        log.info("开始执行异步任务 TaskID: {}", task.getId());
        
        try {
            if (isCancelled(task.getId())) return;

            updateTaskStatus(task.getId(), TaskStatus.WAITING_ANALYSIS);
            if (isCancelled(task.getId())) return;

            updateTaskStatus(task.getId(), TaskStatus.ANALYZING);
            
            Map<String, Object> params = task.getTaskParams();
            String targetJar = (String) params.get("targetJar");
            String sourcePath = (String) params.get("sourcePath");
            String packageFilter = (String) params.get("packageFilter");

            String reportFile = spotBugsAdapter.executeAnalysis(targetJar, sourcePath, packageFilter);
            if (isCancelled(task.getId())) return;
            
            List<UnifiedIssue> issues = normalizer.normalize(reportFile);
            
            log.info("开始提取源码上下文...");
            contextExtractor.enrichContext(issues, sourcePath);
            
            // --- RAG 增强步骤 ---
            log.info("开始进行 RAG 知识检索...");
            for (UnifiedIssue issue : issues) {
                // 仅对 High 级别的或者特定规则进行检索，避免 API 调用过多（如果是本地 Embedding 则无所谓）
                String query = "Fix pattern for " + issue.getRuleId() + " in Java";
                String knowledge = ragService.retrieve(query);
                if (knowledge != null && !knowledge.isEmpty()) {
                    // 将知识附加到 issue 的描述或临时字段中，或者拼接在 CodeSnippet 后
                    // 这里简单起见，追加到 CodeSnippet
                    issue.setCodeSnippet(issue.getCodeSnippet() + "\n\n// [RAG Knowledge Base Reference]\n" + knowledge);
                }
            }
            
            updateTaskStatus(task.getId(), TaskStatus.WAITING_LLM);
            if (isCancelled(task.getId())) return;

            updateTaskStatus(task.getId(), TaskStatus.JUDGING);
            
            String promptContext = JSON.toJSONString(issues);
            String systemPrompt = "你是一个代码审计专家。以下是静态分析工具发现的问题列表（JSON格式）。\n" +
                    "每个问题包含了：\n" +
                    "1. 源代码上下文 (Class/Method)\n" +
                    "2. [RAG Knowledge Base Reference] 附带的修复建议知识库文档\n\n" +
                    "请结合这些信息，分析问题是否误报，并给出最佳修复建议。";
            
            String llmResponse = llmClient.chat(systemPrompt, promptContext);
            if (isCancelled(task.getId())) return;

            task = this.getById(task.getId());
            task.setStatus(TaskStatus.COMPLETED);
            task.setResultSummary(llmResponse);
            this.updateById(task);

        } catch (Exception e) {
            log.error("任务执行失败 TaskID: " + task.getId(), e);
            updateTaskStatus(task.getId(), TaskStatus.FAILED);
            AnalysisTask t = this.getById(task.getId());
            if (t != null) {
                t.setResultSummary("执行失败: " + e.getMessage());
                this.updateById(t);
            }
        }
    }

    private boolean isCancelled(Long taskId) {
        AnalysisTask task = this.getById(taskId);
        if (task != null && task.getStatus() == TaskStatus.CANCELLED) {
            log.info("任务已取消 TaskID: {}", taskId);
            return true;
        }
        return false;
    }

    private void updateTaskStatus(Long taskId, TaskStatus status) {
        AnalysisTask task = new AnalysisTask();
        task.setId(taskId);
        task.setStatus(status);
        this.updateById(task);
    }
}
