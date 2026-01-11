package bupt.staticllm.adapter.analysis.impl;

import bupt.staticllm.adapter.analysis.StaticAnalysisService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class SpotBugsAdapter implements StaticAnalysisService {

    @Value("${spotbugs.jar-path}")
    private String spotBugsJarPath;

    @Value("${spotbugs.report-dir}")
    private String reportBaseDir;

    @Override
    public String executeAnalysis(String targetJarPath, String sourcePath, String packageFilter) {
        // 确保报告目录存在
        File dir = new File(reportBaseDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        String reportPath = reportBaseDir + File.separator + "report_" + System.currentTimeMillis() + ".xml";
        
        // 构建命令
        // java -jar spotbugs.jar -textui -onlyAnalyze "com.easypan.-" -effort:max -high -xml:withMessages -output xxx.xml -sourcepath src xxx.jar
        List<String> command = new ArrayList<>();
        command.add("java");
        command.add("-jar");
        command.add(spotBugsJarPath);
        command.add("-textui");
        command.add("-onlyAnalyze");
        command.add("\""+packageFilter+"\"");
        command.add("-effort:max"); 
        command.add("-high");       
        command.add("-xml:withMessages"); 
        command.add("-output");
        command.add(reportPath);
        command.add("-sourcepath");
        command.add(sourcePath);
        command.add(targetJarPath);

        log.info("执行SpotBugs命令: {}", String.join(" ", command));

        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true); 
            Process process = pb.start();

            // 异步读取控制台输出
            StringBuilder outputLog = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    // 日志输出太多可能会刷屏，这里可以选择性输出
                    log.debug("[SpotBugs] {}", line);
                    outputLog.append(line);
                }
            }

            // 等待执行结束
            boolean finished = process.waitFor(15, TimeUnit.MINUTES);
            if (!finished) {
                process.destroy();
                throw new RuntimeException("SpotBugs分析超时");
            }
            
            log.info("SpotBugs分析结束，退出码: {}", process.exitValue());

            if (process.exitValue() != 0){
                throw new RuntimeException(outputLog.toString());
            }

            return reportPath;
        } catch (Exception e) {
            log.error("SpotBugs执行异常", e);
            throw new RuntimeException("静态分析失败", e);
        }
    }
}

