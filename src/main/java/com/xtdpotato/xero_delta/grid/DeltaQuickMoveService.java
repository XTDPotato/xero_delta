package com.xtdpotato.xero_delta.grid;

import com.xtdpotato.xero_delta.Config;
import com.xtdpotato.xero_delta.compat.BetterLootingPickupCompat;
import com.xtdpotato.xero_delta.compat.DeltaPackAutoEquipService;
import com.xtdpotato.xero_delta.data.ContainerGridRules;
import com.xtdpotato.xero_delta.data.PlayerLayoutSlotRules;
import com.xtdpotato.xero_delta.item.DeltaPackItem;
import com.xtdpotato.xero_delta.data.ModDataStorage;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/** Shared server-side routing for Shift-click and Delta double-click moves. */
public final class DeltaQuickMoveService {
    private DeltaQuickMoveService() {}

    /** Container pickup only targets equipped carriers: backpack, then chest rig. */
    public static boolean moveIntoPlayerDelta(ServerPlayer player, ItemStack stack) {
        return moveIntoPlayerDelta(player, stack, "");
    }

    public static boolean moveIntoPlayerDelta(ServerPlayer player, ItemStack stack,
                                              String excludedStorage) {
        if (player == null || stack == null || stack.isEmpty()) return false;
        boolean carrierItem = stack.getItem() instanceof DeltaPackItem pack
            && ("chest_rig".equals(pack.slotIdentifier()) || "backpack".equals(pack.slotIdentifier()));
        boolean changed = carrierItem
            ? DeltaPackAutoEquipService.tryEquipFromStack(player, stack)
            : tryQuickEquipLoadout(player, stack);
        if (stack.isEmpty()) return true;
        if (!"backpack".equals(excludedStorage)) {
            changed |= DeltaPackAutoEquipService.tryStoreInEquippedCarrier(
                player, stack, "backpack");
        }
        if (!stack.isEmpty() && !"chest_rig".equals(excludedStorage)) {
            changed |= DeltaPackAutoEquipService.tryStoreInEquippedCarrier(
                player, stack, "chest_rig");
        }
        // External-container carry must never fall through to the restricted
        // 1x1 pocket slots. An unlocked safety box is the final legal storage
        // destination before reporting that the item cannot be carried.
        if (!stack.isEmpty()) {
            changed |= BetterLootingPickupCompat.storeInSafetyBox(player, stack);
        }

        return changed;
    }

    /**
     * Moves an item from a corpse or an open external container into the
     * player's actual inventory slots. This is intentionally separate from
     * {@link #moveIntoPlayerDelta}: container transfer must never auto-equip
     * gear or silently insert it into a worn chest rig, backpack or safety box.
     */
    public static boolean moveIntoPlayerInventory(ServerPlayer player, ItemStack stack) {
        if (player == null || stack == null || stack.isEmpty()) return false;
        Inventory inventory = player.getInventory();
        boolean changed = false;
        for (int slot = 0; slot < Inventory.INVENTORY_SIZE && !stack.isEmpty(); slot++) {
            if (!PlayerLayoutSlotRules.canPlace(player, slot, stack)) continue;
            ItemStack existing = inventory.getItem(slot);
            if (existing.isEmpty() || !ItemStack.isSameItemSameComponents(existing, stack)) continue;
            int amount = Math.min(stack.getCount(), Math.min(existing.getMaxStackSize(),
                stack.getMaxStackSize()) - existing.getCount());
            if (amount <= 0) continue;
            existing.grow(amount);
            stack.shrink(amount);
            changed = true;
        }
        for (int slot = 0; slot < Inventory.INVENTORY_SIZE && !stack.isEmpty(); slot++) {
            if (!PlayerLayoutSlotRules.canPlace(player, slot, stack)
                || !inventory.getItem(slot).isEmpty()) continue;
            int amount = Math.min(stack.getCount(), stack.getMaxStackSize());
            inventory.setItem(slot, stack.copyWithCount(amount));
            stack.shrink(amount);
            changed = true;
        }
        if (changed) {
            inventory.setChanged();
            player.inventoryMenu.broadcastChanges();
            if (player.containerMenu != player.inventoryMenu) {
                player.containerMenu.broadcastChanges();
            }
        }
        return changed;
    }

    /** Equips a TACZ weapon into its legal primary/sidearm loadout slot. */
    public static boolean equipWeaponLoadout(ServerPlayer player, ItemStack stack) {
        if (player == null || stack == null || stack.isEmpty()) return false;
        return DeltaPackAutoEquipService.tryQuickEquipWeaponLoadout(player, stack);
    }

