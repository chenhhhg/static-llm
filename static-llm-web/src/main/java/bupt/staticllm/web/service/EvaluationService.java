package bupt.staticllm.web.service;

import bupt.staticllm.core.evaluation.model.EvaluationReport;

public interface EvaluationService {
    /**
     * 评估分析任务
     * @param taskId 任务ID
     * @param benchmarkType 基准测试类型 (e.g., "OWASP-1.2")
     * @param benchmarkPath 基准测试数据路径
     * @return 评估报告
     */
    EvaluationReport evaluateTask(Long taskId, String benchmarkType, String benchmarkPath);
}
