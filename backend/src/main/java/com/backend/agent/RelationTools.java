package com.backend.agent;

import com.backend.document.ContactDocument;
import com.backend.repository.ContactRepository;
import com.backend.repository.UserProfileRepository;
import com.backend.service.IntimacyService;
import com.backend.service.WarningService;
import com.backend.service.RelationAdviceService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 关系运维 Agent 工具集 —— 亲密度计算、维护提醒、疏远预警、挽回方案
 *
 * E 模块 C5：集成 IntimacyService / WarningService / RelationAdviceService 引擎。
 */
@Component
public class RelationTools {

    private final ContactRepository contactRepo;
    private final IntimacyService intimacyService;
    private final WarningService warningService;
    private final RelationAdviceService relationAdviceService;
    private final UserProfileRepository profileRepo;

    public RelationTools(ContactRepository contactRepo,
                         IntimacyService intimacyService,
                         WarningService warningService,
                         RelationAdviceService relationAdviceService,
                         UserProfileRepository profileRepo) {
        this.contactRepo = contactRepo;
        this.intimacyService = intimacyService;
        this.warningService = warningService;
        this.relationAdviceService = relationAdviceService;
        this.profileRepo = profileRepo;
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

        int newIntimacy = intimacyService.calculateIntimacy(contact);
        intimacyService.persistIntimacy(0L, contactId, newIntimacy);

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
        return warningService.checkAllContacts();
    }

    @Tool(description = "检测指定联系人的关系疏远程度，判断是否需要预警")
    public Map<String, Object> detectDrift(
            @ToolParam(description = "联系人 ID") String contactId) {
        var contact = contactRepo.findById(contactId).orElse(null);
        if (contact == null) return Map.of("error", "联系人不存在");
        return warningService.detectDrift(contact);
    }

    @Tool(description = "为疏远的关系生成挽救方案。如果用户选择不挽救，抑制一段时间预警")
    // TODO[表达层完整化]: 拆 mode=light/full，当前 /recover 走 full，light 入口本轮不实现。
    public Map<String, Object> generateRecoverPlan(
            @ToolParam(description = "联系人 ID") String contactId,
            @ToolParam(description = "用户是否选择挽救") boolean chooseRecover) {
        var contact = contactRepo.findById(contactId).orElse(null);
        if (contact == null) return Map.of("error", "联系人不存在");

        if (!chooseRecover) {
            contact.setSuppressWarning(true);
            contact.setWarning(false);
            contact.setRecovering(false);
            contact.setUpdatedAt(LocalDateTime.now());
            contactRepo.save(contact);
            return Map.of("status", "suppressed",
                    "message", "已暂时抑制" + contact.getName() + "的关系预警，30天内不再提醒");
        }

        try {
            // 获取预警上下文
            Map<String, Object> drift = warningService.detectDrift(contact);
            String warningLevel = mapSeverityToLevel((String) drift.get("severity"));
            String warningType = (String) drift.getOrDefault("type", "疏远预警");

            var ctx = new RelationAdviceService.AdviceContext(
                contact.getLastContactDays(),
                contact.getIntimacy(),
                warningType,
                warningLevel
            );

            // P4=存在 -> 拿用户画像
            var profile = profileRepo.findFirstByOrderByLastUpdatedDesc();
            // TODO[多用户]: 改 findByUserId(userId)，与 conversations 加 userId 同期修。

            var advice = relationAdviceService.generatePersonalizedAdvice(contact, profile, ctx);

            contact.setRecovering(true);
            contact.setSuppressWarning(false);
            contact.setUpdatedAt(LocalDateTime.now());
            contactRepo.save(contact);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("status", "recovering");
            response.put("contactId", contactId);
            response.put("plan", Map.of(
                "entryTopics", advice.entryTopics(),
                "openingLine", advice.openingLine(),
                "cautions", advice.cautions(),
                "recoverSteps", advice.recoverSteps(),
                "expectation", advice.expectation()
            ));
            return response;

        } catch (Exception e) {
            return Map.of("status", "error", "message", "生成方案失败: " + e.getMessage());
        }
    }

    /** severity -> warningLevel 映射 */
    private static String mapSeverityToLevel(String severity) {
        if (severity == null) return "YELLOW";
        return switch (severity) {
            case "严重" -> "RED";
            case "中等" -> "ORANGE";
            default -> "YELLOW";
        };
    }
}
