package com.backend.agent;

import com.backend.document.ContactDocument;
import com.backend.repository.ContactRepository;
import com.backend.repository.UserProfileRepository;
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

    @Tool(description = "分析对方名片和当前情境，生成破冰建议。传入双方名片信息、我的心情状态和当前场景类型")
    public Map<String, Object> analyzeCard(
            @ToolParam(description = "我方的兴趣爱好列表") List<String> myInterests,
            @ToolParam(description = "我方的身份标签") List<String> myLabels,
            @ToolParam(description = "我的心情/状态标签，如紧张、兴奋、疲惫") List<String> myMood,
            @ToolParam(description = "对方的兴趣爱好列表") List<String> otherInterests,
            @ToolParam(description = "对方的身份标签") List<String> otherLabels,
            @ToolParam(description = "对方的性格描述") String otherPersonality,
            @ToolParam(description = "当前场景，如：聚会、工作会议、初次见面") String context) {

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
            @SuppressWarnings("unchecked")
            Map<String, Object> aiResult = new com.fasterxml.jackson.databind.ObjectMapper().readValue(result, Map.class);

            // 保存联系人
            ContactDocument contact = new ContactDocument();
            contact.setName(otherLabels.isEmpty() ? "新联系人" : otherLabels.get(0));
            contact.setRelationType("朋友");
            contact.setInterests(otherInterests);
            contact.setLabels(otherLabels);
            contact.setPersonality(otherPersonality);
            contact.setIntimacy(30);  // 初始亲密度
            contact.setLastContactDays(0);
            contact.setCreatedAt(LocalDateTime.now());
            contact.setUpdatedAt(LocalDateTime.now());
            ContactDocument saved = contactRepo.save(contact);

            // 发布 ContactAdded 事件 → RelationAgent 可联动（初始化亲密度等）
            eventPublisher.publishEvent(new AgentEvent.ContactAdded(saved.getId(), saved.getName()));

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("contactId", saved.getId());
            response.put("contact", saved);
            response.put("analysis", aiResult);
            return response;

        } catch (Exception e) {
            return Map.of("status", "error", "message", "破冰分析失败: " + e.getMessage());
        }
    }
}
