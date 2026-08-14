package com.backend.seed;

import com.backend.document.ContactDocument;
import com.backend.document.ConversationDocument;
import com.backend.document.ConversationDocument.Evaluation;
import com.backend.document.ConversationDocument.MessageItem;
import com.backend.repository.ContactRepository;
import com.backend.repository.ConversationRepository;
import com.backend.service.IntimacyService;
import com.backend.service.WarningService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * E 模块亲密度验证 seed（@Profile("seed")，默认不运行）。
 *
 * P0：联系人唯一真相源 = MongoDB。四分量数据全部来自 MongoDB：
 *   时效=lastContactDays、频率=interactions 近30天条数、深度=平均消息数、质量=五维均分。
 *
 * 构造 4 个测试书友覆盖内环/中环/外环，跑亲密度计算 + 预警，并软断言数值区间。
 */
@Component
@Profile("seed")
@RequiredArgsConstructor
@Slf4j
public class IntimacyValidationSeed {

    private final ContactRepository contactRepo;
    private final ConversationRepository conversationRepo;
    private final IntimacyService intimacyService;
    private final WarningService warningService;

    @PostConstruct
    public void seed() {
        log.info("========== IntimacyValidationSeed: START ==========");

        LocalDate now = LocalDate.now();

        // A: 朋友, 近2天, 4次互动, 2场12消息高分会话 → 内环
        seedContactA(now);
        // B: 同事, 40天, 无近期互动, 1场8消息低分会话 → 外环(DECAY)
        seedContactB(now);
        // C: 家人, 60天, 2次近期互动, 1场15消息高分会话 → 中环(STAGNATION, 质量/频率抵消时间)
        seedContactC(now);
        // D: 朋友, 25天, 1次近期互动, 1场5消息中分会话 → 外环(DECAY) + 联系提醒
        seedContactD(now);

        // ── run intimacy calculation ──
        log.info("--- Calculating intimacy for all test contacts ---");
        for (String id : List.of("9001", "9002", "9003", "9004")) {
            contactRepo.findById(id).ifPresent(doc -> {
                int score = intimacyService.calculateIntimacy(doc);
                intimacyService.persistIntimacy(id, score);
                log.info("{}: score={}", doc.getName(), score);
            });
        }

        // ── run warning checks ──
        log.info("--- Running warning checks ---");
        Map<String, Object> result = warningService.checkAllContacts();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> warnings = (List<Map<String, Object>>) result.get("warnings");

        List<Map<String, Object>> testWarnings = warnings.stream()
                .filter(w -> {
                    Object cid = w.get("contactId");
                    return "9001".equals(cid) || "9002".equals(cid)
                            || "9003".equals(cid) || "9004".equals(cid);
                })
                .toList();

        log.info("warnings count (test contacts): {}", testWarnings.size());
        for (Map<String, Object> w : testWarnings) {
            log.info("  type={}, severity={}, contactId={}, name={}",
                    w.get("type"), w.get("severity"), w.get("contactId"), w.get("name"));
        }

        verify(testWarnings, now);
        log.info("========== IntimacyValidationSeed: ALL ASSERTIONS PASSED ==========");
    }

    // ── seed contact A ──

    private void seedContactA(LocalDate now) {
        ContactDocument doc = saveContact("9001", "测试书友A", "朋友", "温和",
                List.of("阅读", "科幻"), List.of("书虫"), 2);

        addInteraction(doc, "聊天", "SEED-A-1", now.minusDays(2));
        addInteraction(doc, "见面", "SEED-A-2", now.minusDays(5));
        addInteraction(doc, "聊天", "SEED-A-3", now.minusDays(9));
        addInteraction(doc, "电话", "SEED-A-4", now.minusDays(12));

        saveConversation("seed-a-1", "9001", now.minusDays(1), 12,
                new int[]{85, 80, 80, 70, 75}, "A 的对话还不错");
        saveConversation("seed-a-2", "9001", now.minusDays(2), 12,
                new int[]{85, 80, 80, 70, 75}, "A 的另一场对话");
    }

    // ── seed contact B ──

