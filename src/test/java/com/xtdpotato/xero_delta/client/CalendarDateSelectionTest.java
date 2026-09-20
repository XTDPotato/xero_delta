package com.xtdpotato.xero_delta.client;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CalendarDateSelectionTest {
    private static final LocalDate JULY_10 = LocalDate.of(2026, 7, 10);

    @Test
    void plainClickReplacesTheCurrentSelection() {
        Set<LocalDate> selected = new LinkedHashSet<>();
        selected.add(JULY_10.minusDays(2L));
        LocalDate anchor = CalendarDateSelection.select(selected, null, JULY_10, false, false);
        assertEquals(Set.of(JULY_10), selected);
        assertEquals(JULY_10, anchor);
    }

    @Test
    void controlClickAddsAndRemovesIndividualDates() {
        Set<LocalDate> selected = new LinkedHashSet<>();
        CalendarDateSelection.select(selected, null, JULY_10, true, false);
        assertTrue(selected.contains(JULY_10));
        CalendarDateSelection.select(selected, JULY_10, JULY_10, true, false);
        assertFalse(selected.contains(JULY_10));
    }

    @Test
    void shiftClickSelectsAnInclusiveRangeFromTheAnchor() {
        Set<LocalDate> selected = new LinkedHashSet<>();
        selected.add(JULY_10);
        LocalDate anchor = CalendarDateSelection.select(selected, JULY_10,
            JULY_10.plusDays(3L), false, true);
        assertEquals(4, selected.size());
        assertTrue(selected.contains(JULY_10));
        assertTrue(selected.contains(JULY_10.plusDays(3L)));
        assertEquals(JULY_10, anchor);
    }

    @Test
    void controlShiftAddsTheRangeToExistingDates() {
        Set<LocalDate> selected = new LinkedHashSet<>();
        LocalDate existing = JULY_10.minusDays(5L);
        selected.add(existing);
        CalendarDateSelection.select(selected, JULY_10,
            JULY_10.plusDays(2L), true, true);
        assertEquals(4, selected.size());
        assertTrue(selected.contains(existing));
    }
}
