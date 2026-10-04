package com.backend.service;

import com.backend.agent.UserProfileTools;
import com.backend.constant.SubSkillConstants;
import com.backend.document.ConversationDocument;
import com.backend.document.TrainingPlanDocument;
import com.backend.document.TrainingPlanDocument.AutoRule;
import com.backend.document.TrainingPlanDocument.Diagnosis;
import com.backend.document.TrainingPlanDocument.EvidenceRef;
import com.backend.document.TrainingPlanDocument.Task;
import com.backend.document.UserProfileDocument;
import com.backend.repository.ConversationRepository;
import com.backend.repository.TrainingPlanRepository;
import com.backend.repository.UserProfileRepository;
import com.backend.util.LlmJsonUtil;
import com.backend.util.WeekKeyUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 训练处方服务（P2）—— 能力提升重构的"开处方"环节。
 *
 * <p>职责：处方生成（{@link #generatePrescription}，P2-3）、周切换与自动回销
 * （{@link #ensureWeekRollover} / {@link #onSessionScored}，P2-4）、提交点评（P2-5）、
 * 今日推荐（P2-6 侧）。管线：感知用规则（{@link PlanStatsService}），表达用 LLM（单次小调用），
 * 校验兜底全规则（设计文档 §3.3）。
 */
@Service
public class PlanService {

    private static final Logger log = LoggerFactory.getLogger(PlanService.class);

    /** 处方生成结果。status: ok | generating | insufficient_data | error */
    public record PrescriptionResult(String status, TrainingPlanDocument plan) {
        public boolean ok() {
            return "ok".equals(status);
        }
    }

    /** LLM 返回的原始任务项（校验前）。 */
    record LlmTask(String type, String subSkill, String title, String instruction,
                   String successCriteria, int target) {
        static LlmTask from(JsonNode n) {
            return new LlmTask(
                    n.path("type").asText(""),
                    n.path("subSkill").asText(""),
                    n.path("title").asText(""),
                    n.path("instruction").asText(""),
                    n.path("successCriteria").asText(""),
                    n.path("target").asInt(1));
        }
    }

    /** 处方生成 Prompt 全文（设计文档施工单 P2-3 卡；{} 为占位符）。 */
    static final String PRESCRIPTION_PROMPT = """
            你是社交能力训练系统的训练处方专家。根据以下统计诊断，为用户生成本周训练处方。

            ## 统计诊断（规则引擎输出，可信）
            {statsJson}

            ## 近期负面证据摘录（用户原话）
            {negEvidence}

            ## 用户既有画像
            总体评价：{assessment}
            主要短板：{topWeaknesses}

            ## 生成要求
            输出 3~5 个任务，严格返回 JSON：
            {"summary":"≤80字，本周主攻什么、为什么（引用统计中的具体分数）",
             "tasks":[{"type":"simulation|realworld|knowledge",
                       "subSkill":"子技能短名，必须出自统计诊断",
                       "title":"≤25字，动词开头",
                       "instruction":"≤120字，教具体做法步骤，不写空话",
                       "successCriteria":"≤30字，可验证的完成标准",
                       "target":1~3的整数}]}
            硬约束：
            1. 至少 1 个 simulation、至少 1 个 realworld，knowledge 至多 1 个；
            2. simulation 任务的 subSkill 必须属于本周主攻焦点：{focusSubSkills}；
            3. 同一子技能最多 2 个任务；
            4. realworld 任务必须是本周内可在线下真实社交中完成、且行为可被自己观察到的；
            5. target 取值：simulation 为 1~2（场次），realworld/knowledge 为 1~3（次数）。
            只返回 JSON。
            """;

    private static final Set<String> TASK_TYPES =
            Set.of(TrainingPlanDocument.TYPE_SIMULATION, TrainingPlanDocument.TYPE_REALWORLD, TrainingPlanDocument.TYPE_KNOWLEDGE);

    /** 单飞锁：处方生成同一时刻至多一个在途（防懒触发并发重复生成，勘误外的实现细节）。 */
    final ReentrantLock genLock = new ReentrantLock();

    private final TrainingPlanRepository planRepo;
    private final ConversationRepository conversationRepo;
    private final UserProfileRepository profileRepo;
    private final com.backend.repository.WeeklyReportRepository weeklyReportRepo;
    private final PlanStatsService planStatsService;
    private final ChatClient chatClient;
    // JSR-310：Task.dueAt 等 LocalDateTime 的 convertValue 深拷贝依赖该模块（R3 联调发现的 P3-2 缺陷修复）
    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    public PlanService(TrainingPlanRepository planRepo,
                       ConversationRepository conversationRepo,
                       UserProfileRepository profileRepo,
                       com.backend.repository.WeeklyReportRepository weeklyReportRepo,
                       PlanStatsService planStatsService,
                       ChatClient.Builder chatClientBuilder) {
        this.planRepo = planRepo;
        this.conversationRepo = conversationRepo;
        this.profileRepo = profileRepo;
        this.weeklyReportRepo = weeklyReportRepo;
        this.planStatsService = planStatsService;
        // 允许注入 null builder（单测经 llm() 接缝覆写绕开 ChatClient）
        this.chatClient = chatClientBuilder == null ? null : chatClientBuilder.build();
    }

    /** LLM 调用接缝：单测子类覆写本方法，避免脆弱地 mock ChatClient 链。 */
    String llm(String prompt) {
        return chatClient.prompt().user(prompt).call().content();
    }

    // ════════════════════════════ P2-3 处方生成 ════════════════════════════

    public PrescriptionResult generatePrescription(Long userId, String triggeredBy) {
        if (!genLock.tryLock()) {
            return new PrescriptionResult("generating", null);
        }
        try {
            List<ConversationDocument> scored = loadScoredConversations();
            long withSubScores = scored.stream()
                    .filter(d -> d.getEvaluation().getSubScores() != null && !d.getEvaluation().getSubScores().isEmpty())
                    .count();
            if (withSubScores < 3) {
                return new PrescriptionResult("insufficient_data", null);
            }
            PlanStatsService.PlanStats stats = planStatsService.compute(scored);
            if (stats.insufficient()) {
                return new PrescriptionResult("insufficient_data", null);
            }

            UserProfileDocument profile = profileRepo.findDefault().orElse(null);
            List<LlmTask> llmTasks = new ArrayList<>();
            String summary;
            try {
                String out = llm(buildPrompt(stats, profile));
                JsonNode root = mapper.readTree(LlmJsonUtil.stripMarkdown(out));
                summary = root.path("summary").asText("");
                root.path("tasks").forEach(t -> llmTasks.add(LlmTask.from(t)));
            } catch (Exception e) {
                // LLM 失败 → 规则模板兜底（处方仍要生成，红线 2）
                log.warn("处方 LLM 生成失败，走规则模板兜底: {}", e.getMessage());
                summary = fallbackSummary(stats);
            }

            List<Task> tasks = assembleTasks(stats, llmTasks);
            if (tasks.isEmpty()) {
                return new PrescriptionResult("error", null);
            }

            // 归档同周旧 active（防御重复）+ 跨周旧 active
            planRepo.findByUserIdOrderByWeekStartDesc(userId).stream()
                    .filter(p -> TrainingPlanDocument.STATUS_ACTIVE.equals(p.getStatus()))
                    .forEach(old -> {
                        old.setStatus(TrainingPlanDocument.STATUS_ARCHIVED);
                        planRepo.save(old);
                    });

            String weekKey = WeekKeyUtil.currentWeekKey();
            TrainingPlanDocument plan = new TrainingPlanDocument();
            plan.setUserId(userId);
            plan.setWeekKey(weekKey);
            plan.setWeekStart(WeekKeyUtil.weekStart(weekKey));
            plan.setWeekEnd(WeekKeyUtil.weekEnd(weekKey));
            plan.setStatus(TrainingPlanDocument.STATUS_ACTIVE);
            plan.setGeneratedBy(triggeredBy);
            plan.setDiagnosis(buildDiagnosis(stats, summary));
            plan.setTasks(tasks);
            plan.setGeneratedAt(LocalDateTime.now());
            planRepo.save(plan);
            log.info("训练处方生成完成: weekKey={}, focus={}, tasks={}, by={}",
                    weekKey, stats.focusSubSkills(), tasks.size(), triggeredBy);
            return new PrescriptionResult("ok", plan);
        } catch (Exception e) {
            log.error("处方生成失败", e);
            return new PrescriptionResult("error", null);
        } finally {
            genLock.unlock();
        }
    }

    private List<ConversationDocument> loadScoredConversations() {
        return conversationRepo.findAllByOrderByCreatedAtDesc().stream()
                .filter(d -> d.getScore() != null && d.getEvaluation() != null)
                .toList();
    }

    private String buildPrompt(PlanStatsService.PlanStats stats, UserProfileDocument profile) {
        String statsJson;
        try {
            statsJson = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(stats);
        } catch (Exception e) {
            statsJson = String.valueOf(stats);
        }
        StringBuilder neg = new StringBuilder();
        for (PlanStatsService.NegEvidence ne : stats.negativeEvidenceTop()) {
            neg.append("[").append(SubSkillConstants.get(ne.subSkill()).cnName()).append("] 原话：\"")
                    .append(ne.quote()).append("\" 问题：").append(ne.issue()).append('\n');
        }
        return PRESCRIPTION_PROMPT
                .replace("{statsJson}", statsJson)
                .replace("{negEvidence}", neg.isEmpty() ? "无" : neg.toString())
                .replace("{assessment}", profile == null || profile.getAssessment() == null ? "暂无" : profile.getAssessment())
                .replace("{topWeaknesses}", profile == null || profile.getTopWeaknesses() == null
                        ? "暂无" : String.join("、", profile.getTopWeaknesses()))
                .replace("{focusSubSkills}", String.join("、", stats.focusSubSkills()));
    }

    private Diagnosis buildDiagnosis(PlanStatsService.PlanStats stats, String summary) {
        Map<String, Object> statsMap;
        try {
            statsMap = mapper.convertValue(stats, Map.class);
        } catch (Exception e) {
            statsMap = new LinkedHashMap<>();
        }
        List<EvidenceRef> refs = stats.negativeEvidenceTop().stream()
                .map(ne -> new EvidenceRef(ne.sessionId(), ne.subSkill()))
                .toList();
        return new Diagnosis(stats.focusSubSkills(), summary, statsMap, refs);
    }

    /**
     * 任务构建 + 结构约束校验（设计文档 §3.2/§3.3）：
     * 白名单/类型非法丢弃 → 约束过滤 → 组成兜底（≥1 sim + ≥1 real，总数 3~5）→ 重新编号。
     */
    List<Task> assembleTasks(PlanStatsService.PlanStats stats, List<LlmTask> llmTasks) {
        List<String> focus = stats.focusSubSkills();
        LocalDateTime dueAt = WeekKeyUtil.weekEnd(WeekKeyUtil.currentWeekKey()).atTime(LocalTime.of(23, 59, 59));
        Map<String, Integer> useCount = new HashMap<>();
        List<Task> out = new ArrayList<>();
        boolean hasKnowledge = false;

        for (LlmTask lt : llmTasks) {
            if (out.size() >= 5) {
                break;
            }
            String key = SubSkillConstants.resolveKey(lt.subSkill());   // 宽容：中文名→短名（R3 联调实测漂移）
            if (key == null || !TASK_TYPES.contains(lt.type())) {
                log.warn("处方任务被丢弃（非法 subSkill/type）: {} / {}", lt.subSkill(), lt.type());
                continue;
            }
            if (TrainingPlanDocument.TYPE_KNOWLEDGE.equals(lt.type())) {
                if (hasKnowledge) {
                    continue;
                }
                hasKnowledge = true;
            }
            if (TrainingPlanDocument.TYPE_SIMULATION.equals(lt.type()) && !focus.contains(key)) {
                log.warn("simulation 任务子技能不在主攻焦点，丢弃: {}", key);
                continue;
            }
            int use = useCount.merge(key, 1, Integer::sum);
            if (use > 2) {
                continue;
            }
            out.add(buildTask(lt.type(), key,
                    blankTo(lt.title(), null), blankTo(lt.instruction(), null), blankTo(lt.successCriteria(), null),
                    clampTarget(lt.type(), lt.target()), stats, dueAt));
        }

        // 组成兜底：≥1 simulation（主焦点）、≥1 realworld；总数补到 ≥3
        if (out.stream().noneMatch(t -> TrainingPlanDocument.TYPE_SIMULATION.equals(t.getType()))) {
            out.add(0, fallbackTask(TrainingPlanDocument.TYPE_SIMULATION, focus.get(0),
                    focus.size() > 1 ? 1 : 2, stats, dueAt));
        }
        if (out.stream().noneMatch(t -> TrainingPlanDocument.TYPE_REALWORLD.equals(t.getType()))) {
            String rk = focus.size() > 1 ? focus.get(1) : focus.get(0);
            out.add(fallbackTask(TrainingPlanDocument.TYPE_REALWORLD, rk, 1, stats, dueAt));
        }
        int guard = 0;
        while (out.size() < 3 && guard++ < 6) {
            String key = focus.get(out.size() % focus.size());
            out.add(fallbackTask(out.size() % 2 == 0
                    ? TrainingPlanDocument.TYPE_REALWORLD : TrainingPlanDocument.TYPE_SIMULATION, key, 1, stats, dueAt));
            // 保证 sim 焦点合法性：sim 的 key 必须在 focus 中（focus.get 天然满足）
        }
        if (out.size() > 5) {
            out = new ArrayList<>(out.subList(0, 5));
        }
        for (int i = 0; i < out.size(); i++) {
            out.get(i).setId("t" + (i + 1));
        }
        return out;
    }

    private Task buildTask(String type, String subSkill, String title, String instruction,
                           String successCriteria, int target, PlanStatsService.PlanStats stats, LocalDateTime dueAt) {
        SubSkillConstants.SubSkill s = SubSkillConstants.get(subSkill);
        Task t = new Task();
        t.setType(type);
        t.setSubSkill(subSkill);
        t.setTitle(title != null ? title : fallbackTitle(type, subSkill, target));
        t.setInstruction(instruction != null ? instruction : SubSkillConstants.defaultInstruction(subSkill));
        t.setSuccessCriteria(successCriteria != null ? successCriteria : fallbackCriteria(type, subSkill, target));
        t.setTarget(target);
        t.setProgress(0);
        t.setStatus(TrainingPlanDocument.TASK_PENDING);
        t.setDueAt(dueAt);
        if (TrainingPlanDocument.TYPE_SIMULATION.equals(type)) {
            t.setVerifyMode(TrainingPlanDocument.VERIFY_AUTO);
            t.setAutoRule(new AutoRule(AutoRule.KIND_SESSION_MIN, subSkill, SubSkillConstants.AUTO_MIN_SCORE, target));
        } else {
            t.setVerifyMode(TrainingPlanDocument.VERIFY_EVIDENCE);
        }
        // whyRefs：诊断负面证据中匹配该子技能者（无则空数组）
        List<EvidenceRef> why = stats.negativeEvidenceTop().stream()
                .filter(ne -> ne.subSkill().equals(subSkill))
                .map(ne -> new EvidenceRef(ne.sessionId(), ne.subSkill()))
                .toList();
        t.setWhyRefs(new ArrayList<>(why));
        return t;
    }

    private Task fallbackTask(String type, String subSkill, int target,
                              PlanStatsService.PlanStats stats, LocalDateTime dueAt) {
        return buildTask(type, subSkill, null, null, null, clampTarget(type, target), stats, dueAt);
    }

    private String fallbackTitle(String type, String subSkill, int target) {
        return switch (type) {
            case TrainingPlanDocument.TYPE_SIMULATION -> SubSkillConstants.defaultSimTaskTitle(subSkill, target);
            case TrainingPlanDocument.TYPE_KNOWLEDGE -> "学「" + SubSkillConstants.get(subSkill).cnName() + "」做法卡并复述要点";
            default -> SubSkillConstants.defaultRealTaskTitle(subSkill, target);
        };
    }

    private String fallbackCriteria(String type, String subSkill, int target) {
        String cn = SubSkillConstants.get(subSkill).cnName();
        return switch (type) {
            case TrainingPlanDocument.TYPE_SIMULATION ->
                    "AI 评分中「" + cn + "」≥" + SubSkillConstants.AUTO_MIN_SCORE + " 分的场次达 " + target + " 场";
            case TrainingPlanDocument.TYPE_KNOWLEDGE -> "复述做法要点并通过教练点评 1 次";
            default -> "提交 " + target + " 次真实场景打卡并通过点评";
        };
    }

    private String fallbackSummary(PlanStatsService.PlanStats stats) {
        StringBuilder sb = new StringBuilder("本周主攻");
        List<String> names = stats.focusSubSkills().stream()
                .map(k -> SubSkillConstants.get(k).cnName()).toList();
        sb.append(String.join("、", names)).append("：");
        for (PlanStatsService.Weakest w : stats.weakest()) {
            if (stats.focusSubSkills().contains(w.subSkill())) {
                sb.append(SubSkillConstants.get(w.subSkill()).cnName()).append("近").append(w.samples())
                        .append("场均").append(w.avg()).append("分，低于预期；");
            }
        }
        sb.append("完成下方训练任务后分数会反映在诊断页。");
        return sb.toString();
    }

    private static int clampTarget(String type, int target) {
        int max = TrainingPlanDocument.TYPE_SIMULATION.equals(type) ? 2 : 3;
        return Math.max(1, Math.min(max, target));
    }

    private static String blankTo(String v, String dft) {
        return v == null || v.isBlank() ? dft : v.trim();
    }

    // ════════════════════════ P2-4 周切换 + 自动回销 ════════════════════════

    /** 单用户模式默认 userId（与 user_profile/conversation 现状一致）。TODO[多用户] 透传。 */
    static final Long DEFAULT_USER_ID = 1L;

    /** 提交点评 Prompt 全文（施工单 P2-5 卡）。 */
    static final String SUBMIT_PROMPT = """
            你是社交训练教练，正在判定用户提交的真实任务打卡是否合格。

            ## 任务
            标题：{title}
            完成标准：{successCriteria}
            做法指导：{instruction}

            ## 用户提交的打卡内容
            {text}

            ## 判定要求
            1. 合格 = 内容包含具体事件要素（谁 / 什么情境 / 说了或做了什么 / 结果如何），且事件与任务要求的行为直接相关；
            2. 纯口号或无细节（"我完成了""今天练了"）→ 不合格；
            3. 事件与任务无关（如任务是"复述确认"却只写了闲聊内容）→ 不合格；
            4. 宁可宽松鼓励，但底线是"能看出真的做过这个动作"。
            返回 JSON：{"accepted": true或false, "aiFeedback": "≤50字，第二人称，合格时具体指出做得好的动作，不合格时给一个可操作的改进建议"}
            只返回 JSON。
            """;

    /** evidence 类任务提交次数上限（防无限重试刷点评）。 */
    static final int MAX_SUBMISSIONS = 3;

    /**
     * 懒周切换（幂等，设计文档 §5.1）：上周 active → 未完成任务 expired → [P3 周报 hook] → 归档 → 生成新周处方。
     * 由 /current、/recommendation 等入口调用；与评分事件解耦。
     */
    public void ensureWeekRollover(Long userId) {
        TrainingPlanDocument stale = null;
        if (genLock.tryLock()) {
            try {
                TrainingPlanDocument active = planRepo
                        .findFirstByUserIdAndStatusOrderByGeneratedAtDesc(userId, TrainingPlanDocument.STATUS_ACTIVE)
                        .orElse(null);
                if (active == null || active.getWeekKey() == null
                        || !WeekKeyUtil.isBefore(active.getWeekKey(), WeekKeyUtil.currentWeekKey())) {
                    return;
                }
                log.info("周切换: 归档 {}（weekKey={}）", active.getId(), active.getWeekKey());
                for (Task t : active.getTasks()) {
                    if (TrainingPlanDocument.TASK_PENDING.equals(t.getStatus())
                            || TrainingPlanDocument.TASK_IN_PROGRESS.equals(t.getStatus())) {
                        t.setStatus(TrainingPlanDocument.TASK_EXPIRED);
                    }
                }
                // P3-1：先产周报（内部幂等且绝不抛出失败，不阻断归档）
                generateWeeklyReport(active);
                active.setStatus(TrainingPlanDocument.STATUS_ARCHIVED);
                planRepo.save(active);
                stale = active;
            } catch (Exception e) {
                log.error("周切换失败", e);
                return;
            } finally {
                genLock.unlock();
            }
        }
        if (stale != null) {
            if (instantiateAdoptedDraft(userId, stale.getWeekKey())) {
                log.info("周切换: 已采纳上周报告的下周草稿，直接实例化（generatedBy=adopted）");
            } else {
                var res = generatePrescription(userId, "auto");
                log.info("周切换新处方: status={}", res.status());
            }
        }
    }

    /**
     * 自动回销（P2-4，设计文档 §3.5）：评分事件触发，纯规则、幂等（creditedSessionIds 去重）、绝不抛出。
     */
    public void onSessionScored(String sessionId) {
        try {
            ConversationDocument conv = conversationRepo.findById(sessionId).orElse(null);
            if (conv == null || conv.getEvaluation() == null) {
                return;
            }
            TrainingPlanDocument plan = planRepo
                    .findFirstByUserIdAndStatusOrderByGeneratedAtDesc(DEFAULT_USER_ID, TrainingPlanDocument.STATUS_ACTIVE)
                    .orElse(null);
            if (plan == null || plan.getTasks() == null) {
                return;
            }
            boolean changed = false;
            for (Task t : plan.getTasks()) {
                if (!TrainingPlanDocument.VERIFY_AUTO.equals(t.getVerifyMode())) {
                    continue;
                }
                if (!TrainingPlanDocument.TASK_PENDING.equals(t.getStatus())
                        && !TrainingPlanDocument.TASK_IN_PROGRESS.equals(t.getStatus())) {
                    continue;
                }
                if (t.getCreditedSessionIds() == null) {
                    t.setCreditedSessionIds(new ArrayList<>());
                }
                if (t.getCreditedSessionIds().contains(sessionId)) {
                    continue; // 幂等：本场已回销过
                }
                if (!matchesAutoRule(t, conv)) {
                    continue;
                }
                t.getCreditedSessionIds().add(sessionId);
                t.setProgress(t.getProgress() + 1);
                if (t.getProgress() >= t.getTarget()) {
                    t.setStatus(TrainingPlanDocument.TASK_DONE);
                    t.setDoneAt(LocalDateTime.now());
                } else {
                    t.setStatus(TrainingPlanDocument.TASK_IN_PROGRESS);
                }
                changed = true;
                log.info("任务自动回销: task={} subSkill={} progress={}/{}",
                        t.getId(), t.getSubSkill(), t.getProgress(), t.getTarget());
            }
            if (changed) {
                planRepo.save(plan);
            }
        } catch (Exception e) {
            log.error("自动回销失败: sessionId={}", sessionId, e);
        }
    }

    /** auto 规则判定（包级可见便于单测）。 */
    static boolean matchesAutoRule(Task t, ConversationDocument conv) {
        AutoRule r = t.getAutoRule();
        if (r == null || r.getKind() == null || r.getSubSkill() == null) {
            return false;
        }
        if (AutoRule.KIND_SESSION_MIN.equals(r.getKind())) {
            Map<String, Integer> subs = conv.getEvaluation().getSubScores();
            if (subs == null) {
                return false;
            }
            Integer v = subs.get(r.getSubSkill());
            int min = r.getMinScore() == null ? SubSkillConstants.AUTO_MIN_SCORE : r.getMinScore();
            return v != null && v >= min;
        }
        if (AutoRule.KIND_FOCUS_COUNT.equals(r.getKind())) {
            return r.getSubSkill().equals(conv.getFocusSubSkill());
        }
        return false;
    }

    // ════════════════════════ P2-5 提交点评 ════════════════════════

    /**
     * evidence 类任务提交打卡 + AI 点评（设计文档 §3.6）。
     *
     * @return 结果 Map{accepted, aiFeedback, progress, status, locked, remaining}；任务不存在 → null（controller 转 404）
     */
    public Map<String, Object> submitTask(String taskId, String text) {
        TrainingPlanDocument plan = planRepo
                .findFirstByUserIdAndStatusOrderByGeneratedAtDesc(DEFAULT_USER_ID, TrainingPlanDocument.STATUS_ACTIVE)
                .orElse(null);
        if (plan == null || plan.getTasks() == null) {
            return null;
        }
        Task task = plan.getTasks().stream()
                .filter(t -> t.getId() != null && t.getId().equals(taskId))
                .findFirst().orElse(null);
        if (task == null) {
            return null;
        }
        if (task.getSubmissions() == null) {
            task.setSubmissions(new ArrayList<>());
        }
        int used = task.getSubmissions().size();
        if (!TrainingPlanDocument.VERIFY_EVIDENCE.equals(task.getVerifyMode())) {
            return Map.of("accepted", false, "aiFeedback", "该任务由训练数据自动完成，无需打卡",
                    "locked", false, "progress", task.getProgress(), "status", task.getStatus(),
                    "remaining", MAX_SUBMISSIONS - used);
        }
        if (used >= MAX_SUBMISSIONS) {
            return Map.of("accepted", false, "aiFeedback", "本任务提交次数已用完，下周可再次获得同类任务",
                    "locked", true, "progress", task.getProgress(), "status", task.getStatus(), "remaining", 0);
        }
        String trimmed = text == null ? "" : text.trim();
        if (trimmed.length() < 10) {
            return Map.of("accepted", false,
                    "aiFeedback", "再多写一点具体发生了什么（谁、说了什么、结果如何），至少10个字",
                    "locked", false, "progress", task.getProgress(), "status", task.getStatus(),
                    "remaining", MAX_SUBMISSIONS - used);
        }

        boolean accepted;
        String feedback;
        try {
            String out = llm(SUBMIT_PROMPT
                    .replace("{title}", task.getTitle() == null ? "" : task.getTitle())
                    .replace("{successCriteria}", task.getSuccessCriteria() == null ? "" : task.getSuccessCriteria())
                    .replace("{instruction}", task.getInstruction() == null ? "" : task.getInstruction())
                    .replace("{text}", trimmed));
            JsonNode root = mapper.readTree(LlmJsonUtil.stripMarkdown(out));
            accepted = root.path("accepted").asBoolean(false);
            feedback = root.path("aiFeedback").asText("");
            if (feedback.isBlank()) {
                feedback = accepted ? "很好，这个动作练到位了！" : "写写具体发生了什么，再试一次";
            }
            if (feedback.length() > 60) {
                feedback = feedback.substring(0, 60);
            }
        } catch (Exception e) {
            log.warn("提交点评 LLM 失败（不计入提交次数）: {}", e.getMessage());
            return Map.of("accepted", false, "aiFeedback", "点评服务开小差了，稍后再试（未计入次数）",
                    "locked", false, "progress", task.getProgress(), "status", task.getStatus(),
                    "remaining", MAX_SUBMISSIONS - used);
        }

        task.getSubmissions().add(new TrainingPlanDocument.Submission(
                trimmed, feedback, accepted, LocalDateTime.now()));
        if (accepted) {
            task.setProgress(task.getProgress() + 1);
            if (task.getProgress() >= task.getTarget()) {
                task.setStatus(TrainingPlanDocument.TASK_DONE);
                task.setDoneAt(LocalDateTime.now());
            } else if (TrainingPlanDocument.TASK_PENDING.equals(task.getStatus())) {
                task.setStatus(TrainingPlanDocument.TASK_IN_PROGRESS);
            }
        }
        planRepo.save(plan);
        return Map.of("accepted", accepted, "aiFeedback", feedback,
                "locked", task.getSubmissions().size() >= MAX_SUBMISSIONS,
                "progress", task.getProgress(), "status", task.getStatus(),
                "remaining", MAX_SUBMISSIONS - task.getSubmissions().size());
    }

    // ════════════════════════ P3-1/P3-2 周报与草稿 ════════════════════════

    /** 周报 Prompt 全文（施工单 P3-1 卡）。 */
    static final String REPORT_PROMPT = """
            你是社交训练教练，为用户撰写本周成长周报。

            ## 本周数据（规则引擎统计，可信）
            任务完成：{tasksDone}/{tasksTotal}（过期 {tasksExpired}）
            评分训练场次：{sessionsScored}（其中靶向 {focusSessions} 场）
            真实任务提交：{submissions} 次（通过 {submissionsAccepted} 次）
            子技能变化（本周 vs 上周，正数为进步）：{deltas}
            本周高光时刻：{highlights}
            本周处方诊断：{diagnosisSummary}

            ## 输出要求
            严格返回 JSON：
            {"aiSummary":"≤120字。结构：先肯定一个具体进步（引用上面的数字），再指出一个卡点，最后给下周一个聚焦建议。第二人称，温暖但不空洞，不堆形容词",
             "nextWeekDraft":{"summary":"≤80字下周诊断","tasks":[{"type":"simulation|realworld|knowledge","subSkill":"子技能短名","title":"≤25字","instruction":"≤120字做法步骤","successCriteria":"≤30字","target":1~3}]}}
            tasks 3~5 个，至少 1 个 simulation、至少 1 个 realworld，knowledge 至多 1 个。
            只返回 JSON。
            """;

    /** 周报生成（幂等：同 weekKey 已存在直接返回；任何异常内部吞并返回 null，不阻断周切换）。 */
    public com.backend.document.WeeklyReportDocument generateWeeklyReport(TrainingPlanDocument plan) {
        if (plan == null || plan.getUserId() == null || plan.getWeekKey() == null) {
            return null;
        }
        var existed = weeklyReportRepo.findFirstByUserIdAndWeekKey(plan.getUserId(), plan.getWeekKey());
        if (existed.isPresent()) {
            return existed.get();
        }
        try {
            java.time.LocalDate ws = plan.getWeekStart() != null ? plan.getWeekStart() : WeekKeyUtil.weekStart(plan.getWeekKey());
            java.time.LocalDate we = plan.getWeekEnd() != null ? plan.getWeekEnd() : WeekKeyUtil.weekEnd(plan.getWeekKey());
            LocalDateTime wStart = ws.atStartOfDay();
            LocalDateTime wEnd = we.atTime(23, 59, 59);

            List<ConversationDocument> scored = loadScoredConversations(); // 时间倒序
            List<ConversationDocument> thisWeek = new ArrayList<>();
            List<ConversationDocument> before = new ArrayList<>();
            for (ConversationDocument d : scored) {
                LocalDateTime at = d.getUpdatedAt() != null ? d.getUpdatedAt() : d.getCreatedAt();
                if (at == null) {
                    continue;
                }
                if (!at.isBefore(wStart) && !at.isAfter(wEnd)) {
                    thisWeek.add(d);
                } else if (at.isBefore(wStart) && before.size() < SubSkillConstants.RECENT_SESSIONS) {
                    before.add(d);
                }
            }

            var completion = computeCompletion(plan, thisWeek);
            var deltas = computeDeltas(thisWeek, before);
            var highlights = pickHighlights(thisWeek);

            String aiSummary;
            com.backend.document.WeeklyReportDocument.NextWeekDraft draft = null;
            try {
                String out = llm(buildReportPrompt(plan, completion, deltas, highlights));
                JsonNode root = mapper.readTree(LlmJsonUtil.stripMarkdown(out));
                aiSummary = root.path("aiSummary").asText("");
                if (aiSummary.isBlank()) {
                    aiSummary = fallbackAiSummary(completion, deltas);
                }
                draft = parseDraft(root.path("nextWeekDraft"), scored);
            } catch (Exception e) {
                log.warn("周报 LLM 调用失败，aiSummary 走规则模板: {}", e.getMessage());
                aiSummary = fallbackAiSummary(completion, deltas);
            }

            var report = new com.backend.document.WeeklyReportDocument();
            report.setUserId(plan.getUserId());
            report.setWeekKey(plan.getWeekKey());
            report.setPlanId(plan.getId());
            report.setCompletion(completion);
            report.setDeltas(deltas);
            report.setHighlights(highlights);
            report.setAiSummary(aiSummary);
            report.setNextWeekDraft(draft);
            report.setAdopted(false);
            report.setCreatedAt(LocalDateTime.now());
            weeklyReportRepo.save(report);
            log.info("周报生成完成: weekKey={} 完成 {}/{}", plan.getWeekKey(),
                    completion.getTasksDone(), completion.getTasksTotal());
            return report;
        } catch (Exception e) {
            log.error("周报生成失败: weekKey={}", plan.getWeekKey(), e);
            return null;
        }
    }

    /** 完成度规则统计（包级可见便于单测）。 */
    static com.backend.document.WeeklyReportDocument.Completion computeCompletion(
            TrainingPlanDocument plan, List<ConversationDocument> thisWeek) {
        int total = plan.getTasks().size();
        int done = 0;
        int expired = 0;
        int subs = 0;
        int accepted = 0;
        for (Task t : plan.getTasks()) {
            if (TrainingPlanDocument.TASK_DONE.equals(t.getStatus())) {
                done++;
            } else if (TrainingPlanDocument.TASK_EXPIRED.equals(t.getStatus())) {
                expired++;
            }
            if (t.getSubmissions() != null) {
                subs += t.getSubmissions().size();
                accepted += (int) t.getSubmissions().stream().filter(TrainingPlanDocument.Submission::isAccepted).count();
            }
        }
        int focus = (int) thisWeek.stream().filter(d -> d.getFocusSubSkill() != null).count();
        return new com.backend.document.WeeklyReportDocument.Completion(
                total, done, expired, thisWeek.size(), focus, subs, accepted);
    }

    /** 子技能周变化：本周均值 vs 前窗口均值，仅两侧都有分的子技能（包级可见）。 */
    static Map<String, Integer> computeDeltas(List<ConversationDocument> thisWeek,
                                              List<ConversationDocument> beforeDesc) {
        Map<String, List<Integer>> cur = collectSubVals(thisWeek);
        Map<String, List<Integer>> prev = collectSubVals(beforeDesc);
        Map<String, Integer> out = new LinkedHashMap<>();
        for (Map.Entry<String, List<Integer>> e : cur.entrySet()) {
            List<Integer> p = prev.get(e.getKey());
            if (p == null || p.isEmpty() || e.getValue().isEmpty()) {
                continue;
            }
            int delta = (int) Math.round(e.getValue().stream().mapToInt(Integer::intValue).average().orElse(0)
                    - p.stream().mapToInt(Integer::intValue).average().orElse(0));
            out.put(e.getKey(), delta);
        }
        return out;
    }

    private static Map<String, List<Integer>> collectSubVals(List<ConversationDocument> docs) {
        Map<String, List<Integer>> map = new LinkedHashMap<>();
        for (ConversationDocument d : docs) {
            if (d.getEvaluation() == null || d.getEvaluation().getSubScores() == null) {
                continue;
            }
            d.getEvaluation().getSubScores().forEach((k, v) -> {
                if (v != null) {
                    map.computeIfAbsent(k, x -> new ArrayList<>()).add(v);
                }
            });
        }
        return map;
    }

    /** 本周正向证据高光 ≤2（按所属子技能周均值降序、子技能去重；包级可见）。 */
    static List<com.backend.document.WeeklyReportDocument.Highlight> pickHighlights(
            List<ConversationDocument> thisWeek) {
        Map<String, Double> avg = new HashMap<>();
        collectSubVals(thisWeek).forEach((k, vals) ->
                avg.put(k, vals.stream().mapToInt(Integer::intValue).average().orElse(0)));
        List<com.backend.document.WeeklyReportDocument.Highlight> pool = new ArrayList<>();
        for (ConversationDocument d : thisWeek) {
            if (d.getEvaluation() == null || d.getEvaluation().getEvidence() == null) {
                continue;
            }
            for (ConversationDocument.EvidenceItem ev : d.getEvaluation().getEvidence()) {
                if (!"positive".equals(ev.getPolarity()) || !SubSkillConstants.isValid(ev.getSubSkill())) {
                    continue;
                }
                pool.add(new com.backend.document.WeeklyReportDocument.Highlight(
                        d.getId(), ev.getSubSkill(), ev.getQuote()));
            }
        }
        pool.sort((x, y) -> Double.compare(
                avg.getOrDefault(y.getSubSkill(), 0.0), avg.getOrDefault(x.getSubSkill(), 0.0)));
        List<com.backend.document.WeeklyReportDocument.Highlight> out = new ArrayList<>();
        Set<String> seen = new java.util.HashSet<>();
        for (var h : pool) {
            if (out.size() >= 2) {
                break;
            }
            if (seen.add(h.getSubSkill())) {
                out.add(h);
            }
        }
        return out;
    }

    private String buildReportPrompt(TrainingPlanDocument plan,
                                     com.backend.document.WeeklyReportDocument.Completion c,
                                     Map<String, Integer> deltas,
                                     List<com.backend.document.WeeklyReportDocument.Highlight> highlights) {
        StringBuilder dl = new StringBuilder();
        deltas.forEach((k, v) -> {
            if (Math.abs(v) >= 3) {
                dl.append(SubSkillConstants.get(k).cnName()).append(v >= 0 ? "+" : "").append(v).append('、');
            }
        });
        StringBuilder hl = new StringBuilder();
        for (var h : highlights) {
            hl.append('[').append(SubSkillConstants.get(h.getSubSkill()).cnName()).append("] \"")
                    .append(h.getQuote()).append("\" ");
        }
        return REPORT_PROMPT
                .replace("{tasksDone}", String.valueOf(c.getTasksDone()))
                .replace("{tasksTotal}", String.valueOf(c.getTasksTotal()))
                .replace("{tasksExpired}", String.valueOf(c.getTasksExpired()))
                .replace("{sessionsScored}", String.valueOf(c.getSessionsScored()))
                .replace("{focusSessions}", String.valueOf(c.getFocusSessions()))
                .replace("{submissions}", String.valueOf(c.getSubmissions()))
                .replace("{submissionsAccepted}", String.valueOf(c.getSubmissionsAccepted()))
                .replace("{deltas}", dl.length() > 0 ? dl.substring(0, dl.length() - 1) : "基本持平")
                .replace("{highlights}", hl.length() > 0 ? hl.toString() : "无")
                .replace("{diagnosisSummary}", plan.getDiagnosis() == null || plan.getDiagnosis().getSummary() == null
                        ? "无" : plan.getDiagnosis().getSummary());
    }

    private String fallbackAiSummary(com.backend.document.WeeklyReportDocument.Completion c,
                                     Map<String, Integer> deltas) {
        String up = deltas.entrySet().stream()
                .filter(e -> e.getValue() > 0)
                .max(Map.Entry.comparingByValue())
                .map(e -> SubSkillConstants.get(e.getKey()).cnName() + "提升" + e.getValue() + "分")
                .orElse("各项保持稳定");
        return "本周完成 " + c.getTasksDone() + "/" + c.getTasksTotal() + " 个任务，训练 " + c.getSessionsScored()
                + " 场。" + up + "。下周保持训练节奏，把打卡复盘写具体。";
    }

    /** LLM 草稿任务复用处方校验器（结构约束一致）。 */
    private com.backend.document.WeeklyReportDocument.NextWeekDraft parseDraft(
            JsonNode node, List<ConversationDocument> scored) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        try {
            PlanStatsService.PlanStats stats = planStatsService.compute(scored);
            if (stats.insufficient()) {
                return null;
            }
            List<LlmTask> llmTasks = new ArrayList<>();
            node.path("tasks").forEach(t -> llmTasks.add(LlmTask.from(t)));
            List<Task> tasks = assembleTasks(stats, llmTasks);
            if (tasks.size() < 3) {
                return null;
            }
            return new com.backend.document.WeeklyReportDocument.NextWeekDraft(
                    node.path("summary").asText(""), tasks);
        } catch (Exception e) {
            log.warn("下周草稿解析失败: {}", e.getMessage());
            return null;
        }
    }

    /** 采纳下周草稿（勘误 #4：冻结快照，防草稿后续被刷新影响）。 */
    public boolean adoptDraft(Long userId, String weekKey) {
        try {
            var rep = weeklyReportRepo.findFirstByUserIdAndWeekKey(userId, weekKey).orElse(null);
            if (rep == null || rep.getNextWeekDraft() == null) {
                return false;
            }
            rep.setAdopted(true);
            rep.setAdoptedDraft(mapper.convertValue(rep.getNextWeekDraft(),
                    com.backend.document.WeeklyReportDocument.NextWeekDraft.class));
            weeklyReportRepo.save(rep);
            return true;
        } catch (Exception e) {
            log.error("采纳草稿失败: weekKey={}", weekKey, e);
            return false;
        }
    }

    /** 重新生成下周草稿（复用处方 prompt + 最新 stats）；成功后 adopted 清除。 */
    public PrescriptionResult refreshDraft(Long userId, String weekKey) {
        if (!genLock.tryLock()) {
            return new PrescriptionResult("generating", null);
        }
        try {
            var rep = weeklyReportRepo.findFirstByUserIdAndWeekKey(userId, weekKey).orElse(null);
            if (rep == null) {
                return new PrescriptionResult("error", null);
            }
            List<ConversationDocument> scored = loadScoredConversations();
            long withSub = scored.stream()
                    .filter(d -> d.getEvaluation().getSubScores() != null && !d.getEvaluation().getSubScores().isEmpty())
                    .count();
            if (withSub < 3) {
                return new PrescriptionResult("insufficient_data", null);
            }
            PlanStatsService.PlanStats stats = planStatsService.compute(scored);
            if (stats.insufficient()) {
                return new PrescriptionResult("insufficient_data", null);
            }
            UserProfileDocument profile = profileRepo.findDefault().orElse(null);
            try {
                String out = llm(buildPrompt(stats, profile));
                JsonNode root = mapper.readTree(LlmJsonUtil.stripMarkdown(out));
                List<LlmTask> llmTasks = new ArrayList<>();
                root.path("tasks").forEach(t -> llmTasks.add(LlmTask.from(t)));
                List<Task> tasks = assembleTasks(stats, llmTasks);
                if (tasks.size() < 3) {
                    return new PrescriptionResult("error", null);
                }
                rep.setNextWeekDraft(new com.backend.document.WeeklyReportDocument.NextWeekDraft(
                        root.path("summary").asText(""), tasks));
                rep.setAdopted(false);
                rep.setAdoptedDraft(null);
                weeklyReportRepo.save(rep);
                return new PrescriptionResult("ok", null);
            } catch (Exception e) {
                log.warn("草稿再生成 LLM 失败: {}", e.getMessage());
                return new PrescriptionResult("error", null);
            }
        } finally {
            genLock.unlock();
        }
    }

    /** 采纳态周切换：用上周报告的 adoptedDraft 直接实例化新处方（返回是否成功）。 */
    private boolean instantiateAdoptedDraft(Long userId, String archivedWeekKey) {
        try {
            var rep = weeklyReportRepo.findFirstByUserIdAndWeekKey(userId, archivedWeekKey).orElse(null);
            if (rep == null || !Boolean.TRUE.equals(rep.getAdopted()) || rep.getAdoptedDraft() == null
                    || rep.getAdoptedDraft().getTasks() == null || rep.getAdoptedDraft().getTasks().size() < 3) {
                return false;
            }
            planRepo.findByUserIdOrderByWeekStartDesc(userId).stream()
                    .filter(p -> TrainingPlanDocument.STATUS_ACTIVE.equals(p.getStatus()))
                    .forEach(old -> {
                        old.setStatus(TrainingPlanDocument.STATUS_ARCHIVED);
                        planRepo.save(old);
                    });
            String weekKey = WeekKeyUtil.currentWeekKey();
            List<String> focus = rep.getAdoptedDraft().getTasks().stream()
                    .map(Task::getSubSkill).distinct().limit(2).toList();
            TrainingPlanDocument plan = new TrainingPlanDocument();
            plan.setUserId(userId);
            plan.setWeekKey(weekKey);
            plan.setWeekStart(WeekKeyUtil.weekStart(weekKey));
            plan.setWeekEnd(WeekKeyUtil.weekEnd(weekKey));
            plan.setStatus(TrainingPlanDocument.STATUS_ACTIVE);
            plan.setGeneratedBy("adopted");
            plan.setDiagnosis(new Diagnosis(focus, rep.getAdoptedDraft().getSummary(), new LinkedHashMap<>(), List.of()));
            plan.setTasks(rep.getAdoptedDraft().getTasks());
            plan.setGeneratedAt(LocalDateTime.now());
            planRepo.save(plan);
            return true;
        } catch (Exception e) {
            log.error("adoptedDraft 实例化失败，回退自动生成", e);
            return false;
        }
    }

    /** 最近一份周报（/current 的 lastReport）。 */
    public com.backend.document.WeeklyReportDocument lastReport(Long userId) {
        var list = weeklyReportRepo.findByUserIdOrderByWeekKeyDesc(userId);
        return list == null || list.isEmpty() ? null : list.get(0);
    }

    /** 周报历史（时间倒序 limit）。 */
    public List<com.backend.document.WeeklyReportDocument> reports(Long userId, int limit) {
        var list = weeklyReportRepo.findByUserIdOrderByWeekKeyDesc(userId);
        return list == null ? List.of() : list.stream().limit(Math.max(1, Math.min(limit, 50))).toList();
    }

    /** 子技能趋势序列（P3-4，勘误 #3）：近 limit 场含该子技能分（时间正序）。 */
    public List<Map<String, Object>> subskillTrend(String subSkill, int limit) {
        if (!SubSkillConstants.isValid(subSkill)) {
            return List.of();
        }
        int cap = Math.max(1, Math.min(limit, 50));
        List<Map<String, Object>> out = new ArrayList<>();
        for (ConversationDocument d : loadScoredConversations()) { // 倒序
            if (out.size() >= cap) {
                break;
            }
            if (d.getEvaluation().getSubScores() == null) {
                continue;
            }
            Integer v = d.getEvaluation().getSubScores().get(subSkill);
            if (v == null) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("sessionId", d.getId());
            row.put("theme", d.getTheme());
            row.put("score", v);
            row.put("scoredAt", d.getUpdatedAt() != null ? d.getUpdatedAt() : d.getCreatedAt());
            out.add(row);
        }
        java.util.Collections.reverse(out); // 正序（旧→新）
        return out;
    }

    // ── P2-6 查询辅助 ──

    public Optional<TrainingPlanDocument> activePlan(Long userId) {
        return planRepo.findFirstByUserIdAndStatusOrderByGeneratedAtDesc(userId, TrainingPlanDocument.STATUS_ACTIVE);
    }

    /**
     * 今日推荐训练（设计文档 §4.2）：主攻焦点 → SubSkillConstants 映射表；
     * taskId = 该计划中第一个未完成/未过期的同焦点 simulation 任务（无则 null=自由靶向）。
     *
     * @param subSkillOverride 可选：任务卡"去训练"按指定子技能取映射行（映射表唯一源仍在后端）
     */
    public Map<String, Object> recommendation(Long userId) {
        return recommendation(userId, null);
    }

    public Map<String, Object> recommendation(Long userId, String subSkillOverride) {
        TrainingPlanDocument plan = activePlan(userId).orElse(null);
        if (plan == null || plan.getDiagnosis() == null
                || plan.getDiagnosis().getFocusSubSkills() == null
                || plan.getDiagnosis().getFocusSubSkills().isEmpty()) {
            return null;
        }
        String focus = plan.getDiagnosis().getFocusSubSkills().get(0);
        String key = subSkillOverride != null && SubSkillConstants.isValid(subSkillOverride)
                ? subSkillOverride : focus;
        SubSkillConstants.Recommendation r = SubSkillConstants.recommendationFor(key);
        if (r == null) {
            return null;
        }
        String taskId = plan.getTasks() == null ? null : plan.getTasks().stream()
                .filter(t -> TrainingPlanDocument.TYPE_SIMULATION.equals(t.getType())
                        && key.equals(t.getSubSkill())
                        && !TrainingPlanDocument.TASK_DONE.equals(t.getStatus())
                        && !TrainingPlanDocument.TASK_EXPIRED.equals(t.getStatus()))
                .map(Task::getId).findFirst().orElse(null);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("subSkill", r.subSkill());
        out.put("cnName", r.cnName());
        out.put("theme", r.theme());
        out.put("personality", r.personality());
        out.put("feed", r.feed());
        out.put("taskId", taskId);
        out.put("summary", plan.getDiagnosis().getSummary());
        return out;
    }

    /** 成长/诊断段统计（读 user_profile 聚合字段）。 */
    public Map<String, Object> currentStats() {
        UserProfileDocument p = profileRepo.findDefault().orElse(null);
        Map<String, Object> m = new LinkedHashMap<>();
        if (p == null) {
            return m;
        }
        m.put("subSkillAverages", p.getSubSkillAverages());
        m.put("subSkillSampleCount", p.getSubSkillSampleCount());
        m.put("badges", p.getBadges());
        m.put("streakDays", p.getStreakDays());
        m.put("bestStreak", p.getBestStreak());
        m.put("totalSessions", p.getTotalSessions());
        m.put("overallScore", p.getOverallScore());
        return m;
    }
}
