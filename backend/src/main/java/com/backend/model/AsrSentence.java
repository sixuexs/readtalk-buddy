package com.backend.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ASR 转写分句结果（阿里云 qwen-audio-3.1-asr-flash 同步版输出）。
 *
 * <p>speakerId 可能为 null：未开启说话人分离、或该句未分离出说话人时。
 * 前端按 speakerId 分组做归属确认，null 归入 unknown。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AsrSentence {

    /** 说话人编号（0 起始）；null = 未识别出说话人。 */
    private Integer speakerId;

    /** 该句文本。 */
    private String text;

    /** 句开始时间（毫秒，相对音频起点）。 */
    private Long beginTime;

    /** 句结束时间（毫秒）。 */
    private Long endTime;
}
