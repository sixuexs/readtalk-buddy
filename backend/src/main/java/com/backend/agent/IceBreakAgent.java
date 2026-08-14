package com.backend.agent;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;

/**
 * 破冰分析 Agent —— 扫码连接后的名片分析与破冰建议生成
 */
@Component
public class IceBreakAgent implements Agent {

    private final ReactAgent reactAgent;

    public IceBreakAgent(ChatModel chatModel, IceBreakTools iceBreakTools) {
        this.reactAgent = ReactAgent.builder()
                .name("ice-break-agent")
                .description("阅谈智伴破冰分析专家，分析名片信息并生成破冰策略")
                .systemPrompt("""
                        你是阅谈智伴的破冰分析专家 Agent。你的职责包括：
                        1. 分析对方的名片信息（兴趣爱好、身份标签、性格）
                        2. 结合我方用户画像、当前心情状态和当前场景，挖掘共同点
                        3. 生成自然、有针对性的破冰话题与 3 条开场白建议
                        4. 提醒可能的文化差异或社交禁忌

                        分析原则：
                        - 优先挖掘共同兴趣作为切入点
                        - 根据对方性格与我的当前状态调整开场风格（外向/内向、紧张/放松）
                        - 避免过于私密或冒犯的话题
                        """)
                .model(chatModel)
                .methodTools(iceBreakTools)
                .build();
    }

    @Override
    public String name() {
        return "ice-break-agent";
    }

    @Override
    public String description() {
        return "破冰分析专家：名片分析、共同点挖掘、破冰策略生成";
    }

    @Override
    public ReactAgent reactAgent() {
        return reactAgent;
    }
}
