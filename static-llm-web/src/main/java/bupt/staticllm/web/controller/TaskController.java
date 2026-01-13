package bupt.staticllm.web.controller;

import bupt.staticllm.common.enums.ReturnCode;
import bupt.staticllm.common.model.AnalysisTask;
import bupt.staticllm.common.response.Result;
import bupt.staticllm.web.model.request.FileAnalysisRequest;
import bupt.staticllm.web.service.AnalysisTaskService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/task")
public class TaskController {

    @Autowired
    private AnalysisTaskService taskService;

    /**
     * 1. 提交分析任务
     */
    @PostMapping("/submit")
    public Result<Long> submitTask(@RequestBody FileAnalysisRequest request) {
        try {
            Long taskId = taskService.submitTask(request);
            log.info("任务已提交: {}", taskId);
            return Result.success(taskId);
        } catch (Exception e) {
            log.error("任务提交失败", e);
            return Result.fail("任务提交失败: " + e.getMessage());
        }
    }

    /**
     * 2. 查询任务详情
     */
    @GetMapping("/{taskId}")
    public Result<AnalysisTask> getTask(@PathVariable Long taskId) {
        AnalysisTask task = taskService.getById(taskId);
        if (task != null) {
            return Result.success(task);
        } else {
            return Result.fail(ReturnCode.NOT_FOUND);
        }
    }

    /**
     * 3. 查询任务列表
     */
    @GetMapping("/list")
    public Result<List<AnalysisTask>> listTasks() {
        List<AnalysisTask> tasks = taskService.list();
        return Result.success(tasks);
    }

    /**
     * 4. 删除任务
     */
    @DeleteMapping("/{taskId}")
    public Result<Void> deleteTask(@PathVariable Long taskId) {
        if (taskService.removeById(taskId)) {
            return Result.success();
        } else {
            return Result.fail("删除失败或任务不存在");
        }
    }

    /**
     * 5. 取消任务
     */
    @PostMapping("/cancel/{taskId}")
    public Result<Void> cancelTask(@PathVariable Long taskId) {
        if (taskService.cancelTask(taskId)) {
            return Result.success();
        } else {
            return Result.fail("取消失败：任务不存在或已完成");
        }
    }
}
