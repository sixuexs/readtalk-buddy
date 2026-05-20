package com.backend.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// DeepSeek API 客户端（OpenAI 兼容格式）
// 端点：POST https://api.deepseek.com/chat/completions
// 模型：deepseek-v4-flash（快速）/ deepseek-v4-pro（高性能）
@Component
public class DeepSeekClient {

    private final RestClient restClient;

    private final String model;

    private final boolean thinkingEnabled;

    private final String reasoningEffort;

    private final double temperature;

    private final int maxTokens;

    public DeepSeekClient(
            @Value("${deepseek.api.url}") String apiUrl,
            @Value("${deepseek.api.key}") String apiKey,
            @Value("${deepseek.api.model:deepseek-v4-flash}") String model,
            @Value("${deepseek.api.thinking:false}") boolean thinkingEnabled,
            @Value("${deepseek.api.reasoning-effort:medium}") String reasoningEffort,
            @Value("${deepseek.api.temperature:0.8}") double temperature,
            @Value("${deepseek.api.max-tokens:1024}") int maxTokens) {
        this.model = model;
        this.thinkingEnabled = thinkingEnabled;
        this.reasoningEffort = reasoningEffort;
        this.temperature = temperature;
        this.maxTokens = maxTokens;

        String baseUrl = apiUrl.endsWith("/") ? apiUrl.substring(0, apiUrl.length() - 1) : apiUrl;
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    // messages: [ { "role": "system"|"user"|"assistant", "content": "..." } ]
    @SuppressWarnings("unchecked")
    public String chat(List<Map<String, String>> messages) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", messages);
        body.put("temperature", temperature);
        body.put("max_tokens", maxTokens);

        if (thinkingEnabled) {
            body.put("thinking", Map.of("type", "enabled"));
        }
        body.put("reasoning_effort", reasoningEffort);

        Map<String, Object> response = restClient.post()
                .uri("/chat/completions")
                .body(body)
                .retrieve()
                .body(Map.class);

        if (response == null) {
            throw new RuntimeException("DeepSeek API 无响应");
        }

        List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
        if (choices == null || choices.isEmpty()) {
            throw new RuntimeException("DeepSeek API 返回空 choices");
        }

        Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
        return (String) message.get("content");
    }
}
