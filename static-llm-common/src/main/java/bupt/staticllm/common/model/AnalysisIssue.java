package bupt.staticllm.common.model;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("analysis_issue")
public class AnalysisIssue implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long taskId;

    private String toolName;
    private String ruleId;
    private String severity;
    private String filePath;
    private Integer startLine;
    private Integer endLine;
    private String message;

    /**
     * 源码片段
     */
    private String codeSnippet;

    /**
     * AI 是否认为是误报 (0: 否/未判定, 1: 是)
     * 这里用 Boolean 或 Integer 都可以，数据库一般用 TINYINT
     */
    private Boolean isFalsePositive;

    /**
     * AI 分析依据
     */
    private String aiReasoning;

    /**
     * AI 修复建议
     */
    private String aiSuggestion;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedTime;
}
