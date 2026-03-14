package bupt.staticllm.web.service.impl;

import bupt.staticllm.adapter.analysis.StaticAnalysisService;
import bupt.staticllm.common.enums.IssueStatus;
import bupt.staticllm.common.enums.TaskStatus;
import bupt.staticllm.common.model.AnalysisTask;
import bupt.staticllm.common.model.UnifiedIssue;
import bupt.staticllm.core.evaluation.impl.OwaspBenchmarkStrategy;
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
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
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
public class AnalysisTaskServiceImpl extends ServiceImpl<AnalysisTaskMapper, AnalysisTask> implements AnalysisTaskService, ApplicationRunner {

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
    private final ExecutorService auditExecutor = Executors.newFixedThreadPool(1);

    // 自定义 RAG 检索并发线程池，并发量 50
    private final ExecutorService ragExecutor = Executors.newFixedThreadPool(80);

    // 任务级线程池，用于执行 processTaskAsync 抽离出的任务
    private final ExecutorService taskExecutor = Executors.newFixedThreadPool(5);

    @Override
    public void run(ApplicationArguments args) throws Exception {
//        log.info("系统启动，开始扫描未完成的任务...");
//        List<AnalysisTask> tasks = this.list(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AnalysisTask>()
//                .notIn(AnalysisTask::getStatus, TaskStatus.COMPLETED, TaskStatus.FAILED, TaskStatus.CANCELLED));
//
//        if (tasks != null && !tasks.isEmpty()) {
//            log.info("发现 {} 个未完成任务，准备恢复执行", tasks.size());
//            for (AnalysisTask task : tasks) {
//                taskExecutor.submit(() -> executeTask(task));
//            }
//        } else {
//            log.info("没有发现未完成的任务");
//        }
    }

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
        
        // 提交到线程池执行
        taskExecutor.submit(() -> executeTask(task));
        
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

    @Override
    public void processTaskAsync(AnalysisTask task) {
        // 保留该方法以兼容接口定义，但实际上逻辑已移至 executeTask
        // 如果外部还有调用此方法的地方，建议改为调用 executeTask 或通过 submitTask 触发
        taskExecutor.submit(() -> executeTask(task));
    }

    private void executeTask(AnalysisTask task) {
        log.info("开始执行任务 TaskID: {}, 当前状态: {}", task.getId(), task.getStatus());
        
        try {
            if (isCancelled(task.getId())) return;

            Map<String, Object> params = task.getTaskParams();
            String targetJar = (String) params.get("targetJar");
            String sourcePath = (String) params.get("sourcePath");
            String packageFilter = (String) params.get("packageFilter");

            // 阶段 1: 静态分析与入库 (如果尚未完成)
            // 判定标准: 状态小于 WAITING_LLM (4)
            if (task.getStatus().getCode() < TaskStatus.WAITING_LLM.getCode()) {
                
                updateTaskStatus(task.getId(), TaskStatus.WAITING_ANALYSIS);
                if (isCancelled(task.getId())) return;

                updateTaskStatus(task.getId(), TaskStatus.ANALYZING);
                
                // 为了安全起见，如果处于此阶段，先清理可能残留的部分 Issue 数据
                analysisIssueMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AnalysisIssue>()
                        .eq(AnalysisIssue::getTaskId, task.getId()));

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
                AnalysisTask finalTask1 = task;
                List<CompletableFuture<AnalysisIssue>> ragFutures = issues.stream()
                    .map(issue -> CompletableFuture.supplyAsync(() -> {
                        try {
                            // 1. RAG 检索
                            String query = String.format(
                                    "How to fix SpotBugs rule '%s': %s",
                                    issue.getRuleId(),     // 规则ID
                                    issue.getMessage()    // 具体的错误信息
                            );
                            String knowledge = ragService.retrieve(query);
                            if (knowledge != null && !knowledge.isEmpty()) {
                                 issue.setCodeSnippet(issue.getCodeSnippet() + "\n\n// [RAG Knowledge Base Reference]\n" + knowledge);
                            }
            
                            // 2. 转换为 DB 实体并保存
                            AnalysisIssue dbIssue = new AnalysisIssue();
                            dbIssue.setTaskId(finalTask1.getId());
                            dbIssue.setToolName(issue.getToolName());
                            dbIssue.setRuleId(issue.getRuleId());
                            dbIssue.setSeverity(issue.getSeverity());
                            dbIssue.setFilePath(issue.getFilePath());
                            dbIssue.setStartLine(issue.getStartLine());
                            dbIssue.setEndLine(issue.getEndLine());
                            dbIssue.setMessage(issue.getMessage());
                            dbIssue.setCodeSnippet(issue.getCodeSnippet());
                            dbIssue.setStatus(IssueStatus.RAG_COMPLETED); // RAG 完成
                            
                            analysisIssueMapper.insert(dbIssue);
                            return dbIssue;
                        } catch (Exception e) {
                            log.error("RAG检索或入库失败", e);
                            return null;
                        }
                    }, ragExecutor))
                    .toList();

                // 等待所有任务完成
                CompletableFuture.allOf(ragFutures.toArray(new CompletableFuture[0])).join();
                
                updateTaskStatus(task.getId(), TaskStatus.WAITING_LLM);
            }

            if (isCancelled(task.getId())) return;

            // 阶段 2: LLM 审计 (断点续传核心逻辑)
            // 此时 DB 中应该已有 Issue 数据。我们查询未完成 LLM 分析的 Issue (aiSuggestion 为空)
            updateTaskStatus(task.getId(), TaskStatus.JUDGING);
            
            // 设置上下文路径，供 Tool 使用 (主线程设置一次，虽然并发任务里也会设置，但为了保险)
            AnalysisContextHolder.setSourcePath(sourcePath);

            // 查询待处理的 Issue (aiSuggestion 为空)
            List<AnalysisIssue> pendingIssues = analysisIssueMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AnalysisIssue>()
                    .eq(AnalysisIssue::getTaskId, task.getId())
                    .isNull(AnalysisIssue::getAiSuggestion) // 核心判断依据
            );

