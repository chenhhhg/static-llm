package bupt.staticllm.core.evaluation.model;

import lombok.Data;

@Data
public class EvaluationResult {
    private BenchmarkCase benchmarkCase;
    private String actualIssueId;
    private MatchStatus matchStatus;
    private String details;

    public enum MatchStatus {
        TP, // True Positive
        FP, // False Positive
        FN, // False Negative
        TN  // True Negative
    }
}
