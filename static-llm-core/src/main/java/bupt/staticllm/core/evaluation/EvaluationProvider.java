package bupt.staticllm.core.evaluation;

import bupt.staticllm.core.evaluation.model.BenchmarkCase;
import java.util.List;

public interface EvaluationProvider {
    /**
     * 加载基准测试用例
     * @param sourcePath 数据源路径（如 CSV 文件路径）
     * @return 基准测试用例列表
     */
    List<BenchmarkCase> loadCases(String sourcePath);
    
    /**
     * 获取提供者类型
     * @return 类型标识（如 "OWASP-1.2"）
     */
    String getType();
}
