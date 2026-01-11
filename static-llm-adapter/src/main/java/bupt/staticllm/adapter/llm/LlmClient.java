package bupt.staticllm.adapter.llm;

/**
 * LLM 客户端接口
 */
public interface LlmClient {
    
    /**
     * 发送 Prompt 获取回复
     * @param systemPrompt 系统设定
     * @param userPrompt 用户输入 (通常包含归一化后的代码问题)
     * @return LLM 回复文本
     */
    String chat(String systemPrompt, String userPrompt);
}

