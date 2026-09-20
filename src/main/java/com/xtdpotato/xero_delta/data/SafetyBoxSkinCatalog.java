package com.xtdpotato.xero_delta.data;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Shared validation rules for the built-in ultimate-safety-box cosmetics. */
public final class SafetyBoxSkinCatalog {
    public static final String TOP_BOX_ID = "xero_delta:safety_box_3x3";
    public static final String DEFAULT_SKIN = "default";
    public static final String PERSISTENT_KEY = "xero_delta_safety_box_skin";

    private static final List<String> SKINS = List.of(
        DEFAULT_SKIN,
        "gilded_glow",
        "gekeluosi_secret",
        "zero_player",
        "watcher",
        "wheel_of_fate"
    );
    private static final Set<String> DEFAULT_UNLOCKS = Set.of(DEFAULT_SKIN, "zero_player");

    private SafetyBoxSkinCatalog() {}

    public static List<String> skins() {
        return SKINS;
    }

    public static boolean isKnown(String skinId) {
        if (skinId == null) return false;
        return SKINS.contains(skinId.trim().toLowerCase(Locale.ROOT));
    }

    public static boolean isUnlockedByDefault(String skinId) {
        return DEFAULT_UNLOCKS.contains(normalize(skinId));
    }

    public static String normalize(String skinId) {
        if (skinId == null) return DEFAULT_SKIN;
        String normalized = skinId.trim().toLowerCase(Locale.ROOT);
        return SKINS.contains(normalized) ? normalized : DEFAULT_SKIN;
    }
}
