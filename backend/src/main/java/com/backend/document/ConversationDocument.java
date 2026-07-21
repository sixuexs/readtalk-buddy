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

    private Evaluation evaluation;         // 详细评分（维度分 + 评语 + 标签）

    private List<MessageItem> messages = new ArrayList<>();

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * 评分详情
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    /* 5维评分: clarity/清晰度, logicality/逻辑性, empathyListening/共情倾听, interactivity/互动性, relaxation/松弛感 */
    public static class Evaluation {
        private int clarity;
        private int logicality;
        private int empathyListening;
        private int interactivity;
        private int relaxation;
        private String comment;            // AI 评语
        private List<String> strengths;    // 优点标签
        private List<String> suggestions;  // 改进建议标签
    }

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
