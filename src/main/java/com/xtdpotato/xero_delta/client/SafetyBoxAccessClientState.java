package com.xtdpotato.xero_delta.client;

import com.xtdpotato.xero_delta.data.SafetyBoxAccessRules;

import java.util.Map;

/** Latest server-authoritative safety-box unlock snapshot. */
public final class SafetyBoxAccessClientState {
    public static final SafetyBoxAccessClientState INSTANCE = new SafetyBoxAccessClientState();
    private volatile Map<String, Long> access = Map.of();

    private SafetyBoxAccessClientState() {}

    public void update(Map<String, Long> values) {
        access = Map.copyOf(values);
    }

    public boolean isUnlocked(String itemId) {
        if (SafetyBoxAccessRules.isPermanent(itemId)) return true;
        long expiresAt = access.getOrDefault(itemId, 0L);
        return expiresAt == Long.MAX_VALUE || expiresAt > System.currentTimeMillis();
    }

    public long expiresAt(String itemId) {
        if (SafetyBoxAccessRules.isPermanent(itemId)) return Long.MAX_VALUE;
        return access.getOrDefault(itemId, 0L);
    }
}
