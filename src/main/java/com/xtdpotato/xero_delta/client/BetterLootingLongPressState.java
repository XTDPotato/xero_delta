package com.xtdpotato.xero_delta.client;

/** Tracks the client-side Better Looting batch action that was caused by a long press. */
public final class BetterLootingLongPressState {
    private static long validUntil;

    private BetterLootingLongPressState() {
    }

    public static void markBatchPickup() {
        validUntil = System.currentTimeMillis() + 4000L;
    }

    public static boolean consumeInventoryFullIntent() {
        long now = System.currentTimeMillis();
        if (validUntil < now) {
            validUntil = 0L;
            return false;
        }
        validUntil = 0L;
        return true;
    }
}