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
        
        // 显式设置时间，作为自动填充失效时的兜底
        task.setCreatedTime(LocalDateTime.now());
        task.setUpdatedTime(LocalDateTime.now());
        
        this.save(task);
        
        // 触发异步处理
        processTaskAsync(task);
        
        return task.getId();
    }

    @Override
    public boolean cancelTask(Long taskId) {
        AnalysisTask task = this.getById(taskId);
        if (task == null) {
            return false;
        }
        // 6之前任意阶段可取消
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
            
            // 从 Map 中获取参数
            Map<String, Object> params = task.getTaskParams();
            String targetJar = (String) params.get("targetJar");
            String sourcePath = (String) params.get("sourcePath");
            String packageFilter = (String) params.get("packageFilter");

            String reportFile = spotBugsAdapter.executeAnalysis(targetJar, sourcePath, packageFilter);
            if (isCancelled(task.getId())) return;
            
            // 归一化
            List<UnifiedIssue> issues = normalizer.normalize(reportFile);
            
            // 上下文提取
            log.info("开始提取源码上下文...");
            contextExtractor.enrichContext(issues, sourcePath);
            
            updateTaskStatus(task.getId(), TaskStatus.WAITING_LLM);
            if (isCancelled(task.getId())) return;

            updateTaskStatus(task.getId(), TaskStatus.JUDGING);
            
            String promptContext = JSON.toJSONString(issues);
            String systemPrompt = "你是一个代码审计专家。以下是静态分析工具发现的问题列表（JSON格式），每个问题都包含'codeSnippet'字段，其中提供了：\n" +
                    "1. 出错类的重要字段\n" +
                    "2. 出错方法的完整代码 (Level 0)\n" +
                    "3. 该方法内部调用的其他方法的签名 (Level +1)\n\n" +
                    "请根据这些上下文信息，仔细分析问题是否为误报，并给出修复建议。";
            
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
