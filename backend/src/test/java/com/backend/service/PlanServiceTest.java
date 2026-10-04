package com.backend.service;

import com.backend.document.ConversationDocument;
import com.backend.document.TrainingPlanDocument;
import com.backend.document.UserProfileDocument;
import com.backend.repository.ConversationRepository;
import com.backend.repository.TrainingPlanRepository;
import com.backend.repository.UserProfileRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/**
 * P2-3 处方生成管线单测。
 * 零 Mockito 依赖（JVM/Java 版本鲁棒）：Spring Data 接口用 JDK 动态代理做 fake，
 * LLM 用 PlanService.llm() 接缝子类覆写。
 */
class PlanServiceTest {

    /** 测试子类：llm 调用替换为 canned 输出（或抛异常验证兜底）。 */
    static class TestPlanService extends PlanService {
        String canned = "{}";
        boolean throwLlm = false;

        TestPlanService(TrainingPlanRepository planRepo, ConversationRepository conversationRepo,
                        UserProfileRepository profileRepo,
                        com.backend.repository.WeeklyReportRepository weeklyRepo) {
            super(planRepo, conversationRepo, profileRepo, weeklyRepo, new PlanStatsService(), null);
        }

        @Override
        String llm(String prompt) {
            if (throwLlm) {
                throw new RuntimeException("llm down");
            }
            return canned;
        }
    }

