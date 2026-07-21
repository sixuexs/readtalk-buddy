package com.backend.document;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;

@Document("conversation_detail")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConversationDetailDocument {

    @Id
    private String id;                     // interaction_meta.biz_id

    private Long userId;
    private Integer type;                  // 1=real, 2=simulation
    private String sceneType;
    private List<Participant> participants;
    private Object simContext;             // JsonNode or Map
    private List<MessageItem> messages;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Participant {
        private Long refId;
        private String role;
        private String tag;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MessageItem {
        private int seq;
        private LocalDateTime ts;
        private String speakerRole;
        private Long speakerRef;
        private String content;
        private String annotation;
    }
}
