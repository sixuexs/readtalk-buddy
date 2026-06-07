package com.backend.model;

import com.backend.document.ConversationDocument;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessage {
    private String id;
    private String role;       // "self" 或 "other"
    private String avatar;
    private String content;
    private long timestamp;

    /**
     * 从 MongoDB 内嵌消息文档转换为 API DTO
     * @param item 消息子文档
     * @return ChatMessage（id = messageOrder 的字符串形式）
     */
    public static ChatMessage fromMessageItem(ConversationDocument.MessageItem item) {
        return new ChatMessage(
                String.valueOf(item.getMessageOrder()),
                item.getRole(),
                item.getAvatar(),
                item.getContent(),
                item.getTimestamp()
        );
    }
}
