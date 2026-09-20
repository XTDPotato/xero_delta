package com.xtdpotato.xero_delta.data;

/** Pure expiry arithmetic shared by safety-box entitlement persistence and tests. */
public final class SafetyBoxExpiry {
    private SafetyBoxExpiry() {
    }

    public static long merge(long currentExpiresAt, long requestedExpiresAt, long now, long permanent) {
        if (currentExpiresAt == permanent || requestedExpiresAt == permanent) return permanent;
        long addedDuration = Math.max(0L, requestedExpiresAt - now);
        long base = Math.max(now, currentExpiresAt);
        if (addedDuration >= permanent - base) return permanent;
        return base + addedDuration;
    }
}
