package com.xtdpotato.xero_delta.client;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChineseLunarCalendarTest {
    @Test
    void convertsChineseNewYear() {
        ChineseLunarCalendar.LunarDate lunar =
            ChineseLunarCalendar.from(LocalDate.of(2024, 2, 10));

        assertEquals(2024, lunar.year());
        assertEquals(1, lunar.month());
        assertEquals(1, lunar.day());
        assertFalse(lunar.leapMonth());
        assertEquals("正月", lunar.displayLabel());
    }

    @Test
    void convertsOrdinarySummerDate() {
        ChineseLunarCalendar.LunarDate lunar =
            ChineseLunarCalendar.from(LocalDate.of(2026, 7, 16));

        assertEquals(2026, lunar.year());
        assertEquals(6, lunar.month());
        assertEquals(3, lunar.day());
        assertFalse(lunar.leapMonth());
        assertEquals("初三", lunar.displayLabel());
    }

    @Test
    void convertsLeapMonthDate() {
        ChineseLunarCalendar.LunarDate lunar =
            ChineseLunarCalendar.from(LocalDate.of(2025, 7, 25));

        assertEquals(6, lunar.month());
        assertEquals(1, lunar.day());
        assertTrue(lunar.leapMonth());
        assertEquals("闰六月", lunar.displayLabel());
    }

    @Test
    void solarTermsOverrideTheCompactLunarLabel() {
        assertEquals("清明", ChineseLunarCalendar.from(LocalDate.of(2024, 4, 4)).displayLabel());
        assertEquals("夏至", ChineseLunarCalendar.from(LocalDate.of(2024, 6, 21)).displayLabel());
        assertEquals("冬至", ChineseLunarCalendar.from(LocalDate.of(2024, 12, 21)).displayLabel());
    }
}