    private void seedContactB(LocalDate now) {
        ContactDocument doc = saveContact("9002", "测试书友B", "同事", "理性",
                List.of("编程", "技术"), List.of("极客"), 40);

        addInteraction(doc, "聊天", "SEED-B-1", now.minusDays(40)); // 超 30 天窗口，不计频率

        saveConversation("seed-b-1", "9002", now.minusDays(40), 8,
                new int[]{45, 40, 40, 30, 35}, "B 的对话");
    }

    // ── seed contact C ──

    private void seedContactC(LocalDate now) {
        ContactDocument doc = saveContact("9003", "测试书友C", "家人", "活泼",
                List.of("历史", "人文"), List.of("话痨"), 60);

        addInteraction(doc, "见面", "SEED-C-1", now.minusDays(5));
        addInteraction(doc, "电话", "SEED-C-2", now.minusDays(18));

        saveConversation("seed-c-1", "9003", now.minusDays(60), 15,
                new int[]{80, 85, 85, 75, 80}, "C 的对话");
    }

    // ── seed contact D ──

    private void seedContactD(LocalDate now) {
        ContactDocument doc = saveContact("9004", "测试书友D", "朋友", "内向",
                List.of("推理", "悬疑"), List.of("细节控"), 25);

        addInteraction(doc, "聊天", "SEED-D-1", now.minusDays(10));

        saveConversation("seed-d-1", "9004", now.minusDays(25), 5,
                new int[]{50, 45, 45, 40, 40}, "D 的对话");
    }

    // ── helpers ──

    private ContactDocument saveContact(String mongoId, String name, String relationType,
                                        String personality, List<String> interests,
                                        List<String> labels, int lastContactDays) {
        ContactDocument doc = new ContactDocument();
        doc.setId(mongoId);
        doc.setName(name);
        doc.setRelationType(relationType);
        doc.setIntimacy(0);
        doc.setLastContactDays(lastContactDays);
        doc.setInterests(interests);
        doc.setLabels(labels);
        doc.setPersonality(personality);
        doc.setSuppressWarning(false);
        doc.setRecovering(false);
        doc.setWarning(false);
        doc.setCreatedAt(LocalDateTime.now());
        doc.setUpdatedAt(LocalDateTime.now());
        return contactRepo.save(doc);
    }

    private void addInteraction(ContactDocument doc, String type, String summary, LocalDate time) {
        ContactDocument.InteractionRecord rec = new ContactDocument.InteractionRecord();
        rec.setType(type);
        rec.setSummary(summary);
        rec.setTime(time.atStartOfDay());
        doc.getInteractions().add(rec);
        contactRepo.save(doc);
    }

    private void saveConversation(String sessionId, String relatedContactId,
                                  LocalDate createdAt, int msgCount, int[] dims, String comment) {
        Evaluation eval = new Evaluation();
        eval.setClarity(dims[0]);
        eval.setLogicality(dims[1]);
        eval.setEmpathyListening(dims[2]);
        eval.setInteractivity(dims[3]);
        eval.setRelaxation(dims[4]);
        eval.setComment(comment);
        eval.setStrengths(new ArrayList<>());
        eval.setSuggestions(new ArrayList<>());

        List<MessageItem> messages = new ArrayList<>();
        for (int i = 0; i < msgCount; i++) {
            MessageItem msg = new MessageItem();
            msg.setMessageOrder(i + 1);
            msg.setRole(i % 2 == 0 ? "self" : "other");
            msg.setContent("占位消息 " + (i + 1) + " — seed data");
            msg.setTimestamp(System.currentTimeMillis());
            messages.add(msg);
        }

        ConversationDocument doc = new ConversationDocument();
        doc.setId(sessionId);
        doc.setRelatedContactId(relatedContactId);
        doc.setTheme("seed-test");
        doc.setPersonality("test");
        doc.setScore(0);
        doc.setEvaluation(eval);
        doc.setMessages(messages);
        doc.setCreatedAt(createdAt.atStartOfDay());
        doc.setUpdatedAt(createdAt.atStartOfDay());
        conversationRepo.save(doc);
    }

    // ── verification ──

