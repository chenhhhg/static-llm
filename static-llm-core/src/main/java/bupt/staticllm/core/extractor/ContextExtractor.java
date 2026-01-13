package bupt.staticllm.core.extractor;

import bupt.staticllm.common.model.UnifiedIssue;

import java.util.List;

public interface ContextExtractor {

    /**
     * 为问题列表提取代码上下文
     * @param issues 归一化后的问题列表
     * @param sourceRoot 源码根目录
     */
    void enrichContext(List<UnifiedIssue> issues, String sourceRoot);
}
