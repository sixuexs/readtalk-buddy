package com.backend.agent;

import com.backend.document.ConversationDocument;
import com.backend.document.UserProfileDocument;
import com.backend.repository.ConversationRepository;
import com.backend.repository.UserProfileRepository;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 用户画像 Agent 工具集
 */
@Component
public class UserProfileTools {

    private final UserProfileRepository profileRepo;
    private final ConversationRepository conversationRepo;
    private final ChatClient chatClient;

    public UserProfileTools(UserProfileRepository profileRepo,
                            ConversationRepository conversationRepo,
                            ChatClient.Builder chatClientBuilder) {
        this.profileRepo = profileRepo;
        this.conversationRepo = conversationRepo;
        this.chatClient = chatClientBuilder.build();
    }

    @Tool(description = "获取用户社交能力画像，包含各维度平均分、趋势数据、优劣势、提升计划")
    public Map<String, Object> getProfile() {
        var profile = profileRepo.findDefault().orElse(null);
        if (profile == null) {
            return Map.of("exists", false, "message", "暂无评估数据，先完成几次情景模拟对话吧");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("exists", true);
        result.put("avgClarity", profile.getAvgClarity());
        result.put("avgLogicality", profile.getAvgLogicality());
        result.put("avgEmpathyListening", profile.getAvgEmpathyListening());
        result.put("avgInteractivity", profile.getAvgInteractivity());
        result.put("avgRelaxation", profile.getAvgRelaxation());
        result.put("overallScore", profile.getOverallScore());
        result.put("totalSessions", profile.getTotalSessions());
        result.put("assessment", profile.getAssessment());
        result.put("topStrengths", profile.getTopStrengths());
        result.put("topWeaknesses", profile.getTopWeaknesses());
        result.put("improvementPlan", profile.getImprovementPlan());
        result.put("weeklyGoals", profile.getWeeklyGoals());
        result.put("scoreHistory", profile.getScoreHistory());
        return result;
    }

    @Tool(description = "基于用户历史评分数据，生成或更新用户能力评估和个性化提升计划")
    public Map<String, Object> assessAbility() {
        // 获取所有已评分会话
        List<ConversationDocument> scored = conversationRepo.findAllByOrderByCreatedAtDesc()
                .stream()
                .filter(d -> d.getScore() != null && d.getEvaluation() != null)
                .collect(Collectors.toList());

        if (scored.isEmpty()) {
            return Map.of("status", "no_data", "message", "暂无已评分的会话");
        }

        // 计算各维度加权平均分
        int totalSessions = scored.size();
        double avgClarity = scored.stream().mapToInt(d -> d.getEvaluation().getClarity()).average().orElse(0);
        double avgLogicality = scored.stream().mapToInt(d -> d.getEvaluation().getLogicality()).average().orElse(0);
        double avgEmpathyListening = scored.stream().mapToInt(d -> d.getEvaluation().getEmpathyListening()).average().orElse(0);
        double avgInteractivity = scored.stream().mapToInt(d -> d.getEvaluation().getInteractivity()).average().orElse(0);
        double avgRelaxation = scored.stream().mapToInt(d -> d.getEvaluation().getRelaxation()).average().orElse(0);
        int overall = (int) ((avgClarity + avgLogicality + avgEmpathyListening + avgInteractivity + avgRelaxation) / 5.0);

        // 收集评分历史
        List<UserProfileDocument.ScoreRecord> history = scored.stream()
                .map(d -> new UserProfileDocument.ScoreRecord(
                        d.getId(), d.getTheme(),
                        d.getScore(), d.getEvaluation().getClarity(),
                        d.getEvaluation().getLogicality(), d.getEvaluation().getEmpathyListening(),
                        d.getEvaluation().getInteractivity(), d.getEvaluation().getRelaxation(),
                        d.getUpdatedAt()))
                .collect(Collectors.toList());

        // 汇总所有评语，调用 AI 生成总体评估和提升计划
        String allComments = scored.stream()
                .map(d -> d.getTheme() + "：" + d.getEvaluation().getComment())
                .collect(Collectors.joining("\n"));

        String profilePrompt = String.format("""
                你是一位社交能力评估专家。根据以下用户历史情景模拟的评分记录，生成用户画像报告。

                ## 评分统计
                - 累计会话数：%d
                - 清晰度均分：%.0f
                - 逻辑思辨力均分：%.0f
                - 共情倾听均分：%.0f
                - 互动积极性均分：%.0f
                - 情绪松弛度均分：%.0f
                - 综合评分：%d

                ## 历史评语
                %s

                ## 输出要求
                以 JSON 格式返回：
                {
                  "assessment": "100字以内总体评价",
                  "topStrengths": ["优势1", "优势2"],
                  "topWeaknesses": ["短板1", "短板2"],
                  "improvementPlan": "200字以内个性化提升计划",
                  "weeklyGoals": ["每周目标1", "每周目标2", "每周目标3"]
                }
                只返回JSON。""",
                totalSessions, avgClarity, avgLogicality, avgEmpathyListening, avgInteractivity, avgRelaxation, overall, allComments);

        try {
            String result = chatClient.prompt().user(profilePrompt).call().content();
            @SuppressWarnings("unchecked")
            Map<String, Object> aiResult = new com.fasterxml.jackson.databind.ObjectMapper().readValue(result, Map.class);

            // 持久化
            UserProfileDocument profile = profileRepo.findDefault()
                    .orElse(new UserProfileDocument());
            profile.setId("default");
            if (profile.getUserId() == null) {
                profile.setUserId(1L);  // 单用户模式，与 /api/user 默认 userId 对齐
            }
            profile.setAvgClarity((int) avgClarity);
            profile.setAvgLogicality((int) avgLogicality);
            profile.setAvgEmpathyListening((int) avgEmpathyListening);
            profile.setAvgInteractivity((int) avgInteractivity);
            profile.setAvgRelaxation((int) avgRelaxation);
            profile.setOverallScore(overall);
            profile.setScoreHistory(history);
            profile.setAssessment((String) aiResult.get("assessment"));
            profile.setTopStrengths((List<String>) aiResult.get("topStrengths"));
            profile.setTopWeaknesses((List<String>) aiResult.get("topWeaknesses"));
            profile.setImprovementPlan((String) aiResult.get("improvementPlan"));
            profile.setWeeklyGoals((List<String>) aiResult.get("weeklyGoals"));
            profile.setTotalSessions(totalSessions);
            profile.setLastUpdated(LocalDateTime.now());
            profileRepo.save(profile);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("overallScore", overall);
            response.put("avgClarity", (int) avgClarity);
            response.put("avgLogicality", (int) avgLogicality);
            response.put("avgEmpathyListening", (int) avgEmpathyListening);
            response.put("avgInteractivity", (int) avgInteractivity);
            response.put("avgRelaxation", (int) avgRelaxation);
            response.put("totalSessions", totalSessions);
            response.put("assessment", aiResult.get("assessment"));
            response.put("topStrengths", aiResult.get("topStrengths"));
            response.put("topWeaknesses", aiResult.get("topWeaknesses"));
            response.put("improvementPlan", aiResult.get("improvementPlan"));
            response.put("weeklyGoals", aiResult.get("weeklyGoals"));
            return response;

        } catch (Exception e) {
            return Map.of("status", "error", "message", "生成画像失败: " + e.getMessage());
        }
    }
}
