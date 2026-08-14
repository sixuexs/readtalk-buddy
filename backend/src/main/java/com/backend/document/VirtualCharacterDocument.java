package com.backend.document;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 虚拟人物 —— 情景模拟域下的完整名片。
 *
 * 供情景模拟与破冰分析复用：破冰"对方名片"可选虚拟人物，模拟训练可选虚拟人物角色。
 */
@Document("virtual_character")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VirtualCharacterDocument {

    @Id
    private String id;

    private String name;                        // 人物名
    private String personality;                 // 性格描述
    private List<String> interests = new ArrayList<>();  // 兴趣爱好
    private List<String> labels = new ArrayList<>();     // 身份/标签
    private String description;                 // 人物简介（可选）

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
