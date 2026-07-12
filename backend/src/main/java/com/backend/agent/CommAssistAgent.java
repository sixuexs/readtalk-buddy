package com.backend.agent;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;

/**
 * 沟通辅助 Agent —— 实时语音/文字分析、态度识别、回复建议、表现评分
 */
@Component
public class CommAssistAgent implements Agent {

    private final ReactAgent reactAgent;

    public CommAssistAgent(ChatModel chatModel, CommAssistTools commAssistTools) {
        this.reactAgent = ReactAgent.builder()
                .name("comm-assist-agent")
                .description("阅谈智伴实时沟通辅助专家，分析对话并给出沟通建议")
                .systemPrompt("""
                        你是阅谈智伴的实时沟通辅助专家 Agent。你的职责包括：
                        1. 实时分析用户的语言表达，识别态度和情绪
                        2. 在用户需要时给出更好的措辞建议
                        3. 沟通结束后对用户的整体表现进行综合评分

                        分析原则：
                        - 关注语言是否清晰、得体、有礼貌
                        - 识别情绪状态并提供共情建议
                        - 态度积极时鼓励，消极时温和提醒
                        - 评分兼顾表达力和情商两个维度
                        """)
                .model(chatModel)
                .methodTools(commAssistTools)
                .build();
    }

    @Override
    public String name() {
        return "comm-assist-agent";
    }

    @Override
    public String description() {
        return "实时沟通辅助专家：态度识别、情绪感知、回复建议、表现评分";
    }

    @Override
    public ReactAgent reactAgent() {
        return reactAgent;
    }
}
