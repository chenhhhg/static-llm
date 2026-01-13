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
}
