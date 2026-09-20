package com.xtdpotato.xero_delta.trading;

/** Fixed military-vendor recycle values for special Delta equipment. */
public final class BuiltinRecycleValueCatalog {
    private BuiltinRecycleValueCatalog() {
    }

    public static long resolve(String itemId, long fallback) {
        return switch (itemId == null ? "" : itemId) {
            case "xero_delta:dar_assault_chest_rig" -> 59_159L;
            case "xero_delta:gto_heavy_tactical_pack" -> 58_114L;
            default -> fallback;
        };
    }
}
