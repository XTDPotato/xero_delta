package com.xtdpotato.xero_delta.data;

import com.xtdpotato.xero_delta.item.MedicalItem;
import com.xtdpotato.xero_delta.item.RepairKitItem;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TridentItem;

import java.util.Locale;

/** Server-authoritative warehouse tabs and their accepted item families. */
public enum WarehouseCategory {
    MAIN("main", "I", 35, 70, new ItemStack(Items.CHEST)),
    MEDICAL("medical", "IV", 8, 8, new ItemStack(Items.POTION)),
    EQUIPMENT("equipment", "IV", 8, 8, new ItemStack(Items.DIAMOND_CHESTPLATE)),
    ITEMS("items", "GTI", 12, 12, new ItemStack(Items.BARREL)),
    AMMO("ammo", "GTI", 8, 8, new ItemStack(Items.ARROW)),
    WEAPONS("weapons", "GTI", 10, 10, new ItemStack(Items.CROSSBOW)),
    COLLECTIBLES("collectibles", "IV", 8, 8, new ItemStack(Items.ENDER_CHEST));

    private final String id;
    private final String tier;
    private final int defaultRows;
    private final int maximumRows;
    private final ItemStack icon;

    WarehouseCategory(String id, String tier, int defaultRows,
                      int maximumRows, ItemStack icon) {
        this.id = id;
        this.tier = tier;
        this.defaultRows = defaultRows;
        this.maximumRows = maximumRows;
        this.icon = icon;
    }

    public String id() { return id; }
    public String tier() { return tier; }
    public int defaultRows() { return defaultRows; }
    public int maximumRows() { return maximumRows; }
    public ItemStack icon() { return icon.copy(); }
    public String translationKey() { return "warehouse.xero_delta.category." + id; }

    public boolean accepts(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return true;
        return switch (this) {
            case MAIN, ITEMS -> true;
            case MEDICAL -> isMedical(stack);
            case EQUIPMENT -> isEquipment(stack);
            case AMMO -> isAmmo(stack);
            case WEAPONS -> isWeapon(stack);
            case COLLECTIBLES -> isCollectible(stack);
        };
    }

    public static WarehouseCategory byId(String id) {
        if (id != null) for (WarehouseCategory category : values()) {
            if (category.id.equalsIgnoreCase(id)) return category;
        }
        return MAIN;
    }

    public static boolean isMedical(ItemStack stack) {
        if (stack.getItem() instanceof MedicalItem || stack.getItem() instanceof RepairKitItem) {
            return true;
        }
        String value = descriptor(stack);
        return containsAny(value, "medical", "medicine", "medkit", "first_aid",
            "surgery", "tourniquet", "bandage", "painkiller", "tablet",
            "injector", "syringe", "stimulant", "repair_kit", "repair_combo");
    }

    public static boolean isEquipment(ItemStack stack) {
        if (stack.getItem() instanceof ArmorItem || stack.getItem() instanceof ShieldItem) {
            return true;
        }
        String value = descriptor(stack);
        return containsAny(value, "helmet", "chestplate", "body_armor",
            "bodyarmor", "armor_vest", "armour_vest");
    }

    public static boolean isAmmo(ItemStack stack) {
        String value = descriptor(stack);
        boolean gunContent = value.contains("tacz") || value.contains("modern_kinetic_gun");
        return gunContent && containsAny(value, "ammo", "ammunition", "bullet",
            "cartridge", "magazine", "shell");
    }

    public static boolean isWeapon(ItemStack stack) {
        if (TaczCompatibilityRules.isTaczGun(stack)
            || stack.getItem() instanceof SwordItem
            || stack.getItem() instanceof BowItem
            || stack.getItem() instanceof CrossbowItem
            || stack.getItem() instanceof ProjectileWeaponItem
            || stack.getItem() instanceof TridentItem) return true;
        String value = descriptor(stack);
        boolean gunContent = value.contains("tacz") || value.contains("modern_kinetic_gun");
        return gunContent && containsAny(value, "attachment", "scope", "sight",
            "muzzle", "silencer", "suppressor", "grip", "stock", "rail",
            "laser", "flashlight", "bipod");
    }

    private static boolean isCollectible(ItemStack stack) {
        if (isMedical(stack) || isEquipment(stack) || isAmmo(stack) || isWeapon(stack)) {
            return false;
        }
        String quality = ServerItemRules.getQualityFor(stack).toLowerCase(Locale.ROOT);
        return "gold".equals(quality) || "red".equals(quality);
    }

    private static String descriptor(ItemStack stack) {
        return (BuiltInRegistries.ITEM.getKey(stack.getItem()) + " "
            + stack.getItem().getClass().getName() + " "
            + stack.getHoverName().getString() + " " + stack.getComponents())
            .toLowerCase(Locale.ROOT);
    }

    private static boolean containsAny(String value, String... needles) {
        for (String needle : needles) if (value.contains(needle)) return true;
        return false;
    }
}
