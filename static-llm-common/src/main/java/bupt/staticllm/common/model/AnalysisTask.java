package bupt.staticllm.common.model;

import bupt.staticllm.common.enums.TaskStatus;
import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.Fastjson2TypeHandler;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;

@Data
@TableName(value = "analysis_task", autoResultMap = true)
public class AnalysisTask implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 任务参数(JSON格式，包含targetJar, sourcePath等)
     */
    @TableField(value = "task_params", typeHandler = Fastjson2TypeHandler.class)
    private Map<String, Object> taskParams;

    /**
     * 使用的分析工具
     */
    private String toolName;

    /**
     * 使用的大模型名称
     */
    private String llmModel;

    /**
     * 任务状态: 0-已提交, 1-下载中, ...
     */
    private TaskStatus status;

    /**
     * 分析结果简述或错误信息
     */
    private String resultSummary;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedTime;
}
