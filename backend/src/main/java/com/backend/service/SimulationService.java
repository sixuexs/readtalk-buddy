package com.backend.service;

import com.backend.agent.AgentEvent;
import com.backend.document.ConversationDocument;
import com.backend.model.*;
import com.backend.store.ConversationStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;

import com.backend.model.SessionSummary;
import java.util.*;

@Service
public class SimulationService {

    private static final Logger log = LoggerFactory.getLogger(SimulationService.class);

    private final ChatClient chatClient;
    private final ConversationStore store;
    private final ApplicationEventPublisher eventPublisher;

    public SimulationService(ChatClient.Builder chatClientBuilder, ConversationStore store,
                             ApplicationEventPublisher eventPublisher) {
        this.chatClient = chatClientBuilder.build();
        this.store = store;
        this.eventPublisher = eventPublisher;
    }

    // 获取可用配置
    public SimulationConfig getConfig() {
        SimulationConfig config = new SimulationConfig();
        config.setThemes(List.of("初次见面", "读书交流", "读后感分享", "面试演练", "日常闲聊"));
        config.setPersonalities(List.of("乐观开朗自来熟", "不善交际慢热", "幽默风趣社牛", "沉稳内敛观察者"));
        return config;
    }

    // 获取场景信息
    public ScenarioInfo getScenario(String scenarioId) {
        ScenarioInfo info = new ScenarioInfo();
        info.setScenarioId(scenarioId);
        info.setTitle("情景模拟");
        info.setDescription("模拟社交场景，练习沟通技巧");
        return info;
    }

    // 开始模拟：构建 system prompt，调用 AI 获取开场白，持久化会话
    public Map<String, Object> startSimulation(StartRequest req) {
        String sessionId = UUID.randomUUID().toString();
        String systemPrompt = buildSystemPrompt(req.getTheme(), req.getPersonality());

        String greeting = chatClient.prompt()
                .system(systemPrompt)
                .user("请用一句简短的开场白向我打招呼，不要超过40个字。")
                .call()
                .content();

        long now = System.currentTimeMillis();
        ChatMessage greetingMsg = new ChatMessage("1", "other", "", greeting, now);

        store.createSession(sessionId, req.getTheme(), req.getPersonality(),
                systemPrompt, greetingMsg);

        Map<String, Object> data = new HashMap<>();
        data.put("sessionId", sessionId);
        data.put("greeting", new GreetingReply(greeting, now));
        return data;
    }

    // 发送消息：从 MongoDB 加载历史，拼接后调用 AI，持久化新消息
    public Map<String, Object> sendMessage(SendRequest req) {
        String sessionId = req.getScenarioId();
        List<ChatMessage> history = store.getMessages(sessionId);

        // 构建用户消息并持久化
        long now = System.currentTimeMillis();
        ChatMessage userMsg = new ChatMessage(
                String.valueOf(history.size() + 1),
                "self", "", req.getMessage(), now);
        store.appendMessage(sessionId, userMsg);
        history.add(userMsg);

        // 构建 Spring AI Messages 列表
        String systemPrompt = store.getSystemPrompt(sessionId);
        List<Message> aiMessages = new ArrayList<>();
        aiMessages.add(new SystemMessage(systemPrompt));

        for (ChatMessage msg : history) {
            if ("other".equals(msg.getRole())) {
                aiMessages.add(new AssistantMessage(msg.getContent()));
            } else {
                aiMessages.add(new UserMessage(msg.getContent()));
            }
        }

        String reply = chatClient.prompt()
                .messages(aiMessages)
                .call()
                .content();

        long replyTs = System.currentTimeMillis();
        ChatMessage replyMsg = new ChatMessage(
                String.valueOf(history.size() + 1),
                "other", "", reply, replyTs);
        store.appendMessage(sessionId, replyMsg);

        Map<String, Object> data = new HashMap<>();
        data.put("reply", new MessageReply(reply, replyTs));
        return data;
    }

