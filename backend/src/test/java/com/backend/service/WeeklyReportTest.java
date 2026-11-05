package com.backend.service;

import com.backend.document.ConversationDocument;
import com.backend.document.TrainingPlanDocument;
import com.backend.document.UserProfileDocument;
import com.backend.document.WeeklyReportDocument;
import com.backend.repository.ConversationRepository;
import com.backend.repository.TrainingPlanRepository;
import com.backend.repository.UserProfileRepository;
import com.backend.repository.WeeklyReportRepository;
import com.backend.util.WeekKeyUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import static com.backend.service.PlanServiceTest.fake;
import static org.junit.jupiter.api.Assertions.*;

/**
 * P3-1/P3-2 周报生成、采纳与草稿刷新单测（llm 接缝 + 动态代理 fake，无 Spring / 无 LLM）。
 */
class WeeklyReportTest {

    static class RptPlanService extends PlanService {
        String canned = "{}";
        boolean throwLlm = false;

        RptPlanService(TrainingPlanRepository planRepo, ConversationRepository convRepo,
                       UserProfileRepository profRepo, WeeklyReportRepository weeklyRepo) {
            super(planRepo, convRepo, profRepo, weeklyRepo, new PlanStatsService(), null);
        }

        @Override
        String llm(String prompt) {
            if (throwLlm) {
                throw new RuntimeException("llm down");
            }
            return canned;
        }
    }

    private List<ConversationDocument> scoredStore;
    private Map<String, WeeklyReportDocument> reportStore;
    private TrainingPlanDocument currentActive;
    private List<TrainingPlanDocument> planStore;
    private RptPlanService svc;

    @BeforeEach
    void setUp() {
        scoredStore = new ArrayList<>();
        reportStore = new HashMap<>();
        planStore = new ArrayList<>();

        Map<String, Function<Object[], Object>> convImpls = new HashMap<>();
        convImpls.put("findAllByOrderByCreatedAtDesc", a -> new ArrayList<>(scoredStore));
        ConversationRepository convRepo = fake(ConversationRepository.class, convImpls, new ArrayList<>());

        Map<String, Function<Object[], Object>> profImpls = new HashMap<>();
        UserProfileDocument p = new UserProfileDocument();
        p.setAssessment("平稳");
        profImpls.put("findDefault", a -> Optional.of(p));
        UserProfileRepository profRepo = fake(UserProfileRepository.class, profImpls, new ArrayList<>());

        Map<String, Function<Object[], Object>> planImpls = new HashMap<>();
        planImpls.put("save", a -> {
            planStore.add((TrainingPlanDocument) a[0]);
            return a[0];
        });
        planImpls.put("findFirstByUserIdAndStatusOrderByGeneratedAtDesc", a -> Optional.ofNullable(currentActive));
        planImpls.put("findByUserIdOrderByWeekStartDesc", a -> new ArrayList<>(planStore));
        TrainingPlanRepository planRepo = fake(TrainingPlanRepository.class, planImpls, new ArrayList<>());

        Map<String, Function<Object[], Object>> weekImpls = new HashMap<>();
        weekImpls.put("findFirstByUserIdAndWeekKey",
                a -> Optional.ofNullable(reportStore.get(String.valueOf(a[1]))));
        weekImpls.put("save", a -> {
            WeeklyReportDocument r = (WeeklyReportDocument) a[0];
            reportStore.put(r.getWeekKey(), r);
            return a[0];
        });
        weekImpls.put("findByUserIdOrderByWeekKeyDesc", a -> reportStore.values().stream()
                .sorted(Comparator.comparing(WeeklyReportDocument::getWeekKey).reversed()).toList());
        WeeklyReportRepository weeklyRepo = fake(WeeklyReportRepository.class, weekImpls, new ArrayList<>());

        svc = new RptPlanService(planRepo, convRepo, profRepo, weeklyRepo);
    }

    private ConversationDocument conv(String id, LocalDateTime at, Map<String, Integer> subs,
                                      List<ConversationDocument.EvidenceItem> evs, String focus) {
        ConversationDocument d = new ConversationDocument();
        d.setId(id);
        d.setTheme("日常闲聊");
        d.setCreatedAt(at);
        d.setUpdatedAt(at);
        d.setScore(60);
        d.setFocusSubSkill(focus);
        ConversationDocument.Evaluation ev = new ConversationDocument.Evaluation();
        ev.setSubScores(subs);
        ev.setEvidence(evs);
        d.setEvaluation(ev);
        return d;
    }

