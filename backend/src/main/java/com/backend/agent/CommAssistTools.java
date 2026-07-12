package com.backend.agent;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 沟通辅助 Agent 工具集 —— 实时语音/文字分析、态度识别、回复建议、表现评分
 */
@Component
public class CommAssistTools {

    private final ChatClient chatClient;
    // 暂用内存存储沟通会话（后续可迁移到 MongoDB）
    private final Map<String, List<Map<String, Object>>> activeSessions = new LinkedHashMap<>();

    public CommAssistTools(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    @Tool(description = "分析用户的一段语音/文字输入，识别态度和情绪，给出实时回复建议")
    public Map<String, Object> analyzeSpeech(
            @ToolParam(description = "用户说的话（语音转文字后的内容）") String text,
            @ToolParam(description = "当前对话上下文，如：与同事讨论项目、与朋友闲聊") String context,
            @ToolParam(description = "会话 ID，用于累积上下文") String sessionId) {

        String prompt = String.format("""
                你是一位实时沟通教练，正在观察用户与他人的对话。分析用户刚说的话，给出建议。

                ## 用户刚说的
                %s

                ## 对话情境
                %s

                ## 分析维度
                1. 态度识别：用户表现出的态度（积极/消极/中性/攻击性/回避）
                2. 情绪感知：用户的情绪状态
                3. 措辞评估：表达是否得体、清晰
                4. 改进建议：如果是你说这句话，你会怎么改进

                以 JSON 格式返回：
                {
                  "attitude": "积极/消极/中性/攻击性/回避",
                  "emotion": "情绪描述（10字以内）",
                  "clarity": "清晰/一般/模糊",
                  "betterVersion": "改进后的表达（如果原话已经很好则返回原话）",
                  "tip": "一条沟通小贴士（30字以内）"
                }
                只返回JSON。""",
                text, context);

        try {
            String result = chatClient.prompt().user(prompt).call().content();
            @SuppressWarnings("unchecked")
            Map<String, Object> analysis = new com.fasterxml.jackson.databind.ObjectMapper().readValue(result, Map.class);

            // 保存到会话历史
            Map<String, Object> record = new LinkedHashMap<>();
            record.put("text", text);
            record.put("analysis", analysis);
            record.put("timestamp", System.currentTimeMillis());
            activeSessions.computeIfAbsent(sessionId, k -> new ArrayList<>()).add(record);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("sessionId", sessionId);
            response.put("analysis", analysis);
            response.put("messageCount", activeSessions.get(sessionId).size());
            return response;

        } catch (Exception e) {
            return Map.of("status", "error", "message", "分析失败: " + e.getMessage());
        }
    }

    @Tool(description = "沟通结束后，对用户整场沟通表现进行综合评分")
    public Map<String, Object> scorePerformance(
            @ToolParam(description = "会话 ID") String sessionId) {

        List<Map<String, Object>> records = activeSessions.getOrDefault(sessionId, List.of());
        if (records.isEmpty()) {
            return Map.of("status", "no_data", "message", "该会话无沟通记录");
        }

        // 汇总统计
        int positive = 0, neutral = 0, negative = 0;
        StringBuilder transcript = new StringBuilder();
        for (var r : records) {
            transcript.append("用户：").append(r.get("text")).append("\n");
            @SuppressWarnings("unchecked")
            var analysis = (Map<String, Object>) r.get("analysis");
            if (analysis != null) {
                String attitude = (String) analysis.get("attitude");
                if ("积极".equals(attitude)) positive++;
                else if ("消极".equals(attitude) || "攻击性".equals(attitude)) negative++;
                else neutral++;
            }
        }
        int total = records.size();

        // AI 综合评分
        String prompt = String.format("""
                你是一位沟通能力评估专家。以下是用户在一次对话中的所有发言和分析记录，请给出综合评分。

                ## 统计数据
                - 总发言数：%d
                - 积极表达：%d 次
                - 中性表达：%d 次
                - 消极/攻击性：%d 次

                ## 发言记录
                %s

                以 JSON 格式返回综合评估：
                {
                  "overallScore": 综合分(0-100),
                  "attitudeScore": 态度分(0-100),
                  "clarityScore": 清晰度分(0-100),
                  "empathyScore": 共情分(0-100),
                  "comment": "80字以内综合评价",
                  "highlights": ["亮点1"],
                  "improvements": ["改进点1", "改进点2"]
                }
                只返回JSON。""",
                total, positive, neutral, negative, transcript.toString());

        try {
            String result = chatClient.prompt().user(prompt).call().content();
            @SuppressWarnings("unchecked")
            Map<String, Object> scoreResult = new com.fasterxml.jackson.databind.ObjectMapper().readValue(result, Map.class);

            // 清理会话
            activeSessions.remove(sessionId);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("sessionId", sessionId);
            response.put("messageCount", total);
            response.put("evaluation", scoreResult);
            return response;

        } catch (Exception e) {
            return Map.of("status", "error", "message", "评分失败: " + e.getMessage());
        }
    }
}
