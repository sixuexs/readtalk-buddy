package com.backend.controller;

import com.backend.agent.AgentRegistry;
import com.backend.agent.CommAssistTools;
import com.backend.agent.IceBreakTools;
import com.backend.agent.RelationTools;
import com.backend.agent.UserProfileTools;
import com.backend.document.VirtualCharacterDocument;
import com.backend.model.ApiResponse;
import com.backend.model.ReviewRequest;
import com.backend.model.SendRequest;
import com.backend.model.StartRequest;
import com.backend.repository.VirtualCharacterRepository;
import com.backend.service.CommAssistService;
import com.backend.service.SimulationService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/simulation")
public class SimulationController {

    private final SimulationService service;
    private final AgentRegistry registry;
    private final UserProfileTools profileTools;
    private final IceBreakTools iceBreakTools;
    private final RelationTools relationTools;
    private final CommAssistTools commAssistTools;
    private final VirtualCharacterRepository virtualCharacterRepo;
    private final CommAssistService commAssistService;

    public SimulationController(SimulationService service, AgentRegistry registry,
                                UserProfileTools profileTools, IceBreakTools iceBreakTools,
                                RelationTools relationTools, CommAssistTools commAssistTools,
                                VirtualCharacterRepository virtualCharacterRepo,
                                CommAssistService commAssistService) {
        this.service = service;
        this.registry = registry;
        this.profileTools = profileTools;
        this.iceBreakTools = iceBreakTools;
        this.relationTools = relationTools;
        this.commAssistTools = commAssistTools;
        this.virtualCharacterRepo = virtualCharacterRepo;
        this.commAssistService = commAssistService;
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

    // DELETE /api/simulation/sessions/{sessionId} — 删除会话（关联书友的亲密度联动重算）
    @DeleteMapping("/sessions/{sessionId}")
    public ApiResponse<?> deleteSession(@PathVariable String sessionId) {
        return ApiResponse.ok(service.deleteSession(sessionId));
    }

    // POST /api/simulation/score?sessionId=xxx — AI 评分（调用/解析失败 → 502 语义，不裸 500，执行协议红线 2）
    @PostMapping("/score")
    public ApiResponse<?> score(@RequestParam String sessionId) {
        try {
            return ApiResponse.ok(service.scoreConversation(sessionId));
        } catch (RuntimeException e) {
            String msg = e.getMessage() == null ? "评分失败" : e.getMessage();
            if (msg.length() > 200) {
                msg = msg.substring(0, 200);
            }
            return new ApiResponse<>(502, Map.of("message", "评分未成功：" + msg));
        }
    }

    // POST /api/simulation/self-review?sessionId=xxx — 保存会后复盘自评
    @PostMapping("/self-review")
    public ApiResponse<?> selfReview(@RequestParam String sessionId,
                                     @RequestBody Map<String, String> body) {
        return ApiResponse.ok(service.saveSelfReview(
                sessionId,
                body.getOrDefault("selfState", ""),
                body.getOrDefault("selfComment", "")));
    }

    // POST /api/simulation/agent — 通过 ReactAgent 处理请求（演示多智能体架构）
    @PostMapping("/agent")
    public ApiResponse<?> agent(@RequestBody Map<String, Object> input) {
        return registry.get("simulation-agent")
                .map(agent -> {
                    try {
                        var response = agent.reactAgent().call(input);
                        return ApiResponse.ok(Map.of("content", response.getText()));
                    } catch (Exception e) {
                        return ApiResponse.ok(Map.of("error", e.getMessage()));
                    }
                })
                .orElse(ApiResponse.ok(Map.of("error", "Agent not found")));
    }

    // GET /api/agents — 列出所有已注册 Agent
    @GetMapping("/agents")
    public ApiResponse<?> agents() {
        var list = registry.all().stream()
                .map(a -> Map.of("name", a.name(), "description", a.description()))
                .toList();
        return ApiResponse.ok(list);
    }

    // GET /api/simulation/profile — 获取用户画像
    @GetMapping("/profile")
    public ApiResponse<?> profile() {
        return ApiResponse.ok(profileTools.getProfile());
    }

    // POST /api/simulation/profile/assess — 生成/更新用户画像
    @PostMapping("/profile/assess")
    public ApiResponse<?> assessProfile() {
        return ApiResponse.ok(profileTools.assessAbility());
    }

    // ──── 关系运维 API ────

    // GET /api/simulation/contacts — 获取所有联系人
    @GetMapping("/contacts")
    public ApiResponse<?> contacts() {
        return ApiResponse.ok(relationTools.listContacts());
    }

    // GET /api/simulation/contacts/check — 检查维护提醒和预警
    @GetMapping("/contacts/check")
    public ApiResponse<?> checkContacts() {
        return ApiResponse.ok(relationTools.checkMaintenance());
    }

    // GET /api/simulation/contacts/{id}/intimacy — 计算亲密度
    @GetMapping("/contacts/{id}/intimacy")
    public ApiResponse<?> calcIntimacy(@PathVariable String id) {
        return ApiResponse.ok(relationTools.calcIntimacy(id));
    }

    // GET /api/simulation/contacts/{id}/drift — 检测疏远
    @GetMapping("/contacts/{id}/drift")
    public ApiResponse<?> detectDrift(@PathVariable String id) {
        return ApiResponse.ok(relationTools.detectDrift(id));
    }

    // POST /api/simulation/contacts/{id}/recover — 挽救方案
    @PostMapping("/contacts/{id}/recover")
    public ApiResponse<?> recover(@PathVariable String id, @RequestBody Map<String, Boolean> body) {
        boolean choose = body.getOrDefault("recover", false);
        return ApiResponse.ok(relationTools.generateRecoverPlan(id, choose));
    }

    // ──── 破冰分析 API ────

    // POST /api/simulation/icebreak — 破冰分析
    @PostMapping("/icebreak")
    public ApiResponse<?> icebreak(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        var result = iceBreakTools.analyzeCard(
                (List<String>) body.getOrDefault("myInterests", List.of()),
                (List<String>) body.getOrDefault("myLabels", List.of()),
                (List<String>) body.getOrDefault("myMood", List.of()),
                (List<String>) body.getOrDefault("otherInterests", List.of()),
                (List<String>) body.getOrDefault("otherLabels", List.of()),
                (String) body.getOrDefault("otherPersonality", ""),
                (String) body.getOrDefault("context", "初次见面"),
                (String) body.get("contactId")
        );
        return ApiResponse.ok(result);
    }

    // POST /api/simulation/icebreak/refresh — 单类建议再生成（不建联系人）
    @PostMapping("/icebreak/refresh")
    public ApiResponse<?> icebreakRefresh(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        var result = iceBreakTools.refreshSection(
                (List<String>) body.getOrDefault("myInterests", List.of()),
                (List<String>) body.getOrDefault("myLabels", List.of()),
                (List<String>) body.getOrDefault("myMood", List.of()),
                (List<String>) body.getOrDefault("otherInterests", List.of()),
                (List<String>) body.getOrDefault("otherLabels", List.of()),
                (String) body.getOrDefault("otherPersonality", ""),
                (String) body.getOrDefault("context", "初次见面"),
                (String) body.getOrDefault("section", "openings")
        );
        return ApiResponse.ok(result);
    }

    // ──── 虚拟人物管理 API（情景模拟域）────

    // GET /api/simulation/virtual-characters — 虚拟人物列表
    @GetMapping("/virtual-characters")
    public ApiResponse<?> virtualCharacters() {
        return ApiResponse.ok(virtualCharacterRepo.findAllByOrderByCreatedAtDesc());
    }

    // POST /api/simulation/virtual-characters — 新增虚拟人物
    @PostMapping("/virtual-characters")
    public ApiResponse<?> createVirtualCharacter(@RequestBody Map<String, Object> body) {
        VirtualCharacterDocument doc = new VirtualCharacterDocument();
        doc.setName(String.valueOf(body.getOrDefault("name", "")));
        doc.setPersonality(String.valueOf(body.getOrDefault("personality", "")));
        doc.setInterests(toStringList(body.get("interests")));
        doc.setLabels(toStringList(body.get("labels")));
        doc.setDescription(String.valueOf(body.getOrDefault("description", "")));
        doc.setCreatedAt(LocalDateTime.now());
        doc.setUpdatedAt(LocalDateTime.now());
        return ApiResponse.ok(virtualCharacterRepo.save(doc));
    }

    // DELETE /api/simulation/virtual-characters/{id} — 删除虚拟人物
    @DeleteMapping("/virtual-characters/{id}")
    public ApiResponse<?> deleteVirtualCharacter(@PathVariable String id) {
        virtualCharacterRepo.deleteById(id);
        return ApiResponse.ok(Map.of("deleted", true));
    }

    /** 将 List<?> 安全转换为 List<String>。 */
    private List<String> toStringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return new java.util.ArrayList<>();
        }
        return list.stream().map(String::valueOf).toList();
    }

    // ──── 沟通辅助 API ────

    // POST /api/simulation/assist/analyze — 实时分析用户输入
    @PostMapping("/assist/analyze")
    public ApiResponse<?> analyzeSpeech(@RequestBody Map<String, String> body) {
        var result = commAssistTools.analyzeSpeech(
                body.getOrDefault("text", ""),
                body.getOrDefault("context", "日常对话"),
                body.getOrDefault("sessionId", UUID.randomUUID().toString())
        );
        return ApiResponse.ok(result);
    }

    // POST /api/simulation/assist/score — 沟通结束评分
    @PostMapping("/assist/score")
    public ApiResponse<?> scoreComm(@RequestBody Map<String, String> body) {
        var result = commAssistTools.scorePerformance(body.getOrDefault("sessionId", ""));
        return ApiResponse.ok(result);
    }

    // ──── 沟通辅助（复盘版）：线下录音转写 + 归属确认复盘 ────

    /**
     * POST /api/simulation/assist/transcribe — 线下录音转写（multipart/form-data）。
     *
     * <p>音频字节仅在内存态持有，转写后即释放：不落盘、不入库（隐私设计）。
     * 前端须用 mp3/aac 压缩格式、单声道、≤5 分钟。
     */
    @PostMapping(value = "/assist/transcribe", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<?> assistTranscribe(@RequestParam("audio") MultipartFile audio,
                                           @RequestParam(value = "format", required = false) String format,
                                           @RequestParam(value = "sampleRate", required = false) Integer sampleRate) {
        try {
            byte[] bytes = audio.getBytes();
            return ApiResponse.ok(commAssistService.transcribe(bytes, format, sampleRate));
        } catch (Exception e) {
            return new ApiResponse<>(502, Map.of("message", brief(e.getMessage())));
        }
    }

    /**
     * POST /api/simulation/assist/review — 归属确认后复盘：五维评分 + 三库落库。
     *
     * <p>body: {sceneType, relatedContactId?, title?, sentences[], speakerMap{}}
     */
    @PostMapping("/assist/review")
    public ApiResponse<?> assistReview(@RequestBody ReviewRequest req) {
        try {
            return ApiResponse.ok(commAssistService.review(req));
        } catch (RuntimeException e) {
            return new ApiResponse<>(502, Map.of("message", brief(e.getMessage())));
        }
    }

    private static String brief(String msg) {
        String m = (msg == null || msg.isBlank()) ? "沟通辅助处理失败" : msg;
        return m.length() > 200 ? m.substring(0, 200) : m;
    }
}
