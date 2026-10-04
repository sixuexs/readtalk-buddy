package com.backend.controller;

import com.backend.constant.SubSkillConstants;
import com.backend.document.ConversationDocument;
import com.backend.document.UserProfileDocument;
import com.backend.model.ApiResponse;
import com.backend.repository.ConversationRepository;
import com.backend.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
@Slf4j
public class UserProfileController {

    private final UserProfileRepository userProfileRepository;
    private final ConversationRepository conversationRepository;

    /** 获取用户档案（含名片字段）。userId 默认 1；查无时回退单用户 default 文档。 */
    @GetMapping("/profile")
    public ApiResponse<?> getProfile(@RequestParam(value = "userId", required = false) Long userId) {
        Long uid = userId == null ? 1L : userId;
        UserProfileDocument doc = userProfileRepository.findByUserId(uid)
                .or(() -> userProfileRepository.findDefault())
                .orElse(null);
        if (doc == null) {
            return new ApiResponse<>(404, Map.of("message", "profile not found"));
        }
        return ApiResponse.ok(doc);
    }

    /** 更新名片字段。文档不存在时自动创建（单用户 id="default"），确保保存不丢失。 */
    @PutMapping("/profile")
    public ApiResponse<?> updateProfile(@RequestBody Map<String, Object> body) {
        Long userId = body.get("userId") == null ? 1L : Long.valueOf(String.valueOf(body.get("userId")));
        UserProfileDocument doc = userProfileRepository.findByUserId(userId)
                .or(() -> userProfileRepository.findDefault())
                .orElseGet(UserProfileDocument::new);
        if (doc.getId() == null || doc.getId().isBlank()) {
            doc.setId("default");
        }
        if (doc.getUserId() == null) {
            doc.setUserId(userId);
        }
        if (body.get("displayName") != null) {
            doc.setDisplayName(String.valueOf(body.get("displayName")));
        }
        if (body.get("biography") != null) {
            doc.setBiography(String.valueOf(body.get("biography")));
        }
        if (body.get("status") != null) {
            doc.setStatus(String.valueOf(body.get("status")));
        }
        if (body.get("avatar") != null) {
            doc.setAvatar(String.valueOf(body.get("avatar")));
        }
        if (body.get("personality") != null) {
            doc.setPersonality(String.valueOf(body.get("personality")));
        }
        if (body.get("interests") != null) {
            doc.setInterests(toStringList(body.get("interests")));
        }
        if (body.get("labels") != null) {
            doc.setLabels(toStringList(body.get("labels")));
        }
        doc.setLastUpdated(LocalDateTime.now());
        userProfileRepository.save(doc);
        return ApiResponse.ok(doc);
    }

