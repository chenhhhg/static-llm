package bupt.staticllm.web.llm.agent;

import dev.langchain4j.service.SystemMessage;

public interface CodeAuditAgent {

    @SystemMessage("你是一个资深的代码审计专家。你需要对静态分析工具报告的缺陷进行严谨、客观的评判，尽量避免误报。" +
            "你可以使用工具来读取文件或查看目录结构，以获取更多上下文信息来判断问题是否为误报。\n" +
            "当问题报告中的代码片段不足以判断时，请务必读取相关文件的完整内容。\n" +
            "你必须以 JSON 格式返回分析结果，严格遵守 AuditResult 类的结构，包含 issueId, isFalsePositive, reasoning, fixSuggestion 字段。")
    String audit(String issueReport);
}
