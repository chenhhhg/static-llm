package bupt.staticllm.core.evaluation.model;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class EvaluationReport {
    /** 持久化后的记录ID */
    private Long recordId;

    private int tpCount;
    private int fpCount;
    private int fnCount;
    private int tnCount;
    
    private double precision;
    private double recall;
    private double f1Score;
    private double benchmarkScore;

    /** 不可映射到Benchmark类别的Issue数量（SpotBugs检出但ruleId无对应Benchmark类别） */
    private int unmappedIssueCount;
    
    private List<EvaluationResult> details;
    private Map<String, CategoryStats> categoryStats;

    @Data
    public static class CategoryStats {
        private int tp;
        private int fp;
        private int fn;
        private int tn;
        private double recall;
    }
}
