package com.backend.util;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * P2-1 ISO 周键工具单测（含跨年用例，WeekFields.ISO 语义为准）。
 */
class WeekKeyUtilTest {

    @Test
    void weekKeyOfKnownDates() {
        assertEquals("2026-W38", WeekKeyUtil.weekKeyOf(LocalDate.of(2026, 9, 14)));
        assertEquals("2026-W01", WeekKeyUtil.weekKeyOf(LocalDate.of(2025, 12, 29))); // 上周日属 2026-W01 前？12-29 是周一
        assertEquals("2026-W53", WeekKeyUtil.weekKeyOf(LocalDate.of(2026, 12, 28))); // 跨年：2026 有第 53 周
        assertEquals("2026-W53", WeekKeyUtil.weekKeyOf(LocalDate.of(2027, 1, 1)));   // 元旦属上一年末周
    }

    @Test
    void weekStartEnd() {
        assertEquals(LocalDate.of(2026, 9, 14), WeekKeyUtil.weekStart("2026-W38")); // 周一
        assertEquals(LocalDate.of(2026, 9, 20), WeekKeyUtil.weekEnd("2026-W38"));   // 周日
        assertEquals(LocalDate.of(2026, 12, 28), WeekKeyUtil.weekStart("2026-W53"));
    }

    @Test
    void roundTrip() {
        for (LocalDate d : new LocalDate[]{LocalDate.of(2026, 1, 1), LocalDate.of(2026, 9, 14),
                LocalDate.of(2026, 12, 31), LocalDate.of(2027, 3, 5)}) {
            String key = WeekKeyUtil.weekKeyOf(d);
            LocalDate start = WeekKeyUtil.weekStart(key);
            assertTrue(!d.isBefore(start) && !d.isAfter(start.plusDays(6)),
                    d + " 应落在 " + key + " 周内（start=" + start + "）");
            assertEquals(key, WeekKeyUtil.weekKeyOf(start));
        }
    }

    @Test
    void ordering() {
        assertTrue(WeekKeyUtil.isBefore("2026-W38", "2026-W39"));
        assertTrue(WeekKeyUtil.isBefore("2026-W53", "2027-W01"));
        assertFalse(WeekKeyUtil.isBefore("2027-W01", "2026-W53"));
    }
}
