package com.backend.service;

import com.backend.constant.WarningConstants;
import com.backend.document.ContactDocument;
import com.backend.repository.ContactRepository;
import com.backend.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 关系图谱聚合服务 — 前端图谱页数据源（只读适配层，不含 E 引擎逻辑）。
 *
 * P0：联系人唯一真相源 = MongoDB contacts（String id）。
 *
 * 职责：
 *   1. getGraph        — MongoDB contacts 全量 + 预警分类（WarningService 结果 -> STAGNATION/DECAY）
 *   2. getAdvice       — 委托 RelationAdviceService 生成个性化建议（无副作用）
 *   3. dismissWarning  — 写 contact.warningDismissedAt（7 天冷却）
 *
 * 预警分类规则（表达层映射，不改 WarningService）：
 *   DECAY(RED)        — WarningService.checkAllContacts 的疏远预警（亲密度 < 40，已过滤 suppress/recovering）
 *   STAGNATION(ORANGE)— lastContactDays > 30 且未触发 DECAY
 *   dismiss 冷却 7 天内的联系人不出预警角标（保留"继续提醒"入口）。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RelationGraphService {

    private final ContactRepository contactRepo;
    private final WarningService warningService;
    private final RelationAdviceService adviceService;
    private final UserProfileRepository profileRepo;

    /** 暂不提醒冷却天数 */
    private static final int DISMISS_COOLDOWN_DAYS = 7;

    // ==================== 1. 图谱数据 ====================

    public Map<String, Object> getGraph() {
        List<ContactDocument> contacts = contactRepo.findAllByOrderByCreatedAtDesc();

        List<Map<String, Object>> contactMaps = new ArrayList<>();
        for (ContactDocument c : contacts) {
            contactMaps.add(toContactMap(c));
        }

        return Map.of("contacts", contactMaps, "warnings", buildWarnings(contacts));
    }

    private Map<String, Object> toContactMap(ContactDocument c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.getId());
        m.put("name", c.getName());
        m.put("relationType", c.getRelationType());
        m.put("category", c.getRelationType() != null ? c.getRelationType() : "other");
        m.put("intimacyScore", c.getIntimacy());
        m.put("personality", c.getPersonality());
        m.put("interests", c.getInterests() != null ? c.getInterests() : List.of());
        m.put("labels", c.getLabels() != null ? c.getLabels() : List.of());
        m.put("avatarUrl", c.getAvatar());
        m.put("lastContactDays", c.getLastContactDays());
        return m;
    }

    // ==================== 2. 预警分类 ====================

    private List<Map<String, Object>> buildWarnings(List<ContactDocument> contacts) {
        // Mongo 侧疏远预警（已按 suppressWarning/recovering 过滤），按姓名索引
        Set<String> decayNames = new HashSet<>();
        Map<String, Object> check = warningService.checkAllContacts();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> mongoWarnings = (List<Map<String, Object>>) check.get("warnings");
        for (Map<String, Object> w : mongoWarnings) {
            decayNames.add((String) w.get("name"));
        }

        List<Map<String, Object>> warnings = new ArrayList<>();
        for (ContactDocument c : contacts) {
            Map<String, Object> w = classifyWarning(c, decayNames);
            if (w != null) warnings.add(w);
        }
        return warnings;
    }

    /** 单联系人预警分类；无预警返回 null。冷却期内仍返回，但带 dismissed=true（前端隐藏角标、保留"继续提醒"入口）。 */
    private Map<String, Object> classifyWarning(ContactDocument c, Set<String> decayNames) {
        boolean dismissed = c.getWarningDismissedAt() != null
                && c.getWarningDismissedAt().isAfter(LocalDateTime.now().minusDays(DISMISS_COOLDOWN_DAYS));

        boolean suppressed = c.isSuppressWarning() || c.isRecovering();

        if (decayNames.contains(c.getName())) {
            return warningMap(c.getId(), "DECAY", "RED",
                    "亲密度滑落至" + c.getIntimacy() + "，关系正在降温", dismissed);
        }
        if (!suppressed && c.getLastContactDays() > WarningConstants.DRIFT_DAYS_THRESHOLD) {
            return warningMap(c.getId(), "STAGNATION", "ORANGE",
                    c.getLastContactDays() + "天未联系", dismissed);
        }
        return null;
    }

    private static Map<String, Object> warningMap(String contactId, String type, String level,
                                                  String reason, boolean dismissed) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("contactId", contactId);
        m.put("type", type);
        m.put("level", level);
        m.put("reason", reason);
        m.put("dismissed", dismissed);
        return m;
    }

    // ==================== 3. 个性化建议 ====================

    // TODO[表达层完整化]: mode=light/full 差异化生成（light 短建议 / full 完整方案），
    //   当前统一返回完整 AdviceResult，前端按 mode 取字段展示。
    public RelationAdviceService.AdviceResult getAdvice(String contactId, String mode) {
        ContactDocument doc = contactRepo.findById(contactId)
                .orElseThrow(() -> new IllegalArgumentException("联系人不存在: " + contactId));

        // 复用图谱预警分类，得到 warningType / warningLevel
        Set<String> decayNames = new HashSet<>();
        Map<String, Object> check = warningService.checkAllContacts();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> mongoWarnings = (List<Map<String, Object>>) check.get("warnings");
        for (Map<String, Object> w : mongoWarnings) {
            decayNames.add((String) w.get("name"));
        }
        Map<String, Object> warning = classifyWarning(doc, decayNames);

        var ctx = new RelationAdviceService.AdviceContext(
                doc.getLastContactDays(),
                doc.getIntimacy(),
                warning != null ? "疏远预警" : null,
                warning != null ? (String) warning.get("level") : "YELLOW"
        );

        var profile = profileRepo.findFirstByOrderByLastUpdatedDesc();
        // TODO[多用户]: 改 findByUserId(userId)

        return adviceService.generatePersonalizedAdvice(doc, profile, ctx);
    }

    // ==================== 4. 暂不提醒 / 继续提醒 ====================

    public void dismissWarning(String contactId) {
        ContactDocument doc = contactRepo.findById(contactId)
                .orElseThrow(() -> new IllegalArgumentException("联系人不存在: " + contactId));
        doc.setWarningDismissedAt(LocalDateTime.now());
        doc.setUpdatedAt(LocalDateTime.now());
        contactRepo.save(doc);
        log.info("联系人 {} 预警已冷却 {} 天", doc.getName(), DISMISS_COOLDOWN_DAYS);
    }

    /** 继续提醒：清除冷却时间戳，预警立即恢复 */
    public void resumeWarning(String contactId) {
        ContactDocument doc = contactRepo.findById(contactId)
                .orElseThrow(() -> new IllegalArgumentException("联系人不存在: " + contactId));
        doc.setWarningDismissedAt(null);
        doc.setUpdatedAt(LocalDateTime.now());
        contactRepo.save(doc);
        log.info("联系人 {} 预警冷却已取消，恢复提醒", doc.getName());
    }
}
