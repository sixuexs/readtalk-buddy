package com.backend.agent;

import com.backend.document.ContactDocument;
import com.backend.repository.ContactRepository;
import com.backend.repository.UserProfileRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 破冰分析 Agent 工具集 —— 扫码连接后的名片分析与破冰建议生成
 */
@Component
@Slf4j
public class IceBreakTools {

    private final ContactRepository contactRepo;
    private final UserProfileRepository profileRepo;
    private final ChatClient chatClient;
    private final ApplicationEventPublisher eventPublisher;

    public IceBreakTools(ContactRepository contactRepo, UserProfileRepository profileRepo,
                         ChatClient.Builder chatClientBuilder,
                         ApplicationEventPublisher eventPublisher) {
        this.contactRepo = contactRepo;
        this.profileRepo = profileRepo;
        this.chatClient = chatClientBuilder.build();
        this.eventPublisher = eventPublisher;
    }

    /** 单类建议再生成（不建联系人、不发事件）。section: openings / topics / warnings */
    public Map<String, Object> refreshSection(
            List<String> myInterests, List<String> myLabels, List<String> myMood,
            List<String> otherInterests, List<String> otherLabels, String otherPersonality,
            String context, String section) {

        Set<String> commonInterests = new HashSet<>(myInterests);
        commonInterests.retainAll(otherInterests);
        String moodText = myMood == null || myMood.isEmpty()
                ? "无特别状态"
                : String.join("、", myMood);

        String task = switch (section) {
            case "topics" ->
                    "生成 3 条建议话题，优先基于共同兴趣，返回 JSON：{\"topics\": [\"话题1\", \"话题2\", \"话题3\"]}";
            case "warnings" ->
                    "生成 3 条避雷提醒（结合对方性格与当前场景），返回 JSON：{\"warnings\": [\"避雷1\", \"避雷2\", \"避雷3\"]}";
            default ->
                    "生成 3 条不同风格的自然开场白，兼顾我方当前状态（如紧张时给更放松的切入方式），返回 JSON：{\"openings\": [\"开场白1\", \"开场白2\", \"开场白3\"]}";
        };

        String prompt = String.format("""
                你是一位社交破冰专家。根据以下信息，%s

                ## 我方信息
                - 兴趣爱好：%s
                - 身份标签：%s
                - 当前心情/状态：%s

                ## 对方信息
                - 兴趣爱好：%s
                - 身份标签：%s
                - 性格：%s

                ## 当前场景
                %s

                ## 共同兴趣
                %s

                只返回JSON，不要输出其他内容。""",
                task,
                String.join("、", myInterests),
                String.join("、", myLabels),
                moodText,
                String.join("、", otherInterests),
                String.join("、", otherLabels),
                otherPersonality,
                context,
                commonInterests.isEmpty() ? "无明显共同兴趣" : String.join("、", commonInterests));

        try {
            String result = chatClient.prompt().user(prompt).call().content();
            if (result == null || result.isBlank()) {
                return Map.of("status", "error", "message", "AI 未返回内容，请重试");
            }
            String json = stripMarkdown(result);
            @SuppressWarnings("unchecked")
            Map<String, Object> aiResult = new com.fasterxml.jackson.databind.ObjectMapper().readValue(json, Map.class);
            List<String> items = aiResult.get(section) instanceof List<?> list
                    ? list.stream().map(String::valueOf).toList()
                    : List.of();
            return Map.of("section", section, "items", items);
        } catch (Exception e) {
            return Map.of("status", "error", "message", "建议再生成失败: " + e.getMessage());
        }
    }