    /** 更新周目标打卡状态（status 与 weeklyGoals 下标对齐）。P0-3：新勾选必须附 ≥5 字复盘附言。 */
    @PutMapping("/profile/weekly-goals")
    public ApiResponse<?> updateWeeklyGoals(@RequestBody Map<String, Object> body) {
        Long userId = body.get("userId") == null ? 1L : Long.valueOf(String.valueOf(body.get("userId")));
        UserProfileDocument doc = userProfileRepository.findByUserId(userId)
                .or(() -> userProfileRepository.findDefault())
                .orElse(null);
        if (doc == null) {
            return new ApiResponse<>(404, Map.of("message", "profile not found"));
        }
        if (!(body.get("status") instanceof List<?> list)) {
            return new ApiResponse<>(400, Map.of("message", "status must be a list"));
        }
        List<Boolean> oldStatus = doc.getWeeklyGoalsStatus() == null
                ? new java.util.ArrayList<>() : doc.getWeeklyGoalsStatus();
        List<Boolean> newStatus = list.stream()
                .map(v -> Boolean.parseBoolean(String.valueOf(v)))
                .toList();

        // 本次提交的附言：submissions: [{index:int, text:string}]（非法条目静默跳过）
        java.util.Map<Integer, String> provided = new java.util.HashMap<>();
        if (body.get("submissions") instanceof List<?> subList) {
            for (Object o : subList) {
                if (o instanceof Map<?, ?> m) {
                    int idx;
                    try {
                        idx = Integer.parseInt(String.valueOf(m.get("index")));
                    } catch (NumberFormatException e) {
                        continue;
                    }
                    String text = m.get("text") == null ? "" : String.valueOf(m.get("text")).trim();
                    if (!text.isEmpty()) {
                        provided.put(idx, text);
                    }
                }
            }
        }
        // 历史附言按 index 索引
        java.util.Map<Integer, UserProfileDocument.GoalSubmission> existing = new java.util.LinkedHashMap<>();
        if (doc.getWeeklyGoalSubmissions() != null) {
            for (UserProfileDocument.GoalSubmission gs : doc.getWeeklyGoalSubmissions()) {
                existing.put(gs.getIndex(), gs);
            }
        }
        // 合并规则：新勾选必须有附言；取消勾选即移除附言；维持勾选保留历史附言（或被本次新附言覆盖）
        List<UserProfileDocument.GoalSubmission> merged = new java.util.ArrayList<>();
        for (int i = 0; i < newStatus.size(); i++) {
            boolean now = Boolean.TRUE.equals(newStatus.get(i));
            boolean was = i < oldStatus.size() && Boolean.TRUE.equals(oldStatus.get(i));
            if (!now) {
                continue;
            }
            String text = provided.get(i);
            if (text != null) {
                if (text.length() < 5) {
                    return new ApiResponse<>(400, Map.of("message", "打卡需附带一句复盘（不少于5字）"));
                }
                merged.add(new UserProfileDocument.GoalSubmission(i, text, LocalDateTime.now()));
            } else if (existing.containsKey(i)) {
                merged.add(existing.get(i));
            } else if (!was) {
                return new ApiResponse<>(400, Map.of("message", "打卡需附带一句复盘（不少于5字）"));
            }
            // was==true 且无新附言无历史附言：P0-3 上线前的旧勾选位，放行不追溯
        }
        doc.setWeeklyGoalsStatus(newStatus);
        doc.setWeeklyGoalSubmissions(merged);
        doc.setLastUpdated(LocalDateTime.now());
        userProfileRepository.save(doc);
        return ApiResponse.ok(Map.of("saved", true,
                "weeklyGoalsStatus", doc.getWeeklyGoalsStatus(),
                "weeklyGoalSubmissions", doc.getWeeklyGoalSubmissions()));
    }

    /** P1-5（勘误 #1）：按子技能查历史证据（新→旧），供诊断段下钻。subSkill 非法 → 400。 */
    @GetMapping("/evidence")
    public ApiResponse<?> getEvidence(@RequestParam String subSkill,
                                      @RequestParam(defaultValue = "20") int limit) {
        if (!SubSkillConstants.isValid(subSkill)) {
            return new ApiResponse<>(400, Map.of("message", "unknown subSkill: " + subSkill));
        }
        int cap = Math.max(1, Math.min(limit, 100));
        List<Map<String, Object>> items = new ArrayList<>();
        for (ConversationDocument conv : conversationRepository.findAllByOrderByCreatedAtDesc()) {
            if (items.size() >= cap) {
                break;
            }
            ConversationDocument.Evaluation eval = conv.getEvaluation();
            if (eval == null || eval.getEvidence() == null) {
                continue;
            }
            for (ConversationDocument.EvidenceItem ei : eval.getEvidence()) {
                if (!subSkill.equals(ei.getSubSkill())) {
                    continue;
                }
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("subSkill", ei.getSubSkill());
                item.put("messageOrder", ei.getMessageOrder());
                item.put("quote", ei.getQuote());
                item.put("issue", ei.getIssue());
                item.put("rewrite", ei.getRewrite());
                item.put("polarity", ei.getPolarity());
                item.put("sessionId", conv.getId());
                item.put("theme", conv.getTheme());
                item.put("scoredAt", conv.getUpdatedAt());
                items.add(item);
                if (items.size() >= cap) {
                    break;
                }
            }
        }
        return ApiResponse.ok(Map.of("items", items));
    }

    /** 将 List<?> 安全转换为 List<String>。 */
    private List<String> toStringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return new java.util.ArrayList<>();
        }
        return list.stream().map(String::valueOf).toList();
    }
}
