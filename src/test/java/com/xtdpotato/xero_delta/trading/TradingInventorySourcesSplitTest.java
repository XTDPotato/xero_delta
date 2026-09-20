package com.xtdpotato.xero_delta.trading;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class TradingInventorySourcesSplitTest {
    @Test
    void prefersTheRightHandNeighbour() {
        List<Integer> order = TradingSplitOrder.destinations(9, 4, 9);

        assertEquals(List.of(5, 3), order.subList(0, 2));
        assertFalse(order.contains(4));
    }

    @Test
    void searchesEverySlotByGridDistance() {
        List<Integer> order = TradingSplitOrder.destinations(18, 4, 9);

        assertEquals(List.of(5, 3, 13), order.subList(0, 3));
        assertEquals(17, order.size());
    }

    @Test
    void externalSourcesDoNotLoseTheFirstPlayerSlot() {
        assertEquals(List.of(0, 1, 2),
            TradingSplitOrder.destinations(3, -1, 9));
    }
}
