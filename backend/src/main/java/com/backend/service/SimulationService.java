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
    private final com.backend.repository.ContactRepository contactRepo;
    private final com.backend.repository.ConversationRepository conversationRepo;
    private final IntimacyService intimacyService;

    public SimulationService(ChatClient.Builder chatClientBuilder, ConversationStore store,
                             ApplicationEventPublisher eventPublisher,
                             com.backend.repository.ContactRepository contactRepo,
                             com.backend.repository.ConversationRepository conversationRepo,
                             IntimacyService intimacyService) {
        this.chatClient = chatClientBuilder.build();
        this.store = store;
        this.eventPublisher = eventPublisher;
        this.contactRepo = contactRepo;
        this.conversationRepo = conversationRepo;
        this.intimacyService = intimacyService;
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
        // P2-7 靶向训练：焦点校验 → 基线计算 → systemPrompt 注入教练段
        String focusSubSkill = normalizeFocus(req.getFocusSubSkill());
        Integer focusBaseline = focusSubSkill == null ? null : computeFocusBaseline(focusSubSkill);
        String systemPrompt = buildSystemPrompt(req.getTheme(), req.getPersonality(), focusSubSkill);

        String greeting = chatClient.prompt()
                .system(systemPrompt)
                .user("请用一句简短的开场白向我打招呼，不要超过40个字。")
                .call()
                .content();

        long now = System.currentTimeMillis();
        ChatMessage greetingMsg = new ChatMessage("1", "other", "", greeting, now);

        store.createSession(sessionId, req.getTheme(), req.getPersonality(),
                req.getRelatedContactId(), systemPrompt, greetingMsg);

        String taskId = req.getPrescriptionTaskId() == null || req.getPrescriptionTaskId().isBlank()
                ? null : req.getPrescriptionTaskId();
        if (focusSubSkill != null || taskId != null) {
            store.attachFocus(sessionId, focusSubSkill, focusBaseline, taskId);
        }

        Map<String, Object> data = new HashMap<>();
        data.put("sessionId", sessionId);
        data.put("greeting", new GreetingReply(greeting, now));
        data.put("focusSubSkill", focusSubSkill);
        data.put("focusBaseline", focusBaseline);
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

    // 获取会话历史（含评分 + 自评）
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
            // P1-5 透出子技能分与证据链（旧会话为 null，前端兼容）
            evalMap.put("subScores", eval.getSubScores());
            evalMap.put("evidence", eval.getEvidence());
            data.put("evaluation", evalMap);
        });
        // 附带复盘自评（如果有）
        store.getSelfReview(sessionId).ifPresent(review -> {
            data.put("selfState", review.selfState());
            data.put("selfComment", review.selfComment());
        });
        // P2-10 附带靶向焦点对比（重访复盘页也可展示）
        store.findConversation(sessionId)
                .filter(doc -> doc.getFocusSubSkill() != null)
                .ifPresent(doc -> {
                    String fk = doc.getFocusSubSkill();
                    ConversationDocument.Evaluation ev = doc.getEvaluation();
                    Integer cur = ev == null || ev.getSubScores() == null ? null : ev.getSubScores().get(fk);
                    Integer base = doc.getFocusBaseline();
                    Map<String, Object> focus = new HashMap<>();
                    focus.put("subSkill", fk);
                    focus.put("cnName", com.backend.constant.SubSkillConstants.isValid(fk)
                            ? com.backend.constant.SubSkillConstants.get(fk).cnName() : fk);
                    focus.put("baseline", base);
                    focus.put("current", cur);
                    focus.put("delta", base != null && cur != null ? cur - base : null);
                    data.put("focus", focus);
                });
        return data;
    }

    // 保存会后复盘自评
    public Map<String, Object> saveSelfReview(String sessionId, String selfState, String selfComment) {
        if (!store.sessionExists(sessionId)) {
            throw new RuntimeException("会话不存在: " + sessionId);
        }
        store.saveSelfReview(sessionId, selfState, selfComment);
        Map<String, Object> data = new HashMap<>();
        data.put("saved", true);
        data.put("selfState", selfState);
        data.put("selfComment", selfComment);
        return data;
    }

    // 获取所有会话摘要列表
    public List<SessionSummary> getSessionList() {
        return store.getSessionSummaries();
    }

    // 删除会话；若该会话关联了书友，删除后重算该书友亲密度（其深度/质量分量曾计入此会话）
    public Map<String, Object> deleteSession(String sessionId) {
        if (!store.sessionExists(sessionId)) {
            throw new RuntimeException("会话不存在: " + sessionId);
        }
        String contactId = store.getRelatedContactId(sessionId).orElse(null);
        store.deleteSession(sessionId);

        if (contactId != null && !contactId.isBlank()) {
            try {
                contactRepo.findById(contactId).ifPresent(contact -> {
                    int score = intimacyService.calculateIntimacy(contact);
                    intimacyService.persistIntimacy(contactId, score);
                    log.info("deleteSession: 会话 {} 关联书友 {} 亲密度已重算为 {}", sessionId, contact.getName(), score);
                });
            } catch (Exception e) {
                // 亲密度重算失败不阻断删除
                log.warn("deleteSession: 重算书友 {} 亲密度失败: {}", contactId, e.getMessage());
            }
        }

        Map<String, Object> data = new HashMap<>();
        data.put("deleted", true);
        data.put("sessionId", sessionId);
        return data;
    }

    // 对会话进行 AI 评分
    public Map<String, Object> scoreConversation(String sessionId) {
        List<ChatMessage> history = store.getMessages(sessionId);
        if (history.isEmpty()) {
            throw new RuntimeException("会话无消息，无法评分");
        }
        ScoringOutcome outcome = scoreDialogueText(buildDialogueText(history));
        store.saveScore(sessionId, outcome.score(), outcome.evaluation());
        // 发布评分完成事件 → UserProfileAgent 等订阅者自动更新
        eventPublisher.publishEvent(new AgentEvent.ScoringCompleted(sessionId, outcome.score()));

        Map<String, Object> response = new HashMap<>();
        response.put("score", outcome.score());
        response.put("evaluation", outcome.evaluation());

        // P2-7 焦点对比：仅靶向训练场次透出（baseline 缺失/无分时字段为 null，前端容错）
        conversationRepo.findById(sessionId)
                .filter(doc -> doc.getFocusSubSkill() != null)
                .ifPresent(doc -> response.put("focus", buildFocus(doc, outcome.evaluation())));
        return response;
    }

    /** 会话消息 → 评分用对话文本（"我：… / 对方：…"）。 */
    static String buildDialogueText(List<ChatMessage> history) {
        StringBuilder dialogText = new StringBuilder();
        for (ChatMessage msg : history) {
            String speaker = "other".equals(msg.getRole()) ? "对方" : "我";
            dialogText.append(speaker).append("：").append(msg.getContent()).append("\n");
        }
        return dialogText.toString();
    }

    /**
     * 复用评分核心：任意"我 / 对方"对话文本 → 五维分 + 子技能分 + 证据链。
     *
     * <p>只做 LLM 调用与解析，<b>不落库、不发事件</b>；供情景模拟（scoreConversation）
     * 与线下沟通复盘（CommAssistService）共用，保证两条链路评分口径一致。
     */
    @SuppressWarnings("unchecked")
    public ScoringOutcome scoreDialogueText(String dialogueText) {
        String scorePrompt = buildScorePrompt(dialogueText);

        String result = chatClient.prompt()
                .user(scorePrompt)
                .call()
                .content();
        // P1-3 健壮性：推理型模型可能因输出 token 耗尽返回空内容 → 紧凑重试一次
        if (result == null || result.isBlank()) {
            log.warn("评分 LLM 返回空内容（疑似推理耗尽输出预算），紧凑重试一次");
            result = chatClient.prompt()
                    .user(scorePrompt + "\n注意：不要输出任何思考铺垫，直接输出紧凑 JSON（无 markdown 代码块）。")
                    .call()
                    .content();
        }

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
            // (empathy_listening). Static prompt check catches the prompt side; this catches AI
            // format drift — if AI omits the key, getIntSafe silently returns 50.
            if (empathyListening == 50 && clarity != 50 && logicality != 50
                    && interactivity != 50 && relaxation != 50) {
                log.warn("empathyListening=50 but other 4 dims vary — likely AI omitted 'empathyListening' key, fell back to default 50");
            }

            ConversationDocument.Evaluation evaluation = new ConversationDocument.Evaluation();
            evaluation.setClarity(clarity);
            evaluation.setLogicality(logicality);
            evaluation.setEmpathyListening(empathyListening);
            evaluation.setInteractivity(interactivity);
            evaluation.setRelaxation(relaxation);
            evaluation.setComment((String) scoreData.get("comment"));
            evaluation.setStrengths((List<String>) scoreData.get("strengths"));
            evaluation.setSuggestions((List<String>) scoreData.get("suggestions"));
            // P1 子技能层：任何失败降级为 null/空，绝不阻断 5 维主分落库（执行协议红线 2）
            evaluation.setSubScores(parseSubScores(scoreData));
            evaluation.setEvidence(parseEvidence(scoreData.get("evidence")));
            return new ScoringOutcome(totalScore, evaluation);
        } catch (Exception e) {
            throw new RuntimeException("评分解析失败: " + e.getMessage(), e);
        }
    }

    /** 构建评分 prompt（5 维锚定 + 子技能观察 + 证据提取）。 */
    private String buildScorePrompt(String dialogueText) {
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
                子项：主动发起新话题、推动性提问、邀请让渡（含cue沉默者）、冷场处理；发言占比仅极端独白（>80%%且无邀请）作刹车扣分。
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

                ## 子技能观察（输出 subScores，共 16 项）
                在给出五维分后，逐项评估以下子技能（0-100 整数）。硬性规则：
                1. 本场对话未出现该子技能的观察机会（如对方全程没有质疑 → non_defensive）→ 该项输出 null，禁止硬打分；
                2. 判据服从前文"横切规则"：道歉/退缩词只计入 less_hedging；堆叠"然后"计入 filler_control；
                   空洞附和（对对对/嗯嗯 无内容）不给 paraphrase、empathic_followup、turn_giving 正向分；
                3. 某维度下子技能的均值应与该维度分大体一致，偏差超过 25 分时须在 comment 中说明主因；
                4. 防刷分同样适用：套话模板（如机械的"听起来你很难过"但语境不符）不给高分。

                %s

                ## 证据提取（输出 evidence，0~4 条）
                - 每个低于 60 分且有观察机会的子技能，至少给 1 条 negative 证据；positive 高光证据最多 1 条；
                - quote：用户原话的逐字片段（不超过60字）；messageOrder：该原话在对话中的消息序号（"我："的第几条，从1计）；
                - issue：一句话说明问题（结合对方上一句的状态，指出用户回应的缺失）；
                - rewrite：更好的说法示范（第一人称、可直接照说、贴合当时语境）；negative 必填，positive 置空字符串；
                - polarity："negative" 或 "positive"。

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
                - subScores: 对象，键为 16 个子技能短名（见上文"子技能观察"），值为 0-100 整数或 null
                - evidence: 数组，0~4 个对象 {subSkill, messageOrder, quote, issue, rewrite, polarity}

                只返回JSON，不要输出其他内容。""",
                dialogueText, com.backend.constant.SubSkillConstants.promptBlock());

        // Sanity: verify dimension keys won't silently fallback to 50
        // (probe checks the key name in the output-format section, quoted form is not required)
        final String promptText = scorePrompt;
        if (!promptText.contains("empathyListening")) {
            log.warn("empathyListening key missing from AI prompt — AI responses will default to 50");
        }
        if (!promptText.contains("clarity") || !promptText.contains("logicality") ||
            !promptText.contains("interactivity") || !promptText.contains("relaxation")) {
            log.warn("One or more 5D keys missing from AI prompt schema");
        }
        return scorePrompt;
    }

    /** 焦点对比卡片数据（P2-7 靶向训练）。 */
    private Map<String, Object> buildFocus(ConversationDocument doc, ConversationDocument.Evaluation evaluation) {
        String fk = doc.getFocusSubSkill();
        Integer cur = evaluation.getSubScores() == null ? null : evaluation.getSubScores().get(fk);
        Integer base = doc.getFocusBaseline();
        Map<String, Object> focus = new HashMap<>();
        focus.put("subSkill", fk);
        focus.put("cnName", com.backend.constant.SubSkillConstants.isValid(fk)
                ? com.backend.constant.SubSkillConstants.get(fk).cnName() : fk);
        focus.put("baseline", base);
        focus.put("current", cur);
        focus.put("delta", base != null && cur != null ? cur - base : null);
        return focus;
    }

    /** 评分产出（总分 + 明细），不落库、不发事件。 */
    public record ScoringOutcome(int score, ConversationDocument.Evaluation evaluation) {}

    // 根据主题和性格构建角色 system prompt（P2-7：带靶向焦点时追加教练段）
    private String buildSystemPrompt(String theme, String personality) {
        return buildSystemPrompt(theme, personality, null);
    }

    private String buildSystemPrompt(String theme, String personality, String focusSubSkill) {
        String base = String.format(
                "你正在参与一个名为「%s」的情景模拟对话。" +
                "你的性格设定是：%s。" +
                "请完全按照这个角色设定来回复，保持自然、流畅的对话风格。每次回复不超过100个字。",
                theme, personality
        );
        if (focusSubSkill == null) {
            return base;
        }
        return base + coachSection(focusSubSkill);
    }

    /** 教练注入段（设计文档 §4.1 模板全文）。包级可见便于单测。 */
    static String coachSection(String focusSubSkill) {
        com.backend.constant.SubSkillConstants.SubSkill s =
                com.backend.constant.SubSkillConstants.get(focusSubSkill);
        if (s == null) {
            return "";
        }
        return String.format("""

                【本场训练焦点】用户正在刻意练习「%s」（%s）。
                你的配合要求：
                1. 在对话中自然制造 2~3 次该行为的练习机会，喂球方式：%s；
                2. 若进行约 6 轮后用户始终未出现该行为，可轻微引导一次（如流露更明显的情绪、直接询问看法），但绝不点破"你在练习"；
                3. 保持你的性格设定不变，喂球必须贴合人设、不出戏。
                """, s.cnName(), s.definition(), s.feed());
    }

    /** 焦点校验：白名单外按自由训练处理（红线 2：不报错只降级）。包级可见便于单测。 */
    static String normalizeFocus(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        if (com.backend.constant.SubSkillConstants.isValid(raw)) {
            return raw;
        }
        log.warn("收到未知 focusSubSkill: {}，按自由训练处理", raw);
        return null;
    }

    /** 焦点基线 = 近 3 场已评分会话该子技能均值（无含分场次 → null）。 */
    private Integer computeFocusBaseline(String focusSubSkill) {
        List<Integer> vals = new ArrayList<>();
        for (ConversationDocument d : conversationRepo.findAllByOrderByCreatedAtDesc()) {
            if (vals.size() >= 3) {
                break;
            }
            if (d.getEvaluation() == null || d.getEvaluation().getSubScores() == null) {
                continue;
            }
            Integer v = d.getEvaluation().getSubScores().get(focusSubSkill);
            if (v != null) {
                vals.add(v);
            }
        }
        return vals.isEmpty() ? null
                : (int) Math.round(vals.stream().mapToInt(Integer::intValue).average().orElse(0));
    }

    /** 从 Map 中安全提取 int 值，缺失或非数字时返回默认值并记录警告 */
    private static int getIntSafe(Map<String, Object> map, String key, int defaultVal) {
        Object val = map.get(key);
        if (val instanceof Number n) return n.intValue();
        log.warn("Missing dimension: {} in AI response, defaulting to {}", key, defaultVal);
        return defaultVal;
    }

    // ──── P1 子技能层宽容解析（设计文档 §2.2；包级可见便于单测） ────

    /**
     * 解析 subScores：白名单 + clamp[0,100]；缺失/非数/显式 null → 该键值为 null。
     * 恒返回 16 键全在的 Map（形状统一，聚合端免判空）。未知键 log.warn（键漂移探针，仿 empathyListening）。
     */
    @SuppressWarnings("unchecked")
    static Map<String, Integer> parseSubScores(Map<String, Object> scoreData) {
        try {
            Object raw = scoreData == null ? null : scoreData.get("subScores");
            Map<String, Object> sub = raw instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
            for (String k : sub.keySet()) {
                if (!com.backend.constant.SubSkillConstants.isValid(k)) {
                    log.warn("subScores 出现白名单外的键（AI 键漂移）: {}", k);
                }
            }
            Map<String, Integer> result = new java.util.LinkedHashMap<>();
            for (com.backend.constant.SubSkillConstants.SubSkill s : com.backend.constant.SubSkillConstants.ALL) {
                Object v = sub.get(s.key());
                result.put(s.key(), v instanceof Number n ? Math.max(0, Math.min(100, n.intValue())) : null);
            }
            return result;
        } catch (Exception e) {
            log.warn("subScores 解析失败，降级为全 null: {}", e.getMessage());
            Map<String, Integer> fallback = new java.util.LinkedHashMap<>();
            for (com.backend.constant.SubSkillConstants.SubSkill s : com.backend.constant.SubSkillConstants.ALL) {
                fallback.put(s.key(), null);
            }
            return fallback;
        }
    }

    /**
     * 解析 evidence：非 List → 空；逐条丢弃坏项（subSkill 非法 / quote 缺失），
     * quote 截断 ≤60，messageOrder 缺 → 0，polarity 非 positive → negative；最多 4 条。
     */
    static List<ConversationDocument.EvidenceItem> parseEvidence(Object raw) {
        List<ConversationDocument.EvidenceItem> items = new ArrayList<>();
        try {
            if (!(raw instanceof List<?> list)) {
                return items;
            }
            for (Object o : list) {
                if (items.size() >= com.backend.constant.SubSkillConstants.EVIDENCE_MAX) {
                    break;
                }
                if (!(o instanceof Map<?, ?> m)) {
                    continue;
                }
                String subSkill = evidStr(m.get("subSkill"));
                if (!com.backend.constant.SubSkillConstants.isValid(subSkill)) {
                    continue;
                }
                String quote = evidStr(m.get("quote"));
                if (quote.isEmpty()) {
                    continue;
                }
                if (quote.length() > com.backend.constant.SubSkillConstants.QUOTE_MAX_LEN) {
                    quote = quote.substring(0, com.backend.constant.SubSkillConstants.QUOTE_MAX_LEN);
                }
                Integer order = m.get("messageOrder") instanceof Number n ? n.intValue() : 0;
                String polarity = evidStr(m.get("polarity"));
                if (!"positive".equals(polarity) && !"negative".equals(polarity)) {
                    polarity = "negative";
                }
                items.add(new ConversationDocument.EvidenceItem(
                        subSkill, order, quote, evidStr(m.get("issue")), evidStr(m.get("rewrite")), polarity));
            }
        } catch (Exception e) {
            log.warn("evidence 解析失败，降级为已收集部分/空: {}", e.getMessage());
        }
        return items;
    }

    private static String evidStr(Object v) {
        return v == null ? "" : String.valueOf(v).trim();
    }
}
