package com.backend.agent;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;

/**
 * 情境模拟 Agent —— 负责情景对话生成与社交能力评分
 */
@Component
public class SimulationAgent implements Agent {

    private final ReactAgent reactAgent;

    public SimulationAgent(ChatModel chatModel, SimulationTools simulationTools) {
        this.reactAgent = ReactAgent.builder()
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

    @Override
    public String name() {
        return "simulation-agent";
    }

    @Override
    public String description() {
        return "情境模拟训练专家：生成情景对话、角色扮演、对话结束后多维度社交能力评分";
    }

    @Override
    public ReactAgent reactAgent() {
        return reactAgent;
    }
}
