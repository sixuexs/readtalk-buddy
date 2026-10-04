package com.backend.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 线下复盘提交请求 —— 前端归属确认后回传。
 *
 * <p>speakerMap: 说话人编号（字符串）→ "user"（是我）| "unknown"（陌生人/未识别）| MongoDB contact id（某书友）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReviewRequest {

    /** 场景类型：one_on_one / salon（白名单外降级为 one_on_one）。 */
    private String sceneType;

    /** 1 对 1 场景关联的书友 MongoDB id；沙龙为 null。 */
    private String relatedContactId;

    /** 展示标题（可选，为空则用场景中文名）。 */
    private String title;

    /** 归属确认后的分句。 */
    private List<SentenceDto> sentences;

    /** 说话人归属映射。 */
    private Map<String, String> speakerMap;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SentenceDto {
        private Integer speakerId;
        private String text;
        private Long beginTime;
        private Long endTime;
    }
}
