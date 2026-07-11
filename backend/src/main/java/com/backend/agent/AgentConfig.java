package com.backend.agent;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
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
 * 阅谈智伴多智能体配置
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

    /**
     * ChatClient.Builder Bean（供 SimulationService 使用）
     */
    @Bean
    public ChatClient.Builder chatClientBuilder(ChatModel chatModel) {
        return ChatClient.builder(chatModel);
    }

    /**
     * DeepSeek ChatModel Bean（替代之前的 OpenAI 适配器）
     */
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
                .toolCallingManager(ToolCallingManager.builder().build())
                .retryTemplate(RetryTemplate.builder().build())
                .build();
    }

    /**
     * 模拟训练 Agent —— 负责情景模拟对话生成与评分
     */
    @Bean
    public ReactAgent simulationAgent(ChatModel chatModel, SimulationTools simulationTools) {
        return ReactAgent.builder()
                .name("simulation-agent")
                .description("阅谈智伴模拟训练专家，负责情景对话生成与社交能力评分")
                .systemPrompt("""
                        你是阅谈智伴的模拟训练专家 Agent。你的职责包括：
                        1. 根据用户选择的主题和角色性格，生成自然、沉浸式的模拟对话
                        2. 在对话结束后，对用户的社交沟通能力进行多维度评分
                        3. 提供具体、可操作的改进建议

                        评分维度：
                        - 表达力：语言是否清晰流畅、表达是否准确
                        - 亲和力：是否展现友善、共情和积极倾听
                        - 逻辑性：思维是否清晰、条理是否分明
                        """)
                .model(chatModel)
                .methodTools(simulationTools)
                .build();
    }
}
