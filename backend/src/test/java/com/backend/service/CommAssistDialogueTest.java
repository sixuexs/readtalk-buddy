package com.backend.service;

import com.backend.model.ChatMessage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 线下复盘复用评分引擎的对话文本构建单测（无 LLM、无 Spring 上下文）。
 *
 * <p>回归目标：scoreConversation 抽取 scoreDialogueText 后，
 * 对话文本的角色映射（self→我 / other→对方）与逐行格式保持不变，
 * 保证线下复盘与情景模拟评分口径一致。
 */
class CommAssistDialogueTest {

    private static ChatMessage msg(String role, String content) {
        return new ChatMessage("1", role, "", content, 0L);
    }

    @Test
    void mapsSelfToMeAndOtherToCounterpart() {
        String text = SimulationService.buildDialogueText(List.of(
                msg("other", "你最近在读什么书？"),
                msg("self", "我在看一本社会心理学的书。")));

        assertEquals("对方：你最近在读什么书？\n我：我在看一本社会心理学的书。\n", text);
    }

    @Test
    void eachMessageOnOwnLineInOrder() {
        String text = SimulationService.buildDialogueText(List.of(
                msg("self", "A"), msg("other", "B"), msg("self", "C")));

        assertEquals("我：A\n对方：B\n我：C\n", text);
        assertEquals(3, text.split("\n").length);
    }

    @Test
    void emptyHistoryGivesEmptyText() {
        assertEquals("", SimulationService.buildDialogueText(List.of()));
    }
}
