package com.backend.agent;

import com.backend.document.ContactDocument;
import com.backend.repository.ContactRepository;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * 关系运维 Agent 工具集 —— 亲密度计算、维护提醒、疏远预警、挽回方案
 */
@Component
public class RelationTools {

    private final ContactRepository contactRepo;
    private final ChatClient chatClient;

    public RelationTools(ContactRepository contactRepo, ChatClient.Builder chatClientBuilder) {
        this.contactRepo = contactRepo;
        this.chatClient = chatClientBuilder.build();
    }

    @Tool(description = "获取所有联系人列表，包含亲密度和关系状态")
    public Map<String, Object> listContacts() {
        List<ContactDocument> contacts = contactRepo.findAllByOrderByCreatedAtDesc();
        return Map.of("contacts", contacts, "total", contacts.size());
    }

    @Tool(description = "计算指定联系人的亲密度，根据互动频率、最近联系时间等综合评分")
    public Map<String, Object> calcIntimacy(
            @ToolParam(description = "联系人 ID") String contactId) {
        var contact = contactRepo.findById(contactId).orElse(null);
        if (contact == null) return Map.of("error", "联系人不存在");

        // 亲密度计算规则
        int base = contact.getIntimacy();
        int interactionBonus = Math.min(contact.getInteractions().size() * 2, 20);
        int recencyPenalty = Math.min(contact.getLastContactDays() / 7, 15);  // 每周衰减

        int newIntimacy = Math.max(0, Math.min(100, base + interactionBonus - recencyPenalty));
        contact.setIntimacy(newIntimacy);
        contact.setUpdatedAt(LocalDateTime.now());
        contactRepo.save(contact);

        return Map.of(
                "contactId", contactId,
                "name", contact.getName(),
                "intimacy", newIntimacy,
                "interactionCount", contact.getInteractions().size(),
                "lastContactDays", contact.getLastContactDays()
        );
    }

    @Tool(description = "检查所有关系，生成维护提醒。包括生日提醒、长期未联系提醒、亲密度下降预警")
    public Map<String, Object> checkMaintenance() {
        List<Map<String, Object>> reminders = new ArrayList<>();
        List<Map<String, Object>> warnings = new ArrayList<>();

        for (var contact : contactRepo.findAll()) {
            // 生日提醒（未来7天内）
            if (contact.getBirthday() != null) {
                LocalDate today = LocalDate.now();
                LocalDate nextBirthday = contact.getBirthday().withYear(today.getYear());
                if (nextBirthday.isBefore(today)) {
                    nextBirthday = nextBirthday.plusYears(1);
                }
                long daysUntil = ChronoUnit.DAYS.between(today, nextBirthday);
                if (daysUntil <= 7) {
                    reminders.add(Map.of(
                            "contactId", contact.getId(),
                            "name", contact.getName(),
                            "type", "生日提醒",
                            "daysUntil", daysUntil,
                            "suggestion", daysUntil == 0 ?
                                    "今天是" + contact.getName() + "的生日，快送上祝福！" :
                                    daysUntil + "天后是" + contact.getName() + "的生日，准备礼物吧"
                    ));
                }
            }

            // 长期未联系提醒
            if (contact.getLastContactDays() > 14 && contact.getLastContactDays() <= 30) {
                reminders.add(Map.of(
                        "contactId", contact.getId(),
                        "name", contact.getName(),
                        "type", "联系提醒",
                        "lastContactDays", contact.getLastContactDays(),
                        "suggestion", "已" + contact.getLastContactDays() + "天未联系" + contact.getName() + "，发个消息问候一下吧"
                ));
            }

            // 亲密度预警（低于阈值且未抑制）
            if (contact.getIntimacy() < 40 && !contact.isSuppressWarning() && !contact.isRecovering()) {
                warnings.add(Map.of(
                        "contactId", contact.getId(),
                        "name", contact.getName(),
                        "type", "疏远预警",
                        "intimacy", contact.getIntimacy(),
                        "suggestion", "与" + contact.getName() + "的关系正在疏远，是否尝试挽救？"
                ));
            }
        }

        return Map.of("reminders", reminders, "warnings", warnings);
    }

    @Tool(description = "检测指定联系人的关系疏远程度，判断是否需要预警")
    public Map<String, Object> detectDrift(
            @ToolParam(description = "联系人 ID") String contactId) {
        var contact = contactRepo.findById(contactId).orElse(null);
        if (contact == null) return Map.of("error", "联系人不存在");

        boolean isDrifting = contact.getIntimacy() < 40 || contact.getLastContactDays() > 30;

        if (isDrifting && !contact.isWarning()) {
            contact.setWarning(true);
            contact.setWarningTime(LocalDateTime.now());
            contactRepo.save(contact);
        }

        return Map.of(
                "contactId", contactId,
                "name", contact.getName(),
                "intimacy", contact.getIntimacy(),
                "lastContactDays", contact.getLastContactDays(),
                "isDrifting", isDrifting,
                "severity", contact.getIntimacy() < 20 ? "严重" :
                            contact.getIntimacy() < 40 ? "中等" : "轻微"
        );
    }

    @Tool(description = "为疏远的关系生成挽救方案。如果用户选择不挽救，抑制一段时间预警")
    public Map<String, Object> generateRecoverPlan(
            @ToolParam(description = "联系人 ID") String contactId,
            @ToolParam(description = "用户是否选择挽救") boolean chooseRecover) {
        var contact = contactRepo.findById(contactId).orElse(null);
        if (contact == null) return Map.of("error", "联系人不存在");

        if (!chooseRecover) {
            // 用户选择不挽救：抑制预警30天
            contact.setSuppressWarning(true);
            contact.setWarning(false);
            contact.setRecovering(false);
            contact.setUpdatedAt(LocalDateTime.now());
            contactRepo.save(contact);
            return Map.of("status", "suppressed",
                    "message", "已暂时抑制" + contact.getName() + "的关系预警，30天内不再提醒");
        }

        // 用户选择挽救：AI 生成方案
        String prompt = String.format("""
                你是一位关系修复专家。用户希望挽救与以下联系人的关系：

                - 姓名：%s
                - 关系类型：%s
                - 当前亲密度：%d/100
                - 最近联系：%d 天前
                - 兴趣爱好：%s
                - 性格：%s

                请以 JSON 格式给出挽救方案：
                {
                  "strategy": "总体策略（100字以内）",
                  "actions": ["具体行动1", "具体行动2", "具体行动3"],
                  "openingMessage": "一条自然不尴尬的开场消息"
                }
                只返回JSON。""",
                contact.getName(), contact.getRelationType(),
                contact.getIntimacy(), contact.getLastContactDays(),
                String.join("、", contact.getInterests()),
                contact.getPersonality() != null ? contact.getPersonality() : "未知");

        try {
            String result = chatClient.prompt().user(prompt).call().content();
            @SuppressWarnings("unchecked")
            Map<String, Object> aiResult = new com.fasterxml.jackson.databind.ObjectMapper().readValue(result, Map.class);

            contact.setRecovering(true);
            contact.setSuppressWarning(false);
            contact.setUpdatedAt(LocalDateTime.now());
            contactRepo.save(contact);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("status", "recovering");
            response.put("contactId", contactId);
            response.put("plan", aiResult);
            return response;

        } catch (Exception e) {
            return Map.of("status", "error", "message", "生成方案失败: " + e.getMessage());
        }
    }
}
