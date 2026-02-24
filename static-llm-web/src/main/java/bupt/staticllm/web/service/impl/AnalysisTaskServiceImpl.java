package bupt.staticllm.web.service.impl;

import bupt.staticllm.adapter.analysis.StaticAnalysisService;
import bupt.staticllm.common.enums.TaskStatus;
import bupt.staticllm.common.model.AnalysisTask;
import bupt.staticllm.common.model.UnifiedIssue;
import bupt.staticllm.core.extractor.ContextExtractor;
import bupt.staticllm.core.normalizer.SpotBugsNormalizer;
import bupt.staticllm.web.context.AnalysisContextHolder;
import bupt.staticllm.web.llm.agent.CodeAuditAgent;
import bupt.staticllm.common.model.AnalysisIssue;
import bupt.staticllm.web.llm.model.AuditResult;
import bupt.staticllm.web.mapper.AnalysisIssueMapper;
import java.util.ArrayList;

import bupt.staticllm.common.model.AnalysisCache;
import bupt.staticllm.web.mapper.AnalysisCacheMapper;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

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
    private CodeAuditAgent codeAuditAgent;

    @Autowired
    private AnalysisIssueMapper analysisIssueMapper;

    @Autowired
    private AnalysisCacheMapper analysisCacheMapper;

    @Autowired
    @org.springframework.context.annotation.Lazy
    private AnalysisTaskService self;

    // 创建一个固定大小的线程池用于并发执行 LLM 审计任务
    private final ExecutorService auditExecutor = Executors.newFixedThreadPool(10);

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
        
        // 使用自我注入的 Bean 调用异步方法，确保 AOP 代理生效
        self.processTaskAsync(task);
        
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

            // --- 秒传机制 Start ---
            String reportFile = null;
            String jarMd5 = null;
            try {
                jarMd5 = calculateMd5(new File(targetJar));
                AnalysisCache cache = analysisCacheMapper.selectOne(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AnalysisCache>()
                        .eq(AnalysisCache::getFileMd5, jarMd5)
                );
                
                if (cache != null) {
                    File cachedReport = new File(cache.getReportPath());
                    if (cachedReport.exists()) {
                        log.info("命中分析缓存，Jar MD5: {}, 报告路径: {}", jarMd5, cache.getReportPath());
                        reportFile = cache.getReportPath();
                    } else {
                        log.warn("分析缓存命中但文件不存在，将重新分析。MD5: {}", jarMd5);
                        analysisCacheMapper.deleteById(cache.getId());
                    }
                }
            } catch (Exception e) {
                log.error("计算MD5或查询缓存失败，将继续执行常规分析", e);
            }
            // --- 秒传机制 End ---

            if (reportFile == null) {
                reportFile = spotBugsAdapter.executeAnalysis(targetJar, sourcePath, packageFilter);
                
                // 保存缓存
                if (jarMd5 != null && reportFile != null) {
                    try {
                        AnalysisCache newCache = new AnalysisCache();
                        newCache.setFileMd5(jarMd5);
                        newCache.setReportPath(reportFile);
                        analysisCacheMapper.insert(newCache);
                    } catch (Exception e) {
                        log.warn("保存分析缓存失败", e);
                    }
                }
            }

            if (isCancelled(task.getId())) return;
            
            List<UnifiedIssue> issues = normalizer.normalize(reportFile);
            
            log.info("开始提取源码上下文...");
            contextExtractor.enrichContext(issues, sourcePath);
            
            // --- RAG 增强步骤 ---
            log.info("开始进行 RAG 知识检索...");
            
            // 准备入库 Issue 列表
            List<AnalysisIssue> dbIssues = new ArrayList<>();

            for (UnifiedIssue issue : issues) {
                // 1. RAG 检索
                String query = "Fix pattern for " + issue.getRuleId() + " in Java";
                String knowledge = ragService.retrieve(query);
                if (knowledge != null && !knowledge.isEmpty()) {
                     issue.setCodeSnippet(issue.getCodeSnippet() + "\n\n// [RAG Knowledge Base Reference]\n" + knowledge);
                }

                // 2. 转换为 DB 实体并保存
                AnalysisIssue dbIssue = new AnalysisIssue();
                dbIssue.setTaskId(task.getId());
                dbIssue.setToolName(issue.getToolName());
                dbIssue.setRuleId(issue.getRuleId());
                dbIssue.setSeverity(issue.getSeverity());
                dbIssue.setFilePath(issue.getFilePath());
                dbIssue.setStartLine(issue.getStartLine());
                dbIssue.setEndLine(issue.getEndLine());
                dbIssue.setMessage(issue.getMessage());
                dbIssue.setCodeSnippet(issue.getCodeSnippet());
                
                analysisIssueMapper.insert(dbIssue);
                dbIssues.add(dbIssue);
            }
            
            updateTaskStatus(task.getId(), TaskStatus.WAITING_LLM);
            if (isCancelled(task.getId())) return;

            updateTaskStatus(task.getId(), TaskStatus.JUDGING);
            
            // 设置上下文路径，供 Tool 使用 (主线程设置一次，虽然并发任务里也会设置，但为了保险)
            AnalysisContextHolder.setSourcePath(sourcePath);

            dbIssues = List.of(dbIssues.get(0));

            try {
                // 3. 并发调用 Agent 获取结构化结果
                AnalysisTask finalTask = task;
                List<CompletableFuture<Void>> futures = dbIssues.stream()
                    .map(dbIssue -> CompletableFuture.runAsync(() -> {
                        try {
                            // 在子线程中设置上下文路径
                            AnalysisContextHolder.setSourcePath(sourcePath);
                            
                            // 构建单个 Issue 的 Prompt
                            List<Map<String, Object>> promptIssues = new ArrayList<>();
                            promptIssues.add(Map.of(
                                "issueId", dbIssue.getId(),
                                "ruleId", dbIssue.getRuleId(),
                                "filePath", dbIssue.getFilePath(),
                                "startLine", dbIssue.getStartLine(),
                                "codeSnippet", dbIssue.getCodeSnippet()
                            ));

                            String promptContext = JSON.toJSONString(promptIssues);
                            String userMessage = "请分析以下静态分析问题（JSON格式）：\n" +
                                    promptContext + "\n\n" +
                                    "该问题包含了源代码片段和可能的修复建议。请务必使用工具读取文件确认上下文。\n" +
                                    "请返回符合 AuditResult 结构的 JSON 数据。";
                            
                            // 调用 Agent
                            AuditResult result = codeAuditAgent.audit(userMessage);
                            
                            if (isCancelled(finalTask.getId())) return;

                            // 4. 更新数据库
                            if (result != null && result.getAnalyses() != null) {
                                for (AuditResult.IssueAnalysis analysis : result.getAnalyses()) {
                                    // 理论上只会有一个，但为了健壮性还是遍历
                                    AnalysisIssue issueToUpdate = new AnalysisIssue();
                                    issueToUpdate.setId(analysis.getIssueId());
                                    issueToUpdate.setIsFalsePositive(analysis.getIsFalsePositive());
                                    issueToUpdate.setAiReasoning(analysis.getReasoning());
                                    issueToUpdate.setAiSuggestion(analysis.getFixSuggestion());
                                    analysisIssueMapper.updateById(issueToUpdate);
                                }
                            }
                        } catch (Exception e) {
                            log.error("单个 Issue 分析失败 IssueID: " + dbIssue.getId(), e);
                        } finally {
                            // 清理子线程上下文
                            AnalysisContextHolder.clear();
                        }
                    }, auditExecutor))
                    .collect(Collectors.toList());

                // 等待所有任务完成
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

                if (isCancelled(task.getId())) return;

                task = this.getById(task.getId());
                task.setStatus(TaskStatus.COMPLETED);
                task.setResultSummary("分析完成，共处理 " + dbIssues.size() + " 个问题。详情请查询 analysis_issue 表。");
                this.updateById(task);
            } finally {
                // 清理主线程上下文
                AnalysisContextHolder.clear();
            }

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

    private String calculateMd5(File file) throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("MD5");
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] byteArray = new byte[1024];
            int bytesCount = 0;
            while ((bytesCount = fis.read(byteArray)) != -1) {
                digest.update(byteArray, 0, bytesCount);
            }
        }
        byte[] bytes = digest.digest();
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
