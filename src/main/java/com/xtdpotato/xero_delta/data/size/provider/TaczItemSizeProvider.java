package com.xtdpotato.xero_delta.data.size.provider;

import com.xtdpotato.xero_delta.data.ItemSize;
import com.xtdpotato.xero_delta.data.ItemSizeRule;
import com.xtdpotato.xero_delta.data.TaczCompatibilityRules;
import com.xtdpotato.xero_delta.data.size.ItemSizeProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;

public final class TaczItemSizeProvider implements ItemSizeProvider {
    @Override
    public String id() {
        return "tacz";
    }

    @Override
    public Tier tier() {
        return Tier.MOD_COMPATIBILITY;
    }

    @Override
    public ItemSizeRule resolve(ItemStack stack, ResourceLocation itemId, Integer recipeIngredientCount) {
        String className = stack.getItem().getClass().getName().toLowerCase(Locale.ROOT);
        if (!"tacz".equals(itemId.getNamespace()) && !className.contains("tacz")) return null;
        String descriptor = (itemId + " " + className + " " + stack.getHoverName().getString() + " "
            + stack.getComponents()).toLowerCase(Locale.ROOT);
        if (TaczCompatibilityRules.isLrTacticalMeleeDescriptor(descriptor)) return rule(1, 1);
        return classifyDescriptor(descriptor);
    }

    public static ItemSizeRule classifyDescriptor(String descriptor) {
        descriptor = descriptor.toLowerCase(Locale.ROOT);
        if (TaczCompatibilityRules.isLrTacticalMeleeDescriptor(descriptor)) return rule(1, 1);

        // A TACZ gun stack contains its installed magazine, ammunition and
        // attachments in components. Classify the gun itself before looking
        // at those component keywords, otherwise a rifle becomes a 1x1 bullet.
        ItemSizeRule gunRule = classifyGun(descriptor);
        if (gunRule != null) return gunRule;

        if (containsAny(descriptor, "drum", "drum_mag", "drummag")) return rule(2, 2);
        if (containsAny(descriptor, "magazine", "extended_mag", "mag_item", "ammo_box", "gun_mag")) return rule(1, 2);
        if (containsAny(descriptor, "ammo", "bullet", "cartridge", "12_gauge", "bmg", "nato")) return rule(1, 1);
        if (containsAny(descriptor, "long_scope", "sniper_scope", "high_power_scope", "scope_6x", "scope_8x")) return rule(2, 1);
        if (containsAny(descriptor, "short_suppressor", "mini_suppressor", "short_silencer")) return rule(1, 1);
        if (containsAny(descriptor, "suppressor", "silencer", "muzzle_silencer")) return rule(1, 2);
        if (containsAny(descriptor, "folding_stock", "fold_stock", "compact_stock")) return rule(1, 1);
        if (containsAny(descriptor, "stock", "buttstock")) return rule(2, 1);
        if (containsAny(descriptor, "scope", "sight", "red_dot", "reddot", "holographic", "holo",
            "magnifier", "grip", "foregrip", "laser", "flashlight", "rail", "attachment")) return rule(1, 1);
        return ItemSizeRule.DEFAULT;
    }

