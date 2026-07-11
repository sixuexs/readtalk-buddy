package com.backend.agent;

import com.backend.document.ConversationDocument;
import com.backend.model.ChatMessage;
import com.backend.service.SimulationService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 模拟训练 Agent 的工具集 —— 将 SimulationService 的能力暴露为 LLM 可调用的 Tool
 */
@Component
public class SimulationTools {

    private final SimulationService service;

    public SimulationTools(SimulationService service) {
        this.service = service;
    }

    @Tool(description = "开始一个新的情景模拟对话。传入对话主题和对方性格，返回 sessionId 和 AI 开场白")
    public Map<String, Object> startSimulation(
            @ToolParam(description = "对话主题，如：初次见面、读书交流、面试演练") String theme,
            @ToolParam(description = "对方角色性格，如：乐观开朗自来熟、沉稳内敛观察者") String personality) {
        var req = new com.backend.model.StartRequest();
        req.setTheme(theme);
        req.setPersonality(personality);
        return service.startSimulation(req);
    }

    @Tool(description = "在已有会话中发送消息并获取 AI 回复。需要传入 sessionId 和消息内容")
    public Map<String, Object> sendMessage(
            @ToolParam(description = "会话 ID") String sessionId,
            @ToolParam(description = "用户发送的消息内容") String message) {
        var req = new com.backend.model.SendRequest();
        req.setScenarioId(sessionId);
        req.setMessage(message);
        return service.sendMessage(req);
    }

    @Tool(description = "对指定会话的完整对话进行 AI 评分，返回总分、维度分（表达力/亲和力/逻辑性）、评语、优点标签、改进建议")
    public Map<String, Object> scoreConversation(
            @ToolParam(description = "会话 ID") String sessionId) {
        return service.scoreConversation(sessionId);
    }
}
