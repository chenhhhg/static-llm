package bupt.staticllm.common.model;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 评估详情表
 */
@Data
@TableName("evaluation_detail")
public class EvaluationDetail implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联的评估记录ID */
    private Long recordId;

    /** 关联的issue ID */
    private Long issueId;

    /** 文件路径 */
    private String filePath;

    /** 规则ID */
    private String ruleId;

    /** 归一化后的漏洞类别 */
    private String normalizedCategory;

    /** Benchmark测试用例名 */
    private String benchmarkTestName;

    /** Benchmark类别 */
    private String benchmarkCategory;

    /** Benchmark标准答案：是否真实漏洞 */
    private Boolean benchmarkIsReal;

    /** 匹配状态: TP/FP/FN/TN */
    private String matchStatus;

    /** AI判定：是否误报 */
    private Boolean aiIsFalsePositive;

    /** AI分析理由 */
    private String aiReasoning;

    /** AI判定是否正确 */
    private Boolean aiCorrect;

    /** 错误类型描述 */
    private String errorType;

    /** 附加详情信息 */
    private String detailInfo;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;
}
