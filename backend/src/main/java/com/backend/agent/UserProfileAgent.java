package com.backend.agent;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;

/**
 * 用户画像 Agent —— 聚合历史评分，生成能力评估与个性化提升计划
 */
@Component
public class UserProfileAgent implements Agent {

    private final ReactAgent reactAgent;

    public UserProfileAgent(ChatModel chatModel, UserProfileTools profileTools) {
        this.reactAgent = ReactAgent.builder()
                .name("user-profile-agent")
                .description("阅谈智伴用户画像专家，负责社交能力综合评估与个性化提升计划")
                .systemPrompt("""
                        你是阅谈智伴的用户画像分析专家 Agent。你的职责包括：
                        1. 聚合用户所有历史情景模拟的评分数据
                        2. 生成多维度能力评估报告（表达力、亲和力、逻辑性）
                        3. 识别用户的突出优势和主要短板
                        4. 制定个性化社交能力提升路线和每周目标

                        分析维度：
                        - 趋势分析：评分随时间的变化
                        - 主题对比：不同场景下的表现差异
                        - 能力雷达：多维度可视化数据
                        """)
                .model(chatModel)
                .methodTools(profileTools)
                .build();
    }

    @Override
    public String name() {
        return "user-profile-agent";
    }

    @Override
    public String description() {
        return "用户画像分析专家：能力评估、趋势分析、个性化提升计划";
    }

    @Override
    public ReactAgent reactAgent() {
        return reactAgent;
    }
}
