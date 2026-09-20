package com.xtdpotato.xero_delta.trading;

import java.util.Locale;

/** Stable category ids used by packets, HTML controls and localisation. */
public enum TradingCategory {
    ALL,
    FAVORITES,
    GUNS,
    EQUIPMENT,
    ATTACHMENTS,
    AMMO,
    CONSUMABLES,
    KEYS,
    BLOCKS,
    REDSTONE,
    MATERIALS,
    TOOLS,
    COLLECTIBLES;

    public static TradingCategory classify(String itemId) {
        if (itemId == null) return COLLECTIBLES;
        String id = itemId.toLowerCase(Locale.ROOT);
        int separator = id.indexOf(':');
        String namespace = separator < 0 ? "minecraft" : id.substring(0, separator);
        String path = separator < 0 ? id : id.substring(separator + 1);

        if (containsAny(path, "filled_map", "treasure_map", "explorer_map", "key", "keycard", "access_card")) {
            return KEYS;
        }
        if (containsAny(path, "redstone", "repeater", "comparator", "piston", "observer", "dispenser",
            "dropper", "hopper", "lever", "daylight_detector", "target", "sculk_sensor")) return REDSTONE;
        if (containsAny(path, "ammo", "bullet", "cartridge", "shell", "round", "arrow")) return AMMO;
        if (containsAny(path, "scope", "sight", "magazine", "silencer", "suppressor", "muzzle", "stock",
            "grip", "laser", "flashlight", "rail", "attachment")) return ATTACHMENTS;
        if (namespace.equals("tacz") && containsAny(path, "gun", "rifle", "pistol", "revolver", "smg",
            "shotgun", "sniper", "machine_gun", "launcher")) return GUNS;
        if (containsAny(path, "gun", "rifle", "pistol", "revolver", "smg", "shotgun", "sniper")) return GUNS;
        if (containsAny(path, "pickaxe", "shovel", "hoe", "fishing_rod", "shears", "flint_and_steel",
            "brush", "wrench", "hammer", "drill", "saw", "tool")) return TOOLS;
        if (containsAny(path, "helmet", "chestplate", "leggings", "boots", "armor", "shield", "backpack",
            "sword", "axe", "bow", "crossbow", "trident", "mace")) return EQUIPMENT;
        if (containsAny(path, "potion", "food", "apple", "bread", "stew", "soup", "berry", "carrot",
            "potato", "bandage", "medkit", "medicine", "syringe", "drink")) return CONSUMABLES;
        if (containsAny(path, "ingot", "nugget", "raw_", "_ore", "gem", "dust", "powder", "shard",
            "fragment", "crystal", "plate", "sheet", "alloy", "scrap", "hide", "leather")) return MATERIALS;
        return COLLECTIBLES;
    }

    static TradingCategory classify(String itemId, boolean blockItem) {
        String safeId = itemId == null ? "" : itemId;
        int separator = safeId.indexOf(':');
        String path = (separator < 0 ? safeId : safeId.substring(separator + 1))
            .toLowerCase(Locale.ROOT);
        if (containsAny(path, "banner", "flag", "painting", "item_frame", "music_disc",
            "skull", "head", "trophy", "collectible")) return COLLECTIBLES;

        TradingCategory semantic = classify(itemId);
        if (!blockItem) return semantic;

        // Match the creative inventory's broad separation: real placeable items belong to
        // the block tab even when their id also contains material words such as ore/ingot.
        // Redstone components keep their dedicated creative-style category.
        return semantic == REDSTONE ? REDSTONE : BLOCKS;
    }

    public boolean matches(String itemId, boolean favorite) {
        if (this == ALL) return true;
        if (this == FAVORITES) return favorite;
        return classify(itemId) == this;
    }

    private static boolean containsAny(String text, String... needles) {
        for (String needle : needles) if (text.contains(needle)) return true;
        return false;
    }
}
