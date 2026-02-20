package bupt.staticllm.web.context;

/**
 * 用于在线程中传递分析任务的上下文信息（如源码根路径）
 */
public class AnalysisContextHolder {

    private static final ThreadLocal<String> SOURCE_PATH = new ThreadLocal<>();

    public static void setSourcePath(String path) {
        SOURCE_PATH.set(path);
    }

    public static String getSourcePath() {
        return SOURCE_PATH.get();
    }

    public static void clear() {
        SOURCE_PATH.remove();
    }
}
