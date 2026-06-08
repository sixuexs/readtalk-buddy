package com.backend.store;

import com.backend.model.ChatMessage;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

// 内存会话存储：key 为 sessionId，value 为消息列表
@Component
public class ConversationStore {

    private final ConcurrentHashMap<String, List<ChatMessage>> store = new ConcurrentHashMap<>();

    public void put(String sessionId, List<ChatMessage> messages) {
        store.put(sessionId, messages);
    }

    public List<ChatMessage> get(String sessionId) {
        return store.getOrDefault(sessionId, new ArrayList<>());
    }

    public void append(String sessionId, ChatMessage message) {
        store.computeIfAbsent(sessionId, k -> new ArrayList<>()).add(message);
    }
}
