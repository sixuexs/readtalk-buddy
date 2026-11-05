package com.backend.controller;

import com.backend.document.TrainingPlanDocument;
import com.backend.model.ApiResponse;
import com.backend.service.PlanService;
import com.backend.service.PlanService.PrescriptionResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 训练处方端点（P2-6，设计文档 §7 / 施工单勘误 #7）。
 * <p>reports / adopt / draft-refresh 三端点在 P3-1/P3-2 补。单用户模式 userId 固定 1。
 */
@RestController
@RequestMapping("/api/plan")
@RequiredArgsConstructor
@Slf4j
public class PlanController {

    private static final Long USER_ID = 1L;

    private final PlanService planService;

    /** 首屏聚合：先懒周切换；无 active 处方且样本足够时同步生成（单飞锁防并发）。 */
    @GetMapping("/current")
    public ApiResponse<?> current() {
        planService.ensureWeekRollover(USER_ID);
        Map<String, Object> data = new LinkedHashMap<>();
        boolean generating = false;
        boolean insufficientData = false;
        Optional<TrainingPlanDocument> planOpt = planService.activePlan(USER_ID);
        if (planOpt.isEmpty()) {
            PrescriptionResult res = planService.generatePrescription(USER_ID, "auto");
            switch (res.status()) {
                case "ok" -> planOpt = Optional.ofNullable(res.plan());
                case "generating" -> generating = true;
                case "insufficient_data" -> insufficientData = true;
                default -> log.warn("处方生成异常状态: {}", res.status());
            }
        }
        data.put("generating", generating);
        data.put("insufficientData", insufficientData);
        data.put("plan", planOpt.orElse(null));
        data.put("lastReport", planService.lastReport(USER_ID)); // P3-1
        data.put("recommendation", planService.recommendation(USER_ID));
        data.put("stats", planService.currentStats());
        return ApiResponse.ok(data);
    }

    /** 手动重新生成处方（旧计划归档；前端弹窗已明示进度将归档）。 */
    @PostMapping("/regenerate")
    public ApiResponse<?> regenerate() {
        PrescriptionResult res = planService.generatePrescription(USER_ID, "manual");
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", res.status());
        data.put("plan", res.plan());
        return ApiResponse.ok(data);
    }

    /** evidence 类任务提交打卡 + AI 点评。任务不存在 → 404。 */
    @PostMapping("/tasks/{taskId}/submit")
    public ApiResponse<?> submit(@PathVariable String taskId, @RequestBody Map<String, Object> body) {
        String text = body.get("text") == null ? null : String.valueOf(body.get("text"));
        Map<String, Object> result = planService.submitTask(taskId, text);
        if (result == null) {
            return new ApiResponse<>(404, Map.of("message", "active plan or task not found"));
        }
        return ApiResponse.ok(result);
    }

    /** 今日推荐训练单点查询（前端直启模拟用）。subSkill 可选：任务卡按自身焦点取映射行。 */
    @GetMapping("/recommendation")
    public ApiResponse<?> recommendation(@RequestParam(required = false) String subSkill) {
        planService.ensureWeekRollover(USER_ID);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("recommendation", planService.recommendation(USER_ID, subSkill));
        return ApiResponse.ok(data);
    }

    // ──── P3-1 / P3-2 周报 ────

    /** 周报历史（时间倒序，limit ≤50）。 */
    @GetMapping("/reports")
    public ApiResponse<?> reports(@RequestParam(defaultValue = "8") int limit) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("items", planService.reports(USER_ID, limit));
        return ApiResponse.ok(data);
    }

    /** 采纳下周草稿（冻结快照；周切换据此实例化）。 */
    @PostMapping("/reports/{weekKey}/adopt")
    public ApiResponse<?> adopt(@PathVariable String weekKey) {
        boolean ok = planService.adoptDraft(USER_ID, weekKey);
        if (!ok) {
            return new ApiResponse<>(404, Map.of("message", "report or nextWeekDraft not found"));
        }
        return ApiResponse.ok(Map.of("adopted", true));
    }

    /** 重新生成下周草稿（复用最新统计；采纳态被清除）。 */
    @PostMapping("/reports/{weekKey}/draft-refresh")
    public ApiResponse<?> draftRefresh(@PathVariable String weekKey) {
        var res = planService.refreshDraft(USER_ID, weekKey);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", res.status());
        return ApiResponse.ok(data);
    }

    /** 子技能趋势序列（成长段折线；subSkill 非法 → 400）。 */
    @GetMapping("/subskill-trend")
    public ApiResponse<?> subskillTrend(@RequestParam String subSkill,
                                        @RequestParam(defaultValue = "10") int limit) {
        Map<String, Object> data = new LinkedHashMap<>();
        if (!com.backend.constant.SubSkillConstants.isValid(subSkill)) {
            return new ApiResponse<>(400, Map.of("message", "unknown subSkill: " + subSkill));
        }
        data.put("subSkill", subSkill);
        data.put("points", planService.subskillTrend(subSkill, limit));
        return ApiResponse.ok(data);
    }
}
