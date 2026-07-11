package com.backend.service;

import com.backend.document.ConversationDocument;
import com.backend.model.*;
import com.backend.store.ConversationStore;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;

import com.backend.model.SessionSummary;
import java.util.*;

@Service
public class SimulationService {

    private final ChatClient chatClient;
    private final ConversationStore store;

    public SimulationService(ChatClient.Builder chatClientBuilder, ConversationStore store) {
        this.chatClient = chatClientBuilder.build();
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

    // 开始模拟：构建 system prompt，调用 AI 获取开场白，持久化会话
    public Map<String, Object> startSimulation(StartRequest req) {
        String sessionId = UUID.randomUUID().toString();
        String systemPrompt = buildSystemPrompt(req.getTheme(), req.getPersonality());

        String greeting = chatClient.prompt()
                .system(systemPrompt)
                .user("请用一句简短的开场白向我打招呼，不要超过40个字。")
                .call()
                .content();

        long now = System.currentTimeMillis();
        ChatMessage greetingMsg = new ChatMessage("1", "other", "", greeting, now);

        store.createSession(sessionId, req.getTheme(), req.getPersonality(),
                systemPrompt, greetingMsg);

        Map<String, Object> data = new HashMap<>();
        data.put("sessionId", sessionId);
        data.put("greeting", new GreetingReply(greeting, now));
        return data;
    }

    // 发送消息：从 MongoDB 加载历史，拼接后调用 AI，持久化新消息
    public Map<String, Object> sendMessage(SendRequest req) {
        String sessionId = req.getScenarioId();
        List<ChatMessage> history = store.getMessages(sessionId);

        // 构建用户消息并持久化
        long now = System.currentTimeMillis();
        ChatMessage userMsg = new ChatMessage(
                String.valueOf(history.size() + 1),
                "self", "", req.getMessage(), now);
        store.appendMessage(sessionId, userMsg);
        history.add(userMsg);

        // 构建 Spring AI Messages 列表
        String systemPrompt = store.getSystemPrompt(sessionId);
        List<Message> aiMessages = new ArrayList<>();
        aiMessages.add(new SystemMessage(systemPrompt));

        for (ChatMessage msg : history) {
            if ("other".equals(msg.getRole())) {
                aiMessages.add(new AssistantMessage(msg.getContent()));
            } else {
                aiMessages.add(new UserMessage(msg.getContent()));
            }
        }

        String reply = chatClient.prompt()
                .messages(aiMessages)
                .call()
                .content();

        long replyTs = System.currentTimeMillis();
        ChatMessage replyMsg = new ChatMessage(
                String.valueOf(history.size() + 1),
                "other", "", reply, replyTs);
        store.appendMessage(sessionId, replyMsg);

        Map<String, Object> data = new HashMap<>();
        data.put("reply", new MessageReply(reply, replyTs));
        return data;
    }

    // 获取会话历史（含评分）
    public Map<String, Object> getHistory(String sessionId) {
        List<ChatMessage> history = store.getMessages(sessionId);
        Map<String, Object> data = new HashMap<>();
        data.put("messages", history);
        // 附带评分数据（如果有）
        store.getEvaluation(sessionId).ifPresent(eval -> {
            Map<String, Object> evalMap = new HashMap<>();
            evalMap.put("score", store.getScore(sessionId).orElse(null));
            evalMap.put("expression", eval.getExpression());
            evalMap.put("affinity", eval.getAffinity());
            evalMap.put("logic", eval.getLogic());
            evalMap.put("comment", eval.getComment());
            evalMap.put("strengths", eval.getStrengths());
            evalMap.put("suggestions", eval.getSuggestions());
            data.put("evaluation", evalMap);
        });
        return data;
    }

    // 获取所有会话摘要列表
    public List<SessionSummary> getSessionList() {
        return store.getSessionSummaries();
    }

    // 对会话进行 AI 评分
    @SuppressWarnings("unchecked")
    public Map<String, Object> scoreConversation(String sessionId) {
        List<ChatMessage> history = store.getMessages(sessionId);
        if (history.isEmpty()) {
            throw new RuntimeException("会话无消息，无法评分");
        }

        // 构建对话文本
        StringBuilder dialogText = new StringBuilder();
        for (ChatMessage msg : history) {
            String speaker = "other".equals(msg.getRole()) ? "对方" : "我";
            dialogText.append(speaker).append("：").append(msg.getContent()).append("\n");
        }

        String scorePrompt = String.format("""
                你是一位专业的社交沟通能力评估专家。请根据以下对话内容，评估"我"的社交沟通能力。

                ## 对话内容
                %s

                ## 评分要求
                请以 JSON 格式返回评分结果，包含以下字段：
                - expression: 表达力评分 (0-100)，评估语言表达是否清晰、流畅
                - affinity: 亲和力评分 (0-100)，评估是否展现了友善、共情
                - logic: 逻辑性评分 (0-100)，评估思维是否清晰、条理是否分明
                - totalScore: 综合总分 (0-100)，取三个维度的加权平均
                - comment: 评语 (50-100字)，给出整体评价和改进方向
                - strengths: 优点标签数组，1-3个关键词
                - suggestions: 改进建议标签数组，1-3个关键词

                只返回JSON，不要输出其他内容。""",
                dialogText.toString());

        String result = chatClient.prompt()
                .user(scorePrompt)
                .call()
                .content();

        // 解析 JSON 结果
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            Map<String, Object> scoreData = mapper.readValue(result, Map.class);

            int totalScore = ((Number) scoreData.get("totalScore")).intValue();
            int expression = ((Number) scoreData.get("expression")).intValue();
            int affinity = ((Number) scoreData.get("affinity")).intValue();
            int logic = ((Number) scoreData.get("logic")).intValue();
            String comment = (String) scoreData.get("comment");

            List<String> strengths = (List<String>) scoreData.get("strengths");
            List<String> suggestions = (List<String>) scoreData.get("suggestions");

            // 持久化评分
            ConversationDocument.Evaluation evaluation = new ConversationDocument.Evaluation(
                    expression, affinity, logic, comment, strengths, suggestions);
            store.saveScore(sessionId, totalScore, evaluation);

            Map<String, Object> response = new HashMap<>();
            response.put("score", totalScore);
            response.put("evaluation", evaluation);
            return response;

        } catch (Exception e) {
            throw new RuntimeException("评分解析失败: " + e.getMessage(), e);
        }
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
