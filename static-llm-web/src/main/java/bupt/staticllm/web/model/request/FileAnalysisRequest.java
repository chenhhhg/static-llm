package bupt.staticllm.web.model.request;

import lombok.Data;

@Data
public class FileAnalysisRequest {
    /**
     * 目标Jar包路径
     */
    private String targetJar;
    
    /**
     * 源码路径
     */
    private String sourcePath;
    
    /**
     * 包过滤器 (e.g. com.easypan.-)
     */
    private String packageFilter;

    /**
     * 是否为 Benchmark 评估模式。
     * 开启后，AI 分析阶段将跳过无法映射到 Benchmark 类别的 issue，以节省 token。
     */
    private Boolean benchmarkMode = false;
}
