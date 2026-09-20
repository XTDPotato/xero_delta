package com.xtdpotato.xero_delta.data;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.Set;

/** Server-side placement policy for the optional restricted inventory layout. */
public final class PlayerLayoutSlotRules {
    private static final Set<String> MEDICAL_PATHS = Set.of(
        "battlefield_medical_kit", "outdoor_medical_kit", "dek_field_surgery_kit",
        "dve_painkillers", "field_first_aid_kit", "tactical_quick_release_surgery_kit",
        "bottled_antibiotics", "strong_injector", "vehicle_first_aid_kit",
        "cat_tourniquet", "simple_surgery_kit", "extended_release_painkillers",
        "simple_injector", "elastic_bandage", "stamina_booster", "perception_booster",
        "m2_muscle_injector", "stamina_activation_injection", "oe2_combat_stimulant",
        "perception_activation_injection", "m1_muscle_booster", "norepinephrine"
    );

    private PlayerLayoutSlotRules() {}

    public static boolean enabled(ServerPlayer player) {
        return player != null && !player.isCreative()
            && PlayerLayoutRulesData.get(player.server).enabled();
    }

    public static boolean canPlace(ServerPlayer player, int inventoryIndex, ItemStack stack) {
        if (!enabled(player) || stack == null || stack.isEmpty()) return true;
        if (PlayerLayoutInventoryPolicy.isDisabledMainSlot(inventoryIndex)) return false;
        if (inventoryIndex < 0 || inventoryIndex >= 9) return true;
        return switch (inventoryIndex) {
            case 0, 1 -> true;
            case 2 -> isTaczGun(stack) && isHandgun(stack);
            case 3 -> TaczCompatibilityRules.isLrTacticalMelee(stack);
            case 4, 5, 6, 7, 8 -> {
                ItemSize size = ServerItemRules.getSizeFor(stack);
                yield isPocketSizeAllowed(size);
            }
            default -> false;
        };
    }

    static boolean isPocketSizeAllowed(ItemSize size) {
        return size != null && size.width() == 1 && size.height() == 1;
    }

    public static boolean isMedical(ItemStack stack) {
        var id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return "xero_delta".equals(id.getNamespace()) && MEDICAL_PATHS.contains(id.getPath());
    }

    public static boolean isTaczGun(ItemStack stack) {
        return TaczCompatibilityRules.isTaczGun(stack);
    }

    public static boolean isHandgun(ItemStack stack) {
        return TaczCompatibilityRules.isHandgun(stack);
    }

    /**
     * Drops stacks that cannot exist in the restricted layout. This also
     * repairs saves created before external container menus used the player's
     * real inventory index for slot validation.
     */
    public static int ejectDisabledMainInventory(ServerPlayer player) {
        if (player == null || !enabled(player)) return 0;
        Inventory inventory = player.getInventory();
        int ejected = 0;
        for (int slot = 0; slot < PlayerLayoutInventoryPolicy.HOTBAR_SIZE; slot++) {
            ItemStack stored = inventory.getItem(slot);
            if (stored.isEmpty() || canPlace(player, slot, stored)) continue;
            inventory.setItem(slot, ItemStack.EMPTY);
            player.drop(stored.copy(), false);
            ejected++;
        }
        for (int slot = PlayerLayoutInventoryPolicy.HOTBAR_SIZE;
             slot < PlayerLayoutInventoryPolicy.INVENTORY_SIZE; slot++) {
            ItemStack stored = inventory.getItem(slot);
            if (stored.isEmpty()) continue;
            ItemStack dropped = stored.copy();
            inventory.setItem(slot, ItemStack.EMPTY);
            player.drop(dropped, false);
            ejected++;
        }
        if (ejected > 0) {
            inventory.setChanged();
            player.inventoryMenu.broadcastChanges();
            if (player.containerMenu != player.inventoryMenu) {
                player.containerMenu.broadcastChanges();
            }
        }
        return ejected;
    }
}
