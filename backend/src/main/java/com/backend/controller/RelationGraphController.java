package com.backend.controller;

import com.backend.model.ApiResponse;
import com.backend.service.RelationGraphService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 关系图谱页 REST 端点（前端 pages/relation/graph.vue 数据源）
 */
@RestController
@RequestMapping("/api/relation")
public class RelationGraphController {

    private final RelationGraphService service;

    public RelationGraphController(RelationGraphService service) {
        this.service = service;
    }

    // GET /api/relation/graph — 图谱数据（contacts + warnings）
    @GetMapping("/graph")
    public ApiResponse<?> graph() {
        return ApiResponse.ok(service.getGraph());
    }

    // GET /api/relation/contacts/{id}/advice?mode=light|full — 个性化建议
    @GetMapping("/contacts/{id}/advice")
    public ApiResponse<?> advice(@PathVariable String id,
                                 @RequestParam(defaultValue = "light") String mode) {
        return ApiResponse.ok(service.getAdvice(id, mode));
    }

    // POST /api/relation/contacts/{id}/dismiss — 暂不提醒（7 天冷却）
    @PostMapping("/contacts/{id}/dismiss")
    public ApiResponse<?> dismiss(@PathVariable String id) {
        service.dismissWarning(id);
        return ApiResponse.ok(Map.of("dismissed", true));
    }

    // POST /api/relation/contacts/{id}/resume — 继续提醒（取消冷却）
    @PostMapping("/contacts/{id}/resume")
    public ApiResponse<?> resume(@PathVariable String id) {
        service.resumeWarning(id);
        return ApiResponse.ok(Map.of("resumed", true));
    }
}
