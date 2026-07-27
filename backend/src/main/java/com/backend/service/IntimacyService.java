package com.backend.service;

import com.backend.agent.AgentEvent;
import com.backend.constant.IntimacyConstants;
import com.backend.document.ContactDocument;
import com.backend.document.ConversationDocument;
import com.backend.repository.ContactRepository;
import com.backend.repository.ConversationRepository;
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
 * 四分量公式（全面等权）：round(100 × (时效×0.25 + 频率×0.25 + 深度×0.25 + 质量×0.25))
 *
 * 当前实现态（第一版降级）：
 *   P1=无关联字段 → 深度/质量 = 0
 *   P3=interaction_meta 空 → 频率 = 0
 *   仅时效分量贡献分 ≈ round(100 × ttl_decay(lastContactDays) × 0.25)
 *   例：lastContactDays=0 → ~25；=30 → ~9；=60 → ~6
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IntimacyService {

    private final ContactRepository contactRepo;
    private final ContactJpaRepository contactJpaRepository;
    private final ConversationRepository conversationRepo;

    /** 按公式计算亲密度（不入库）。 */
    public int calculateIntimacy(ContactDocument contact) {
        double ttl = ttlComponent(contact);
        double frequency = IntimacyConstants.DEGRADE_FREQ_SCORE;  // TODO[P3]: read from interaction_meta
        double depth, quality;

        try {
            Long contactId = Long.valueOf(contact.getId());
            DepthQualityResult dq = loadDepthQuality(contactId);
            depth = dq.depth();
            quality = dq.quality();
        } catch (NumberFormatException e) {
            log.debug("calculateIntimacy: contact id {} not parseable as Long, depth/quality degrade",
                    contact.getId());
            depth = IntimacyConstants.DEGRADE_DEPTH_SCORE;
            quality = IntimacyConstants.DEGRADE_QUALITY_SCORE;
        }

        double raw = 100.0 * (ttl * IntimacyConstants.W_TTL
                           + frequency * IntimacyConstants.W_FREQ
                           + depth * IntimacyConstants.W_DEPTH
                           + quality * IntimacyConstants.W_QUALITY);

        int score = (int) Math.round(raw);
        log.info("calculateIntimacy: id={} ttl={} depth={} quality={} raw={} score={}",
                contact.getId(), ttl, depth, quality, raw, score);
        return Math.max(IntimacyConstants.FLOOR, Math.min(100, score));
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

    // ─── 深度/质量评估 ───

    static final int RECENT_N = 5;

    /**
     * 从最近 N 条已评分对话中评估该联系人的对话深度与质量。
     *
     * 读源：conversations.Evaluation（MongoDB，当前唯一已实现写源）。
     * 等 evaluation_record 写入实现后，数据源可切为 evaluation_record.self_relative — 届时修改读源。
     *
     * TODO[多用户]：查询补 userId 过滤（与 conversations 加 userId 同期）。
     */
    private DepthQualityResult loadDepthQuality(Long contactId) {
        List<ConversationDocument> docs = conversationRepo
                .findByRelatedContactIdOrderByCreatedAtDesc(contactId);

        // 过滤有评分的文档
        List<ConversationDocument.Evaluation> evals = docs.stream()
                .filter(d -> d.getEvaluation() != null)
                .map(ConversationDocument::getEvaluation)
                .limit(RECENT_N)
                .toList();

        if (evals.isEmpty()) {
            log.debug("loadDepthQuality: contactId={} → 无已评分对话，深度/质量降级", contactId);
            return new DepthQualityResult(0.0, 0.0);
        }

        double depthRaw = evals.stream()
                .mapToDouble(e -> (e.getEmpathyListening() + e.getRelaxation()) / 2.0)
                .average()
                .orElse(0.0);
        double depth = depthRaw / 100.0;

        double qualityRaw = evals.stream()
                .mapToDouble(e -> (e.getClarity() + e.getLogicality() + e.getInteractivity()) / 3.0)
                .average()
                .orElse(0.0);
        double quality = qualityRaw / 100.0;

        log.debug("loadDepthQuality: contactId={}, samples={}, depthRaw={:.1f}, depth={:.3f}, qualityRaw={:.1f}, quality={:.3f}",
                contactId, evals.size(), depthRaw, depth, qualityRaw, quality);
        return new DepthQualityResult(depth, quality);
    }

    /** 深度/质量计算结果 */
    private record DepthQualityResult(double depth, double quality) {}

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
        return Math.max(IntimacyConstants.TTL_FLOOR, decay);
    }
}
