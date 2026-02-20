package bupt.staticllm.web.config;

import bupt.staticllm.web.llm.agent.CodeAuditAgent;
import bupt.staticllm.web.llm.tools.ProjectContextTools;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class LlmConfig {

    @Value("${llm.deepseek.api-key}")
    private String apiKey;

    @Value("${llm.deepseek.base-url:https://api.deepseek.com}")
    private String baseUrl;
    
    @Value("${llm.deepseek.model:deepseek-chat}")
    private String modelName;

    @Bean
    public ChatLanguageModel deepSeekChatModel() {
        // 使用 OpenAiChatModel 适配 DeepSeek
        return OpenAiChatModel.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .modelName(modelName)
                .timeout(Duration.ofSeconds(120)) // 增加超时时间，因为工具调用可能耗时
                .logRequests(true)
                .logResponses(true)
                .build();
    }

    @Bean
    public CodeAuditAgent codeAuditAgent(ChatLanguageModel chatModel, ProjectContextTools tools) {
        return AiServices.builder(CodeAuditAgent.class)
                .chatLanguageModel(chatModel)
                .tools(tools) // 注入工具
                .chatMemory(MessageWindowChatMemory.withMaxMessages(10)) // 保持多轮对话记忆
                .build();
    }
}