    private static ItemSizeRule classifyGun(String descriptor) {
        boolean gunItem = containsAny(descriptor, "gunitem", "gun_item", "modern_kinetic_gun");
        if (containsAny(descriptor, "rocket_launcher", "rocket-launcher", "grenade_launcher",
            "grenade-launcher", "launcher", "bazooka", "rpg", "panzerfaust", "recoilless",
            "\u69b4\u5f39\u53d1\u5c04\u5668", "\u706b\u7bad\u7b52", "\u706b\u7bad\u53d1\u5c04\u5668")) return rule(5, 2);
        if (containsAny(descriptor, "pistol", "revolver", "handgun", "glock", "deagle", "m1911", "p320", "usp", "cz75", "p226")
            || gunPathEquals(descriptor, "m9")) return rule(2, 1);
        if (containsAny(descriptor, "submachine", "submachine_gun", "smg", "mp5", "mp7", "vector",
            "ump", "p90", "pp19", "bizon", "uzi", "mac10", "scorpion")) {
            return gunPathEquals(descriptor, "uzi") || containsAny(descriptor, " uzi ") ? rule(3, 2) : rule(4, 2);
        }
        if (containsAny(descriptor, "sniper", "marksman", "anti_material", "anti-materiel", "awm",
            "awp", "svd", "mosin", "kar98", "ax50", "intervention", "50cal", ".50")
            || gunPathEqualsAny(descriptor, "m24", "m700", "m95", "m82", "m107")) {
            if (gunPathEqualsAny(descriptor, "m95", "m82", "m107")) return rule(6, 2);
            if (gunPathEquals(descriptor, "m700")) return rule(5, 1);
            return stableVariant(descriptor, new int[][]{{5, 1}, {5, 1}, {5, 1}, {5, 2}, {6, 2}});
        }
        if (containsAny(descriptor, "shotgun") || gunPathStartsWith(descriptor, "db")
            || gunPathEqualsAny(descriptor, "m870", "s12k", "m1014", "aa12", "saiga12", "spas12")) {
            if (gunPathEquals(descriptor, "m870") || gunPathStartsWith(descriptor, "db")) return rule(5, 1);
            if (gunPathEquals(descriptor, "s12k")) return rule(5, 2);
            return stableVariant(descriptor, new int[][]{{5, 1}, {4, 1}, {5, 2}});
        }
        if (containsAny(descriptor, "machine_gun", "machine-gun", "light_machine", "heavy_machine",
            "lmg", "hmg", "pkp", "m60", "mg42", "m2hb", "minigun")
            || gunPathEqualsAny(descriptor, "m249", "m250", "rpk")) {
            return gunPathEquals(descriptor, "m250") ? rule(6, 2) : rule(5, 2);
        }
        if (containsAny(descriptor, "assault_rifle", "assault-rifle", "battle_rifle", "battle-rifle",
            "rifle", "ak47", "ak74", "m4", "m16", "hk416", "g36", "famas", "qbz", "scar_h",
            "scar-h", "fn_fal", "mk14", "m14", "ar10")) return rule(5, 2);
        return gunItem ? rule(5, 2) : null;
    }

    private static ItemSizeRule stableVariant(String descriptor, int[][] variants) {
        String identity = stableIdentity(descriptor);
        int[] selected = variants[Math.floorMod(identity.hashCode(), variants.length)];
        return rule(selected[0], selected[1]);
    }

    private static String stableIdentity(String descriptor) {
        var matcher = java.util.regex.Pattern.compile(
            "(?i)(?:gunid|gun_id)[^a-z0-9_.:/-]+([a-z0-9_.-]+:[a-z0-9_./-]+)")
            .matcher(descriptor);
        if (matcher.find()) return matcher.group(1);
        matcher = java.util.regex.Pattern.compile("(?i)([a-z0-9_.-]+:[a-z0-9_./-]+)")
            .matcher(descriptor);
        String fallback = descriptor;
        while (matcher.find()) fallback = matcher.group(1);
        return fallback;
    }

    private static boolean gunPathStartsWith(String descriptor, String prefix) {
        String path = gunModel(descriptor);
        return path.startsWith(prefix + "_") || path.startsWith(prefix + "-") || path.equals(prefix);
    }

    private static boolean gunPathEquals(String descriptor, String expected) {
        return gunModel(descriptor).equals(expected);
    }

    private static boolean gunPathEqualsAny(String descriptor, String... expected) {
        String model = gunModel(descriptor);
        for (String value : expected) if (model.equals(value)) return true;
        return false;
    }

    private static String gunModel(String descriptor) {
        String identity = stableIdentity(descriptor);
        int separator = identity.indexOf(':');
        String path = separator >= 0 ? identity.substring(separator + 1) : identity;
        int slash = path.lastIndexOf('/');
        return slash >= 0 ? path.substring(slash + 1) : path;
    }

    private static ItemSizeRule rule(int width, int height) {
        return new ItemSizeRule(new ItemSize(width, height), true, false);
    }

    private static boolean containsAny(String value, String... needles) {
        for (String needle : needles) if (value.contains(needle)) return true;
        return false;
    }
}
