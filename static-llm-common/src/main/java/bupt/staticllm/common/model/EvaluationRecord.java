package bupt.staticllm.common.model;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 评估记录主表
 */
@Data
@TableName("evaluation_record")
public class EvaluationRecord implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联的分析任务ID */
    private Long taskId;

    /** 基准测试类型 */
    private String benchmarkType;

    /** 基准测试文件路径 */
    private String benchmarkPath;

    /** 评估模式: FULL=全量, AI_ONLY=仅AI分析, AI_MISJUDGMENT=AI误判分析 */
    private String evalMode;

    // ========== 评估类指标（FULL / AI_ONLY 模式） ==========

    private Integer tpCount;
    private Integer fpCount;
    private Integer fnCount;
    private Integer tnCount;
    private Double precisionRate;
    private Double recallRate;
    private Double f1Score;
    private Double benchmarkScore;

    /** 不可映射到Benchmark类别的Issue数量 */
    private Integer unmappedIssueCount;

    // ========== AI误判分析指标（AI_MISJUDGMENT 模式） ==========

    /** AI已分析总数 */
    private Integer totalAnalyzed;

    /** 匹配到Benchmark的数量 */
    private Integer matchedCount;

    /** AI判断正确数量 */
    private Integer correctCount;

    /** AI判断错误数量 */
    private Integer wrongCount;

    /** AI正确率 */
    private Double accuracy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedTime;
}
