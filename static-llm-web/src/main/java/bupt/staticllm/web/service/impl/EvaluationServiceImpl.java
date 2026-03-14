package bupt.staticllm.web.service.impl;

import bupt.staticllm.common.enums.IssueStatus;
import bupt.staticllm.common.model.AnalysisIssue;
import bupt.staticllm.common.model.EvaluationDetail;
import bupt.staticllm.common.model.EvaluationRecord;
import bupt.staticllm.common.model.UnifiedIssue;
import bupt.staticllm.core.evaluation.EvaluationProvider;
import bupt.staticllm.core.evaluation.EvaluationStrategy;
import bupt.staticllm.core.evaluation.impl.OwaspBenchmarkStrategy;
import bupt.staticllm.core.evaluation.model.AiMisjudgmentReport;
import bupt.staticllm.core.evaluation.model.BenchmarkCase;
import bupt.staticllm.core.evaluation.model.EvaluationReport;
import bupt.staticllm.core.evaluation.model.EvaluationResult;
import bupt.staticllm.web.service.AnalysisIssueService;
import bupt.staticllm.web.service.EvaluationDetailService;
import bupt.staticllm.web.service.EvaluationRecordService;
import bupt.staticllm.web.service.EvaluationService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class EvaluationServiceImpl implements EvaluationService {

    private final AnalysisIssueService analysisIssueService;
    private final EvaluationRecordService evaluationRecordService;
    private final EvaluationDetailService evaluationDetailService;
    private final List<EvaluationProvider> providers;
    private final List<EvaluationStrategy> strategies;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EvaluationReport evaluateTask(Long taskId, String benchmarkType, String benchmarkPath, boolean aiOnly) {
        // 1. Select Provider and Strategy
        EvaluationProvider provider = providers.stream()
                .filter(p -> p.getType().equalsIgnoreCase(benchmarkType))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported benchmark type: " + benchmarkType));

        EvaluationStrategy strategy = strategies.stream()
                .filter(s -> s.getType().equalsIgnoreCase(benchmarkType))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported strategy type: " + benchmarkType));

        // 2. Load Benchmark Cases
        List<BenchmarkCase> expectedCases = provider.loadCases(benchmarkPath);
        log.info("Loaded {} benchmark cases from {}", expectedCases.size(), benchmarkPath);

        // 3. Load Actual Issues
        List<AnalysisIssue> analysisIssues;
        if (aiOnly) {
            analysisIssues = analysisIssueService.list(
                    new LambdaQueryWrapper<AnalysisIssue>()
                            .eq(AnalysisIssue::getTaskId, taskId)
                            .eq(AnalysisIssue::getStatus, IssueStatus.COMPLETED)
            );
            log.info("[aiOnly=true] Loaded {} AI-analyzed issues for task {}", analysisIssues.size(), taskId);
        } else {
            analysisIssues = analysisIssueService.list(
                    new LambdaQueryWrapper<AnalysisIssue>()
                            .eq(AnalysisIssue::getTaskId, taskId)
            );
            log.info("[aiOnly=false] Loaded {} total issues for task {}", analysisIssues.size(), taskId);
        }
        List<UnifiedIssue> actualIssues = convertToUnified(analysisIssues, aiOnly);
        log.info("Converted to {} unified issues for evaluation", actualIssues.size());

        // 调试日志
        if (!actualIssues.isEmpty()) {
            log.info("=== Actual Issues Sample (first 5) ===");
            actualIssues.stream().limit(5).forEach(issue ->
                log.info("  ruleId={}, filePath={}, normalizedCategory={}",
                    issue.getRuleId(), issue.getFilePath(),
                    strategy.normalizeCategory(issue.getRuleId()))
            );
        }
        if (!expectedCases.isEmpty()) {
            log.info("=== Expected Cases Sample (first 5) ===");
            expectedCases.stream().limit(5).forEach(c ->
                log.info("  filename={}, category={}, isReal={}",
                    c.getFilename(), c.getCategory(), c.isRealVulnerability())
            );
        }

        // 4. 统计不可映射到Benchmark类别的Issue数量
        int unmappedIssueCount = (int) actualIssues.stream()
                .filter(issue -> !OwaspBenchmarkStrategy.isMappedCategory(issue.getRuleId()))
                .count();
        if (unmappedIssueCount > 0) {
            // 按ruleId分组统计
            Map<String, Long> unmappedByRule = actualIssues.stream()
                    .filter(issue -> !OwaspBenchmarkStrategy.isMappedCategory(issue.getRuleId()))
                    .collect(Collectors.groupingBy(UnifiedIssue::getRuleId, Collectors.counting()));
            log.info("[评估] 不可映射到Benchmark类别的Issue: 总数={}, 按规则分布={}", unmappedIssueCount, unmappedByRule);
        }

        // 5. Perform Evaluation
        List<EvaluationResult> results = new ArrayList<>();
        for (BenchmarkCase expected : expectedCases) {
            EvaluationResult result = strategy.evaluate(expected, actualIssues);
            results.add(result);
        }

        // 6. Calculate Metrics
        EvaluationReport report = calculateMetrics(results);
        report.setUnmappedIssueCount(unmappedIssueCount);

        // 7. 持久化评估记录
        String evalMode = aiOnly ? "AI_ONLY" : "FULL";
        EvaluationRecord record = new EvaluationRecord();
        record.setTaskId(taskId);
        record.setBenchmarkType(benchmarkType);
        record.setBenchmarkPath(benchmarkPath);
        record.setEvalMode(evalMode);
        record.setTpCount(report.getTpCount());
        record.setFpCount(report.getFpCount());
        record.setFnCount(report.getFnCount());
        record.setTnCount(report.getTnCount());
        record.setPrecisionRate(report.getPrecision());
        record.setRecallRate(report.getRecall());
        record.setF1Score(report.getF1Score());
        record.setBenchmarkScore(report.getBenchmarkScore());
        record.setUnmappedIssueCount(unmappedIssueCount);
        evaluationRecordService.save(record);
        log.info("[评估持久化] 保存评估记录 id={}, mode={}", record.getId(), evalMode);

        // 持久化评估详情
        List<EvaluationDetail> details = new ArrayList<>();
        for (EvaluationResult result : results) {
            BenchmarkCase bc = result.getBenchmarkCase();
            EvaluationDetail detail = new EvaluationDetail();
            detail.setRecordId(record.getId());
            detail.setBenchmarkTestName(bc.getFilename());
            detail.setBenchmarkCategory(bc.getCategory());
            detail.setBenchmarkIsReal(bc.isRealVulnerability());
            detail.setMatchStatus(result.getMatchStatus().name());
            detail.setDetailInfo(result.getDetails());
            details.add(detail);
        }
        if (!details.isEmpty()) {
            evaluationDetailService.saveBatch(details, 500);
            log.info("[评估持久化] 保存 {} 条评估详情", details.size());
        }

        // 把记录ID设到report中返回
        report.setRecordId(record.getId());

        return report;
    }

    private List<UnifiedIssue> convertToUnified(List<AnalysisIssue> analysisIssues, boolean aiOnly) {
        return analysisIssues.stream()
                // 仅在aiOnly模式下，过滤掉AI判定为误报的issue
                .filter(issue -> !aiOnly || issue.getIsFalsePositive() == null || !issue.getIsFalsePositive())
                .map(issue -> {
                    UnifiedIssue unified = new UnifiedIssue();
                    unified.setToolName(issue.getToolName());
                    unified.setRuleId(issue.getRuleId());
                    unified.setSeverity(issue.getSeverity());
                    unified.setFilePath(issue.getFilePath());
                    unified.setStartLine(issue.getStartLine() != null ? issue.getStartLine() : 0);
                    unified.setEndLine(issue.getEndLine() != null ? issue.getEndLine() : 0);
                    unified.setMessage(issue.getMessage());
                    unified.setCodeSnippet(issue.getCodeSnippet());
                    return unified;
                }).collect(Collectors.toList());
    }

    private EvaluationReport calculateMetrics(List<EvaluationResult> results) {
        EvaluationReport report = new EvaluationReport();
        report.setDetails(results);

        int tp = 0, fp = 0, fn = 0, tn = 0;
        Map<String, EvaluationReport.CategoryStats> categoryStatsMap = new HashMap<>();

        for (EvaluationResult result : results) {
            String category = result.getBenchmarkCase().getCategory();
            EvaluationReport.CategoryStats stats = categoryStatsMap.computeIfAbsent(category, k -> new EvaluationReport.CategoryStats());

            switch (result.getMatchStatus()) {
                case TP:
                    tp++;
                    stats.setTp(stats.getTp() + 1);
                    break;
                case FP:
                    fp++;
                    stats.setFp(stats.getFp() + 1);
                    break;
                case FN:
                    fn++;
                    stats.setFn(stats.getFn() + 1);
                    break;
                case TN:
                    tn++;
                    stats.setTn(stats.getTn() + 1);
                    break;
            }
        }

        report.setTpCount(tp);
        report.setFpCount(fp);
        report.setFnCount(fn);
        report.setTnCount(tn);

        // Calculate global metrics
        report.setPrecision(calculatePrecision(tp, fp));
        report.setRecall(calculateRecall(tp, fn));
        report.setF1Score(calculateF1(report.getPrecision(), report.getRecall()));
        report.setBenchmarkScore(calculateBenchmarkScore(tp, fp, fn, tn));

        // Calculate category metrics
        for (EvaluationReport.CategoryStats stats : categoryStatsMap.values()) {
            stats.setRecall(calculateRecall(stats.getTp(), stats.getFn()));
        }
        report.setCategoryStats(categoryStatsMap);

        return report;
    }

    private double calculatePrecision(int tp, int fp) {
        if (tp + fp == 0) return 0.0;
        return (double) tp / (tp + fp);
    }

    private double calculateRecall(int tp, int fn) {
        if (tp + fn == 0) return 0.0;
        return (double) tp / (tp + fn);
    }

    private double calculateF1(double precision, double recall) {
        if (precision + recall == 0) return 0.0;
        return 2 * precision * recall / (precision + recall);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiMisjudgmentReport analyzeAiMisjudgments(Long taskId, String benchmarkType, String benchmarkPath) {
        // 1. 获取策略和Provider
        EvaluationProvider provider = providers.stream()
                .filter(p -> p.getType().equalsIgnoreCase(benchmarkType))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported benchmark type: " + benchmarkType));

        EvaluationStrategy strategy = strategies.stream()
                .filter(s -> s.getType().equalsIgnoreCase(benchmarkType))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported strategy type: " + benchmarkType));

        // 2. 加载 Benchmark 标准答案
        List<BenchmarkCase> expectedCases = provider.loadCases(benchmarkPath);
        log.info("[误判分析] 加载 {} 条 Benchmark 标准答案", expectedCases.size());

        // 按 filename 建立索引，方便查找
        Map<String, BenchmarkCase> benchmarkMap = new HashMap<>();
        for (BenchmarkCase c : expectedCases) {
            benchmarkMap.put(c.getFilename(), c);
        }

        // 3. 加载 AI 已分析完成的 issue
        List<AnalysisIssue> completedIssues = analysisIssueService.list(
                new LambdaQueryWrapper<AnalysisIssue>()
                        .eq(AnalysisIssue::getTaskId, taskId)
                        .eq(AnalysisIssue::getStatus, IssueStatus.COMPLETED)
        );
        log.info("[误判分析] 加载 {} 条 AI 已分析的 issue", completedIssues.size());

        // 4. 逐条对比
        List<AiMisjudgmentReport.MisjudgmentDetail> allDetails = new ArrayList<>();
        List<AiMisjudgmentReport.MisjudgmentDetail> misjudgments = new ArrayList<>();
        int matchedCount = 0;
        int correctCount = 0;
        int wrongCount = 0;

        // 调试统计：记录每个阶段被过滤掉的数量
        int skipNoTestName = 0;
        int skipNoBenchmarkCase = 0;
        int skipCategoryMismatch = 0;
        Map<String, Integer> unmappedRuleIds = new HashMap<>();
        Map<String, Integer> categoryMismatchDetails = new HashMap<>();

        for (AnalysisIssue issue : completedIssues) {
            // 尝试从 filePath 中提取 BenchmarkTest 编号
            String benchmarkTestName = extractBenchmarkTestName(issue.getFilePath());
            if (benchmarkTestName == null) {
                skipNoTestName++;
                continue; // 无法匹配到 Benchmark 用例，跳过
            }

            BenchmarkCase benchmarkCase = benchmarkMap.get(benchmarkTestName);
            if (benchmarkCase == null) {
                skipNoBenchmarkCase++;
                continue; // 在 Benchmark 中找不到对应用例
            }

            // 还需要检查类别是否匹配
            String normalizedCategory = strategy.normalizeCategory(issue.getRuleId());
            if (!normalizedCategory.equalsIgnoreCase(benchmarkCase.getCategory())) {
                skipCategoryMismatch++;
                String mismatchKey = issue.getRuleId() + "(" + normalizedCategory + ") != " + benchmarkCase.getCategory();
                categoryMismatchDetails.merge(mismatchKey, 1, Integer::sum);
                // 如果归一化后的类别等于原始ruleId，说明CATEGORY_MAPPING中缺少这个映射
                if (normalizedCategory.equals(issue.getRuleId())) {
                    unmappedRuleIds.merge(issue.getRuleId(), 1, Integer::sum);
                }
                continue; // 类别不匹配，不是同一类漏洞的对比
            }

            matchedCount++;

            AiMisjudgmentReport.MisjudgmentDetail detail = new AiMisjudgmentReport.MisjudgmentDetail();
            detail.setIssueId(issue.getId());
            detail.setFilePath(issue.getFilePath());
            detail.setRuleId(issue.getRuleId());
            detail.setNormalizedCategory(normalizedCategory);
            detail.setBenchmarkTestName(benchmarkTestName);
            detail.setBenchmarkIsReal(benchmarkCase.isRealVulnerability());
            detail.setAiIsFalsePositive(issue.getIsFalsePositive());
            detail.setAiReasoning(issue.getAiReasoning());

            // 判断 AI 是否正确
            // Benchmark说是真漏洞 -> AI应该判定isFalsePositive=false（非误报）
            // Benchmark说不是真漏洞 -> AI应该判定isFalsePositive=true（是误报）
            boolean aiSaysReal = issue.getIsFalsePositive() == null || !issue.getIsFalsePositive();
            boolean benchmarkSaysReal = benchmarkCase.isRealVulnerability();
            boolean isCorrect = (aiSaysReal == benchmarkSaysReal);

            detail.setAiCorrect(isCorrect);

            if (!isCorrect) {
                wrongCount++;
                if (benchmarkSaysReal) {
                    // benchmarkSaysReal=true, aiSaysReal必为false（因为!isCorrect意味着二者不等）
                    detail.setErrorType("AI误判为假阳性 → 实际为真实漏洞（漏报）");
                } else {
                    // benchmarkSaysReal=false, aiSaysReal必为true
                    detail.setErrorType("AI未识别为假阳性 → 实际为非漏洞（误报）");
                }
                misjudgments.add(detail);
            } else {
                correctCount++;
                detail.setErrorType(null);
            }

            allDetails.add(detail);
        }

        // 5. 组装报告
        AiMisjudgmentReport report = new AiMisjudgmentReport();
        report.setTotalAnalyzed(completedIssues.size());
        report.setMatchedCount(matchedCount);
        report.setCorrectCount(correctCount);
        report.setWrongCount(wrongCount);
        report.setAccuracy(matchedCount > 0 ? (double) correctCount / matchedCount : 0.0);
        report.setMisjudgments(misjudgments);
        report.setAllDetails(allDetails);

        // 输出过滤统计日志
        log.info("[误判分析] 过滤统计: 总共={}, 无TestName跳过={}, 无BenchmarkCase跳过={}, 类别不匹配跳过={}, 最终匹配={}",
                completedIssues.size(), skipNoTestName, skipNoBenchmarkCase, skipCategoryMismatch, matchedCount);
        if (!unmappedRuleIds.isEmpty()) {
            log.warn("[误判分析] 未映射的ruleId（CATEGORY_MAPPING中缺失）: {}", unmappedRuleIds);
        }
        if (!categoryMismatchDetails.isEmpty()) {
            log.info("[误判分析] 类别不匹配详情: {}",
                    categoryMismatchDetails.entrySet().stream().limit(20).collect(Collectors.toList()));
        }

        log.info("[误判分析] 完成: 已分析={}, 匹配Benchmark={}, 正确={}, 错误={}, 正确率={}",
                completedIssues.size(), matchedCount, correctCount, wrongCount,
                String.format("%.2f%%", report.getAccuracy() * 100));

        // 6. 持久化误判分析记录
        EvaluationRecord record = new EvaluationRecord();
        record.setTaskId(taskId);
        record.setBenchmarkType(benchmarkType);
        record.setBenchmarkPath(benchmarkPath);
        record.setEvalMode("AI_MISJUDGMENT");
        record.setTotalAnalyzed(completedIssues.size());
        record.setMatchedCount(matchedCount);
        record.setCorrectCount(correctCount);
        record.setWrongCount(wrongCount);
        record.setAccuracy(report.getAccuracy());
        evaluationRecordService.save(record);
        log.info("[误判分析持久化] 保存评估记录 id={}", record.getId());

        // 持久化详情
        List<EvaluationDetail> detailEntities = new ArrayList<>();
        for (AiMisjudgmentReport.MisjudgmentDetail d : allDetails) {
            EvaluationDetail entity = new EvaluationDetail();
            entity.setRecordId(record.getId());
            entity.setIssueId(d.getIssueId());
            entity.setFilePath(d.getFilePath());
            entity.setRuleId(d.getRuleId());
            entity.setNormalizedCategory(d.getNormalizedCategory());
            entity.setBenchmarkTestName(d.getBenchmarkTestName());
            entity.setBenchmarkIsReal(d.isBenchmarkIsReal());
            entity.setAiIsFalsePositive(d.getAiIsFalsePositive());
            entity.setAiReasoning(d.getAiReasoning());
            entity.setAiCorrect(d.isAiCorrect());
            entity.setErrorType(d.getErrorType());
            detailEntities.add(entity);
        }
        if (!detailEntities.isEmpty()) {
            evaluationDetailService.saveBatch(detailEntities, 500);
            log.info("[误判分析持久化] 保存 {} 条详情", detailEntities.size());
        }

        // 把记录ID设到report中返回
        report.setRecordId(record.getId());

        return report;
    }

    /**
     * 从文件路径中提取 BenchmarkTest 名称
     * 例如："org/owasp/benchmark/testcode/BenchmarkTest00001.java" -> "BenchmarkTest00001"
     */
    private String extractBenchmarkTestName(String filePath) {
        if (filePath == null) return null;
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("(BenchmarkTest\\d+)")
                .matcher(filePath);
        return matcher.find() ? matcher.group(1) : null;
    }

    private double calculateBenchmarkScore(int tp, int fp, int fn, int tn) {
        // OWASP Benchmark Score = TPR - FPR
        // Recall (TPR) = TP / (TP + FN)
        // False Positive Rate (FPR) = FP / (FP + TN)
        // 返回 0~1 的小数，与 precision/recall 保持一致，前端统一 ×100% 显示
        
        double tpr = calculateRecall(tp, fn);
        
        double fpr = 0.0;
        if (fp + tn > 0) {
            fpr = (double) fp / (fp + tn);
        }
        
        return tpr - fpr;
    }
}