    private Map<String, Integer> subs(Object... kv) {
        Map<String, Integer> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], (Integer) kv[i + 1]);
        }
        return m;
    }

    @Test
    void staticHelpersComputeCompletionDeltasHighlights() {
        LocalDate ws = LocalDate.of(2026, 9, 7); // 2026-W37 周一
        LocalDateTime mid = ws.plusDays(2).atTime(10, 0);

        TrainingPlanDocument plan = new TrainingPlanDocument();
        TrainingPlanDocument.Task t1 = new TrainingPlanDocument.Task();
        t1.setStatus(TrainingPlanDocument.TASK_DONE);
        TrainingPlanDocument.Task t2 = new TrainingPlanDocument.Task();
        t2.setStatus(TrainingPlanDocument.TASK_EXPIRED);
        t2.setSubmissions(new ArrayList<>(List.of(
                new TrainingPlanDocument.Submission("a", "fb", true, mid),
                new TrainingPlanDocument.Submission("b", "fb", false, mid))));
        plan.setTasks(List.of(t1, t2));

        ConversationDocument in1 = conv("w1", mid, subs("emotion_naming", 60, "paraphrase", 90),
                List.of(new ConversationDocument.EvidenceItem(
                        "paraphrase", 2, "你的意思是…", "", "", "positive")), "emotion_naming");
        ConversationDocument in2 = conv("w2", mid.plusDays(1), subs("emotion_naming", 70), List.of(), null);
        ConversationDocument before1 = conv("b1", mid.minusDays(10), subs("emotion_naming", 40, "paraphrase", 70), List.of(), null);

        var c = PlanService.computeCompletion(plan, List.of(in1, in2));
        assertEquals(2, c.getTasksTotal());
        assertEquals(1, c.getTasksDone());
        assertEquals(1, c.getTasksExpired());
        assertEquals(2, c.getSessionsScored());
        assertEquals(1, c.getFocusSessions());
        assertEquals(2, c.getSubmissions());
        assertEquals(1, c.getSubmissionsAccepted());

        var deltas = PlanService.computeDeltas(List.of(in1, in2), List.of(before1));
        assertEquals(25, deltas.get("emotion_naming")); // (60+70)/2=65 − 40 = 25
        // 断言修正：65-40=25
        var hi = PlanService.pickHighlights(List.of(in1, in2));
        assertEquals(1, hi.size());
        assertEquals("paraphrase", hi.get(0).getSubSkill());
    }

    @Test
    void generateReportEndToEndIdempotentAndLlmFallback() {
        // 本周 3 场（updatedAt 落在 W37），无更早对照
        LocalDate w37Start = WeekKeyUtil.weekStart("2026-W37");
        LocalDateTime at = w37Start.atTime(12, 0);
        for (int i = 0; i < 3; i++) {
            scoredStore.add(conv("s" + i, at.plusDays(i), subs("emotion_naming", 40 + i, "paraphrase", 80),
                    List.of(new ConversationDocument.EvidenceItem(
                            "paraphrase", 2, "所以说你…", "", "", "positive")), i == 0 ? "emotion_naming" : null));
        }
        TrainingPlanDocument plan = new TrainingPlanDocument();
        plan.setUserId(1L);
        plan.setWeekKey("2026-W37");
        plan.setWeekStart(w37Start);
        plan.setWeekEnd(WeekKeyUtil.weekEnd("2026-W37"));
        plan.setStatus(TrainingPlanDocument.STATUS_ACTIVE);
        TrainingPlanDocument.Task t = new TrainingPlanDocument.Task();
        t.setStatus(TrainingPlanDocument.TASK_DONE);
        plan.setTasks(new ArrayList<>(List.of(t)));
        plan.setDiagnosis(new TrainingPlanDocument.Diagnosis(List.of("emotion_naming"), "主攻情绪命名", Map.of(), List.of()));

        svc.canned = "{\"aiSummary\":\"本周完成1/1任务，训练3场。情绪命名有进步，继续！\",\"nextWeekDraft\":null}";
        var r = svc.generateWeeklyReport(plan);
        assertNotNull(r);
        assertEquals("2026-W37", r.getWeekKey());
        assertEquals(1, r.getCompletion().getTasksDone());
        assertEquals(3, r.getCompletion().getSessionsScored());
        assertTrue(r.getAiSummary().contains("1/1"));
        assertEquals(1, r.getHighlights().size());

        // 幂等：已存在直接返回旧件，不产新草稿
        svc.throwLlm = true;
        var r2 = svc.generateWeeklyReport(plan);
        assertSame(r, r2);
    }

    @Test
    void llmFailureUsesTemplateSummary() {
        LocalDate w36Start = WeekKeyUtil.weekStart("2026-W36");
        scoredStore.add(conv("s1", w36Start.atTime(9, 0), subs("emotion_naming", 50), List.of(), null));
        TrainingPlanDocument plan = new TrainingPlanDocument();
        plan.setUserId(1L);
        plan.setWeekKey("2026-W36");
        plan.setWeekStart(w36Start);
        plan.setWeekEnd(WeekKeyUtil.weekEnd("2026-W36"));
        plan.setTasks(new ArrayList<>());
        svc.throwLlm = true;

        var r = svc.generateWeeklyReport(plan);
        assertNotNull(r);
        assertTrue(r.getAiSummary().contains("本周完成"));
        assertNull(r.getNextWeekDraft());
    }

    @Test
    void adoptThenRolloverInstantiatesAdoptedDraft() {
        // 上周（W37）active 计划 + 已采纳草稿的周报
        currentActive = new TrainingPlanDocument();
        currentActive.setId("old");
        currentActive.setUserId(1L);
        currentActive.setWeekKey("2026-W37");
        currentActive.setWeekStart(WeekKeyUtil.weekStart("2026-W37"));
        currentActive.setWeekEnd(WeekKeyUtil.weekEnd("2026-W37"));
        currentActive.setStatus(TrainingPlanDocument.STATUS_ACTIVE);
        TrainingPlanDocument.Task pending = new TrainingPlanDocument.Task();
        pending.setId("t1");
        pending.setStatus(TrainingPlanDocument.TASK_PENDING);
        currentActive.setTasks(new ArrayList<>(List.of(pending)));

        TrainingPlanDocument.Task draftT1 = new TrainingPlanDocument.Task();
        draftT1.setId("t1");
        draftT1.setType(TrainingPlanDocument.TYPE_SIMULATION);
        draftT1.setSubSkill("emotion_naming");
        draftT1.setStatus(TrainingPlanDocument.TASK_PENDING);
        TrainingPlanDocument.Task draftT2 = new TrainingPlanDocument.Task();
        draftT2.setId("t2");
        draftT2.setType(TrainingPlanDocument.TYPE_REALWORLD);
        draftT2.setSubSkill("paraphrase");
        draftT2.setStatus(TrainingPlanDocument.TASK_PENDING);
        TrainingPlanDocument.Task draftT3 = new TrainingPlanDocument.Task();
        draftT3.setId("t3");
        draftT3.setType(TrainingPlanDocument.TYPE_KNOWLEDGE);
        draftT3.setSubSkill("turn_giving");
        draftT3.setStatus(TrainingPlanDocument.TASK_PENDING);
        WeeklyReportDocument rep = new WeeklyReportDocument();
        rep.setUserId(1L);
        rep.setWeekKey("2026-W37");
        rep.setAdopted(true);
        rep.setAdoptedDraft(new WeeklyReportDocument.NextWeekDraft("继续情绪与复述", List.of(draftT1, draftT2, draftT3)));
        reportStore.put("2026-W37", rep);

        svc.throwLlm = true; // 防周报/处方 LLM 真调用（generate 兜底模板）
        svc.ensureWeekRollover(1L);

        assertEquals(TrainingPlanDocument.STATUS_ARCHIVED, currentActive.getStatus());
        assertEquals(TrainingPlanDocument.TASK_EXPIRED, pending.getStatus());
        TrainingPlanDocument adopted = planStore.stream()
                .filter(x -> TrainingPlanDocument.STATUS_ACTIVE.equals(x.getStatus()))
                .reduce((first, second) -> second).orElse(null);
        assertNotNull(adopted);
        assertEquals("adopted", adopted.getGeneratedBy());
        assertEquals(3, adopted.getTasks().size());
        assertEquals("emotion_naming", adopted.getDiagnosis().getFocusSubSkills().get(0));
        // 草稿优先：不应再产出 auto 生成的第二份 active 处方
        long actives = planStore.stream()
                .filter(x -> TrainingPlanDocument.STATUS_ACTIVE.equals(x.getStatus())).count();
        assertEquals(1, actives);
    }

    @Test
    void refreshDraftUpdatesAndClearsAdopted() {
        LocalDate w38Start = WeekKeyUtil.weekStart("2026-W38");
        for (int i = 0; i < 3; i++) {
            scoredStore.add(conv("s" + i, w38Start.plusDays(i).atTime(10, 0),
                    subs("emotion_naming", 40, "paraphrase", 85, "active_questioning", 85),
                    List.of(), null));
        }
        WeeklyReportDocument rep = new WeeklyReportDocument();
        rep.setUserId(1L);
        rep.setWeekKey("2026-W37");
        rep.setAdopted(true);
        reportStore.put("2026-W37", rep);

        svc.canned = "{\"summary\":\"下周继续\",\"tasks\":[" +
                "{\"type\":\"simulation\",\"subSkill\":\"emotion_naming\",\"title\":\"练2场\",\"instruction\":\"i\",\"successCriteria\":\"s\",\"target\":2}," +
                "{\"type\":\"realworld\",\"subSkill\":\"emotion_naming\",\"title\":\"真实用1次\",\"instruction\":\"i\",\"successCriteria\":\"s\",\"target\":1}," +
                "{\"type\":\"realworld\",\"subSkill\":\"paraphrase\",\"title\":\"复述2次\",\"instruction\":\"i\",\"successCriteria\":\"s\",\"target\":2}]}";

        var res = svc.refreshDraft(1L, "2026-W37");
        assertEquals("ok", res.status());
        WeeklyReportDocument stored = reportStore.get("2026-W37");
        assertNotNull(stored.getNextWeekDraft());
        assertEquals(3, stored.getNextWeekDraft().getTasks().size());
        assertEquals(Boolean.FALSE, stored.getAdopted());
        assertNull(stored.getAdoptedDraft());
    }

    @Test
    void adoptDraftRejectsMissing() {
        assertFalse(svc.adoptDraft(1L, "2030-W01"));
    }
}
