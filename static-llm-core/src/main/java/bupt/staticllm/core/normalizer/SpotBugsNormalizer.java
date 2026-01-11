package bupt.staticllm.core.normalizer;

import bupt.staticllm.common.model.UnifiedIssue;
import lombok.extern.slf4j.Slf4j;
import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class SpotBugsNormalizer {

    public List<UnifiedIssue> normalize(String reportFilePath) {
        List<UnifiedIssue> issues = new ArrayList<>();
        try {
            File file = new File(reportFilePath);
            if (!file.exists()) {
                log.warn("报告文件不存在: {}", reportFilePath);
                return issues;
            }

            SAXReader reader = new SAXReader();
            // 防止XXE注入 (可选配置)
            try {
                reader.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            } catch (Exception ignored) {}
            
            Document document = reader.read(file);
            Element root = document.getRootElement();

            // 解析 BugInstance 节点
            List<Element> bugInstances = root.elements("BugInstance");
            for (Element bug : bugInstances) {
                UnifiedIssue issue = new UnifiedIssue();
                issue.setToolName("SpotBugs");
                issue.setRuleId(bug.attributeValue("type"));
                issue.setSeverity(convertPriority(bug.attributeValue("priority")));
                issue.setMessage(bug.elementText("LongMessage"));

                // 获取代码位置信息 - 通常取第一个SourceLine
                Element sourceLine = bug.element("SourceLine");
                if (sourceLine != null) {
                    issue.setFilePath(sourceLine.attributeValue("sourcepath"));
                    String start = sourceLine.attributeValue("start");
                    String end = sourceLine.attributeValue("end");
                    issue.setStartLine(start != null ? Integer.parseInt(start) : -1);
                    issue.setEndLine(end != null ? Integer.parseInt(end) : -1);
                }
                
                issues.add(issue);
            }
        } catch (Exception e) {
            log.error("归一化解析失败", e);
            throw new RuntimeException("归一化解析失败", e);
        }
        return issues;
    }

    private String convertPriority(String priority) {
        // SpotBugs: 1=High, 2=Normal, 3=Low
        if ("1".equals(priority)) return "HIGH";
        if ("2".equals(priority)) return "MEDIUM";
        return "LOW";
    }
}

