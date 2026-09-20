package com.xtdpotato.xero_delta.client;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

/** Gregorian to Chinese lunar calendar conversion for the supported trading-calendar range. */
public final class ChineseLunarCalendar {
    private static final int MIN_YEAR = 1901;
    private static final LocalDate BASE_DATE = LocalDate.of(1901, 2, 19);
    private static final int[] YEAR_INFO = {
        0x04AE0, 0x0A570, 0x054D5, 0x0D260, 0x0D950, 0x16554, 0x056A0, 0x09AD0, 0x055D2, 0x04AE0,
        0x0A5B6, 0x0A4D0, 0x0D250, 0x1D255, 0x0B540, 0x0D6A0, 0x0ADA2, 0x095B0, 0x14977, 0x04970,
        0x0A4B0, 0x0B4B5, 0x06A50, 0x06D40, 0x1AB54, 0x02B60, 0x09570, 0x052F2, 0x04970, 0x06566,
        0x0D4A0, 0x0EA50, 0x16A95, 0x05AD0, 0x02B60, 0x186E3, 0x092E0, 0x1C8D7, 0x0C950, 0x0D4A0,
        0x1D8A6, 0x0B550, 0x056A0, 0x1A5B4, 0x025D0, 0x092D0, 0x0D2B2, 0x0A950, 0x0B557, 0x06CA0,
        0x0B550, 0x15355, 0x04DA0, 0x0A5B0, 0x14573, 0x052B0, 0x0A9A8, 0x0E950, 0x06AA0, 0x0AEA6,
        0x0AB50, 0x04B60, 0x0AAE4, 0x0A570, 0x05260, 0x0F263, 0x0D950, 0x05B57, 0x056A0, 0x096D0,
        0x04DD5, 0x04AD0, 0x0A4D0, 0x0D4D4, 0x0D250, 0x0D558, 0x0B540, 0x0B6A0, 0x195A6, 0x095B0,
        0x049B0, 0x0A974, 0x0A4B0, 0x0B27A, 0x06A50, 0x06D40, 0x0AF46, 0x0AB60, 0x09570, 0x04AF5,
        0x04970, 0x064B0, 0x074A3, 0x0EA50, 0x06B58, 0x05AC0, 0x0AB60, 0x096D5, 0x092E0, 0x0C960,
        0x0D954, 0x0D4A0, 0x0DA50, 0x07552, 0x056A0, 0x0ABB7, 0x025D0, 0x092D0, 0x0CAB5, 0x0A950,
        0x0B4A0, 0x0BAA4, 0x0AD50, 0x055D9, 0x04BA0, 0x0A5B0, 0x15176, 0x052B0, 0x0A930, 0x07954,
        0x06AA0, 0x0AD50, 0x05B52, 0x04B60, 0x0A6E6, 0x0A4E0, 0x0D260, 0x0EA65, 0x0D530, 0x05AA0,
        0x076A3, 0x096D0, 0x04AFB, 0x04AD0, 0x0A4D0, 0x1D0B6, 0x0D250, 0x0D520, 0x0DD45, 0x0B5A0,
        0x056D0, 0x055B2, 0x049B0, 0x0A577, 0x0A4B0, 0x0AA50, 0x1B255, 0x06D20, 0x0ADA0, 0x14B63,
        0x09370, 0x049F8, 0x04970, 0x064B0, 0x168A6, 0x0EA50, 0x06B20, 0x1A6C4, 0x0AAE0, 0x092E0,
        0x0D2E3, 0x0C960, 0x0D557, 0x0D4A0, 0x0DA50, 0x05D55, 0x056A0, 0x0A6D0, 0x055D4, 0x052D0,
        0x0A9B8, 0x0A950, 0x0B4A0, 0x0B6A6, 0x0AD50, 0x055A0, 0x0ABA4, 0x0A5B0, 0x052B0, 0x0B273,
        0x06930, 0x07337, 0x06AA0, 0x0AD50, 0x14B55, 0x04B60, 0x0A570, 0x054E4, 0x0D260, 0x0E968,
        0x0D520, 0x0DAA0, 0x16AA6, 0x056D0, 0x04AE0, 0x0A9D4, 0x0A4D0, 0x0D150, 0x0F252, 0x0D520
    };
    private static final String[] MONTH_NAMES = {
        "", "正月", "二月", "三月", "四月", "五月", "六月",
        "七月", "八月", "九月", "十月", "冬月", "腊月"
    };
    private static final String[] DAY_NAMES = {
        "", "初一", "初二", "初三", "初四", "初五", "初六", "初七", "初八", "初九", "初十",
        "十一", "十二", "十三", "十四", "十五", "十六", "十七", "十八", "十九", "二十",
        "廿一", "廿二", "廿三", "廿四", "廿五", "廿六", "廿七", "廿八", "廿九", "三十"
    };
    private static final String[] SOLAR_TERM_NAMES = {
        "小寒", "大寒", "立春", "雨水", "惊蛰", "春分",
        "清明", "谷雨", "立夏", "小满", "芒种", "夏至",
        "小暑", "大暑", "立秋", "处暑", "白露", "秋分",
        "寒露", "霜降", "立冬", "小雪", "大雪", "冬至"
    };
    private static final int[] SOLAR_TERM_MINUTES = {
        0, 21208, 42467, 63836, 85337, 107014,
        128867, 150921, 173149, 195551, 218072, 240693,
        263343, 285989, 308563, 331033, 353350, 375494,
        397447, 419210, 440795, 462224, 483532, 504758
    };
    private static final long SOLAR_TERM_BASE_MILLIS =
        Instant.parse("1900-01-06T02:05:00Z").toEpochMilli();
    private static final double TROPICAL_YEAR_MILLIS = 31_556_925_974.7D;
    private static final LocalDate MAX_DATE = calculateMaxDate();

