package bupt.staticllm.web.service.impl;

import bupt.staticllm.common.enums.IssueStatus;
import bupt.staticllm.common.model.AnalysisIssue;
import bupt.staticllm.common.model.UnifiedIssue;
import bupt.staticllm.core.evaluation.EvaluationProvider;
import bupt.staticllm.core.evaluation.EvaluationStrategy;
import bupt.staticllm.core.evaluation.model.BenchmarkCase;
import bupt.staticllm.core.evaluation.model.EvaluationReport;
import bupt.staticllm.core.evaluation.model.EvaluationResult;
import bupt.staticllm.web.service.AnalysisIssueService;
import bupt.staticllm.web.service.EvaluationService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

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
    private final List<EvaluationProvider> providers;
    private final List<EvaluationStrategy> strategies;

    @Override
    public EvaluationReport evaluateTask(Long taskId, String benchmarkType, String benchmarkPath) {
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
        // Only load issues that have been analyzed by AI (status == COMPLETED)
        List<AnalysisIssue> analysisIssues = analysisIssueService.list(
                new LambdaQueryWrapper<AnalysisIssue>()
                        .eq(AnalysisIssue::getTaskId, taskId)
                        .eq(AnalysisIssue::getStatus, IssueStatus.COMPLETED)
        );
        List<UnifiedIssue> actualIssues = convertToUnified(analysisIssues);
        log.info("Loaded {} actual issues for task {} (after AI filtering)", actualIssues.size(), taskId);

        // 4. Perform Evaluation
        List<EvaluationResult> results = new ArrayList<>();
        for (BenchmarkCase expected : expectedCases) {
            EvaluationResult result = strategy.evaluate(expected, actualIssues);
            results.add(result);
        }

        // 5. Calculate Metrics
        return calculateMetrics(results);
    }

    private List<UnifiedIssue> convertToUnified(List<AnalysisIssue> analysisIssues) {
        return analysisIssues.stream()
                // Filter out issues identified as False Positive by AI
                .filter(issue -> issue.getIsFalsePositive() == null || !issue.getIsFalsePositive())
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

    private double calculateBenchmarkScore(int tp, int fp, int fn, int tn) {
        // OWASP Benchmark Score = (Recall - False Positive Rate) * 100
        // Recall (TPR) = TP / (TP + FN)
        // False Positive Rate (FPR) = FP / (FP + TN)
        
        double tpr = calculateRecall(tp, fn);
        
        double fpr = 0.0;
        if (fp + tn > 0) {
            fpr = (double) fp / (fp + tn);
        }
        
        return (tpr - fpr) * 100;
    }
}
