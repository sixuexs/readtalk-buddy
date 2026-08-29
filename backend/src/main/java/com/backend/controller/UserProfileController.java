package com.backend.controller;

import com.backend.document.UserProfileDocument;
import com.backend.model.ApiResponse;
import com.backend.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
@Slf4j
public class UserProfileController {

    private final UserProfileRepository userProfileRepository;

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

    /** 将 List<?> 安全转换为 List<String>。 */
    private List<String> toStringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return new java.util.ArrayList<>();
        }
        return list.stream().map(String::valueOf).toList();
    }
}
