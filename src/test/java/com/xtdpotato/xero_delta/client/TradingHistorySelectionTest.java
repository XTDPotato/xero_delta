package com.xtdpotato.xero_delta.client;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TradingHistorySelectionTest {
    private static final List<String> ORDERED = List.of("a", "b", "c", "d", "e");

    @Test
    void normalClickReplacesSelection() {
        Set<String> selected = new LinkedHashSet<>(List.of("a", "b"));
        String anchor = TradingHistorySelection.select(selected, ORDERED, "a", "d", false, false);
        assertEquals(Set.of("d"), selected);
        assertEquals("d", anchor);
    }

    @Test
    void controlClickTogglesIndividualRows() {
        Set<String> selected = new LinkedHashSet<>(List.of("a"));
        TradingHistorySelection.select(selected, ORDERED, "a", "c", true, false);
        assertEquals(Set.of("a", "c"), selected);
        TradingHistorySelection.select(selected, ORDERED, "c", "a", true, false);
        assertEquals(Set.of("c"), selected);
    }

    @Test
    void shiftClickSelectsContiguousRange() {
        Set<String> selected = new LinkedHashSet<>(List.of("a"));
        String anchor = TradingHistorySelection.select(selected, ORDERED, "b", "e", false, true);
        assertEquals(Set.of("b", "c", "d", "e"), selected);
        assertEquals("b", anchor);
    }

    @Test
    void controlShiftAddsRangeToExistingSelection() {
        Set<String> selected = new LinkedHashSet<>(List.of("a"));
        TradingHistorySelection.select(selected, ORDERED, "c", "e", true, true);
        assertEquals(Set.of("a", "c", "d", "e"), selected);
    }
}
