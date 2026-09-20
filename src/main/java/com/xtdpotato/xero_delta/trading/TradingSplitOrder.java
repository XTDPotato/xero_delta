package com.xtdpotato.xero_delta.trading;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Pure slot-order calculation used when splitting inventory stacks. */
final class TradingSplitOrder {
    private TradingSplitOrder() {
    }

    static List<Integer> destinations(int slots, int sourceSlot, int columns) {
        int safeColumns = Math.max(1, Math.min(Math.max(1, slots), columns));
        boolean sourceInRange = sourceSlot >= 0 && sourceSlot < slots;
        int sourceColumn = sourceInRange ? sourceSlot % safeColumns : 0;
        int sourceRow = sourceInRange ? sourceSlot / safeColumns : 0;
        List<Integer> order = new ArrayList<>();
        for (int slot = 0; slot < slots; slot++) {
            if (!sourceInRange || slot != sourceSlot) order.add(slot);
        }
        order.sort(Comparator
            .comparingInt((Integer slot) -> Math.abs(slot % safeColumns - sourceColumn)
                + Math.abs(slot / safeColumns - sourceRow))
            .thenComparingInt(slot -> directionRank(
                slot % safeColumns - sourceColumn, slot / safeColumns - sourceRow))
            .thenComparingInt(Integer::intValue));
        return order;
    }

    private static int directionRank(int dx, int dy) {
        if (dy == 0 && dx > 0) return 0;
        if (dy == 0 && dx < 0) return 1;
        if (dx == 0 && dy > 0) return 2;
        if (dx == 0 && dy < 0) return 3;
        return 4;
    }
}
