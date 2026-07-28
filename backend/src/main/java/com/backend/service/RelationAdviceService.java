package com.backend.service;

import com.backend.document.ContactDocument;
import com.backend.document.UserProfileDocument;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.*;

/**
 * E 模块 C4 — 个性化关系建议引擎（独立 LLM 生成器）。
 *
 * 职责：纯 LLM 生成文本，无副作用。冷却/挽救状态仍由 RelationTools 控制。
 */
@Service
@Slf4j
public class RelationAdviceService {

    private final ChatClient chatClient;
    private final ObjectMapper mapper = new ObjectMapper();

    public RelationAdviceService(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    /** 上下文（由 RelationTools 组装）*/
    public record AdviceContext(
        int daysSinceLastContact,
        int intimacyScore,
        String warningType,   // "疏远预警" or null
        String warningLevel    // "RED" / "ORANGE" / "YELLOW" — from detectDrift.severity mapping
    ) {}

    /** 输出 schema（对齐设计）*/
    public record AdviceResult(
        List<String> entryTopics,
        String openingLine,
        List<String> cautions,
        List<String> recoverSteps,
        String expectation
    ) {}

    /** 纯 LLM 生成建议（无副作用）。*/
    public AdviceResult generatePersonalizedAdvice(
            ContactDocument contact, UserProfileDocument profile, AdviceContext ctx) {

        // severity -> warningLevel mapping
        String severity = ctx.warningLevel() != null ? ctx.warningLevel() : "YELLOW";
        String toneRule = switch (severity) {
            case "RED" -> "强调关系价值、禁止施压或制造焦虑";
            case "ORANGE" -> "明确建议具体行动";
            default -> "轻松提醒，自然关怀";
        };

        String interests = contact.getInterests() != null && !contact.getInterests().isEmpty()
                ? String.join("、", contact.getInterests()) : "未知";
        String labels = contact.getLabels() != null && !contact.getLabels().isEmpty()
                ? String.join("、", contact.getLabels()) : "无";
        String personality = contact.getPersonality() != null ? contact.getPersonality() : "未知";
        String strengths = profile != null && profile.getTopStrengths() != null
                && !profile.getTopStrengths().isEmpty()
                ? String.join("、", profile.getTopStrengths()) : "暂无";

        boolean hasWeaknesses = profile != null && profile.getTopWeaknesses() != null
                && !profile.getTopWeaknesses().isEmpty();
        String weaknesses = hasWeaknesses
                ? String.join("、", profile.getTopWeaknesses()) : "暂无明显不足";
        String weaknessRule = hasWeaknesses
                ? "用户有不足，给\"可直接说出口的具体话术\"（如\"你可以说：上次聊到摄影，你拍的风景真好看\"），而非抽象要求（如\"多找共同话题\"）。"
                : "用户暂无明显不足，不需给改进话术。";

        String prompt = String.format("""
            你是关系维护顾问。根据以下信息为用户生成个性化关系建议。

            ## 联系人画像
            - 姓名：%s
            - 关系类型：%s
            - 兴趣爱好：%s
            - 身份标签：%s
            - 性格：%s

            ## 用户画像
            - 优势：%s
            - 不足：%s

            ## 关系现状
            - 上次联系：%d 天前
            - 亲密度：%d/100
            - 预警类型：%s
            - 预警级别：%s

            ## 硬约束
            1. entryTopics 至少 1 条显式引用联系人的兴趣爱好 / 性格 / 身份标签之一。
            2. %s
            3. 语气按预警级别：%s。
            4. 禁止居高临下或制造愧疚/焦虑的措辞（如"你再不联系就晚了""你怎么又冷落了"）。

            ## 备注
            无上次交流内容，据画像+关系现状给建议。

            请严格按以下 JSON 格式返回（只返回 JSON，不要包裹 markdown 代码块）：
            {
              "entryTopics": ["话题1", "话题2"],
              "openingLine": "一条自然开场白",
              "cautions": ["注意事项1", "注意事项2"],
              "recoverSteps": ["挽救步骤1", "挽救步骤2"],
              "expectation": "预期效果一句话"
            }""",
            contact.getName(),
            contact.getRelationType() != null ? contact.getRelationType() : "未知",
            interests, labels, personality,
            strengths, weaknesses,
            ctx.daysSinceLastContact(), ctx.intimacyScore(),
            ctx.warningType() != null ? ctx.warningType() : "无",
            severity,
            weaknessRule,
            toneRule
        );

        // TODO[表达层完整化]: 支持 mode（light/full）差异化输出
        try {
            String response = chatClient.prompt().user(prompt).call().content();
            String json = stripMarkdown(response);
            @SuppressWarnings("unchecked")
            Map<String, Object> map = mapper.readValue(json, Map.class);
            return new AdviceResult(
                (List<String>) map.getOrDefault("entryTopics", List.of()),
                (String) map.getOrDefault("openingLine", ""),
                (List<String>) map.getOrDefault("cautions", List.of()),
                (List<String>) map.getOrDefault("recoverSteps", List.of()),
                (String) map.getOrDefault("expectation", "")
            );
        } catch (Exception e) {
            log.warn("LLM 生成建议失败，使用规则 fallback: {}", e.getMessage());
            return fallback(contact);
        }
    }

    /** 去除 LLM 响应中的 markdown 代码块包裹，提取 JSON 子串。*/
    private String stripMarkdown(String response) {
        if (response == null) return "";
        String s = response.trim();
        if (s.startsWith("```")) {
            int start = s.indexOf('\n');
            int end = s.lastIndexOf("```");
            if (start >= 0 && end > start) {
                s = s.substring(start + 1, end).trim();
            }
        }
        // 兜底：提取最外层 {...} 子串
        if (!s.startsWith("{")) {
            int l = s.indexOf('{');
            int r = s.lastIndexOf('}');
            if (l >= 0 && r > l) {
                s = s.substring(l, r + 1);
            }
        }
        return s;
    }

    /** 规则 fallback — 同 schema，不抛异常。*/
    private AdviceResult fallback(ContactDocument contact) {
        List<String> interests = contact.getInterests() != null ? contact.getInterests() : List.of();
        String personality = contact.getPersonality() != null ? contact.getPersonality() : "";

        List<String> entryTopics = new ArrayList<>();
        for (String i : interests) {
            entryTopics.add("最近在" + i + "上有什么新收获？");
        }
        if (entryTopics.isEmpty()) entryTopics.add("最近过得怎么样？");

        List<String> cautions = new ArrayList<>();
        if (personality.contains("内向") || personality.contains("敏感")) {
            cautions.add("注意语气温和，给对方足够的回应空间");
        }
        cautions.add("避免谈论过于私密的话题，先建立舒适感");

        return new AdviceResult(
            entryTopics,
            "好久不见，最近还好吗？",
            cautions,
            List.of("每隔几天发一次消息，保持自然频率", "关注对方朋友圈，适时互动"),
            "重新建立联系，让关系回归自然状态"
        );
    }
}
