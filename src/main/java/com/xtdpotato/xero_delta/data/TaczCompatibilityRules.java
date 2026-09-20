package com.xtdpotato.xero_delta.data;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;

/** Component-sensitive rules for TACZ content packs. */
public final class TaczCompatibilityRules {
    private TaczCompatibilityRules() {
    }

    public static String descriptor(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "";
        return (BuiltInRegistries.ITEM.getKey(stack.getItem()) + " "
            + stack.getItem().getClass().getName() + " "
            + stack.getHoverName().getString() + " "
            + stack.getComponents()).toLowerCase(Locale.ROOT);
    }

    public static boolean isLrTacticalMelee(ItemStack stack) {
        return isLrTacticalMeleeDescriptor(descriptor(stack));
    }

    public static boolean isTaczGun(ItemStack stack) {
        return isTaczGunDescriptor(descriptor(stack));
    }

    public static boolean isTaczGunDescriptor(String descriptor) {
        if (descriptor == null || descriptor.isBlank()) return false;
        String value = descriptor.toLowerCase(Locale.ROOT);
        boolean taczItem = value.contains("tacz") || value.contains("modern_kinetic_gun");
        return taczItem && containsAny(value,
            "gunitem", "gun_item", "modern_kinetic_gun");
    }

    public static boolean isHandgun(ItemStack stack) {
        return isHandgunDescriptor(descriptor(stack));
    }

    public static boolean isHandgunDescriptor(String descriptor) {
        if (!isTaczGunDescriptor(descriptor)) return false;
        String value = descriptor.toLowerCase(Locale.ROOT);
        return containsAny(value, "pistol", "revolver", "handgun", "glock", "deagle",
            "m1911", "p320", "usp", "cz75", "p226")
            || value.matches(".*(?:^|[/ _:])m9(?:$|[ }/,]).*");
    }

    public static boolean isLrTacticalMeleeDescriptor(String descriptor) {
        if (descriptor == null || descriptor.isBlank()) return false;
        String value = descriptor.toLowerCase(Locale.ROOT);
        boolean lrWorkshop = containsAny(value, "lr_tactical", "lrtactical", "lr:tactical",
            "lr_tactical_workshop", "lrtacticalworkshop", "lrworkshop",
            "lr's tactical workshop", "lr tactical workshop", "lr\u7684\u6218\u672f\u5de5\u574a");
        return lrWorkshop && containsAny(value, "melee", "knife", "dagger", "sword", "katana",
            "machete", "cleaver", "axe", "hatchet", "hammer", "bat", "baton", "crowbar",
            "shovel", "spear", "wrench", "chainsaw", "\u8fd1\u6218", "\u5315\u9996",
            "\u5200", "\u5251", "\u65a7", "\u9524", "\u68cd");
    }

    private static boolean containsAny(String value, String... needles) {
        for (String needle : needles) if (value.contains(needle)) return true;
        return false;
    }
}
