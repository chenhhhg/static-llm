package bupt.staticllm.adapter.llm.impl;

import bupt.staticllm.adapter.llm.LlmClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class MockLlmClient implements LlmClient {

    @Override
    public String chat(String systemPrompt, String userPrompt) {
        log.info("Mock LLM Request - System: {}, User Length: {}", systemPrompt, userPrompt.length());
        
        // 模拟返回
        return "{\n" +
                "  \"analysis\": \"这是一个模拟的LLM回复。\",\n" +
                "  \"suggestions\": \"请检查空指针异常。\"\n" +
                "}";
    }
}

