package com.backend.util;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.time.temporal.WeekFields;

/**
 * ISO-8601 周键工具（P2-1）：weekKey = 周基年 + "-W" + 两位周序（如 2026-W38），周一为始。
 * <p>周键定长，字典序即时间序（{@link #isBefore}）。跨年归属一律以 {@link WeekFields#ISO} 为准。
 */
public final class WeekKeyUtil {

    private WeekKeyUtil() {}

    private static final WeekFields WF = WeekFields.ISO;

    /** 日期 → ISO 周键（如 2026-09-14 → "2026-W38"；2027-01-01 → "2026-W53"）。 */
    public static String weekKeyOf(LocalDate date) {
        return String.format("%d-W%02d", date.get(WF.weekBasedYear()), date.get(WF.weekOfWeekBasedYear()));
    }

    public static String currentWeekKey() {
        return weekKeyOf(LocalDate.now());
    }

    /** 该 ISO 周的周一（1 月 4 日恒在第 1 周，据此定位）。 */
    public static LocalDate weekStart(String weekKey) {
        int year = Integer.parseInt(weekKey.substring(0, 4));
        int week = Integer.parseInt(weekKey.substring(weekKey.indexOf('W') + 1));
        return LocalDate.of(year, 1, 4)
                .with(WF.weekOfWeekBasedYear(), week)
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    /** 该 ISO 周的周日。 */
    public static LocalDate weekEnd(String weekKey) {
        return weekStart(weekKey).plusDays(6);
    }

    /** 周键先后比较（定长格式，字典序==时间序）。 */
    public static boolean isBefore(String a, String b) {
        return a.compareTo(b) < 0;
    }
}