    public static boolean moveFromPlayerStorage(ServerPlayer player, ItemStack stack,
                                                String sourceStorage) {
        if (player == null || stack == null || stack.isEmpty()) return false;
        boolean carrierItem = stack.getItem() instanceof DeltaPackItem pack
            && ("chest_rig".equals(pack.slotIdentifier()) || "backpack".equals(pack.slotIdentifier()));
        if (!carrierItem && tryQuickEquipLoadout(player, stack)) return true;
        if (player.containerMenu != player.inventoryMenu) {
            return moveIntoExternalContainer(player, stack);
        }
        boolean moved = false;
        long value = ModDataStorage.get(player.serverLevel()).getPriceFor(stack);
        if (!"safety_box".equals(sourceStorage)
            && value >= Config.INSTANCE.quickMoveValueThreshold.get()) {
            moved |= BetterLootingPickupCompat.storeInSafetyBox(player, stack);
            if (stack.isEmpty()) return true;
        }
        if (!"backpack".equals(sourceStorage)) {
            moved |= DeltaPackAutoEquipService.tryStoreInEquippedCarrier(
                player, stack, "backpack");
        }
        if (!stack.isEmpty() && !"chest_rig".equals(sourceStorage)) {
            moved |= DeltaPackAutoEquipService.tryStoreInEquippedCarrier(
                player, stack, "chest_rig");
        }
        return moved;
    }

    public static boolean moveIntoExternalContainer(ServerPlayer player, ItemStack stack) {
        AbstractContainerMenu menu = player.containerMenu;
        if (menu == null || menu == player.inventoryMenu || stack.isEmpty()) return false;
        if (Config.INSTANCE.itemGridEnabled.get()
            && ContainerGridRules.isScreenEnabled(menu.getClass().getName())) {
            ContainerGridHelper.refresh(menu, ModDataStorage::getCachedSizeFor);
        }

        Set<Slot> gridTargets = Collections.newSetFromMap(new IdentityHashMap<>());
        boolean moved = false;
        for (Slot slot : menu.slots) {
            if (!isExternalTarget(slot) || stack.isEmpty()) continue;
            if (ContainerGridHelper.isGridSlotEnabled(menu, slot)) {
                gridTargets.add(slot);
                continue;
            }
            ItemStack target = slot.getItem();
            if (target.isEmpty() || !slot.mayPlace(stack)
                || !GridBackingStore.isSameItemIgnoringRotation(target, stack)) continue;
            int limit = Math.min(slot.getMaxStackSize(target), target.getMaxStackSize());
            int amount = Math.min(stack.getCount(), limit - target.getCount());
            if (amount <= 0) continue;
            ItemStack insertion = stack.copyWithCount(amount);
            int inserted = amount - slot.safeInsert(insertion, amount).getCount();
            if (inserted > 0) {
                stack.shrink(inserted);
                slot.setChanged();
                moved = true;
            }
        }
        if (!stack.isEmpty() && !gridTargets.isEmpty()) {
            moved |= ContainerGridHelper.transferIntoGrid(menu, stack, gridTargets, false,
                ModDataStorage::getCachedSizeFor);
        }
        for (Slot slot : menu.slots) {
            if (stack.isEmpty()) break;
            if (!isExternalTarget(slot)
                || ContainerGridHelper.isGridSlotEnabled(menu, slot)
                || !ContainerGridHelper.isSafeNonGridTransferTarget(menu, slot)
                || !slot.getItem().isEmpty() || !slot.mayPlace(stack)) continue;
            int amount = Math.min(stack.getCount(),
                Math.min(slot.getMaxStackSize(stack), stack.getMaxStackSize()));
            if (amount <= 0) continue;
            ItemStack insertion = stack.copyWithCount(amount);
            int inserted = amount - slot.safeInsert(insertion, amount).getCount();
            if (inserted > 0) {
                stack.shrink(inserted);
                slot.setChanged();
                moved = true;
            }
        }
        if (moved) {
            ContainerGridHelper.invalidate(menu);
            menu.broadcastChanges();
        }
        return moved;
    }

    private static boolean tryQuickEquipLoadout(ServerPlayer player, ItemStack stack) {
        if (equipWeaponLoadout(player, stack)) return true;
        return DeltaPackAutoEquipService.tryQuickEquipLoadout(player, stack,
            (identifier, displaced) -> {
                if (player.containerMenu != player.inventoryMenu) {
                    return moveIntoExternalContainer(player, displaced);
                }
                String alternate = "chest_rig".equals(identifier)
                    ? "backpack" : "chest_rig";
                return DeltaPackAutoEquipService.tryStoreInEquippedCarrier(
                    player, displaced, alternate);
            });
    }

    private static boolean isExternalTarget(Slot slot) {
        return slot != null && slot.isActive() && !(slot.container instanceof Inventory);
    }
}