    // 获取会话历史（含评分）
    public Map<String, Object> getHistory(String sessionId) {
        List<ChatMessage> history = store.getMessages(sessionId);
        Map<String, Object> data = new HashMap<>();
        data.put("messages", history);
        // 附带评分数据（如果有）
        store.getEvaluation(sessionId).ifPresent(eval -> {
            Map<String, Object> evalMap = new HashMap<>();
            evalMap.put("score", store.getScore(sessionId).orElse(null));
            evalMap.put("clarity", eval.getClarity());
            evalMap.put("logicality", eval.getLogicality());
            evalMap.put("empathyListening", eval.getEmpathyListening());
            evalMap.put("interactivity", eval.getInteractivity());
            evalMap.put("relaxation", eval.getRelaxation());
            evalMap.put("comment", eval.getComment());
            evalMap.put("strengths", eval.getStrengths());
            evalMap.put("suggestions", eval.getSuggestions());
            data.put("evaluation", evalMap);
        });
        return data;
    }

    // 获取所有会话摘要列表
    public List<SessionSummary> getSessionList() {
        return store.getSessionSummaries();
    }

    // 对会话进行 AI 评分
    @SuppressWarnings("unchecked")
    public Map<String, Object> scoreConversation(String sessionId) {
        List<ChatMessage> history = store.getMessages(sessionId);
        if (history.isEmpty()) {
            throw new RuntimeException("会话无消息，无法评分");
        }

        // 构建对话文本
        StringBuilder dialogText = new StringBuilder();
        for (ChatMessage msg : history) {
            String speaker = "other".equals(msg.getRole()) ? "对方" : "我";
            dialogText.append(speaker).append("：").append(msg.getContent()).append("\n");
        }

        String scorePrompt = String.format("""
                你是一位专业的社交沟通能力评估专家。请根据以下对话内容，评估"我"的社交沟通能力。

                ## 评分设计原则
                1. 正交性：每个语言特征只计入一个维度，不重复计分。
                2. 防刷分：伪信号不得正向分（如空洞附和、关联词套壳无实质内容）。
                3. 文本可测边界：仅基于文本可观察信号打分，不臆测不可见特征。

                ## 横切规则（关键特征归属）
                - 停顿填充词（那个/就是说/嗯/呃）→ 表达清晰度
                - 自我贬低/退缩词（可能我不对/随便说说/我说不好）→ 情绪松弛度
                - 道歉词（不好意思/抱歉/打扰一下）→ 情绪松弛度（看后接是否退让），绝不作共情正向
                - 单句内问题（半截句、口误修正）→ 表达清晰度；跨句结构问题 → 逻辑思辨力
                - 连续堆叠"然后…然后…"→ 表达清晰度填充；单次连接有先后/因果分句的"然后"→ 逻辑思辨力
                - 共情性追问（指向对方内在体验）→ 共情与倾听；话题推动/征询表态/破冰/邀请提问 → 互动积极性
                - 空洞附和（对对对/嗯嗯/是的 无内容）→ 共情与倾听、互动积极性 均不计正向
                - 修辞性反问（不期待回答、强化论点）→ 逻辑思辨力；征询性反问（期待对方表态）→ 互动积极性

                ## 对话内容
                %s

                ## 五维评分指南

                ### 1. 表达清晰度 (clarity) [0-100]
                定义：能否准确、流畅地输出观点，不词不达意、不冗赘。只评"是否说清楚"，不评对错与条理。
                指标：填充词密度（每百字<3不扣）、句法完整率、冗余率、用词准确度。
                锚定：
                - 0-39：填充密集、大量半截句或词不达意，听者难抓观点
                - 40-69：能表达观点，但有明显冗余/填充/偶有词不达意
                - 70-89：观点清晰、表达流畅，偶有1-2处填充不影响理解
                - 90-100：精准简洁、无冗余、用词准确，一听即明
                边界：不收自我贬低词（归 relaxation）、不收道歉词（归 relaxation）。仅输出整数。

                ### 2. 逻辑思辨力 (logicality) [0-100]
                定义：发言是否有条理、观点是否有支撑。只评"组织得清不清楚"，不评流畅度。
                指标：关联词有效使用、观点-论据匹配、论证结构完整度、观点自洽性。
                防刷分：关联词不按密度计高分；"首先/其次/最后"套壳但论据空=伪逻辑，落及格档，不得因有关联词即给高分。
                锚定：
                - 0-39：观点跳跃、无论据、或前后自相矛盾
                - 40-69：有观点有关联词壳，但论据空/因果牵强
                - 70-89：观点清晰有支撑、论证链基本完整
                - 90-100：论证严密、多层支撑、结构清晰
                边界：单句内口误/半截句归 clarity；修辞性反问归本维。仅输出整数。

                ### 3. 共情与倾听 (empathyListening) [0-100]
                定义：能否听见对方（基于对方信息回应）+ 能否接住对方（识别并回应情绪与立场）。
                分两组：倾听（延展率、自说自话率、共情性追问率）+ 共情（情绪命名/回应、视角接纳/肯定）。
                防刷分：空洞附和（对对对/嗯嗯/是的 无内容）不计正向；只有带内容的肯定（复述/延伸对方观点、命名对方情绪）才算正向。
                锚定：
                - 0-39：自说自话、无视对方，或全程空洞附和
                - 40-69：有回应但停表面，未真正接住情绪/观点
                - 70-89：能基于对方发言延展并显式回应其情绪或视角
                - 90-100：精准命名情绪/复述观点并深化，让对方感到被听见
                边界：道歉词本身不作本维正向；共情性追问归本维，话题推动提问归 interactivity。仅输出整数。

                ### 4. 互动积极性 (interactivity) [0-100]
                定义：是否主动推动对话向前、维持流转、邀请对方参与、化解冷场。只评"做没做推动/维持/邀请动作"。
                子项：主动发起新话题、推动性提问、邀请让渡（含cue沉默者）、冷场处理；发言占比仅极端独白（>80%且无邀请）作刹车扣分。
                防刷分：问号不直接计高分，须语义过滤；空洞附和=中性噪声，不计正向。
                锚定：
                - 0-39：全程被动，只附和或沉默，从不发起/提问/邀请
                - 40-69：有接话但被动，不推进不抛问题不让渡
                - 70-89：主动提问或抛新角度，维持流转
                - 90-100：主动推进+邀请对方+平衡发言+化解冷场
                边界：共情性追问归 empathy；紧张但努力提问→本维不扣；松弛但回避不说话→本维低。仅输出整数。

                ### 5. 情绪松弛度 (relaxation) [0-100]
                定义：交流时的从容/自信程度，是否被紧张驱使而退缩、防御或掩饰。
                指标：退缩/自我贬低词密度（每百字<2不扣）、道歉退让词密度、防御性语言、自我表露意愿。
                置信度约束：仅就文本可观察的退缩/防御/表露信号打分，禁止臆测语音层面的紧张或从容。
                防刷分：纯沉默/纯附和不得高分——须结合自我表露意愿判断，无实质输出则保守给中低分。
                锚定：
                - 0-39：退缩/道歉密集、频繁自我否定收回观点、几乎不表露
                - 40-69：偶有退缩、观点多hedging、表露浅
                - 70-89：较少退缩词、能表达立场、有一定表露
                - 90-100：从容、立场清晰、不靠道歉铺垫、敢表露脆弱、被质疑不防御
                边界：停顿填充词归 clarity；道歉词本身只可能是本维信号。仅输出整数。

                ## 输出格式
                请以 JSON 格式返回评分结果，包含以下字段：
                - clarity: 表达清晰度 (0-100)
                - logicality: 逻辑思辨力 (0-100)
                - empathyListening: 共情与倾听 (0-100)
                - interactivity: 互动积极性 (0-100)
                - relaxation: 情绪松弛度 (0-100)
                - totalScore: 综合总分 (0-100)，取五个维度的等权平均
                - comment: 评语 (50-100字)，给出整体评价和改进方向
                - strengths: 优点标签数组，1-3个关键词
                - suggestions: 改进建议标签数组，1-3个关键词

                只返回JSON，不要输出其他内容。""",
                dialogText.toString());

        // Sanity: verify empathyListening key won't silently fallback to 50
        // This is the only dimension where camelCase != snake_case (empathy_listening)
        final String promptText = scorePrompt;
        if (!promptText.contains("\"empathyListening\"")) {
            log.warn("empathyListening key missing from AI prompt — AI responses will default to 50");
        }
        if (!promptText.contains("\"clarity\"") || !promptText.contains("\"logicality\"") || 
            !promptText.contains("\"interactivity\"") || !promptText.contains("\"relaxation\"")) {
            log.warn("One or more 5D keys missing from AI prompt schema");
        }

        String result = chatClient.prompt()
                .user(scorePrompt)
                .call()
                .content();

        // 解析 JSON 结果
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            Map<String, Object> scoreData = mapper.readValue(result, Map.class);

            int totalScore = getIntSafe(scoreData, "totalScore", 50);
            int clarity = getIntSafe(scoreData, "clarity", 50);
            int logicality = getIntSafe(scoreData, "logicality", 50);
            int empathyListening = getIntSafe(scoreData, "empathyListening", 50);
            int interactivity = getIntSafe(scoreData, "interactivity", 50);
            int relaxation = getIntSafe(scoreData, "relaxation", 50);

            // Runtime probe: empathyListening is the ONLY dimension where camelCase ≠ snake_case
            // (empathy_listening). Static prompt check above catches the prompt side;
            // this catches AI format drift — if AI omits the key, getIntSafe silently returns 50.
            if (empathyListening == 50 && clarity != 50 && logicality != 50
                    && interactivity != 50 && relaxation != 50) {
                log.warn("empathyListening=50 but other 4 dims vary — likely AI omitted 'empathyListening' key, fell back to default 50");
            }

            String comment = (String) scoreData.get("comment");

            List<String> strengths = (List<String>) scoreData.get("strengths");
            List<String> suggestions = (List<String>) scoreData.get("suggestions");

            // 持久化评分
            ConversationDocument.Evaluation evaluation = new ConversationDocument.Evaluation();
            evaluation.setClarity(clarity);
            evaluation.setLogicality(logicality);
            evaluation.setEmpathyListening(empathyListening);
            evaluation.setInteractivity(interactivity);
            evaluation.setRelaxation(relaxation);
            evaluation.setComment(comment);
            evaluation.setStrengths(strengths);
            evaluation.setSuggestions(suggestions);
            store.saveScore(sessionId, totalScore, evaluation);

            // 发布评分完成事件 → UserProfileAgent 等订阅者自动更新
            eventPublisher.publishEvent(new AgentEvent.ScoringCompleted(sessionId, totalScore));

            Map<String, Object> response = new HashMap<>();
            response.put("score", totalScore);
            response.put("evaluation", evaluation);
            return response;

        } catch (Exception e) {
            throw new RuntimeException("评分解析失败: " + e.getMessage(), e);
        }
    }

    // 根据主题和性格构建角色 system prompt
    private String buildSystemPrompt(String theme, String personality) {
        return String.format(
                "你正在参与一个名为「%s」的情景模拟对话。" +
                "你的性格设定是：%s。" +
                "请完全按照这个角色设定来回复，保持自然、流畅的对话风格。每次回复不超过100个字。",
                theme, personality
        );
    }

    /** 从 Map 中安全提取 int 值，缺失或非数字时返回默认值并记录警告 */
    private static int getIntSafe(Map<String, Object> map, String key, int defaultVal) {
        Object val = map.get(key);
        if (val instanceof Number n) return n.intValue();
        log.warn("Missing dimension: {} in AI response, defaulting to {}", key, defaultVal);
        return defaultVal;
    }
}
