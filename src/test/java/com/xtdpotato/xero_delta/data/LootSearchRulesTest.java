package com.xtdpotato.xero_delta.data;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LootSearchRulesTest {
    @Test
    void corpseEquipmentAndCarrierItemsAreVisibleWithoutSearching() {
        for (int slotId : List.of(0, 1, 2, 3, 4, 10, 11)) {
            assertEquals(true, LootSearchRules.isImmediatelyVisibleCorpseSlot(slotId));
        }
        for (int slotId : List.of(5, 6, 7, 8, 9, 12, 13, 53, 54)) {
            assertEquals(false, LootSearchRules.isImmediatelyVisibleCorpseSlot(slotId));
        }
    }

    @Test
    void qualitiesUseConfiguredSearchDurations() {
        assertEquals(10, LootSearchRules.durationTicks("gray"));
        assertEquals(10, LootSearchRules.durationTicks("white"));
        assertEquals(20, LootSearchRules.durationTicks("green"));
        assertEquals(35, LootSearchRules.durationTicks("blue"));
        assertEquals(40, LootSearchRules.durationTicks("purple"));
        assertEquals(60, LootSearchRules.durationTicks("gold"));
        assertEquals(75, LootSearchRules.durationTicks("red"));
    }

    @Test
    void corpsePocketRangeRemainsAvailableForLayoutClassification() {
        for (int slotId : List.of(5, 6, 7, 8, 9)) {
            assertEquals(true, LootSearchRules.isPocketCorpseSlot(slotId));
        }
        for (int slotId : List.of(0, 4, 10, 11, 13, 53)) {
            assertEquals(false, LootSearchRules.isPocketCorpseSlot(slotId));
        }
    }

    @Test
    void defaultOrderRunsTopToBottomAndLeftToRight() {
        List<Integer> ordered = LootSearchRules.visualOrder(List.of(
            new LootSearchRules.Cell(4, 36, 18),
            new LootSearchRules.Cell(2, 18, 0),
            new LootSearchRules.Cell(1, 0, 0),
            new LootSearchRules.Cell(3, 0, 18)));
        assertEquals(List.of(1, 2, 3, 4), ordered);
    }

    @Test
    void hoveredHiddenItemBecomesNextWithoutInterruptingCurrent() {
        assertEquals(List.of(4, 2, 3),
            LootSearchRules.prioritizeNext(List.of(2, 3, 4), 4));
        assertEquals(List.of(2, 3, 4),
            LootSearchRules.prioritizeNext(List.of(2, 3, 4), 9));
    }
}
