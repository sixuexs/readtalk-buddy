package com.backend.service;

import com.backend.client.DeepSeekClient;
import com.backend.model.*;
import com.backend.store.ConversationStore;
import org.springframework.stereotype.Service;

import com.backend.model.SessionSummary;
import java.util.*;

@Service
public class SimulationService {

    private final DeepSeekClient deepSeekClient;
    private final ConversationStore store;

    public SimulationService(DeepSeekClient deepSeekClient, ConversationStore store) {
        this.deepSeekClient = deepSeekClient;
        this.store = store;
    }

    // 获取可用配置
    public SimulationConfig getConfig() {
        SimulationConfig config = new SimulationConfig();
        config.setThemes(List.of("初次见面", "读书交流", "读后感分享", "面试演练", "日常闲聊"));
        config.setPersonalities(List.of("乐观开朗自来熟", "不善交际慢热", "幽默风趣社牛", "沉稳内敛观察者"));
        return config;
    }

    // 获取场景信息
    public ScenarioInfo getScenario(String scenarioId) {
        ScenarioInfo info = new ScenarioInfo();
        info.setScenarioId(scenarioId);
        info.setTitle("情景模拟");
        info.setDescription("模拟社交场景，练习沟通技巧");
        return info;
    }

    // 开始模拟：构建 system prompt，调用 DeepSeek 获取开场白，持久化会话
    public Map<String, Object> startSimulation(StartRequest req) {
        String sessionId = UUID.randomUUID().toString();
        String systemPrompt = buildSystemPrompt(req.getTheme(), req.getPersonality());

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemPrompt));
        messages.add(Map.of("role", "user", "content", "请用一句简短的开场白向我打招呼，不要超过40个字。"));

        String greeting = deepSeekClient.chat(messages);

        long now = System.currentTimeMillis();
        ChatMessage greetingMsg = new ChatMessage("1", "other", "", greeting, now);

        // 持久化：保存会话信息、systemPrompt 和第一条消息
        store.createSession(sessionId, req.getTheme(), req.getPersonality(),
                systemPrompt, greetingMsg);

        Map<String, Object> data = new HashMap<>();
        data.put("sessionId", sessionId);
        data.put("greeting", new GreetingReply(greeting, now));
        return data;
    }

    // 发送消息：从 MongoDB 加载历史，拼接后调用 DeepSeek，持久化新消息
    public Map<String, Object> sendMessage(SendRequest req) {
        String sessionId = req.getScenarioId();
        List<ChatMessage> history = store.getMessages(sessionId);

        // 构建用户消息并持久化
        long now = System.currentTimeMillis();
        ChatMessage userMsg = new ChatMessage(
                String.valueOf(history.size() + 1),
                "self", "", req.getMessage(), now);
        store.appendMessage(sessionId, userMsg);
        history.add(userMsg); // 加入内存列表，用于构建 DeepSeek 请求

        // 从数据库获取真实的 systemPrompt（修复之前硬编码 prompt 的问题）
        String systemPrompt = store.getSystemPrompt(sessionId);
        List<Map<String, String>> deepseekMessages = new ArrayList<>();
        deepseekMessages.add(Map.of("role", "system", "content", systemPrompt));

        for (ChatMessage msg : history) {
            String role = "other".equals(msg.getRole()) ? "assistant" : "user";
            deepseekMessages.add(Map.of("role", role, "content", msg.getContent()));
        }

        String reply = deepSeekClient.chat(deepseekMessages);

        long replyTs = System.currentTimeMillis();
        ChatMessage replyMsg = new ChatMessage(
                String.valueOf(history.size() + 1),
                "other", "", reply, replyTs);
        store.appendMessage(sessionId, replyMsg);

        Map<String, Object> data = new HashMap<>();
        data.put("reply", new MessageReply(reply, replyTs));
        return data;
    }

    // 获取会话历史
    public Map<String, Object> getHistory(String sessionId) {
        List<ChatMessage> history = store.getMessages(sessionId);
        Map<String, Object> data = new HashMap<>();
        data.put("messages", history);
        return data;
    }

    // 获取所有会话摘要列表
    public List<SessionSummary> getSessionList() {
        return store.getSessionSummaries();
    }

    // 根据主题和性格构建角色 system prompt
    private String buildSystemPrompt(String theme, String personality) {
        return String.format(
                "你正在参与一个名为「%s」的情景模拟对话。" +
                "你的性格设定是：%s。" +
                "请完全按照这个角色设定来回复，保持自然、流畅的对话风格。每次回复不超过100个字。",
                theme, personality
        );
    }
}
