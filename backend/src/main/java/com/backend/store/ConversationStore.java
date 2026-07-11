package com.backend.store;

import com.backend.document.ConversationDocument;
import com.backend.document.ConversationDocument.MessageItem;
import com.backend.model.ChatMessage;
import com.backend.model.SessionSummary;
import com.backend.repository.ConversationRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * MongoDB 会话存储 —— 替代原 ConcurrentHashMap 实现
 */
@Component
public class ConversationStore {

    private final ConversationRepository repo;

    public ConversationStore(ConversationRepository repo) {
        this.repo = repo;
    }

    /**
     * 创建新会话，保存第一条消息和 systemPrompt
     */
    public void createSession(String sessionId, String theme, String personality,
                              String systemPrompt, ChatMessage firstMessage) {
        ConversationDocument doc = new ConversationDocument();
        doc.setId(sessionId);
        doc.setTheme(theme);
        doc.setPersonality(personality);
        doc.setSystemPrompt(systemPrompt);

        MessageItem item = toMessageItem(firstMessage, 1);
        doc.setMessages(new ArrayList<>(List.of(item)));

        LocalDateTime now = LocalDateTime.now();
        doc.setCreatedAt(now);
        doc.setUpdatedAt(now);

        repo.save(doc);
    }

    /**
     * 获取会话的所有消息（转为 API DTO）
     */
    public List<ChatMessage> getMessages(String sessionId) {
        Optional<ConversationDocument> doc = repo.findById(sessionId);
        if (doc.isEmpty()) {
            return new ArrayList<>();
        }
        return doc.get().getMessages().stream()
                .map(ChatMessage::fromMessageItem)
                .collect(Collectors.toList());
    }

    /**
     * 向会话追加一条消息
     */
    public void appendMessage(String sessionId, ChatMessage message) {
        Optional<ConversationDocument> opt = repo.findById(sessionId);
        if (opt.isEmpty()) {
            return;
        }
        ConversationDocument doc = opt.get();
        int nextOrder = doc.getMessages().size() + 1;
        MessageItem item = toMessageItem(message, nextOrder);
        doc.getMessages().add(item);
        doc.setUpdatedAt(LocalDateTime.now());
        repo.save(doc);
    }

    /**
     * 获取会话的 systemPrompt（修复之前硬编码 prompt 的问题）
     */
    public String getSystemPrompt(String sessionId) {
        return repo.findById(sessionId)
                .map(ConversationDocument::getSystemPrompt)
                .orElse("你正在参与一个情景模拟对话。请自然、友好地回复对方，保持对话流畅。");
    }

    /**
     * 检查会话是否存在
     */
    public boolean sessionExists(String sessionId) {
        return repo.existsById(sessionId);
    }

    /**
     * 获取所有会话的摘要列表（按创建时间倒序）
     */
    public List<SessionSummary> getSessionSummaries() {
        List<ConversationDocument> docs = repo.findAllByOrderByCreatedAtDesc();
        return docs.stream()
                .map(doc -> {
                    SessionSummary s = new SessionSummary();
                    s.setSessionId(doc.getId());
                    s.setTheme(doc.getTheme());
                    s.setPersonality(doc.getPersonality());
                    s.setScore(doc.getScore());
                    s.setMessageCount(doc.getMessages().size());
                    s.setLastActivity(doc.getMessages().isEmpty() ? 0 :
                            doc.getMessages().get(doc.getMessages().size() - 1).getTimestamp());
                    s.setEvaluation(SessionSummary.EvaluationSummary.fromDocument(doc.getEvaluation()));
                    return s;
                })
                .collect(Collectors.toList());
    }

    /**
     * 保存评分结果
     */
    /**
     * 获取评分（如果有）
     */
    public Optional<Integer> getScore(String sessionId) {
        return repo.findById(sessionId).map(ConversationDocument::getScore);
    }

    /**
     * 获取详细评分（如果有）
     */
    public Optional<ConversationDocument.Evaluation> getEvaluation(String sessionId) {
        return repo.findById(sessionId).map(ConversationDocument::getEvaluation);
    }

    public void saveScore(String sessionId, int score, ConversationDocument.Evaluation evaluation) {
        repo.findById(sessionId).ifPresent(doc -> {
            doc.setScore(score);
            doc.setEvaluation(evaluation);
            doc.setUpdatedAt(LocalDateTime.now());
            repo.save(doc);
        });
    }

    private MessageItem toMessageItem(ChatMessage dto, int order) {
        MessageItem item = new MessageItem();
        item.setMessageOrder(order);
        item.setRole(dto.getRole());
        item.setAvatar(dto.getAvatar() != null ? dto.getAvatar() : "");
        item.setContent(dto.getContent());
        item.setTimestamp(dto.getTimestamp());
        return item;
    }
}
