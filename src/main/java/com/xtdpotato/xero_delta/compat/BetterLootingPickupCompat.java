package com.xtdpotato.xero_delta.compat;

import com.xtdpotato.xero_delta.data.SafetyBoxAccessData;
import com.xtdpotato.xero_delta.data.PlayerLayoutSlotRules;
import com.xtdpotato.xero_delta.grid.GridBackingStore;
import com.xtdpotato.xero_delta.item.SafetyBoxItem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;

/** Server-side pickup bridge for Better Looting's direct Inventory.add call. */
public final class BetterLootingPickupCompat {
    private static final String SAFETY_BOX_SLOT = "safety_box";

    private BetterLootingPickupCompat() {}

    /**
     * Delta pickup auto-equips eligible gear, then tries backpack, chest rig,
     * safety box and finally the five 1x1 pocket slots. Items stay on the ground
     * when every legal destination is full.
     */
    public static boolean addToInventoryAndExistingSafetyBox(Inventory inventory,
                                                              ItemStack remainder) {
        if (!(inventory.player instanceof ServerPlayer player)
            || !PlayerLayoutSlotRules.enabled(player)) {
            return inventory.add(remainder);
        }
        DeltaPackAutoEquipService.PickupTransaction transaction =
            DeltaPackAutoEquipService.prepareCarrierPickup(player, remainder);
        if (transaction.preparation() == DeltaPackAutoEquipService.PickupPreparation.BLOCKED) {
            com.xtdpotato.xero_delta.network.ModNetwork.sendTranslatedNoticePlain(
                player, "storage.xero_delta.ground_pack_contents_no_space");
            return false;
        }
        boolean changed = DeltaPackAutoEquipService.tryAutoEquipArmorPickup(player, remainder);
        if (!remainder.isEmpty()) {
            changed |= DeltaPackAutoEquipService.tryEquipFromStack(player, remainder);
        }
        if (!remainder.isEmpty()) {
            changed |= storePickupRemainder(player, remainder);
        }
        if (transaction.preparation() == DeltaPackAutoEquipService.PickupPreparation.READY
            && !remainder.isEmpty()) {
            transaction.rollback();
            return false;
        }
        transaction.commit();
        return changed;
    }

    /** Continues a Delta pickup through every storage fallback without duplicating partial stacks. */
    public static boolean storePickupRemainder(ServerPlayer player, ItemStack remainder) {
        if (player == null || remainder == null || remainder.isEmpty()) return false;
        boolean changed = DeltaPackAutoEquipService.tryAutoEquipTaczGunPickup(player, remainder);
        if (!remainder.isEmpty()) {
            changed |= DeltaPackAutoEquipService.tryStoreInEquippedBackpack(player, remainder);
        }
        if (!remainder.isEmpty()) changed |= storeInSafetyBox(player, remainder);
        if (!remainder.isEmpty()) changed |= storeInPockets(player, remainder);
        return changed;
    }

    public static boolean storeInSafetyBox(ServerPlayer player, ItemStack remainder) {
        if (remainder.isEmpty()) return false;

        var curiosOptional = CuriosApi.getCuriosInventory(player);
        if (curiosOptional.isEmpty()) return false;
        var curios = curiosOptional.get();
        var slotHandler = curios.getStacksHandler(SAFETY_BOX_SLOT).orElse(null);
        if (slotHandler == null || slotHandler.getSlots() <= 0
            || !curios.isSlotActive(SAFETY_BOX_SLOT, 0)) {
            return false;
        }

        ItemStack carrier = slotHandler.getStacks().getStackInSlot(0);
        if (!(carrier.getItem() instanceof SafetyBoxItem box)) return false;
        String itemId = carrier.getItemHolder().getKey().location().toString();
        if (!SafetyBoxAccessData.get(player.server).isUnlocked(
            player.getUUID(), itemId, System.currentTimeMillis())) {
            return false;
        }

        GridBackingStore store = new GridBackingStore(
            carrier, box.getGridWidth(), box.getGridHeight());
        boolean changed = stackIntoExisting(store, remainder);
        if (!remainder.isEmpty()) {
            GridBackingStore.PlacementResult placement = store.findFreePlacement(remainder);
            if (placement.status() == GridBackingStore.PlacementStatus.CAN_PLACE
                && store.place(placement.x(), placement.y(), remainder, placement.rotated())) {
                remainder.setCount(0);
                changed = true;
            }
        }
        if (!changed) return false;

        curios.setEquippedCurio(SAFETY_BOX_SLOT, 0, carrier.copy());
        slotHandler.update();
        player.getInventory().setChanged();
        player.inventoryMenu.broadcastChanges();
        if (player.containerMenu != player.inventoryMenu) {
            player.containerMenu.broadcastChanges();
        }
        return true;
    }

    public static boolean storeInPockets(ServerPlayer player, ItemStack remainder) {
        if (player == null || remainder == null || remainder.isEmpty()
            || !PlayerLayoutSlotRules.canPlace(player, 4, remainder)) return false;
        boolean changed = false;
        Inventory inventory = player.getInventory();
        for (int slot = 4; slot <= 8 && !remainder.isEmpty(); slot++) {
            ItemStack existing = inventory.getItem(slot);
            if (existing.isEmpty()
                || !ItemStack.isSameItemSameComponents(existing, remainder)) continue;
            int move = Math.min(remainder.getCount(),
                Math.min(existing.getMaxStackSize(), inventory.getMaxStackSize()) - existing.getCount());
            if (move <= 0) continue;
            existing.grow(move);
            remainder.shrink(move);
            changed = true;
        }
        for (int slot = 4; slot <= 8 && !remainder.isEmpty(); slot++) {
            if (!inventory.getItem(slot).isEmpty()
                || !PlayerLayoutSlotRules.canPlace(player, slot, remainder)) continue;
            int move = Math.min(remainder.getCount(),
                Math.min(remainder.getMaxStackSize(), inventory.getMaxStackSize()));
            inventory.setItem(slot, remainder.copyWithCount(move));
            remainder.shrink(move);
            changed = true;
        }
        if (changed) sync(player);
        return changed;
    }

    static boolean stackIntoExisting(GridBackingStore store, ItemStack remainder) {
        boolean changed = false;
        for (int index = 0; index < store.getSize() && !remainder.isEmpty(); index++) {
            int x = index % store.getWidth();
            int y = index / store.getWidth();
            if (store.canStackAt(x, y, remainder)) {
                changed |= store.stackInto(x, y, remainder);
            }
        }
        return changed;
    }

    private static void sync(ServerPlayer player) {
        player.getInventory().setChanged();
        player.inventoryMenu.broadcastChanges();
        if (player.containerMenu != player.inventoryMenu) {
            player.containerMenu.broadcastChanges();
        }
    }
}