            // Benchmark 模式：过滤掉无法映射到 Benchmark 类别的 issue，节省 token
            Boolean benchmarkMode = (Boolean) params.get("benchmarkMode");
            if (Boolean.TRUE.equals(benchmarkMode) && pendingIssues != null) {
                int beforeSize = pendingIssues.size();
                List<AnalysisIssue> skippedIssues = pendingIssues.stream()
                    .filter(i -> !OwaspBenchmarkStrategy.isMappedCategory(i.getRuleId()))
                    .toList();
                pendingIssues = pendingIssues.stream()
                    .filter(i -> OwaspBenchmarkStrategy.isMappedCategory(i.getRuleId()))
                    .toList();
                // 将跳过的 issue 按 ruleId 分组后批量更新，避免逐条操作数据库
                Map<String, List<AnalysisIssue>> groupedByRule = skippedIssues.stream()
                    .collect(Collectors.groupingBy(i -> i.getRuleId() != null ? i.getRuleId() : "UNKNOWN"));
                for (Map.Entry<String, List<AnalysisIssue>> entry : groupedByRule.entrySet()) {
                    List<Long> ids = entry.getValue().stream().map(AnalysisIssue::getId).toList();
                    String skipMsg = "[SKIPPED] 该 ruleId(" + entry.getKey() + ") 无法映射到 Benchmark 类别，已跳过 AI 分析";
                    analysisIssueMapper.update(null,
                        new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<AnalysisIssue>()
                            .in(AnalysisIssue::getId, ids)
                            .set(AnalysisIssue::getAiSuggestion, skipMsg)
                            .set(AnalysisIssue::getStatus, IssueStatus.COMPLETED));
                }
                log.info("[Benchmark模式] TaskID: {} 过滤前={}, 过滤后={}, 跳过={}",
                    task.getId(), beforeSize, pendingIssues.size(), skippedIssues.size());
            }

