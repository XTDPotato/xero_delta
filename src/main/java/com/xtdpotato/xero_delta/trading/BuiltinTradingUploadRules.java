package com.xtdpotato.xero_delta.trading;

import java.util.Set;

/** Built-in market upload defaults which may still be overridden by administrator rules. */
public final class BuiltinTradingUploadRules {
    private static final Set<String> BLOCKED_BY_DEFAULT = Set.of(
        "xero_delta:dar_assault_chest_rig",
        "xero_delta:gto_heavy_tactical_pack"
    );

    private BuiltinTradingUploadRules() {
    }

    public static boolean defaultAllowed(String itemId) {
        return itemId == null || !BLOCKED_BY_DEFAULT.contains(itemId);
    }
}
