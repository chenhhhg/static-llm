package bupt.staticllm.adapter.analysis;

/**
 * 静态分析服务接口
 */
public interface StaticAnalysisService {
    
    /**
     * 执行静态分析
     * @param targetJarPath 目标Jar包路径 (e.g. D:\...\target\easypan-1.0.jar)
     * @param sourcePath 源码路径 (e.g. D:\...\src\main\java)
     * @param packageFilter 包过滤器 (e.g. com.easypan.-)
     * @return 分析报告文件的绝对路径
     */
    String executeAnalysis(String targetJarPath, String sourcePath, String packageFilter);
}

