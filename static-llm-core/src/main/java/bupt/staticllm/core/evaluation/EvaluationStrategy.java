package bupt.staticllm.core.evaluation;

import bupt.staticllm.common.model.UnifiedIssue;
import bupt.staticllm.core.evaluation.model.BenchmarkCase;
import bupt.staticllm.core.evaluation.model.EvaluationResult;
import java.util.List;

public interface EvaluationStrategy {
    /**
     * 执行评估匹配
     * @param expected 预期结果（基准测试用例）
     * @param actualList 实际分析结果列表
     * @return 评估结果
     */
    EvaluationResult evaluate(BenchmarkCase expected, List<UnifiedIssue> actualList);

    /**
     * 规范化漏洞类别
     * @param rawCategory 原始类别
     * @return 规范化后的类别
     */
    String normalizeCategory(String rawCategory);
    
    /**
     * 获取策略类型
     * @return 类型标识（如 "OWASP-1.2"）
     */
    String getType();
}
