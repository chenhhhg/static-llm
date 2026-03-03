package bupt.staticllm.web.llm.agent;

import dev.langchain4j.service.SystemMessage;

public interface CodeAuditAgent {

    @SystemMessage(fromResource = "prompts/code-audit-system.txt")
    String audit(String issueReport);
}
