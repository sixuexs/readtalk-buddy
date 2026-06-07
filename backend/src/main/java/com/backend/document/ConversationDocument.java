package com.backend.document;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Document("conversation")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConversationDocument {

    @Id
    private String id;                     // sessionId (UUID)

    private String theme;                  // 场景主题
    private String personality;            // 角色性格
    private String systemPrompt;           // 完整的系统 prompt

    private Integer score;                 // 社交能力评分 (0-100)，null 表示未评分

    private List<MessageItem> messages = new ArrayList<>();

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * 内嵌消息子文档
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MessageItem {
        private int messageOrder;          // 会话内消息序号 (1, 2, 3...)
        private String role;               // "self" 或 "other"
        private String avatar;             // 头像地址（预留）
        private String content;            // 消息内容
        private long timestamp;            // 毫秒时间戳
    }
}
