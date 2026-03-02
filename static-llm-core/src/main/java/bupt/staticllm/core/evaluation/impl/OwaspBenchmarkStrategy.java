package bupt.staticllm.core.evaluation.impl;

import bupt.staticllm.common.model.UnifiedIssue;
import bupt.staticllm.core.evaluation.EvaluationStrategy;
import bupt.staticllm.core.evaluation.model.BenchmarkCase;
import bupt.staticllm.core.evaluation.model.EvaluationResult;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class OwaspBenchmarkStrategy implements EvaluationStrategy {

    private static final Map<String, String> CATEGORY_MAPPING = new HashMap<>();

    static {
        // SpotBugs types to OWASP categories
        CATEGORY_MAPPING.put("SQL_INJECTION", "sql-injection");
        CATEGORY_MAPPING.put("SQL_NONCONSTANT_STRING_PASSED_TO_EXECUTE", "sql-injection");
        CATEGORY_MAPPING.put("XSS_REQUEST_PARAMETER_TO_SERVLET_WRITER", "xss");
        CATEGORY_MAPPING.put("XSS_REQUEST_PARAMETER_TO_SEND_ERROR", "xss");
        // Add more mappings as needed
    }

    @Override
    public EvaluationResult evaluate(BenchmarkCase expected, List<UnifiedIssue> actualList) {
        EvaluationResult result = new EvaluationResult();
        result.setBenchmarkCase(expected);

        // Find matching issue in actual list
        UnifiedIssue matchedIssue = actualList.stream()
                .filter(issue -> isMatch(expected, issue))
                .findFirst()
                .orElse(null);

        if (expected.isRealVulnerability()) {
            if (matchedIssue != null) {
                result.setMatchStatus(EvaluationResult.MatchStatus.TP);
                result.setActualIssueId(matchedIssue.getRuleId()); // Use ruleId as ID
                result.setDetails("Detected correctly: " + matchedIssue.getRuleId());
            } else {
                result.setMatchStatus(EvaluationResult.MatchStatus.FN);
                result.setDetails("Missed vulnerability");
            }
        } else {
            if (matchedIssue != null) {
                result.setMatchStatus(EvaluationResult.MatchStatus.FP);
                result.setActualIssueId(matchedIssue.getRuleId());
                result.setDetails("False alarm: " + matchedIssue.getRuleId());
            } else {
                result.setMatchStatus(EvaluationResult.MatchStatus.TN);
                result.setDetails("Correctly ignored");
            }
        }

        return result;
    }

    private boolean isMatch(BenchmarkCase expected, UnifiedIssue actual) {
        // 1. File name match
        // Benchmark filename usually is class name like BenchmarkTest00001
        // Actual filename might be full path or just name.
        if (actual.getFilePath() == null || !actual.getFilePath().contains(expected.getFilename())) {
            return false;
        }

        // 2. Category match
        String normalizedActualCategory = normalizeCategory(actual.getRuleId());
        return normalizedActualCategory.equalsIgnoreCase(expected.getCategory());
    }

    @Override
    public String normalizeCategory(String rawCategory) {
        return CATEGORY_MAPPING.getOrDefault(rawCategory, rawCategory);
    }

    @Override
    public String getType() {
        return "OWASP-1.2";
    }
}