    private void verify(List<Map<String, Object>> warnings, LocalDate now) {
        log.info("--- Verification (debug mode, assertions soft-fail) ---");

        ContactDocument a = contactRepo.findById("9001").orElseThrow();
        ContactDocument b = contactRepo.findById("9002").orElseThrow();
        ContactDocument c = contactRepo.findById("9003").orElseThrow();
        ContactDocument d = contactRepo.findById("9004").orElseThrow();

        // 1. A: 近2天，4次互动 + 深度0.6 + 质量0.78 → 内环 (~78)
        assertBetween("A score", a.getIntimacy(), 76, 80);
        log.info("✅ 1. A score={} ∈ [76,80]", a.getIntimacy());

        // 2. B: 40天，无近期互动，低分 → 外环 (~29)
        assertBetween("B score", b.getIntimacy(), 28, 31);
        log.info("✅ 2. B score={} ∈ [28,31]", b.getIntimacy());

        // 3. C: 60天但质量高+近期互动 → 中环 (~55)
        assertBetween("C score", c.getIntimacy(), 53, 57);
        log.info("✅ 3. C score={} ∈ [53,57]", c.getIntimacy());

        // 4. D: 25天，中等 → 外环 (~36)
        assertBetween("D score", d.getIntimacy(), 35, 38);
        log.info("✅ 4. D score={} ∈ [35,38]", d.getIntimacy());

        // 5. C(家人,60d) > B(同事,40d) — 质量/频率抵消时间衰减
        assertGreater("C(家人,60d) > B(同事,40d)", c.getIntimacy(), b.getIntimacy());
        log.info("✅ 5. C score({}) > B score({}) — 质量/频率抵消时间衰减生效", c.getIntimacy(), b.getIntimacy());

        // 6. B score > 0 (TTL 分量兜底)
        assertGreater("B score > 0", b.getIntimacy(), 0);
        log.info("✅ 6. B score={} > 0 — TTL component active", b.getIntimacy());

        // 7. 疏远预警（WarningService: intimacy < 40 且未抑制）
        boolean aNoWarn = warnings.stream().noneMatch(w -> "9001".equals(w.get("contactId")));
        boolean bHasWarn = warnings.stream().anyMatch(w ->
                "9002".equals(w.get("contactId")) && "疏远预警".equals(w.get("type")));
        boolean dHasWarn = warnings.stream().anyMatch(w ->
                "9004".equals(w.get("contactId")) && "疏远预警".equals(w.get("type")));
        boolean cNoWarn = warnings.stream().noneMatch(w -> "9003".equals(w.get("contactId")));

        assertTrue("A no warnings", aNoWarn);
        log.info("✅ 7a. A: no warnings ✓");
        assertTrue("B has 疏远预警", bHasWarn);
        log.info("✅ 7b. B(score={}): 疏远预警 ✓", b.getIntimacy());
        assertTrue("D has 疏远预警", dHasWarn);
        log.info("✅ 7c. D(score={}): 疏远预警 ✓", d.getIntimacy());
        assertTrue("C no 疏远预警 (≥40)", cNoWarn);
        log.info("✅ 7d. C(score={}): no 疏远预警（由表达层 STAGNATION 覆盖）✓", c.getIntimacy());

        // 8. Warning count is reasonable
        assertLessOrEqual("test warnings count ≤ 3", warnings.size(), 3);
        log.info("✅ 8. Test warnings count={} ≤ 3", warnings.size());

        log.info("--- ALL ASSERTIONS PASSED ---");
    }

    // ── assertion helpers ──

    private void assertBetween(String label, int actual, int min, int max) {
        if (actual < min || actual > max) {
            String msg = String.format("FAIL %s: expected [%d,%d], got %d", label, min, max, actual);
            log.error(msg);
            throw new AssertionError(msg);
        }
    }

    private void assertGreater(String label, int actual, int threshold) {
        if (actual <= threshold) {
            String msg = String.format("FAIL %s: expected > %d, got %d", label, threshold, actual);
            log.error(msg);
            throw new AssertionError(msg);
        }
    }

    private void assertTrue(String label, boolean condition) {
        if (!condition) {
            String msg = "FAIL " + label;
            log.error(msg);
            throw new AssertionError(msg);
        }
    }

    private void assertLessOrEqual(String label, int actual, int max) {
        if (actual > max) {
            String msg = String.format("FAIL %s: expected ≤ %d, got %d", label, max, actual);
            log.error(msg);
            throw new AssertionError(msg);
        }
    }
}
