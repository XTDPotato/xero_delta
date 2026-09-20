package com.xtdpotato.xero_delta.data;

import java.util.Locale;

final class RecipeValueMultiplier {
    private RecipeValueMultiplier() {
    }

    static double forTypeId(String typeId) {
        if (typeId == null) return 1.10;
        String type = typeId.toLowerCase(Locale.ROOT);
        if (type.contains("smelt") || type.contains("blast") || type.contains("smok")
            || type.contains("campfire")) {
            return 1.15;
        }
        if (type.contains("smith")) return 1.25;
        if (type.contains("stonecut")) return 1.05;
        return 1.10;
    }
}
