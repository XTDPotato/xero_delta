package com.xtdpotato.xero_delta.data;

import java.util.Locale;

/** Explicit vanilla quality exceptions that do not depend on registry state. */
final class VanillaQualityOverrides {
    private VanillaQualityOverrides() {
    }

    static String find(String path) {
        path = path.toLowerCase(Locale.ROOT);
        if (path.endsWith("_spawn_egg")) return "red";
        return switch (path) {
            case "slime_ball", "tripwire_hook", "damaged_anvil", "large_amethyst_bud" -> "blue";
            case "wind_charge", "redstone_lamp", "chipped_anvil", "amethyst_cluster" -> "purple";
            case "spectral_arrow", "anvil" -> "gold";
            case "medium_amethyst_bud" -> "green";
            case "small_amethyst_bud" -> "gray";
            default -> null;
        };
    }
}
