package bupt.staticllm.common.model;

import lombok.Data;

import java.io.Serializable;

/**
 * 归一化后的标准缺陷模型
 */
@Data
public class UnifiedIssue implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 工具名称 (e.g., "SpotBugs")
     */
    private String toolName;

    /**
     * 规则ID (e.g., "NP_NULL_ON_SOME_PATH")
     */
    private String ruleId;

    /**
     * 严重程度 (HIGH, MEDIUM, LOW)
     */
    private String severity;

    /**
     * 相对文件路径
     */
    private String filePath;

    /**
     * 起始行
     */
    private int startLine;

    /**
     * 结束行
     */
    private int endLine;

    /**
     * 原始报错信息
     */
    private String message;

    /**
     * (可选) 对应的源码片段，用于增强Prompt
     */
    private String codeSnippet;
}

