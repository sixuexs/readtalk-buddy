package com.backend.agent;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.ai.deepseek.api.DeepSeekApi;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.support.RetryTemplate;

/**
 * 阅谈智伴多智能体共享基础设施配置
 * <p>
 * 提供所有 Agent 公用的 Bean：ChatModel、ChatClient.Builder、ToolCallingManager
 * 各特化 Agent 在自己的 @Component 中构建 ReactAgent
 */
@Configuration
public class AgentConfig {

    @Value("${deepseek.api.key}")
    private String apiKey;

    @Value("${deepseek.api.url}")
    private String baseUrl;

    @Value("${deepseek.api.model:deepseek-v4-flash}")
    private String model;

    @Value("${deepseek.api.temperature:0.8}")
    private double temperature;

    @Value("${deepseek.api.max-tokens:1024}")
    private int maxTokens;

    /** DeepSeek ChatModel —— 所有 Agent 共享 */
    @Bean
    public ChatModel chatModel() {
        DeepSeekApi api = DeepSeekApi.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .build();

        DeepSeekChatOptions options = DeepSeekChatOptions.builder()
                .model(model)
                .temperature(temperature)
                .maxTokens(maxTokens)
                .build();

        return DeepSeekChatModel.builder()
                .deepSeekApi(api)
                .defaultOptions(options)
                .toolCallingManager(toolCallingManager())
                .retryTemplate(RetryTemplate.builder().build())
                .build();
    }

    /** ChatClient.Builder —— SimulationService 等服务层使用 */
    @Bean
    public ChatClient.Builder chatClientBuilder(ChatModel chatModel) {
        return ChatClient.builder(chatModel);
    }

    /** ToolCallingManager —— Agent 工具调用管理 */
    @Bean
    public ToolCallingManager toolCallingManager() {
        return ToolCallingManager.builder().build();
    }
}
