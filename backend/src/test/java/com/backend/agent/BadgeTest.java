package com.backend.agent;

import com.backend.document.UserProfileDocument.BadgeRecord;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * P3-3 徽章 upsert 纯函数单测。
 */
class BadgeTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 14, 12, 0);

    @Test
    void boundaries() {
        List<BadgeRecord> badges = new ArrayList<>();
        assertFalse(UserProfileTools.applyBadge(badges, "emotion_naming", 79.5, NOW), "80 以下不达成");
        assertTrue(badges.isEmpty());
        assertTrue(UserProfileTools.applyBadge(badges, "emotion_naming", 80, NOW), "恰 80 → Lv1");
        assertEquals(1, badges.get(0).getLevel());
        assertTrue(UserProfileTools.applyBadge(badges, "emotion_naming", 90, NOW), "恰 90 → Lv2");
        assertEquals(2, badges.get(0).getLevel());
    }

    @Test
    void upsertNoDuplicateAndNoDowngrade() {
        List<BadgeRecord> badges = new ArrayList<>();
        UserProfileTools.applyBadge(badges, "paraphrase", 82, NOW);
        assertFalse(UserProfileTools.applyBadge(badges, "paraphrase", 81, NOW.plusDays(1)), "同级不重复触发");
        assertFalse(UserProfileTools.applyBadge(badges, "paraphrase", 50, NOW.plusDays(2)), "状态回落不回收/不降级");
        assertEquals(1, badges.size());
        assertEquals(1, badges.get(0).getLevel());
    }

    @Test
    void achievedAtKeepsFirstTimeAndUpgradeStamps() {
        List<BadgeRecord> badges = new ArrayList<>();
        UserProfileTools.applyBadge(badges, "turn_giving", 85, NOW);
        BadgeRecord b = badges.get(0);
        assertEquals(NOW, b.getAchievedAt());
        UserProfileTools.applyBadge(badges, "turn_giving", 95, NOW.plusDays(3));
        assertEquals(NOW, b.getAchievedAt(), "achievedAt 保持首次");
        assertEquals(NOW.plusDays(3), b.getLastUpgradedAt());
        assertEquals(2, b.getLevel());
    }
}