            if (pendingIssues != null && !pendingIssues.isEmpty()) {
                log.info("TaskID: {} 还有 {} 个 Issue 等待 LLM 分析", task.getId(), pendingIssues.size());
                
                AnalysisTask finalTask = task;
                List<CompletableFuture<Void>> futures = pendingIssues.stream()
                    .map(dbIssue -> CompletableFuture.runAsync(() -> {
                        try {
                            // 在子线程中设置上下文路径
                            AnalysisContextHolder.setSourcePath(sourcePath);
                            
                            // 构建单个 Issue 的 Prompt
                            Map<String, Object> promptIssue = Map.of(
                                "issueId", dbIssue.getId(),
                                "ruleId", dbIssue.getRuleId(),
                                "filePath", dbIssue.getFilePath(),
                                "startLine", dbIssue.getStartLine(),
                                "codeSnippet", dbIssue.getCodeSnippet()
                            );

                            String userMessage = JSON.toJSONString(promptIssue);
                            
                            // 调用 Agent
                            String resultStr = codeAuditAgent.audit(userMessage);
//                            log.info("LLM Raw Response: {}", resultStr);
                            
                            AuditResult result = null;
                            try {
                                // 增强的解析逻辑
                                String jsonContent = resultStr;
                                // 1. 尝试提取 Markdown 代码块
                                java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("```json\\s*([\\s\\S]*?)\\s*```");
                                java.util.regex.Matcher matcher = pattern.matcher(resultStr);
                                if (matcher.find()) {
                                    jsonContent = matcher.group(1);
                                }

                                // 2. 尝试解析对象
                                String objectStr = jsonContent;
                                int jsonStart = jsonContent.indexOf("{");
                                int jsonEnd = jsonContent.lastIndexOf("}");
                                if (jsonStart >= 0 && jsonEnd > jsonStart) {
                                    objectStr = jsonContent.substring(jsonStart, jsonEnd + 1);
                                    result = JSON.parseObject(objectStr, AuditResult.class);
                                } else {
                                    throw new RuntimeException("No JSON object found in content");
                                }
                            } catch (Exception e) {
                                // 3. 尝试解析数组
                                try {
                                    // 重新从 resultStr 或 jsonContent 中尝试提取数组？
                                    // 这里使用 jsonContent (可能已去除 markdown)
                                    String jsonContent = resultStr;
                                    java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("```(?:json)?\\s*([\\s\\S]*?)\\s*```");
                                    java.util.regex.Matcher matcher = pattern.matcher(resultStr);
                                    if (matcher.find()) {
                                        jsonContent = matcher.group(1);
                                    }
                                    
                                    String arrayStr = jsonContent;
                                    int jsonStart = jsonContent.indexOf("[");
                                    int jsonEnd = jsonContent.lastIndexOf("]");
                                    if (jsonStart >= 0 && jsonEnd > jsonStart) {
                                        arrayStr = jsonContent.substring(jsonStart, jsonEnd + 1);
                                        List<AuditResult> list = JSON.parseArray(arrayStr, AuditResult.class);
                                        if (list != null && !list.isEmpty()) {
                                            result = list.get(0);
                                        }
                                    } else {
                                        throw e;
                                    }
                                } catch (Exception ex) {
                                    log.error("Failed to parse LLM response. Raw: {}", resultStr, ex);
                                    throw ex;
                                }
                            }
                            
                            if (isCancelled(finalTask.getId())) return;

                            // 4. 更新数据库
                            if (result != null) {
                                AnalysisIssue issueToUpdate = new AnalysisIssue();
                                issueToUpdate.setId(result.getIssueId());
                                issueToUpdate.setIsFalsePositive(result.getIsFalsePositive());
                                issueToUpdate.setAiReasoning(result.getReasoning());
                                issueToUpdate.setAiSuggestion(result.getFixSuggestion());
                                issueToUpdate.setStatus(IssueStatus.COMPLETED); // LLM 分析完成
                                analysisIssueMapper.updateById(issueToUpdate);
                            }
                        } catch (Exception e) {
                            log.error("单个 Issue 分析失败 IssueID: " + dbIssue.getId(), e);
                            AnalysisIssue failedIssue = new AnalysisIssue();
                            failedIssue.setId(dbIssue.getId());
                            failedIssue.setStatus(IssueStatus.FAILED);
                            analysisIssueMapper.updateById(failedIssue);
                        } finally {
                            // 清理子线程上下文
                            AnalysisContextHolder.clear();
                        }
                    }, auditExecutor))
                    .toList();

                // 等待所有任务完成
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            } else {
                log.info("TaskID: {} 所有 Issue 已完成 LLM 分析", task.getId());
            }

            if (isCancelled(task.getId())) return;

            // 统计最终结果
            Long totalIssues = analysisIssueMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AnalysisIssue>()
                    .eq(AnalysisIssue::getTaskId, task.getId())
            );

            task = this.getById(task.getId());
            task.setStatus(TaskStatus.COMPLETED);
            task.setResultSummary("分析完成，共处理 " + totalIssues + " 个问题。详情请查询 analysis_issue 表。");
            this.updateById(task);

        } catch (Exception e) {
            log.error("任务执行失败 TaskID: " + task.getId(), e);
            updateTaskStatus(task.getId(), TaskStatus.FAILED);
            AnalysisTask t = this.getById(task.getId());
            if (t != null) {
                t.setResultSummary("执行失败: " + e.getMessage());
                this.updateById(t);
            }
        } finally {
            // 清理主线程上下文
            AnalysisContextHolder.clear();
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
