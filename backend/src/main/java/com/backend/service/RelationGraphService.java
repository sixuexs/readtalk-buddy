package com.backend.service;

import com.backend.constant.WarningConstants;
import com.backend.document.ContactDocument;
import com.backend.entity.ContactEntity;
import com.backend.repository.ContactRepository;
import com.backend.repository.UserProfileRepository;
import com.backend.repository.jpa.ContactJpaRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * 关系图谱聚合服务 — 前端图谱页数据源（只读适配层，不含 E 引擎逻辑）。
 *
 * 职责：
 *   1. getGraph      — MySQL contact 全量 + 预警分类（WarningService 结果 -> STAGNATION/DECAY）
 *   2. getAdvice     — 委托 RelationAdviceService 生成个性化建议（无副作用）
 *   3. dismissWarning — 写 contact.warning_dismissed_at（7 天冷却）
 *
 * 预警分类规则（表达层映射，不改 WarningService）：
 *   DECAY(RED)        — WarningService.checkAllContacts 的疏远预警（亲密度 < 40，已过滤 suppress/recovering）
 *   STAGNATION(ORANGE)— lastContactDays > 30 且未触发 DECAY
 *   dismiss 冷却 7 天内的联系人不出预警。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RelationGraphService {

    private final ContactJpaRepository contactJpaRepo;
    private final ContactRepository contactRepo;
    private final WarningService warningService;
    private final RelationAdviceService adviceService;
    private final UserProfileRepository profileRepo;

    private final ObjectMapper mapper = new ObjectMapper();

    /** 暂不提醒冷却天数 */
    private static final int DISMISS_COOLDOWN_DAYS = 7;

    // ==================== 1. 图谱数据 ====================

    public Map<String, Object> getGraph() {
        List<ContactEntity> actives = activeContacts();

        List<Map<String, Object>> contacts = new ArrayList<>();
        for (ContactEntity c : actives) {
            contacts.add(toContactMap(c));
        }

        return Map.of("contacts", contacts, "warnings", buildWarnings(actives));
    }

    /** 未软删联系人；同名多行（seed 重跑产生）取最新 id。 */
    private List<ContactEntity> activeContacts() {
        Map<String, ContactEntity> byName = new LinkedHashMap<>();
        for (ContactEntity c : contactJpaRepo.findAll()) {
            if (c.getDeletedAt() != null) continue;
            ContactEntity prev = byName.get(c.getName());
            if (prev == null || c.getId() > prev.getId()) {
                byName.put(c.getName(), c);
            }
        }
        return new ArrayList<>(byName.values());
    }

    private Map<String, Object> toContactMap(ContactEntity c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.getId());
        m.put("name", c.getName());
        m.put("relationType", c.getRelationType());
        m.put("category", c.getCategory());
        m.put("intimacyScore", c.getIntimacyScore());
        m.put("personality", c.getPersonality());
        m.put("interests", parseJsonList(c.getInterests()));
        m.put("labels", parseJsonList(c.getLabels()));
        m.put("avatarUrl", c.getAvatarUrl());
        m.put("lastContactTime", c.getLastContactTime());
        return m;
    }

    // ==================== 2. 预警分类 ====================

    private List<Map<String, Object>> buildWarnings(List<ContactEntity> actives) {
        // Mongo 侧疏远预警（已按 suppressWarning/recovering 过滤），按姓名索引
        Set<String> decayNames = new HashSet<>();
        Map<String, Object> check = warningService.checkAllContacts();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> mongoWarnings = (List<Map<String, Object>>) check.get("warnings");
        for (Map<String, Object> w : mongoWarnings) {
            decayNames.add((String) w.get("name"));
        }

        // Mongo 档案（抑制标记查询用）
        Map<String, ContactDocument> docsByName = new HashMap<>();
        for (ContactDocument doc : contactRepo.findAll()) {
            docsByName.putIfAbsent(doc.getName(), doc);
        }

        List<Map<String, Object>> warnings = new ArrayList<>();
        for (ContactEntity c : actives) {
            Map<String, Object> w = classifyWarning(c, decayNames, docsByName.get(c.getName()));
            if (w != null) warnings.add(w);
        }
        return warnings;
    }

    /** 单联系人预警分类；无预警返回 null。冷却期内仍返回，但带 dismissed=true（前端隐藏角标、保留"继续提醒"入口）。 */
    private Map<String, Object> classifyWarning(ContactEntity c, Set<String> decayNames, ContactDocument doc) {
        boolean dismissed = c.getWarningDismissedAt() != null
                && c.getWarningDismissedAt().isAfter(LocalDateTime.now().minusDays(DISMISS_COOLDOWN_DAYS));

        long days = daysSinceLastContact(c);
        boolean suppressed = doc != null && (doc.isSuppressWarning() || doc.isRecovering());

        if (decayNames.contains(c.getName())) {
            return warningMap(c.getId(), "DECAY", "RED",
                    "亲密度滑落至" + c.getIntimacyScore().intValue() + "，关系正在降温", dismissed);
        }
        if (!suppressed && days > WarningConstants.DRIFT_DAYS_THRESHOLD) {
            return warningMap(c.getId(), "STAGNATION", "ORANGE", days + "天未联系", dismissed);
        }
        return null;
    }

    private static Map<String, Object> warningMap(Long contactId, String type, String level,
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
    public RelationAdviceService.AdviceResult getAdvice(Long contactId, String mode) {
        ContactEntity entity = contactJpaRepo.findById(contactId)
                .filter(c -> c.getDeletedAt() == null)
                .orElseThrow(() -> new IllegalArgumentException("联系人不存在: " + contactId));

        ContactDocument doc = contactRepo.findFirstByName(entity.getName());
        if (doc == null) doc = toTransientDoc(entity);

        // 复用图谱预警分类，得到 warningType / warningLevel
        Set<String> decayNames = new HashSet<>();
        Map<String, Object> check = warningService.checkAllContacts();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> mongoWarnings = (List<Map<String, Object>>) check.get("warnings");
        for (Map<String, Object> w : mongoWarnings) {
            decayNames.add((String) w.get("name"));
        }
        Map<String, Object> warning = classifyWarning(entity, decayNames, doc.getId() != null ? doc : null);

        var ctx = new RelationAdviceService.AdviceContext(
                (int) daysSinceLastContact(entity),
                entity.getIntimacyScore().intValue(),
                warning != null ? "疏远预警" : null,
                warning != null ? (String) warning.get("level") : "YELLOW"
        );

        var profile = profileRepo.findFirstByOrderByLastUpdatedDesc();
        // TODO[多用户]: 改 findByUserId(userId)

        return adviceService.generatePersonalizedAdvice(doc, profile, ctx);
    }

    /** MongoDB 无匹配档案时，用 MySQL 字段拼临时档案供 LLM 生成 */
    private ContactDocument toTransientDoc(ContactEntity e) {
        ContactDocument doc = new ContactDocument();
        doc.setName(e.getName());
        doc.setRelationType(e.getRelationType());
        doc.setPersonality(e.getPersonality());
        doc.setInterests(parseJsonList(e.getInterests()));
        doc.setLabels(parseJsonList(e.getLabels()));
        doc.setIntimacy(e.getIntimacyScore().intValue());
        doc.setLastContactDays((int) daysSinceLastContact(e));
        return doc;
    }

    // ==================== 4. 暂不提醒 / 继续提醒 ====================

    public void dismissWarning(Long contactId) {
        ContactEntity entity = contactJpaRepo.findById(contactId)
                .filter(c -> c.getDeletedAt() == null)
                .orElseThrow(() -> new IllegalArgumentException("联系人不存在: " + contactId));
        entity.setWarningDismissedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        contactJpaRepo.save(entity);
        log.info("联系人 {} 预警已冷却 {} 天", entity.getName(), DISMISS_COOLDOWN_DAYS);
    }

    /** 继续提醒：清除冷却时间戳，预警立即恢复 */
    public void resumeWarning(Long contactId) {
        ContactEntity entity = contactJpaRepo.findById(contactId)
                .filter(c -> c.getDeletedAt() == null)
                .orElseThrow(() -> new IllegalArgumentException("联系人不存在: " + contactId));
        entity.setWarningDismissedAt(null);
        entity.setUpdatedAt(LocalDateTime.now());
        contactJpaRepo.save(entity);
        log.info("联系人 {} 预警冷却已取消，恢复提醒", entity.getName());
    }

    // ==================== helpers ====================

    private long daysSinceLastContact(ContactEntity c) {
        if (c.getLastContactTime() == null) return 0;
        return Math.max(0, ChronoUnit.DAYS.between(c.getLastContactTime(), LocalDateTime.now()));
    }

    private List<String> parseJsonList(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return mapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            log.warn("JSON 数组解析失败: {}", json);
            return List.of();
        }
    }
}
