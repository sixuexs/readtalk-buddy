package com.backend.agent;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * P1-4 streak 纯函数单测（无 Spring 上下文）。
 */
class StreakTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 14);

    @Test
    void yesterdayContinuesStreak() {
        int[] r = UserProfileTools.computeStreak(TODAY.minusDays(1), 3, 5, TODAY);
        assertEquals(4, r[0]);
        assertEquals(5, r[1], "best 不低于历史最高");
    }

    @Test
    void gapResetsTo1() {
        int[] r = UserProfileTools.computeStreak(TODAY.minusDays(3), 7, 7, TODAY);
        assertEquals(1, r[0]);
        assertEquals(7, r[1], "断签不降 best");
    }

    @Test
    void firstEverActivity() {
        int[] r = UserProfileTools.computeStreak(null, 0, 0, TODAY);
        assertEquals(1, r[0]);
        assertEquals(1, r[1]);
    }

    @Test
    void newRecordUpdatesBest() {
        int[] r = UserProfileTools.computeStreak(TODAY.minusDays(1), 9, 9, TODAY);
        assertEquals(10, r[0]);
        assertEquals(10, r[1]);
    }
}
