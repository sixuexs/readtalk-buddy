package com.backend.model;

import com.backend.document.ConversationDocument;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 会话摘要 —— 用于社交记录列表
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SessionSummary {
    private String sessionId;
    private String theme;
    private String personality;
    private Integer score;
    private int messageCount;
    private long lastActivity;

    // 详细评分（维度分 + 评语 + 标签）
    private EvaluationSummary evaluation;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EvaluationSummary {
        private int expression;
        private int affinity;
        private int logic;
        private String comment;
        private List<String> strengths;
        private List<String> suggestions;

        public static EvaluationSummary fromDocument(ConversationDocument.Evaluation eval) {
            if (eval == null) return null;
            return new EvaluationSummary(
                    eval.getExpression(), eval.getAffinity(), eval.getLogic(),
                    eval.getComment(), eval.getStrengths(), eval.getSuggestions()
            );
        }
    }
}