    /** 接口 fake：按方法名路由；未覆盖的 Optional→empty / List→空 / 其它→null。记录全部调用。 */
    @SuppressWarnings("unchecked")
    static <T> T fake(Class<T> iface, Map<String, Function<Object[], Object>> impls,
                      List<Object[]> calls) {
        return (T) Proxy.newProxyInstance(iface.getClassLoader(), new Class<?>[]{iface},
                (proxy, method, args) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        return switch (method.getName()) {
                            case "hashCode" -> System.identityHashCode(proxy);
                            case "equals" -> proxy == args[0];
                            default -> proxy.toString();
                        };
                    }
                    calls.add(new Object[]{method.getName(), args});
                    Function<Object[], Object> fn = impls.get(method.getName());
                    if (fn != null) {
                        return fn.apply(args);
                    }
                    Class<?> rt = method.getReturnType();
                    if (rt == Optional.class) return Optional.empty();
                    if (rt == List.class) return List.of();
                    if (rt == boolean.class || rt == Boolean.class) return false;
                    if (rt == int.class) return 0;
                    return null;
                });
    }

    private List<Object> savedPlans;
    private List<Object[]> planCalls;
    private List<ConversationDocument> scoredStore;
    private List<TrainingPlanDocument> weekPlans;
    /** 由本字段驱动 findFirstByUserIdAndStatusOrderByGeneratedAtDesc（active 计划夹具） */
    private TrainingPlanDocument currentActive;
    private TestPlanService svc;

    @BeforeEach
    void setUp() {
        savedPlans = new ArrayList<>();
        planCalls = new ArrayList<>();
        scoredStore = new ArrayList<>();
        weekPlans = new ArrayList<>();

        Map<String, Function<Object[], Object>> planImpls = new HashMap<>();
        planImpls.put("save", a -> {
            savedPlans.add(a[0]);
            return a[0];
        });
        planImpls.put("findByUserIdOrderByWeekStartDesc", a -> new ArrayList<>(weekPlans));
        planImpls.put("findFirstByUserIdAndStatusOrderByGeneratedAtDesc", a -> Optional.ofNullable(currentActive));
        TrainingPlanRepository planRepo = fake(TrainingPlanRepository.class, planImpls, planCalls);

        Map<String, Function<Object[], Object>> convImpls = new HashMap<>();
        convImpls.put("findAllByOrderByCreatedAtDesc", a -> new ArrayList<>(scoredStore));
        convImpls.put("findById", a -> scoredStore.stream()
                .filter(d -> d.getId().equals(a[0])).findFirst());
        ConversationRepository conversationRepo = fake(ConversationRepository.class, convImpls, new ArrayList<>());

        UserProfileDocument p = new UserProfileDocument();
        p.setAssessment("总体平稳，共情偏弱");
        p.setTopWeaknesses(List.of("情绪回应"));
        Map<String, Function<Object[], Object>> profImpls = new HashMap<>();
        profImpls.put("findDefault", a -> Optional.of(p));
        UserProfileRepository profileRepo = fake(UserProfileRepository.class, profImpls, new ArrayList<>());

        Map<String, Function<Object[], Object>> weekImpls = new HashMap<>();
        com.backend.repository.WeeklyReportRepository weeklyRepo =
                fake(com.backend.repository.WeeklyReportRepository.class, weekImpls, new ArrayList<>());

        svc = new TestPlanService(planRepo, conversationRepo, profileRepo, weeklyRepo);
    }

    /** 构造 count 场含子技能分、emotion_naming 恒低 40（→ 主攻焦点）、其余 85 的会话。 */
    private void stubScoredSessions(int count) {
        for (int i = 0; i < count; i++) {
            ConversationDocument d = new ConversationDocument();
            d.setId("s" + i);
            d.setTheme("日常闲聊");
            d.setCreatedAt(LocalDateTime.now().minusDays(i + 1));
            d.setScore(60);
            ConversationDocument.Evaluation ev = new ConversationDocument.Evaluation();
            Map<String, Integer> subs = new LinkedHashMap<>();
            for (String k : List.of("emotion_naming", "paraphrase", "active_questioning", "filler_control")) {
                subs.put(k, "emotion_naming".equals(k) ? 40 : 85);
            }
            ev.setSubScores(subs);
            ev.setEvidence(List.of(new ConversationDocument.EvidenceItem(
                    "emotion_naming", 3, "嗯嗯", "未回应情绪", "听起来你有点失落", "negative")));
            d.setEvaluation(ev);
            scoredStore.add(d);
        }
    }

    @Test
    void insufficientWhenFewSessions() {
        stubScoredSessions(2);
        var r = svc.generatePrescription(1L, "auto");
        assertEquals("insufficient_data", r.status());
        assertTrue(savedPlans.isEmpty());
    }

    @Test
    void happyPathGeneratesValidPlan() throws Exception {
        stubScoredSessions(4);
        svc.canned = new ObjectMapper().writeValueAsString(Map.of(
                "summary", "本周主攻情绪命名",
                "tasks", List.of(
                        task("simulation", "emotion_naming", "完成2场情绪命名模拟", 2),
                        task("realworld", "emotion_naming", "真实对话命名情绪2次", 2),
                        task("knowledge", "paraphrase", "学复述确认三步并复述", 1))));

        var r = svc.generatePrescription(1L, "auto");

        assertEquals("ok", r.status());
        TrainingPlanDocument plan = r.plan();
        assertNotNull(plan);
        assertEquals(List.of("emotion_naming"), plan.getDiagnosis().getFocusSubSkills());
        assertEquals(3, plan.getTasks().size());
        assertEquals(List.of("t1", "t2", "t3"),
                plan.getTasks().stream().map(TrainingPlanDocument.Task::getId).toList());
        var sim = plan.getTasks().get(0);
        assertEquals(TrainingPlanDocument.VERIFY_AUTO, sim.getVerifyMode());
        assertEquals(70, sim.getAutoRule().getMinScore());
        assertEquals("emotion_naming", sim.getAutoRule().getSubSkill());
        var rw = plan.getTasks().get(1);
        assertEquals(TrainingPlanDocument.VERIFY_EVIDENCE, rw.getVerifyMode());
        assertFalse(rw.getWhyRefs().isEmpty());
        assertEquals(TrainingPlanDocument.STATUS_ACTIVE, plan.getStatus());
        assertTrue(plan.getWeekKey().matches("\\d{4}-W\\d{2}")); // 如 2026-W38
        assertTrue(savedPlans.contains(plan));
    }

    @Test
    void badTasksFilteredAndCompositionBackfilled() throws Exception {
        stubScoredSessions(4);
        svc.canned = new ObjectMapper().writeValueAsString(Map.of(
                "summary", "s",
                "tasks", List.of(
                        task("simulation", "foo_bar", "非法子技能", 2),           // 白名单外 → 丢弃
                        task("xyz", "emotion_naming", "非法类型", 1),             // 类型外 → 丢弃
                        task("simulation", "active_questioning", "非焦点sim", 2), // sim 不在 focus → 丢弃
                        task("realworld", "paraphrase", "复述确认打卡", 2))));     // 合法保留

        var r = svc.generatePrescription(1L, "auto");

        assertEquals("ok", r.status());
        var tasks = r.plan().getTasks();
        assertTrue(tasks.size() >= 3 && tasks.size() <= 5, "补齐后应落在 3~5，实际 " + tasks.size());
        assertTrue(tasks.stream().anyMatch(t -> "simulation".equals(t.getType())
                && "emotion_naming".equals(t.getSubSkill())), "兜底补齐了主焦点 sim 任务");
        assertTrue(tasks.stream().allMatch(t -> List.of("simulation", "realworld", "knowledge").contains(t.getType())));
        assertTrue(tasks.stream().filter(t -> "knowledge".equals(t.getType())).count() <= 1);
    }

    @Test
    void llmFailureFallsBackToTemplates() {
        stubScoredSessions(3);
        svc.throwLlm = true;

        var r = svc.generatePrescription(1L, "auto");

        assertEquals("ok", r.status(), "LLM 失败仍应产出模板处方");
        assertTrue(r.plan().getTasks().size() >= 3);
        assertTrue(r.plan().getDiagnosis().getSummary().contains("情绪命名"));
    }

    @Test
    void oldActivePlanArchived() {
        stubScoredSessions(3);
        svc.canned = "{\"summary\":\"s\",\"tasks\":[]}"; // 空任务 → 全模板兜底
        TrainingPlanDocument old = new TrainingPlanDocument();
        old.setUserId(1L);
        old.setWeekKey("2026-W01");
        old.setStatus(TrainingPlanDocument.STATUS_ACTIVE);
        weekPlans.add(old);

        var r = svc.generatePrescription(1L, "manual");

        assertEquals("ok", r.status());
        assertEquals(TrainingPlanDocument.STATUS_ARCHIVED, old.getStatus(), "旧 active 计划应被归档");
    }

    @Test
    void concurrentCallReturnsGenerating() throws Exception {
        stubScoredSessions(3);
        // ReentrantLock 同线程可重入 → 必须由另一线程持锁模拟并发在途
        java.util.concurrent.Semaphore held = new java.util.concurrent.Semaphore(0);
        java.util.concurrent.Semaphore release = new java.util.concurrent.Semaphore(0);
        Thread holder = new Thread(() -> {
            svc.genLock.lock();
            held.release();
            try {
                release.acquire();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                svc.genLock.unlock();
            }
        });
        holder.start();
        held.acquire();
        try {
            var r = svc.generatePrescription(1L, "auto");
            assertEquals("generating", r.status());
        } finally {
            release.release();
            holder.join(2000);
        }
    }

    // ─────────────── P2-4 自动回销 / 周切换 ───────────────

    private TrainingPlanDocument planWith(TrainingPlanDocument.Task... ts) {
        TrainingPlanDocument p = new TrainingPlanDocument();
        p.setId("plan-fix");
        p.setUserId(1L);
        p.setWeekKey(WeekKeyUtilTestCurrent());
        p.setStatus(TrainingPlanDocument.STATUS_ACTIVE);
        p.setGeneratedAt(LocalDateTime.now());
        List<TrainingPlanDocument.Task> list = new ArrayList<>();
        for (var t : ts) {
            list.add(t);
        }
        p.setTasks(list);
        return p;
    }

    private static String WeekKeyUtilTestCurrent() {
        return com.backend.util.WeekKeyUtil.currentWeekKey();
    }

    private TrainingPlanDocument.Task autoTask(String id, String subSkill, int minScore, int target) {
        TrainingPlanDocument.Task t = new TrainingPlanDocument.Task();
        t.setId(id);
        t.setType(TrainingPlanDocument.TYPE_SIMULATION);
        t.setSubSkill(subSkill);
        t.setTitle("针对" + subSkill + "的训练");
        t.setVerifyMode(TrainingPlanDocument.VERIFY_AUTO);
        t.setAutoRule(new TrainingPlanDocument.AutoRule(
                TrainingPlanDocument.AutoRule.KIND_SESSION_MIN, subSkill, minScore, target));
        t.setTarget(target);
        t.setStatus(TrainingPlanDocument.TASK_PENDING);
        return t;
    }

    private TrainingPlanDocument.Task evidenceTask(String id, String subSkill, int target) {
        TrainingPlanDocument.Task t = new TrainingPlanDocument.Task();
        t.setId(id);
        t.setType(TrainingPlanDocument.TYPE_REALWORLD);
        t.setSubSkill(subSkill);
        t.setTitle("真实场景运用" + subSkill);
        t.setSuccessCriteria("打卡并通过点评");
        t.setInstruction("在真实对话中主动使用");
        t.setVerifyMode(TrainingPlanDocument.VERIFY_EVIDENCE);
        t.setTarget(target);
        t.setStatus(TrainingPlanDocument.TASK_PENDING);
        return t;
    }

    @Test
    void onSessionScoredCreditsAndIdempotent() {
        stubScoredSessions(3); // s0..s2: paraphrase 85, emotion 40
        currentActive = planWith(autoTask("t1", "paraphrase", 70, 2));

        svc.onSessionScored("s0");
        var task = currentActive.getTasks().get(0);
        assertEquals(1, task.getProgress());
        assertEquals(TrainingPlanDocument.TASK_IN_PROGRESS, task.getStatus());

        svc.onSessionScored("s0"); // 幂等：同场重复触发不重复计
        assertEquals(1, task.getProgress());

        svc.onSessionScored("s1");
        assertEquals(2, task.getProgress());
        assertEquals(TrainingPlanDocument.TASK_DONE, task.getStatus());
        assertNotNull(task.getDoneAt());
    }

    @Test
    void onSessionScoredBelowThresholdNoCredit() {
        stubScoredSessions(2);
        currentActive = planWith(autoTask("t1", "emotion_naming", 70, 2)); // emotion=40 < 70
        svc.onSessionScored("s0");
        assertEquals(0, currentActive.getTasks().get(0).getProgress());
        assertTrue(currentActive.getTasks().get(0).getCreditedSessionIds().isEmpty());
    }

    @Test
    void rolloverExpiresArchivesAndRegenerates() {
        stubScoredSessions(3);
        svc.canned = "{\"summary\":\"s\",\"tasks\":[]}"; // 全模板兜底
        currentActive = planWith(autoTask("t1", "emotion_naming", 70, 2), evidenceTask("t2", "paraphrase", 1));
        currentActive.setWeekKey("2020-W01");
        weekPlans.add(currentActive);

        svc.ensureWeekRollover(1L);

        assertEquals(TrainingPlanDocument.TASK_EXPIRED, currentActive.getTasks().get(0).getStatus());
        assertEquals(TrainingPlanDocument.TASK_EXPIRED, currentActive.getTasks().get(1).getStatus());
        assertEquals(TrainingPlanDocument.STATUS_ARCHIVED, currentActive.getStatus());
        TrainingPlanDocument fresh = savedPlans.stream()
                .filter(o -> o instanceof TrainingPlanDocument)
                .map(o -> (TrainingPlanDocument) o)
                .filter(p -> TrainingPlanDocument.STATUS_ACTIVE.equals(p.getStatus()))
                .findFirst().orElse(null);
        assertNotNull(fresh, "周切换应生成新处方");
        assertEquals(com.backend.util.WeekKeyUtil.currentWeekKey(), fresh.getWeekKey());
    }

    @Test
    void rolloverSkipsCurrentWeek() {
        currentActive = planWith(autoTask("t1", "emotion_naming", 70, 2));
        svc.ensureWeekRollover(1L);
        assertEquals(TrainingPlanDocument.STATUS_ACTIVE, currentActive.getStatus());
        assertEquals(TrainingPlanDocument.TASK_PENDING, currentActive.getTasks().get(0).getStatus());
    }

    // ─────────────── P2-5 提交点评 ───────────────

    @Test
    void submitTaskRulePrechecksAndAccept() {
        currentActive = planWith(autoTask("t1", "paraphrase", 70, 2), evidenceTask("t2", "emotion_naming", 2));

        // auto 任务引导，不落提交
        var r1 = svc.submitTask("t1", "今天我在聊天里主动命名了对方的情绪，对方明显放松了，还反过来问我怎么知道的");
        assertFalse((Boolean) r1.get("accepted"));
        assertTrue(String.valueOf(r1.get("aiFeedback")).contains("自动完成"));

        // 短文拒绝（不调 LLM）
        var r2 = svc.submitTask("t2", "练了");
        assertFalse((Boolean) r2.get("accepted"));
        assertEquals(0, currentActive.getTasks().get(1).getSubmissions().size());

        // 合格提交 → LLM accepted → 进度推进
        svc.canned = "{\"accepted\":true,\"aiFeedback\":\"很好，你抓住了对方的失落并说出来\"}";
        var r3 = svc.submitTask("t2", "同事说方案被驳回，我回了一句听起来你为这个方案熬了不少夜，挺失落的吧，她愣了一下说是的");
        assertTrue((Boolean) r3.get("accepted"));
        assertEquals(1, r3.get("progress"));
        assertEquals(TrainingPlanDocument.TASK_IN_PROGRESS, currentActive.getTasks().get(1).getStatus());

        // accepted 但 target=2 → 未 done
        var r4 = svc.submitTask("t2", "读书会轮到分享，我先复述了上一位的观点再谈自己看法，讨论明显接得更快了");
        assertEquals(2, r4.get("progress"));
        assertEquals(TrainingPlanDocument.TASK_DONE, currentActive.getTasks().get(1).getStatus());
    }

    @Test
    void submitTaskLlmFailureNotCounted() {
        currentActive = planWith(evidenceTask("t1", "emotion_naming", 1));
        svc.throwLlm = true;
        var r = svc.submitTask("t1", "朋友抱怨加班多，我说听起来你最近真的很累，她说是啊终于有人懂我了");
        assertFalse((Boolean) r.get("accepted"));
        assertTrue(String.valueOf(r.get("aiFeedback")).contains("未计入次数"));
        assertEquals(0, currentActive.getTasks().get(0).getSubmissions().size());
        assertEquals(3, r.get("remaining"));
    }

    @Test
    void submitTaskLockedAfterMax() {
        currentActive = planWith(evidenceTask("t1", "emotion_naming", 3));
        var task = currentActive.getTasks().get(0);
        for (int i = 0; i < 3; i++) {
            task.getSubmissions().add(new TrainingPlanDocument.Submission("旧提交内容" + i, "旧点评", false, LocalDateTime.now()));
        }
        var r = svc.submitTask("t1", "这次写了足够长的内容描述真实发生的对话过程和结果");
        assertTrue((Boolean) r.get("locked"));
        assertEquals(0, r.get("remaining"));
    }

    @Test
    void submitTaskNotFoundReturnsNull() {
        currentActive = planWith(evidenceTask("t1", "emotion_naming", 1));
        assertNull(svc.submitTask("t99", "足够长的一段真实事件描述，谁说了什么做了什么结果"));
    }

    @Test
    void recommendationMapsFocusAndTask() {
        currentActive = planWith(autoTask("t1", "emotion_naming", 70, 2));
        currentActive.setDiagnosis(new TrainingPlanDocument.Diagnosis(
                List.of("emotion_naming"), "本周主攻情绪命名", Map.of(), List.of()));

        var rec = svc.recommendation(1L);

        assertNotNull(rec);
        assertEquals("emotion_naming", rec.get("subSkill"));
        assertEquals("情绪命名", rec.get("cnName"));
        assertEquals("日常闲聊", rec.get("theme"));
        assertEquals("不善交际慢热", rec.get("personality"));
        assertEquals("t1", rec.get("taskId"));
    }

    @Test
    void recommendationNullWithoutDiagnosis() {
        currentActive = planWith(autoTask("t1", "emotion_naming", 70, 2));
        assertNull(svc.recommendation(1L));
    }

    private Map<String, Object> task(String type, String subSkill, String title, int target) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", type);
        m.put("subSkill", subSkill);
        m.put("title", title);
        m.put("instruction", "步骤：先…再…");
        m.put("successCriteria", "标准：" + title);
        m.put("target", target);
        return m;
    }
}
