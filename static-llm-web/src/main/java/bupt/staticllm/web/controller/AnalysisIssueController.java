package bupt.staticllm.web.controller;

import bupt.staticllm.common.model.AnalysisIssue;
import bupt.staticllm.common.response.Result;
import bupt.staticllm.web.service.AnalysisIssueService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/issue")
@Tag(name = "Issue Management", description = "分析结果问题管理接口")
public class AnalysisIssueController {

    @Autowired
    private AnalysisIssueService analysisIssueService;

    @Operation(summary = "根据任务ID查询问题列表")
    @GetMapping("/list/{taskId}")
    public Result<List<AnalysisIssue>> listByTaskId(@PathVariable Long taskId) {
        List<AnalysisIssue> issues = analysisIssueService.getByTaskId(taskId);
        return Result.success(issues);
    }

    @Operation(summary = "查询问题详情")
    @GetMapping("/{id}")
    public Result<AnalysisIssue> getIssue(@PathVariable Long id) {
        AnalysisIssue issue = analysisIssueService.getById(id);
        if (issue != null) {
            return Result.success(issue);
        } else {
            return Result.fail("问题不存在");
        }
    }
}
