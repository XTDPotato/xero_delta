package com.xtdpotato.xero_delta.client;

/** Pure frame-level mapping kept separate from optional Minecraft APIs. */
final class LegendaryTooltipQualityMap {
    private LegendaryTooltipQualityMap() {
    }

    static String resolve(int index, String rarityQuality) {
        if (index == -2) return null;
        if (index == -1) return "gray".equals(rarityQuality) ? null : rarityQuality;
        if (index == 0 && ("blue".equals(rarityQuality) || "purple".equals(rarityQuality))) {
            return rarityQuality;
        }
        return switch (index) {
            case 0 -> "red";
            case 1 -> "gold";
            case 2 -> "purple";
            case 3 -> "blue";
            case 4 -> "green";
            default -> "gray";
        };
    }
}
