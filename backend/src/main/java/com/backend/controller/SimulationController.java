package com.backend.controller;

import com.backend.model.ApiResponse;
import com.backend.model.SendRequest;
import com.backend.model.StartRequest;
import com.backend.service.SimulationService;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/simulation")
public class SimulationController {

    private final SimulationService service;
    private final ReactAgent simulationAgent;

    public SimulationController(SimulationService service, ReactAgent simulationAgent) {
        this.service = service;
        this.simulationAgent = simulationAgent;
    }

    // GET /api/simulation/config
    @GetMapping("/config")
    public ApiResponse<?> getConfig() {
        return ApiResponse.ok(service.getConfig());
    }

    // GET /api/simulation/scenario?scenarioId=xxx
    @GetMapping("/scenario")
    public ApiResponse<?> getScenario(@RequestParam String scenarioId) {
        return ApiResponse.ok(service.getScenario(scenarioId));
    }

    // POST /api/simulation/start
    @PostMapping("/start")
    public ApiResponse<?> start(@RequestBody StartRequest req) {
        return ApiResponse.ok(service.startSimulation(req));
    }

    // POST /api/simulation/send
    @PostMapping("/send")
    public ApiResponse<?> send(@RequestBody SendRequest req) {
        return ApiResponse.ok(service.sendMessage(req));
    }

    // GET /api/simulation/history?sessionId=xxx
    @GetMapping("/history")
    public ApiResponse<?> history(@RequestParam String sessionId) {
        return ApiResponse.ok(service.getHistory(sessionId));
    }

    // GET /api/simulation/sessions — 获取所有会话摘要列表
    @GetMapping("/sessions")
    public ApiResponse<?> sessions() {
        return ApiResponse.ok(service.getSessionList());
    }

    // POST /api/simulation/score?sessionId=xxx — AI 评分
    @PostMapping("/score")
    public ApiResponse<?> score(@RequestParam String sessionId) {
        return ApiResponse.ok(service.scoreConversation(sessionId));
    }

    // POST /api/simulation/agent — 通过 ReactAgent 处理请求（演示多智能体架构）
    @PostMapping("/agent")
    public ApiResponse<?> agent(@RequestBody Map<String, Object> input) {
        try {
            var response = simulationAgent.call(input);
            return ApiResponse.ok(Map.of("content", response.getText()));
        } catch (Exception e) {
            return ApiResponse.ok(Map.of("error", e.getMessage()));
        }
    }
}
