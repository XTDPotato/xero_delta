package com.xtdpotato.xero_delta.data;

import com.xtdpotato.xero_delta.item.MedicalItem;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

import java.util.Set;

/** Built-in safety-box exceptions that remain allowed across every insertion path. */
public final class SafetyBoxItemPolicy {
    private static final Set<String> ARMOR_REPAIR_PATHS = Set.of(
        "advanced_armor_repair_combo", "advanced_helmet_repair_combo",
        "precision_armor_repair_kit", "precision_helmet_repair_kit",
        "standard_armor_repair_kit", "standard_helmet_repair_kit",
        "homemade_armor_repair_kit", "homemade_helmet_repair_kit"
    );

    private SafetyBoxItemPolicy() {
    }

    public static boolean isExplicitlyAllowed(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (stack.getItem() instanceof MedicalItem) return true;
        return isExplicitlyAllowedId(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
    }

    static boolean isExplicitlyAllowedId(String itemId) {
        if (itemId == null || !itemId.startsWith("xero_delta:")) return false;
        return ARMOR_REPAIR_PATHS.contains(itemId.substring("xero_delta:".length()));
    }
}
