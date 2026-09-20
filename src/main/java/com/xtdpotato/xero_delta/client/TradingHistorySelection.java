package com.xtdpotato.xero_delta.client;

import java.util.List;
import java.util.Set;

/** Pure Ctrl/Shift range-selection rules shared by the transaction history UI. */
public final class TradingHistorySelection {
    private TradingHistorySelection() {
    }

    public static <T> T select(Set<T> selected, List<T> ordered, T anchor, T clicked,
                               boolean controlDown, boolean shiftDown) {
        if (shiftDown && anchor != null) {
            int anchorIndex = ordered.indexOf(anchor);
            int clickedIndex = ordered.indexOf(clicked);
            if (anchorIndex >= 0 && clickedIndex >= 0) {
                if (!controlDown) selected.clear();
                int from = Math.min(anchorIndex, clickedIndex);
                int to = Math.max(anchorIndex, clickedIndex);
                for (int index = from; index <= to; index++) selected.add(ordered.get(index));
                return anchor;
            }
        }
        if (controlDown) {
            if (!selected.add(clicked)) selected.remove(clicked);
        } else {
            selected.clear();
            selected.add(clicked);
        }
        return clicked;
    }
}
