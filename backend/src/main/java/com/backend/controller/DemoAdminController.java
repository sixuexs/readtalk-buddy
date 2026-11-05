package com.backend.controller;

import com.backend.model.ApiResponse;
import com.backend.seed.DemoShowcaseSeed;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 演示数据管理端点（仅 @Profile("demo") 存在，默认不注册）。
 *
 * 答辩/演示现场一键重置到演示态，两种触发方式：
 *   浏览器地址栏直接访问 http://localhost:8080/api/demo/reset（GET）
 *   或命令行 curl -X POST http://localhost:8080/api/demo/reset
 */
@RestController
@RequestMapping("/api/demo")
@Profile("demo")
@RequiredArgsConstructor
public class DemoAdminController {

    private final DemoShowcaseSeed demoSeed;

    @PostMapping("/reset")
    public ApiResponse<?> reset() {
        demoSeed.rebuild();
        return ApiResponse.ok(Map.of("reset", true));
    }

    /** 浏览器地址栏友好：GET 同样触发重置（仅 demo profile 暴露此端点）。 */
    @GetMapping("/reset")
    public ApiResponse<?> resetViaGet() {
        demoSeed.rebuild();
        return ApiResponse.ok(Map.of("reset", true, "message", "演示数据已重建，可关闭此页"));
    }
}