    private ChineseLunarCalendar() {
    }

    public static boolean supports(LocalDate date) {
        return date != null && !date.isBefore(BASE_DATE) && !date.isAfter(MAX_DATE);
    }

    public static LunarDate from(LocalDate date) {
        if (!supports(date)) {
            throw new IllegalArgumentException("Supported date range: " + BASE_DATE + " to " + MAX_DATE);
        }
        long offset = ChronoUnit.DAYS.between(BASE_DATE, date);
        int lunarYear = MIN_YEAR;
        while (lunarYear < MIN_YEAR + YEAR_INFO.length) {
            int yearDays = yearDays(lunarYear);
            if (offset < yearDays) break;
            offset -= yearDays;
            lunarYear++;
        }
        int leapMonth = leapMonth(lunarYear);
        for (int month = 1; month <= 12; month++) {
            int days = monthDays(lunarYear, month);
            if (offset < days) return create(date, lunarYear, month, (int) offset + 1, false);
            offset -= days;
            if (leapMonth == month) {
                int leapDays = leapDays(lunarYear);
                if (offset < leapDays) return create(date, lunarYear, month, (int) offset + 1, true);
                offset -= leapDays;
            }
        }
        throw new IllegalStateException("Unable to convert date " + date);
    }

    private static LunarDate create(LocalDate date, int year, int month, int day, boolean leap) {
        String solarTerm = solarTerm(date);
        return new LunarDate(year, month, day, leap, MONTH_NAMES[month], DAY_NAMES[day], solarTerm);
    }

    public static String solarTerm(LocalDate date) {
        int first = (date.getMonthValue() - 1) * 2;
        for (int index = first; index <= first + 1; index++) {
            long millis = SOLAR_TERM_BASE_MILLIS
                + (long) ((date.getYear() - 1900) * TROPICAL_YEAR_MILLIS)
                + SOLAR_TERM_MINUTES[index] * 60_000L;
            LocalDate termDate = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate();
            if (termDate.equals(date)) return SOLAR_TERM_NAMES[index];
        }
        return "";
    }

    private static int yearDays(int year) {
        int days = 0;
        for (int month = 1; month <= 12; month++) days += monthDays(year, month);
        return days + leapDays(year);
    }

    private static int monthDays(int year, int month) {
        int info = yearInfo(year);
        return (info & (0x10000 >> month)) != 0 ? 30 : 29;
    }

    private static int leapMonth(int year) {
        return yearInfo(year) & 0xF;
    }

    private static int leapDays(int year) {
        if (leapMonth(year) == 0) return 0;
        return (yearInfo(year) & 0x10000) != 0 ? 30 : 29;
    }

    private static int yearInfo(int year) {
        int index = year - MIN_YEAR;
        if (index < 0 || index >= YEAR_INFO.length) {
            throw new IllegalArgumentException("Unsupported lunar year " + year);
        }
        return YEAR_INFO[index];
    }

    private static LocalDate calculateMaxDate() {
        long days = 0L;
        for (int year = MIN_YEAR; year < MIN_YEAR + YEAR_INFO.length; year++) days += yearDays(year);
        return BASE_DATE.plusDays(days - 1L);
    }

    public record LunarDate(int year, int month, int day, boolean leapMonth,
                            String monthName, String dayName, String solarTerm) {
        public String displayLabel() {
            if (!solarTerm.isBlank()) return solarTerm;
            if (day == 1) return (leapMonth ? "闰" : "") + monthName;
            return dayName;
        }
    }
}
