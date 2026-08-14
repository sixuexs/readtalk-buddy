package com.backend.service;

import com.backend.agent.AgentEvent;
import com.backend.constant.IntimacyConstants;
import com.backend.document.ContactDocument;
import com.backend.document.ConversationDocument;
import com.backend.repository.ContactRepository;
import com.backend.repository.ConversationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * E 模块 C2 — 亲密度计算引擎。
 *
 * 四分量公式（全面等权）：round(100 × (时效×0.25 + 频率×0.25 + 深度×0.25 + 质量×0.25))
 *
 * 数据源统一到 MongoDB（P0 决策）：
 *   时效   = lastContactDays 指数衰减
 *   频率   = interactions 近 FREQ_WINDOW_DAYS 天条数，归一化（≥FREQ_FULL_COUNT = 1.0）
 *   深度   = 该书友最近 N 场会话的平均消息数，归一化
 *   质量   = 该书友最近 N 场已评分会话的五维均分 / 100
 *
 * 无该书友历史会话时深度/质量降级为 0，不再有"Long.valueOf(ObjectId) 抛异常"的硬伤。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IntimacyService {

    private final ContactRepository contactRepo;
    private final ConversationRepository conversationRepo;

    /** 深度/质量取样窗口（最近 N 场会话） */
    static final int RECENT_N = 5;

    /** 按公式计算亲密度（不入库）。 */
    public int calculateIntimacy(ContactDocument contact) {
        double ttl = ttlComponent(contact);
        double frequency = frequencyComponent(contact);

        DepthQualityResult dq = loadDepthQuality(contact.getId());
        double depth = dq.depth();
        double quality = dq.quality();

        double raw = 100.0 * (ttl * IntimacyConstants.W_TTL
                           + frequency * IntimacyConstants.W_FREQ
                           + depth * IntimacyConstants.W_DEPTH
                           + quality * IntimacyConstants.W_QUALITY);

        int score = (int) Math.round(raw);
        log.info("calculateIntimacy: id={} ttl={} freq={} depth={} quality={} raw={} score={}",
                contact.getId(), ttl, frequency, depth, quality, raw, score);
        return Math.max(IntimacyConstants.FLOOR, Math.min(100, score));
    }

    /**
     * 双写亲密度 — E 模块唯一写库收口。
     *
     * P0：联系人以 MongoDB 为唯一真相源，此方法只写 MongoDB contacts.intimacy。
     * MySQL contact.intimacy_score 冻结保留，不再由亲密度主链路维护。
     */
    public void persistIntimacy(String contactId, int score) {
        ContactDocument doc = contactRepo.findById(contactId).orElse(null);
        if (doc == null) {
            log.warn("persistIntimacy: contact {} not found in MongoDB", contactId);
            return;
        }
        doc.setIntimacy(score);
        doc.setUpdatedAt(LocalDateTime.now());
        contactRepo.save(doc);
        log.debug("persistIntimacy: contact={} score={}", doc.getName(), score);
    }

    /** 批量刷新所有联系人的亲密度（定时任务入口）。 */
    public void refreshAllIntimacy() {
        List<ContactDocument> contacts = contactRepo.findAll();
        log.info("refreshAllIntimacy: processing {} contacts", contacts.size());
        for (var c : contacts) {
            int score = calculateIntimacy(c);
            persistIntimacy(c.getId(), score);
        }
        log.info("refreshAllIntimacy: done");
    }

    // ─── 分量计算 ───

    /** 时效：指数衰减 lastContactDays → [TTL_FLOOR, 1.0] */
    double ttlComponent(ContactDocument doc) {
        int days = doc.getLastContactDays();
        double decay = Math.pow(0.5, days / IntimacyConstants.TTL_HALFLIFE_DAYS);
        return Math.max(IntimacyConstants.TTL_FLOOR, decay);
    }

    /** 频率：近 FREQ_WINDOW_DAYS 天 interactions 条数，归一化到 [0,1] */
    double frequencyComponent(ContactDocument contact) {
        if (contact.getInteractions() == null || contact.getInteractions().isEmpty()) {
            return IntimacyConstants.DEGRADE_FREQ_SCORE;
        }
        LocalDateTime cutoff = LocalDateTime.now().minus(IntimacyConstants.FREQ_WINDOW_DAYS, ChronoUnit.DAYS);
        long count = contact.getInteractions().stream()
                .filter(i -> i.getTime() != null && i.getTime().isAfter(cutoff))
                .count();
        if (count == 0) return IntimacyConstants.DEGRADE_FREQ_SCORE;
        return Math.min(1.0, (double) count / IntimacyConstants.FREQ_FULL_COUNT);
    }

    // ─── 深度/质量评估 ───

    /**
     * 从该书友最近 N 场会话评估深度与质量。
     *
     * 深度：平均消息数（/DEPTH_FULL_MESSAGES 归一化）
     * 质量：已评分会话五维均分（/100 归一化）
     */
    private DepthQualityResult loadDepthQuality(String contactId) {
        List<ConversationDocument> docs = conversationRepo
                .findByRelatedContactIdOrderByCreatedAtDesc(contactId);

        if (docs.isEmpty()) {
            log.debug("loadDepthQuality: contactId={} → 无历史会话，深度/质量降级", contactId);
            return new DepthQualityResult(0.0, 0.0);
        }

        double depthRaw = docs.stream()
                .limit(RECENT_N)
                .mapToInt(d -> d.getMessages() == null ? 0 : d.getMessages().size())
                .average()
                .orElse(0.0);
        double depth = Math.min(1.0, depthRaw / IntimacyConstants.DEPTH_FULL_MESSAGES);

        List<ConversationDocument> evals = docs.stream()
                .filter(d -> d.getEvaluation() != null)
                .limit(RECENT_N)
                .toList();

        double quality = IntimacyConstants.DEGRADE_QUALITY_SCORE;
        if (!evals.isEmpty()) {
            double qualityRaw = evals.stream()
                    .mapToDouble(d -> fiveDimAvg(d.getEvaluation()))
                    .average()
                    .orElse(0.0);
            quality = qualityRaw / 100.0;
        }

        log.debug("loadDepthQuality: contactId={}, samples={}, depthRaw={}, depth={}, quality={}",
                contactId, evals.size(), depthRaw, depth, quality);
        return new DepthQualityResult(depth, quality);
    }

    /** 五维均分 */
    private static double fiveDimAvg(ConversationDocument.Evaluation e) {
        return (e.getClarity() + e.getLogicality() + e.getEmpathyListening()
                + e.getInteractivity() + e.getRelaxation()) / 5.0;
    }

    /** 深度/质量计算结果 */
    private record DepthQualityResult(double depth, double quality) {}

    // ─── 事件联动 ───

    /**
     * 评分完成 → 刷新绑定书友的亲密度。
     *
     * sessionId → conversations doc → relatedContactId（MongoDB contacts id）
     *   → 追加一条"情景模拟"互动（供频率分量） + 重算亲密度。
     * 模拟不更新 lastContactDays（那是真实联系才更新的时效信号）。
     */
    @EventListener
    public void onScoringCompleted(AgentEvent.ScoringCompleted event) {
        String sessionId = event.sessionId();
        conversationRepo.findById(sessionId).ifPresent(conv -> {
            String contactId = conv.getRelatedContactId();
            if (contactId == null || contactId.isBlank()) {
                log.debug("onScoringCompleted: session {} 未绑定书友，跳过亲密度刷新", sessionId);
                return;
            }
            contactRepo.findById(contactId).ifPresent(contact -> {
                if (contact.getInteractions() == null) {
                    contact.setInteractions(new ArrayList<>());
                }
                ContactDocument.InteractionRecord rec = new ContactDocument.InteractionRecord();
                rec.setType("情景模拟");
                rec.setSummary("情景模拟训练评分 " + event.score());
                rec.setTime(LocalDateTime.now());
                contact.getInteractions().add(rec);
                contactRepo.save(contact);

                int score = calculateIntimacy(contact);
                persistIntimacy(contactId, score);
                log.info("onScoringCompleted: contact={} intimacy={} (after sim score {})",
                        contact.getName(), score, event.score());
            });
        });
    }
}
