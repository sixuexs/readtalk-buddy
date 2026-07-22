package com.backend.service;

import com.backend.agent.AgentEvent;
import com.backend.constant.IntimacyConstants;
import com.backend.document.ContactDocument;
import com.backend.repository.ContactRepository;
import com.backend.repository.jpa.ContactJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * E 模块 C2 — 亲密度计算引擎。
 *
 * 四分量公式：round(时效×W_TTL + 频率×W_FREQ + 深度×W_DEPTH + 质量×W_QUALITY)
 *
 * 当前实现态（第一版降级）：
 *   P1=无关联字段 → 深度/质量 = 0
 *   P3=interaction_meta 空 → 频率 = 0
 *   仅时效分量贡献分 ≈ round(ttl_decay(lastContactDays) × 0.35)
 *   例：lastContactDays=0 → ~35；=30 → ~10；=60 → ~3
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IntimacyService {

    private final ContactRepository contactRepo;
    private final ContactJpaRepository contactJpaRepository;

    /** 按公式计算亲密度（不入库）。 */
    public int calculateIntimacy(ContactDocument contact) {
        double ttl = ttlComponent(contact);
        double raw = ttl * IntimacyConstants.W_TTL;
        int score = (int) Math.round(raw);
        return Math.max(0, Math.min(100, score));
    }

    /**
     * 双写亲密度 — E 模块唯一写库收口。
     *
     * (a) MySQL contact.intimacy_score = score（度量字段写主）
     * (b) MongoDB contacts.intimacy = score（前端读存值，不发 ContactSavedEvent）
     *
     * 规范：calcIntimacy(实时) 与 refreshAllIntimacy(定时) 都只调此方法。
     * TODO[阶段二]: 去掉 (b) Mongo 写回。
     */
    @Transactional
    public void persistIntimacy(Long userId, String contactId, int score) {
        ContactDocument doc = contactRepo.findById(contactId).orElse(null);
        if (doc == null) {
            log.warn("persistIntimacy: contact {} not found in MongoDB", contactId);
            return;
        }

        // (a) MySQL — 度量字段写主
        contactJpaRepository.findByUserIdAndName(userId, doc.getName())
                .ifPresent(entity -> {
                    entity.setIntimacyScore(BigDecimal.valueOf(score));
                    entity.setUpdatedAt(LocalDateTime.now());
                    contactJpaRepository.save(entity);
                });

        // (b) MongoDB — 直接字段写入（不触发 saveOrUpdate / ContactSavedEvent）
        doc.setIntimacy(score);
        doc.setUpdatedAt(LocalDateTime.now());
        contactRepo.save(doc);

        log.debug("persistIntimacy: contact={} score={} (dual-write)", doc.getName(), score);
    }

    /** 批量刷新所有联系人的亲密度（定时任务入口）。 */
    public void refreshAllIntimacy() {
        List<ContactDocument> contacts = contactRepo.findAll();
        log.info("refreshAllIntimacy: processing {} contacts", contacts.size());
        for (var c : contacts) {
            int score = calculateIntimacy(c);
            persistIntimacy(0L, c.getId(), score);
        }
        log.info("refreshAllIntimacy: done");
    }

    // ─── 分量计算（降级）───

    /**
     * 评分完成 → 刷新亲密度（管道骨架）。
     *
     * TODO[P1+P3]: 管道通后 event.sessionId() → conversations doc → contactId
     *    → 读取 interaction_meta 频率 + conversations.Evaluation 深度/质量
     *    → calculateIntimacy(contact) 启用全四分量
     *    → persistIntimacy(userId, contactId, score)
     */
    @EventListener
    public void onScoringCompleted(AgentEvent.ScoringCompleted event) {
        log.debug("onScoringCompleted: sessionId={} — TODO[P1+P3] 待 contactId 管线通后启用全分量亲密度刷新",
                event.sessionId());
    }

    /** 时效：指数衰减 lastContactDays → [TTL_FLOOR, 1.0] → ×100 */
    double ttlComponent(ContactDocument doc) {
        int days = doc.getLastContactDays();
        double decay = Math.pow(0.5, days / IntimacyConstants.TTL_HALFLIFE_DAYS);
        return Math.max(IntimacyConstants.TTL_FLOOR, decay) * 100.0;
    }
}
