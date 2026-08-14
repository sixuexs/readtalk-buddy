package com.backend.model;

import lombok.Data;

@Data
public class StartRequest {
    private String theme;
    private String personality;
    /** 练习对象（书友）的 MongoDB contacts id，可选；为空表示纯能力训练，不计入亲密度 */
    private String relatedContactId;
}
