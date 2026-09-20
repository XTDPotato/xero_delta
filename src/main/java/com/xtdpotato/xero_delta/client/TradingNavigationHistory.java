package com.xtdpotato.xero_delta.client;

import java.util.ArrayDeque;
import java.util.Deque;

/** Navigation history for trading screens. Top-level tabs are contexts, not navigation entries. */
final class TradingNavigationHistory {
    private static final int MAX_ENTRIES = 12;
    private final Deque<String> entries = new ArrayDeque<>();

    void acceptContext(String context) {
        if (context != null && context.startsWith("root:")) entries.clear();
    }

    void remember(String screen) {
        if (screen == null || screen.isBlank()) return;
        if (!entries.isEmpty() && screen.equals(entries.peekLast())) return;
        entries.addLast(screen);
        while (entries.size() > MAX_ENTRIES) entries.removeFirst();
    }

    String pop() {
        return entries.isEmpty() ? "" : entries.removeLast();
    }

    int size() {
        return entries.size();
    }
}
