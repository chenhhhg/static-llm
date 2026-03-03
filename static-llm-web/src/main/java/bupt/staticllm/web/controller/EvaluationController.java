package bupt.staticllm.web.controller;

import bupt.staticllm.common.model.EvaluationDetail;
import bupt.staticllm.common.model.EvaluationRecord;
import bupt.staticllm.common.response.Result;
import bupt.staticllm.core.evaluation.model.AiMisjudgmentReport;
import bupt.staticllm.core.evaluation.model.EvaluationReport;
import bupt.staticllm.web.model.request.EvaluateRequest;
import bupt.staticllm.web.service.EvaluationDetailService;
import bupt.staticllm.web.service.EvaluationRecordService;
import bupt.staticllm.web.service.EvaluationService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "评估管理")
@RestController
@RequestMapping("/api/evaluation")
@RequiredArgsConstructor
public class EvaluationController {

    private final EvaluationService evaluationService;
    private final EvaluationRecordService evaluationRecordService;
    private final EvaluationDetailService evaluationDetailService;

    @Operation(summary = "执行评估")
    @PostMapping("/evaluate")
    public Result<EvaluationReport> evaluate(@RequestBody EvaluateRequest request) {
        EvaluationReport report = evaluationService.evaluateTask(
                request.getTaskId(),
                request.getBenchmarkType(),
                request.getBenchmarkPath(),
                Boolean.TRUE.equals(request.getAiOnly()));
        return Result.success(report);
    }

    @Operation(summary = "AI误判分析", description = "将AI已分析的issue与Benchmark标准答案逐条对比，找出AI判断错误的case")
    @PostMapping("/ai-misjudgments")
    public Result<AiMisjudgmentReport> analyzeAiMisjudgments(@RequestBody EvaluateRequest request) {
        AiMisjudgmentReport report = evaluationService.analyzeAiMisjudgments(
                request.getTaskId(),
                request.getBenchmarkType(),
                request.getBenchmarkPath());
        return Result.success(report);
    }

    @Operation(summary = "查询评估历史记录列表", description = "按任务ID查询所有评估记录，不传taskId则查全部")
    @GetMapping("/records")
    public Result<List<EvaluationRecord>> listRecords(
            @RequestParam(required = false) Long taskId) {
        LambdaQueryWrapper<EvaluationRecord> wrapper = new LambdaQueryWrapper<>();
        if (taskId != null) {
            wrapper.eq(EvaluationRecord::getTaskId, taskId);
        }
        wrapper.orderByDesc(EvaluationRecord::getCreatedTime);
        List<EvaluationRecord> records = evaluationRecordService.list(wrapper);
        return Result.success(records);
    }

    @Operation(summary = "查询评估记录详情", description = "根据评估记录ID查询单条记录和所有详情")
    @GetMapping("/records/{recordId}")
    public Result<EvaluationRecord> getRecord(@PathVariable Long recordId) {
        EvaluationRecord record = evaluationRecordService.getById(recordId);
        if (record == null) {
            return Result.fail("评估记录不存在: " + recordId);
        }
        return Result.success(record);
    }

    @Operation(summary = "查询评估详情列表", description = "根据评估记录ID查询逐条对比详情")
    @GetMapping("/records/{recordId}/details")
    public Result<List<EvaluationDetail>> listDetails(
            @PathVariable Long recordId,
            @RequestParam(required = false) Boolean onlyWrong) {
        LambdaQueryWrapper<EvaluationDetail> wrapper = new LambdaQueryWrapper<EvaluationDetail>()
                .eq(EvaluationDetail::getRecordId, recordId);
        // 支持仅查看AI判断错误的记录
        if (Boolean.TRUE.equals(onlyWrong)) {
            wrapper.eq(EvaluationDetail::getAiCorrect, false);
        }
        wrapper.orderByAsc(EvaluationDetail::getId);
        List<EvaluationDetail> details = evaluationDetailService.list(wrapper);
        return Result.success(details);
    }
}
