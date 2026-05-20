package com.backend.controller;

import com.backend.model.ApiResponse;
import com.backend.model.SendRequest;
import com.backend.model.StartRequest;
import com.backend.service.SimulationService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/simulation")
public class SimulationController {

    private final SimulationService service;

    public SimulationController(SimulationService service) {
        this.service = service;
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
}
