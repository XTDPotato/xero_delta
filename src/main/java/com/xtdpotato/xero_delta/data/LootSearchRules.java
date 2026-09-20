package com.xtdpotato.xero_delta.data;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Pure timing and ordering rules for sequential container looting. */
public final class LootSearchRules {
    private LootSearchRules() {
    }

    public static boolean isImmediatelyVisibleCorpseSlot(int slotId) {
        return slotId >= 0 && slotId <= 4 || slotId == 10 || slotId == 11;
    }

    /** Corpse pocket range retained for layout/rule callers; searching now uses one queue. */
    public static boolean isPocketCorpseSlot(int slotId) {
        return slotId >= 5 && slotId <= 9;
    }

    public static int durationTicks(String quality) {
        return switch (quality == null ? "gray" : quality.toLowerCase(java.util.Locale.ROOT)) {
            case "red", "mythic" -> 75;
            case "gold", "legendary" -> 60;
            case "purple", "epic" -> 40;
            case "blue" -> 35;
            case "green", "rare" -> 20;
            default -> 10;
        };
    }

    public static List<Integer> visualOrder(List<Cell> cells) {
        return cells.stream()
            .sorted(Comparator.comparingInt(Cell::y)
                .thenComparingInt(Cell::x)
                .thenComparingInt(Cell::slotId))
            .map(Cell::slotId)
            .toList();
    }

    public static List<Integer> prioritizeNext(List<Integer> queuedSlots, int slotId) {
        if (!queuedSlots.contains(slotId)) return List.copyOf(queuedSlots);
        List<Integer> result = new ArrayList<>(queuedSlots.size());
        result.add(slotId);
        for (int queued : queuedSlots) if (queued != slotId) result.add(queued);
        return List.copyOf(result);
    }

    public record Cell(int slotId, int x, int y) {
    }
}
