package bupt.staticllm.web.controller;

import bupt.staticllm.common.response.Result;
import bupt.staticllm.core.evaluation.model.EvaluationReport;
import bupt.staticllm.web.model.request.EvaluateRequest;
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
    public Result<EvaluationReport> evaluate(@RequestBody EvaluateRequest request) {
        
        EvaluationReport report = evaluationService.evaluateTask(
                request.getTaskId(),
                request.getBenchmarkType(),
                request.getBenchmarkPath());
        return Result.success(report);
    }
}
