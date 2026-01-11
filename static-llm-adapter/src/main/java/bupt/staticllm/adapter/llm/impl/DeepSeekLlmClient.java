package bupt.staticllm.adapter.llm.impl;

import bupt.staticllm.adapter.llm.LlmClient;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@ConditionalOnProperty(name = "llm.provider", havingValue = "deepseek")
public class DeepSeekLlmClient implements LlmClient {

    @Value("${llm.deepseek.api-key}")
    private String apiKey;

    @Value("${llm.deepseek.base-url:https://api.deepseek.com}")
    private String baseUrl;

    @Value("${llm.deepseek.model:deepseek-chat}")
    private String model;

    @Override
    public String chat(String systemPrompt, String userPrompt) {
        String url = baseUrl + "/chat/completions";
        
        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("stream", false);

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemPrompt));
        messages.add(Map.of("role", "user", "content", userPrompt));
        body.put("messages", messages);

        log.info("DeepSeek Request: Model={}, URL={}", model, url);

        try (HttpResponse response = HttpRequest.post(url)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .body(JSON.toJSONString(body))
                .timeout(60000) // 60s timeout
                .execute()) {

            if (!response.isOk()) {
                log.error("DeepSeek API Error: Status={}, Body={}", response.getStatus(), response.body());
                throw new RuntimeException("DeepSeek API request failed: " + response.getStatus());
            }

            String responseBody = response.body();
            // log.debug("DeepSeek Response: {}", responseBody);

            JSONObject json = JSON.parseObject(responseBody);
            JSONArray choices = json.getJSONArray("choices");
            if (choices != null && !choices.isEmpty()) {
                JSONObject firstChoice = choices.getJSONObject(0);
                return firstChoice.getJSONObject("message").getString("content");
            }
            
            return "";
        } catch (Exception e) {
            log.error("DeepSeek call failed", e);
            throw new RuntimeException("DeepSeek call failed", e);
        }
    }
}
