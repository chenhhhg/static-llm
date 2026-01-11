package bupt.staticllm.web.controller;

import bupt.staticllm.web.job.AnalysisJob;
import lombok.extern.slf4j.Slf4j;
import org.quartz.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/task")
public class TaskController {

    @Autowired
    private Scheduler scheduler;

    /**
     * 提交分析任务
     * 参数示例：
     * {
     *   "targetJar": "D:\\JavaProject\\easypan-all\\easypan-java\\target\\easypan-1.0.jar",
     *   "sourcePath": "D:\\JavaProject\\easypan-all\\easypan-java\\src\\main\\java",
     *   "packageFilter": "com.easypan.-"
     * }
     */
    @PostMapping("/submit")
    public Map<String, Object> submitTask(@RequestBody Map<String, String> params) {
        Map<String, Object> result = new HashMap<>();
        try {
            String targetJar = params.get("targetJar");
            String sourcePath = params.get("sourcePath");
            String packageFilter = params.get("packageFilter");
            
            // 简单的ID生成
            Long taskId = Math.abs(UUID.randomUUID().getMostSignificantBits());

            // 构建 JobDetail
            JobDetail jobDetail = JobBuilder.newJob(AnalysisJob.class)
                    .withIdentity("analysisTask-" + taskId, "analysisGroup")
                    .usingJobData("targetJar", targetJar)
                    .usingJobData("sourcePath", sourcePath)
                    .usingJobData("packageFilter", packageFilter)
                    .usingJobData("taskId", taskId)
                    .build();

            // 构建 Trigger (立即执行)
            Trigger trigger = TriggerBuilder.newTrigger()
                    .withIdentity("trigger-" + taskId, "analysisGroup")
                    .startNow()
                    .build();

            // 调度任务
            scheduler.scheduleJob(jobDetail, trigger);

            result.put("code", 200);
            result.put("msg", "任务提交成功");
            result.put("taskId", taskId);
            
            log.info("任务已提交: {}", taskId);

        } catch (SchedulerException e) {
            log.error("任务调度失败", e);
            result.put("code", 500);
            result.put("msg", "任务提交失败: " + e.getMessage());
        }
        return result;
    }
}

