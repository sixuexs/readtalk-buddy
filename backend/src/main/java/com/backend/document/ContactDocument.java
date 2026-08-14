package com.backend.document;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 关系人 —— 关系图谱和破冰分析的数据基础
 */
@Document("contacts")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ContactDocument {

    @Id
    private String id;                          // 自动生成

    // 基本信息
    private String name;                        // 姓名
    private String relationType;                // 关系类型：家人/朋友/同事/同学
    private String avatar;                      // 头像首字母或URL

    // 名片信息（扫码连接时交换）
    private List<String> interests = new ArrayList<>();  // 兴趣爱好
    private List<String> labels = new ArrayList<>();     // 身份标签
    private String personality;                 // 性格描述
    private String note;                        // 备注

    // 关系运维数据
    private int intimacy;                       // 亲密度 (0-100)
    private LocalDate birthday;                 // 生日
    private int lastContactDays;                // 最近联系距今多少天
    private List<InteractionRecord> interactions = new ArrayList<>();

    // 预警数据
    private boolean warning;                    // 是否需要预警
    private boolean recovering;                 // 是否正在挽救中
    private LocalDateTime warningTime;          // 预警时间
    private boolean suppressWarning;            // 是否暂时抑制预警
    private LocalDateTime warningDismissedAt;   // 暂不提醒时间（7天冷却），null 表示未冷却

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InteractionRecord {
        private String type;                    // 互动类型：聊天/见面/电话
        private String summary;                 // 简要描述
        private LocalDateTime time;
    }
}
