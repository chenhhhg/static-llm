package bupt.staticllm.web.service;

import bupt.staticllm.core.evaluation.model.AiMisjudgmentReport;
import bupt.staticllm.core.evaluation.model.EvaluationReport;

public interface EvaluationService {
    /**
     * 评估分析任务
     * @param taskId 任务ID
     * @param benchmarkType 基准测试类型 (e.g., "OWASP-1.2")
     * @param benchmarkPath 基准测试数据路径
     * @param aiOnly 是否仅评估AI已分析的issue
     * @return 评估报告
     */
    EvaluationReport evaluateTask(Long taskId, String benchmarkType, String benchmarkPath, boolean aiOnly);

    /**
     * 分析 AI 误判情况
     * 将已完成 AI 分析的 issue 与 Benchmark 标准答案逐条对比，
     * 找出 AI 判断错误的 case。
     * @param taskId 任务ID
     * @param benchmarkType 基准测试类型
     * @param benchmarkPath 基准测试数据路径
     * @return AI 误判分析报告
     */
    AiMisjudgmentReport analyzeAiMisjudgments(Long taskId, String benchmarkType, String benchmarkPath);
}
