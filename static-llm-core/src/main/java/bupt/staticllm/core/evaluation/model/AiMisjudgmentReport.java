package bupt.staticllm.core.evaluation.model;

import lombok.Data;

import java.util.List;

/**
 * AI 误判分析报告
 * 将 AI 已分析的 issue 与 Benchmark 标准答案进行逐条对比，
 * 找出 AI 判断错误的 case。
 */
@Data
public class AiMisjudgmentReport {

    /** 持久化后的记录ID */
    private Long recordId;

    /** AI 已分析的 issue 总数 */
    private int totalAnalyzed;

    /** 与 Benchmark 匹配上的 issue 数（filePath + category 匹配） */
    private int matchedCount;

    /** AI 判断正确的数量 */
    private int correctCount;

    /** AI 判断错误的数量 */
    private int wrongCount;

    /** AI 正确率 (correctCount / matchedCount) */
    private double accuracy;

    /** 误判详情列表（仅包含判断错误的 case） */
    private List<MisjudgmentDetail> misjudgments;

    /** 全部对比详情列表（包含正确和错误的） */
    private List<MisjudgmentDetail> allDetails;

    @Data
    public static class MisjudgmentDetail {
        /** 数据库中 issue 的 ID */
        private Long issueId;

        /** 文件路径 */
        private String filePath;

        /** SpotBugs 规则 ID */
        private String ruleId;

        /** 映射后的漏洞类别 */
        private String normalizedCategory;

        /** Benchmark 对应的测试用例名（如 BenchmarkTest00001） */
        private String benchmarkTestName;

        /** Benchmark 标准答案：是否为真实漏洞 */
        private boolean benchmarkIsReal;

        /** AI 判定：是否为误报 */
        private Boolean aiIsFalsePositive;

        /** AI 分析理由 */
        private String aiReasoning;

        /** AI 判定结果是否正确 */
        private boolean aiCorrect;

        /** 错误类型描述（如 "AI误判为假阳性，实际为真漏洞" 或 "AI未识别为假阳性，实际为非漏洞"） */
        private String errorType;
    }
}
