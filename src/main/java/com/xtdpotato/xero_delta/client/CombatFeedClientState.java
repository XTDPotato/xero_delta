package com.xtdpotato.xero_delta.client;

import com.xtdpotato.xero_delta.network.CombatFeedPacket;
import net.minecraft.Util;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/** Bounded client queue for right-side combat feed entries. */
public final class CombatFeedClientState {
    public static final CombatFeedClientState INSTANCE = new CombatFeedClientState();
    static final long ENTRY_DURATION_MS = 6_000L;
    private static final int MAX_ENTRIES = 5;
    private final ArrayDeque<Entry> entries = new ArrayDeque<>();

    private CombatFeedClientState() {
    }

    public void add(CombatFeedPacket packet) {
        long now = Util.getMillis();
        prune(now);
        entries.addLast(new Entry(packet, now));
        while (entries.size() > MAX_ENTRIES) entries.removeFirst();
    }

    public List<Entry> activeEntries() {
        return activeEntries(Util.getMillis());
    }

    List<Entry> activeEntries(long now) {
        prune(now);
        return List.copyOf(new ArrayList<>(entries));
    }

    public void clear() {
        entries.clear();
    }

    private void prune(long now) {
        while (!entries.isEmpty()
            && now - entries.peekFirst().createdAtMs() >= ENTRY_DURATION_MS) {
            entries.removeFirst();
        }
    }

    public record Entry(CombatFeedPacket packet, long createdAtMs) {
    }
}
