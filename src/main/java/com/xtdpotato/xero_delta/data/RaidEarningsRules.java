package com.xtdpotato.xero_delta.data;

/** Pure overflow-safe arithmetic for current-raid currency. */
public final class RaidEarningsRules {
    public static final long MAX_EARNINGS = 9_000_000_000_000_000L;

    private RaidEarningsRules() {
    }

    public static long add(long current, long value) {
        long safeCurrent = Math.max(0L, Math.min(MAX_EARNINGS, current));
        if (value <= 0L) return safeCurrent;
        if (value >= MAX_EARNINGS - safeCurrent) return MAX_EARNINGS;
        return safeCurrent + value;
    }
}