    /** 去除 LLM 响应中的 markdown 代码块包裹，提取 JSON 子串。 */
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
        if (!s.startsWith("{")) {
            int l = s.indexOf('{');
            int r = s.lastIndexOf('}');
            if (l >= 0 && r > l) {
                s = s.substring(l, r + 1);
            }
        }
        return s;
    }

    @Tool(description = "分析对方名片和当前情境，生成破冰建议。传入双方名片信息、我的心情状态和当前场景类型；若对方已在通讯录中传 contactId 复用，不重复建档")
    public Map<String, Object> analyzeCard(
            @ToolParam(description = "我方的兴趣爱好列表") List<String> myInterests,
            @ToolParam(description = "我方的身份标签") List<String> myLabels,
            @ToolParam(description = "我的心情/状态标签，如紧张、兴奋、疲惫") List<String> myMood,
            @ToolParam(description = "对方的兴趣爱好列表") List<String> otherInterests,
            @ToolParam(description = "对方的身份标签") List<String> otherLabels,
            @ToolParam(description = "对方的性格描述") String otherPersonality,
            @ToolParam(description = "当前场景，如：聚会、工作会议、初次见面") String context,
            @ToolParam(description = "已有联系人的 MongoDB id（可空）；非空时复用该联系人，不新建") String contactId) {

        // 找共同兴趣
        Set<String> commonInterests = new HashSet<>(myInterests);
        commonInterests.retainAll(otherInterests);

        String moodText = myMood == null || myMood.isEmpty()
                ? "无特别状态"
                : String.join("、", myMood);

        // 构建分析 prompt
        String prompt = String.format("""
                你是一位社交破冰专家。分析以下情境，生成破冰建议。

                ## 我方信息
                - 兴趣爱好：%s
                - 身份标签：%s
                - 当前心情/状态：%s

                ## 对方信息
                - 兴趣爱好：%s
                - 身份标签：%s
                - 性格：%s

                ## 当前场景
                %s

                ## 共同兴趣
                %s

                ## 输出要求
                以 JSON 格式返回：
                {
                  "openings": ["开场白1", "开场白2", "开场白3"],
                  "topics": ["建议话题1", "建议话题2", "建议话题3"],
                  "warnings": ["避雷1", "避雷2"]
                }
                要求：
                - openings 给 3 条不同风格的自然开场白，兼顾我方当前状态（如紧张时给更放松的切入方式）
                - topics 给 3 条建议话题，优先基于共同兴趣
                - warnings 给需要避开的雷区（结合对方性格与场景）
                只返回JSON。""",
                String.join("、", myInterests),
                String.join("、", myLabels),
                moodText,
                String.join("、", otherInterests),
                String.join("、", otherLabels),
                otherPersonality,
                context,
                commonInterests.isEmpty() ? "无明显共同兴趣" : String.join("、", commonInterests));

        try {
            String result = chatClient.prompt().user(prompt).call().content();
            if (result == null || result.isBlank()) {
                throw new RuntimeException("AI 未返回内容（推理预算可能耗尽），请重试");
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> aiResult = new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue(stripMarkdown(result), Map.class);

            // 联系人归位：传了 contactId 复用 → 按名字去重复用 → 全新才建档
            ContactDocument contact = resolveContact(contactId, otherLabels);
            boolean isNew = false;
            if (contact == null) {
                contact = new ContactDocument();
                contact.setName(otherLabels.isEmpty() ? "新联系人" : otherLabels.get(0));
                contact.setRelationType("朋友");
                contact.setIntimacy(30);  // 初始亲密度
                contact.setLastContactDays(0);
                contact.setCreatedAt(LocalDateTime.now());
                isNew = true;
            }
            // 名片信息始终以本次分析为准刷新（虚拟人物第二次选中走 name 复用分支时同样更新）
            contact.setInterests(otherInterests);
            contact.setLabels(otherLabels);
            contact.setPersonality(otherPersonality);
            contact.setUpdatedAt(LocalDateTime.now());
            ContactDocument saved = contactRepo.save(contact);

            // 仅新联系人发布 ContactAdded 事件 → RelationAgent 可联动
            if (isNew) {
                eventPublisher.publishEvent(new AgentEvent.ContactAdded(saved.getId(), saved.getName()));
            }

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("contactId", saved.getId());
            response.put("contact", saved);
            response.put("analysis", aiResult);
            return response;

        } catch (Exception e) {
            return Map.of("status", "error", "message", "破冰分析失败: " + e.getMessage());
        }
    }

    /** 归位联系人：优先按 contactId 查；查不到则按名字去重（防止虚拟人物/扫码重复建档）。包级可见便于测试。 */
    ContactDocument resolveContact(String contactId, List<String> otherLabels) {
        if (contactId != null && !contactId.isBlank()) {
            ContactDocument byId = contactRepo.findById(contactId).orElse(null);
            if (byId != null) return byId;
            log.warn("analyzeCard: contactId {} 不存在，回退名字去重", contactId);
        }
        String name = otherLabels.isEmpty() ? "新联系人" : otherLabels.get(0);
        return contactRepo.findFirstByName(name);
    }
}
