package com.backend.model;

import lombok.Data;

@Data
public class StartRequest {
    private String theme;
    private String personality;
    /** 练习对象（书友）的 MongoDB contacts id，可选；为空表示纯能力训练，不计入亲密度 */
    private String relatedContactId;
    /** P2-7 靶向训练：焦点子技能短名（SubSkillConstants 白名单），非法/为空按自由训练 */
    private String focusSubSkill;
    /** P2-7 靶向训练：关联处方任务 id（可空），评分事件自动回销用 */
    private String prescriptionTaskId;
}
