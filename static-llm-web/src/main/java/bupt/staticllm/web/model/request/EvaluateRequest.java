package bupt.staticllm.web.model.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "评估请求参数")
public class EvaluateRequest {

    @Schema(description = "任务ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long taskId;

    @Schema(description = "基准测试类型", defaultValue = "OWASP-1.2")
    private String benchmarkType = "OWASP-1.2";

    @Schema(description = "基准测试文件路径", requiredMode = Schema.RequiredMode.REQUIRED)
    private String benchmarkPath;

    @Schema(description = "是否仅评估AI已分析的issue（true=仅AI分析后的, false=全量）", defaultValue = "false")
    private Boolean aiOnly = false;
}
