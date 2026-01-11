package bupt.staticllm.web.job;

import com.alibaba.fastjson2.JSON;
import bupt.staticllm.adapter.analysis.StaticAnalysisService;
import bupt.staticllm.common.model.UnifiedIssue;
import bupt.staticllm.core.normalizer.SpotBugsNormalizer;
import bupt.staticllm.adapter.llm.LlmClient;
import lombok.extern.slf4j.Slf4j;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.quartz.QuartzJobBean;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class AnalysisJob extends QuartzJobBean {

    @Autowired
    private StaticAnalysisService spotBugsAdapter;

    @Autowired
    private SpotBugsNormalizer normalizer;

    @Autowired
    private LlmClient llmClient;

    @Override
    protected void executeInternal(JobExecutionContext context) throws JobExecutionException {
        // 1. 获取任务参数
        String targetJar = context.getMergedJobDataMap().getString("targetJar");
        String sourcePath = context.getMergedJobDataMap().getString("sourcePath");
        String packageFilter = context.getMergedJobDataMap().getString("packageFilter");
        Long taskId = context.getMergedJobDataMap().getLong("taskId");

        log.info("开始执行分析任务 TaskID: {}, Jar: {}", taskId, targetJar);

        try {
            // TODO: 1. 拉取代码
            
            // 2. 执行 SpotBugs
            String reportFile = spotBugsAdapter.executeAnalysis(targetJar, sourcePath, packageFilter);
            log.info("SpotBugs分析完成，报告路径: {}", reportFile);

            // 3. 归一化处理
            List<UnifiedIssue> issues = normalizer.normalize(reportFile);
            log.info("归一化完成，发现 {} 个问题", issues.size());

            // 4. 构建 Prompt (简单JSON转换)
            String promptContext = JSON.toJSONString(issues);
            String systemPrompt = "你是一个代码审计专家。以下是静态分析工具发现的问题列表（JSON格式）。请分析这些问题，去除误报，并给出修复建议。";
            
            log.info("准备调用LLM，上下文长度: {}", promptContext.length());

            // 5. 调用 LLM
            String llmResponse = llmClient.chat(systemPrompt, promptContext);
            log.info("LLM响应: {}", llmResponse);

            // TODO: 6. 保存结果到数据库

        } catch (Exception e) {
            log.error("任务执行失败 TaskID: " + taskId, e);
        }
    }
}

