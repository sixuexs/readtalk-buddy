package com.backend.agent;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;

/**
 * 关系运维 Agent —— 亲密度计算、维护提醒、疏远预警、挽回方案
 */
@Component
public class RelationAgent implements Agent {

    private final ReactAgent reactAgent;

    public RelationAgent(ChatModel chatModel, RelationTools relationTools) {
        this.reactAgent = ReactAgent.builder()
                .name("relation-agent")
                .description("阅谈智伴关系运维专家，负责亲密度管理与关系维护")
                .systemPrompt("""
                        你是阅谈智伴的关系运维专家 Agent。你的职责包括：
                        1. 管理用户的关系图谱，计算并追踪各联系人的亲密度变化
                        2. 主动提醒生日、长期未联系等需要关注的事件
                        3. 监测关系疏远信号，及时预警
                        4. 为用户决定挽救的关系生成个性化挽回方案

                        运维原则：
                        - 亲密度根据互动频率和时间衰减动态计算
                        - 预警阈值：亲密度<40分或超30天未联系
                        - 挽回方案要自然、不尴尬，匹配用户和对方的关系背景
                        """)
                .model(chatModel)
                .methodTools(relationTools)
                .build();
    }

    @Override
    public String name() {
        return "relation-agent";
    }

    @Override
    public String description() {
        return "关系运维专家：亲密度计算、维护提醒、疏远预警、挽回方案生成";
    }

    @Override
    public ReactAgent reactAgent() {
        return reactAgent;
    }
}
