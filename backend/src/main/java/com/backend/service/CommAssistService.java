package com.backend.service;

import com.backend.agent.AgentEvent;
import com.backend.client.AliyunAsrClient;
import com.backend.constant.AssistConstants;
import com.backend.document.ContactDocument;
import com.backend.document.ConversationDetailDocument;
import com.backend.document.ConversationDocument;
import com.backend.entity.ContactEntity;
import com.backend.entity.EvaluationRecord;
import com.backend.entity.InteractionMeta;
import com.backend.model.AsrSentence;
import com.backend.model.ReviewRequest;
import com.backend.repository.ContactRepository;
import com.backend.repository.ConversationDetailRepository;
import com.backend.repository.ConversationRepository;
import com.backend.repository.jpa.EvaluationRecordRepository;
import com.backend.repository.jpa.InteractionMetaRepository;
import com.backend.util.TextMaskUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 沟通辅助（复盘版）编排服务 —— 线下沟通「录音 → 转写 → 归属确认 → 五维评分 → 落库」。
 *
 * <p>设计要点：
 * <ul>
 *   <li><b>统一 ID</b>：reviewId 同时作为 conversation._id / conversation_detail._id /
 *       interaction_meta.biz_id，三库一个键串联，便于后续按 sessionId 反查评估记录。</li>
 *   <li><b>复用评分</b>：直接调 {@link SimulationService#scoreDialogueText(String)}，
 *       与情景模拟共用同一套五维 prompt + 子技能 + 证据链，口径一致。</li>
 *   <li><b>隐私</b>：音频字节仅在本方法内存态持有，转写后即释放；不落盘、不入库；
 *       只把脱敏前的转写文本交给 LLM，声纹特征不接收不存储。</li>
 *   <li><b>优雅降级</b>：MySQL 写入失败只记日志不阻断复盘主链路（执行协议红线 2）。</li>
 * </ul>
 */
@Service
public class CommAssistService {

    private static final Logger log = LoggerFactory.getLogger(CommAssistService.class);

    /** 主题展示名（写入 conversation.theme）。 */
    private static final String OFFLINE_PERSONALITY = "线下录音";

    private final AliyunAsrClient asrClient;
    private final SimulationService simulationService;
    private final ConversationRepository conversationRepo;
    private final ConversationDetailRepository detailRepo;
    private final ContactRepository contactRepo;
    private final ContactService contactService;
    private final InteractionMetaRepository interactionMetaRepo;
    private final EvaluationRecordRepository evaluationRecordRepo;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final long defaultUserId;
    /** 是否在入 LLM 与落库前对转写文本做轻量 PII 脱敏（默认开启）。 */
    private final boolean maskPii;

    public CommAssistService(AliyunAsrClient asrClient,
                             SimulationService simulationService,
                             ConversationRepository conversationRepo,
                             ConversationDetailRepository detailRepo,
                             ContactRepository contactRepo,
                             ContactService contactService,
                             InteractionMetaRepository interactionMetaRepo,
                             EvaluationRecordRepository evaluationRecordRepo,
                             ApplicationEventPublisher eventPublisher,
                             @Value("${assist.default-user-id:1001}") long defaultUserId,
                             @Value("${assist.mask-pii:true}") boolean maskPii) {
        this.asrClient = asrClient;
        this.simulationService = simulationService;
        this.conversationRepo = conversationRepo;
        this.detailRepo = detailRepo;
        this.contactRepo = contactRepo;
        this.contactService = contactService;
        this.interactionMetaRepo = interactionMetaRepo;
        this.evaluationRecordRepo = evaluationRecordRepo;
        this.eventPublisher = eventPublisher;
        this.defaultUserId = defaultUserId;
        this.maskPii = maskPii;
    }

    // ────────────────────────── 转写 ──────────────────────────

    /**
     * 音频 → 带说话人标签的分句转写（音频用毕即释放，不落盘不入库）。
     *
     * @return sentences + speakers 汇总（供前端归属确认页渲染下拉）
     */
    public Map<String, Object> transcribe(byte[] audio, String format, Integer sampleRate) {
        if (audio == null || audio.length == 0) {
            throw new RuntimeException("未收到音频数据");
        }
        if (audio.length > AssistConstants.MAX_AUDIO_BYTES) {
            throw new RuntimeException("音频过大（" + (audio.length / 1024 / 1024)
                    + "MB），同步识别上限约 7MB，请控制录音时长在 5 分钟以内");
        }

        List<AsrSentence> sentences = asrClient.transcribe(audio, format, sampleRate);

        // 按说话人分组汇总（speakerId 可能为 null → 归入 unknown 组）
        Map<Integer, List<AsrSentence>> grouped = new LinkedHashMap<>();
        for (AsrSentence s : sentences) {
            grouped.computeIfAbsent(s.getSpeakerId(), k -> new ArrayList<>()).add(s);
        }
        List<Map<String, Object>> speakers = new ArrayList<>();
        for (Map.Entry<Integer, List<AsrSentence>> e : grouped.entrySet()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("speakerId", e.getKey());
            item.put("sentenceCount", e.getValue().size());
            item.put("sample", e.getValue().isEmpty() ? "" : e.getValue().get(0).getText());
            speakers.add(item);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("sentences", sentences);
        data.put("speakers", speakers);
        data.put("speakerCount", grouped.size());
        data.put("mock", asrClient.isMock());
        log.info("线下转写完成：分句 {} 条，说话人 {} 个，mock={}", sentences.size(), grouped.size(), asrClient.isMock());
        return data;
    }

    // ────────────────────────── 复盘落库 ──────────────────────────

    /** 归属确认后的分句（含角色归属）。 */
    private record RoleSentence(Integer speakerId, String role, String speakerRef,
                               String text, Long beginTime, Long endTime) {}

    /**
     * 复盘：归属确认结果 → 五维评分 → MongoDB(conversation + conversation_detail)
     * + MySQL(interaction_meta + evaluation_record) → 发布评分完成事件。
     */
    public Map<String, Object> review(ReviewRequest req) {
        String scene = AssistConstants.isValidScene(req.getSceneType())
                ? req.getSceneType() : AssistConstants.SCENE_ONE_ON_ONE;
        List<ReviewRequest.SentenceDto> rawSentences =
                req.getSentences() == null ? List.of() : req.getSentences();
        Map<String, String> speakerMap =
                req.getSpeakerMap() == null ? Map.of() : req.getSpeakerMap();

        if (rawSentences.isEmpty()) {
            throw new RuntimeException("无转写内容，无法复盘");
        }
        boolean hasUser = speakerMap.values().stream().anyMatch(AssistConstants.MAP_USER::equals);
        if (!hasUser) {
            throw new RuntimeException("请先标注哪一位说话人是「我」");
        }

        // 1. 角色归属解析
        List<RoleSentence> rows = resolveRoles(rawSentences, speakerMap);

        // 2. 评分（复用情景模拟同一套五维引擎）
        int otherSpeakerCount = (int) rows.stream()
                .filter(r -> !AssistConstants.ROLE_USER.equals(r.role()))
                .map(RoleSentence::speakerRef)
                .distinct().count();
        String dialogueText = buildReviewDialogueText(rows, scene, otherSpeakerCount);
        SimulationService.ScoringOutcome outcome = simulationService.scoreDialogueText(dialogueText);

        // 3. 统一 ID：conversation._id = conversation_detail._id = interaction_meta.biz_id
        String reviewId = AssistConstants.newBizId();

        // 4. 写虚拟 conversation 文档（供 review.vue / 画像统计 / 事件链复用）
        saveConversation(reviewId, req, scene, rows, outcome);

        // 5. 写 conversation_detail（多人逐句明细）
        List<String> participantIds = saveDetail(reviewId, scene, rows);

        // 6. 写 MySQL 中枢表（失败降级，不阻断）
        saveMySql(reviewId, scene, req, rows, participantIds, outcome);

        // 7. 发布评分完成事件 → 画像刷新 + 亲密度联动
        eventPublisher.publishEvent(new AgentEvent.ScoringCompleted(reviewId, outcome.score()));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("sessionId", reviewId);
        data.put("bizId", reviewId);
        data.put("sceneType", scene);
        data.put("score", outcome.score());
        data.put("evaluation", outcome.evaluation());
        data.put("participantCount", participantIds.size() + 1);
        log.info("线下复盘完成：id={}, scene={}, score={}, 参与书友 {} 人",
                reviewId, scene, outcome.score(), participantIds.size());
        return data;
    }

    /** 角色解析：speakerMap 值 → user / contact(Mongo id) / unknown。 */
    private List<RoleSentence> resolveRoles(List<ReviewRequest.SentenceDto> raw,
                                            Map<String, String> speakerMap) {
        List<RoleSentence> rows = new ArrayList<>();
        for (ReviewRequest.SentenceDto s : raw) {
            if (s == null || s.getText() == null || s.getText().isBlank()) {
                continue;
            }
            String mapped = s.getSpeakerId() == null ? null : speakerMap.get(String.valueOf(s.getSpeakerId()));
            String role;
            String ref = null;
            if (AssistConstants.MAP_USER.equals(mapped)) {
                role = AssistConstants.ROLE_USER;
            } else if (mapped == null || AssistConstants.MAP_UNKNOWN.equals(mapped)) {
                role = AssistConstants.ROLE_UNKNOWN;
            } else {
                role = AssistConstants.ROLE_CONTACT;
                ref = mapped;
            }
            // 落库与入 LLM 前统一脱敏（单点处理：评分文本 / conversation / conversation_detail 三处同源一致）
            String text = maskPii ? TextMaskUtil.mask(s.getText().trim()) : s.getText().trim();
            rows.add(new RoleSentence(s.getSpeakerId(), role, ref, text,
                    s.getBeginTime(), s.getEndTime()));
        }
        if (rows.stream().noneMatch(r -> AssistConstants.ROLE_USER.equals(r.role()))) {
            throw new RuntimeException("未识别到「我」的发言内容");
        }
        return rows;
    }

    /**
     * 评分用对话文本。
     *
     * <p>只纳入 user 与 contact 归属的句子（unknown = 未识别/隐私丢弃，不计入评分）；
     * 多人沙龙追加一行说明，让模型知道「对方」是多个人。
     */
    private String buildReviewDialogueText(List<RoleSentence> rows, String scene, int otherSpeakerCount) {
        StringBuilder sb = new StringBuilder();
        if (AssistConstants.SCENE_SALON.equals(scene) && otherSpeakerCount > 1) {
            sb.append("（说明：本场为多人沙龙，\"对方\"包含多位参与者。")
              .append("请据此评估\"我\"在多人场景中的表现，尤其关注是否邀请沉默者、是否平衡发言。）\n");
        }
        for (RoleSentence r : rows) {
            if (AssistConstants.ROLE_UNKNOWN.equals(r.role())) {
                continue;
            }
            String speaker = AssistConstants.ROLE_USER.equals(r.role()) ? "我" : "对方";
            sb.append(speaker).append("：").append(r.text()).append("\n");
        }
        return sb.toString();
    }

    // ── 落库：MongoDB conversation（虚拟会话，承载评分与前端展示） ──

    private void saveConversation(String reviewId, ReviewRequest req, String scene,
                                  List<RoleSentence> rows, SimulationService.ScoringOutcome outcome) {
        ConversationDocument doc = new ConversationDocument();
        doc.setId(reviewId);
        doc.setTheme(req.getTitle() == null || req.getTitle().isBlank()
                ? AssistConstants.sceneCn(scene) : req.getTitle().trim());
        doc.setPersonality(OFFLINE_PERSONALITY);
        doc.setSystemPrompt("");
        doc.setScore(outcome.score());
        doc.setEvaluation(outcome.evaluation());
        doc.setOrigin(AssistConstants.ORIGIN_OFFLINE);
        doc.setUserId(defaultUserId);
        // 1 对 1 场景绑定书友（供亲密度联动）；沙龙不绑定单一书友
        if (AssistConstants.SCENE_ONE_ON_ONE.equals(scene)) {
            String contactId = req.getRelatedContactId();
            if (contactId == null || contactId.isBlank()) {
                contactId = rows.stream()
                        .filter(r -> AssistConstants.ROLE_CONTACT.equals(r.role()))
                        .map(RoleSentence::speakerRef)
                        .filter(java.util.Objects::nonNull)
                        .findFirst().orElse(null);
            }
            doc.setRelatedContactId(contactId);
        }

        long base = System.currentTimeMillis();
        List<ConversationDocument.MessageItem> messages = new ArrayList<>();
        int order = 1;
        for (RoleSentence r : rows) {
            // 只写入可归属发言：与评分用对话文本严格同源同序，
            // 保证 AI 返回的 evidence.messageOrder 能对回 messages[].messageOrder
            if (AssistConstants.ROLE_UNKNOWN.equals(r.role())) {
                continue;
            }
            ConversationDocument.MessageItem item = new ConversationDocument.MessageItem();
            item.setMessageOrder(order++);
            item.setRole(AssistConstants.ROLE_USER.equals(r.role()) ? "self" : "other");
            item.setAvatar("");
            item.setContent(r.text());
            item.setTimestamp(base + (r.beginTime() == null ? 0L : r.beginTime()));
            messages.add(item);
        }
        doc.setMessages(messages);

        LocalDateTime now = LocalDateTime.now();
        doc.setCreatedAt(now);
        doc.setUpdatedAt(now);
        conversationRepo.save(doc);
    }

    // ── 落库：MongoDB conversation_detail（逐句 + 说话人归属） ──

    private List<String> saveDetail(String reviewId, String scene, List<RoleSentence> rows) {
        List<ConversationDetailDocument.MessageItem> messages = new ArrayList<>();
        Set<String> participantIds = new LinkedHashSet<>();
        long durationMs = 0L;
        int seq = 1;
        LocalDateTime detailBase = LocalDateTime.now();

        for (RoleSentence r : rows) {
            ConversationDetailDocument.MessageItem item = new ConversationDetailDocument.MessageItem();
            item.setSeq(seq++);
            item.setSpeakerRole(r.role());
            item.setSpeakerRef(r.speakerRef());
            item.setContent(r.text());
            // annotation（keywords/emotion/intent/responds_to_seq）第一版置 null，不阻塞写入
            item.setAnnotation(null);
            long offset = r.beginTime() == null ? 0L : r.beginTime();
            item.setTs(detailBase.plusNanos(offset * 1_000_000L));
            if (r.endTime() != null) {
                durationMs = Math.max(durationMs, r.endTime());
            }
            messages.add(item);
            if (AssistConstants.ROLE_CONTACT.equals(r.role()) && r.speakerRef() != null) {
                participantIds.add(r.speakerRef());
            }
        }

        List<ConversationDetailDocument.Participant> participants = new ArrayList<>();
        for (String cid : participantIds) {
            // user 自己不列入 participants（对齐设计文档 §5）
            participants.add(new ConversationDetailDocument.Participant(
                    cid, AssistConstants.ROLE_CONTACT, null));
        }

        ConversationDetailDocument detail = new ConversationDetailDocument();
        detail.setId(reviewId);
        detail.setUserId(defaultUserId);
        detail.setType(AssistConstants.SOURCE_REAL);
        detail.setSceneType(scene);
        detail.setParticipants(participants);
        detail.setSimContext(null);
        detail.setMessages(messages);
        LocalDateTime now = LocalDateTime.now();
        detail.setCreatedAt(now);
        detail.setUpdatedAt(now);
        try {
            detailRepo.save(detail);
        } catch (Exception e) {
            log.error("conversation_detail 写入失败（不阻断复盘）: id={}, err={}", reviewId, e.getMessage());
        }
        return new ArrayList<>(participantIds);
    }

    // ── 落库：MySQL interaction_meta + evaluation_record ──

    private void saveMySql(String reviewId, String scene, ReviewRequest req,
                           List<RoleSentence> rows, List<String> participantIds,
                           SimulationService.ScoringOutcome outcome) {
        try {
            // MongoDB contact id → MySQL contact.id（BIGINT）映射；缺失则降级置 null
            List<Long> mysqlParticipantIds = new ArrayList<>();
            for (String cid : participantIds) {
                Long mid = mapToMySqlContactId(cid);
                if (mid != null) {
                    mysqlParticipantIds.add(mid);
                }
            }

            Long relatedContactId = null;
            if (AssistConstants.SCENE_ONE_ON_ONE.equals(scene)) {
                String cid = req.getRelatedContactId();
                if (cid == null || cid.isBlank()) {
                    cid = participantIds.isEmpty() ? null : participantIds.get(0);
                }
                relatedContactId = mapToMySqlContactId(cid);
            }

            long durationMs = rows.stream()
                    .filter(r -> r.endTime() != null)
                    .mapToLong(RoleSentence::endTime)
                    .max().orElse(0L);
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime startedAt = now.minusSeconds(durationMs / 1000);

            InteractionMeta meta = new InteractionMeta();
            meta.setBizId(reviewId);
            meta.setUserId(defaultUserId);
            meta.setSource(AssistConstants.SOURCE_REAL);
            meta.setSceneType(scene);
            meta.setRelatedContactId(relatedContactId);
            meta.setParticipantContactIds(mysqlParticipantIds.isEmpty() ? null
                    : objectMapper.writeValueAsString(mysqlParticipantIds));
            meta.setStartedAt(startedAt);
            meta.setEndedAt(now);
            meta.setDurationSeconds((int) (durationMs / 1000));
            meta.setParticipantCount(participantIds.size() + 1);
            meta.setProcessStatus(1);
            meta.setCreatedAt(now);
            meta.setUpdatedAt(now);
            InteractionMeta savedMeta = interactionMetaRepo.save(meta);

            ConversationDocument.Evaluation ev = outcome.evaluation();
            EvaluationRecord record = new EvaluationRecord();
            record.setUserId(defaultUserId);
            record.setSource(AssistConstants.SOURCE_REAL);
            record.setSceneType(scene);
            record.setScoreClarity(ev.getClarity());
            record.setScoreLogicality(ev.getLogicality());
            record.setScoreEmpathyListening(ev.getEmpathyListening());
            record.setScoreInteractivity(ev.getInteractivity());
            record.setScoreRelaxation(ev.getRelaxation());
            record.setScoreOverall(outcome.score());
            record.setAiAnalysisText(ev.getComment());
            record.setSelfRelative(null);
            record.setSelfState(null);
            record.setSelfComment(null);
            record.setRelatedContactId(relatedContactId);
            record.setInteractionId(savedMeta.getId());
            record.setCreatedAt(now);
            record.setUpdatedAt(now);
            evaluationRecordRepo.save(record);

            log.info("MySQL 中枢表写入完成：biz_id={}, interaction_id={}, 参与书友(MySQL)={}",
                    reviewId, savedMeta.getId(), mysqlParticipantIds.size());
        } catch (Exception e) {
            // MySQL 不可用/未建表时不阻断复盘主链路（红线 2：任何失败降级）
            log.error("MySQL 中枢表写入失败（不阻断复盘）: id={}, err={}", reviewId, e.getMessage());
        }
    }

    /** MongoDB contact id → MySQL contact.id；contact 不存在或同步失败返回 null。 */
    private Long mapToMySqlContactId(String mongoContactId) {
        if (mongoContactId == null || mongoContactId.isBlank()) {
            return null;
        }
        try {
            ContactDocument doc = contactRepo.findById(mongoContactId).orElse(null);
            if (doc == null) {
                log.warn("联系人 {} 在 MongoDB 中不存在，MySQL 侧降级置 null", mongoContactId);
                return null;
            }
            ContactEntity entity = contactService.saveOrUpdate(doc, defaultUserId);
            return entity == null ? null : entity.getId();
        } catch (Exception e) {
            log.warn("联系人 {} 映射到 MySQL 失败：{}", mongoContactId, e.getMessage());
            return null;
        }
    }
}
