package bupt.staticllm.web.controller;

import bupt.staticllm.common.response.Result;
import bupt.staticllm.core.evaluation.model.EvaluationReport;
import bupt.staticllm.web.service.EvaluationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "评估管理")
@RestController
@RequestMapping("/api/evaluation")
@RequiredArgsConstructor
public class EvaluationController {

    private final EvaluationService evaluationService;

    @Operation(summary = "执行评估")
    @PostMapping("/evaluate")
    public Result<EvaluationReport> evaluate(
            @RequestParam Long taskId,
            @RequestParam(defaultValue = "OWASP-1.2") String benchmarkType,
            @RequestParam String benchmarkPath) {
        
        EvaluationReport report = evaluationService.evaluateTask(taskId, benchmarkType, benchmarkPath);
        return Result.success(report);
    }
}
