package com.xtdpotato.xero_delta.mixin;

import com.xtdpotato.xero_delta.Config;
import com.xtdpotato.xero_delta.data.ItemSize;
import com.xtdpotato.xero_delta.data.KnifeSkinRules;
import com.xtdpotato.xero_delta.data.ModDataStorage;
import com.xtdpotato.xero_delta.data.PlayerLayoutSlotRules;
import com.xtdpotato.xero_delta.grid.GridBackingStore;
import com.xtdpotato.xero_delta.grid.DeltaQuickMoveService;
import com.xtdpotato.xero_delta.compat.DeltaPackAutoEquipService;
import com.xtdpotato.xero_delta.network.ModNetwork;
import net.minecraft.core.NonNullList;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

@Mixin(Inventory.class)
public abstract class InventoryMixin {
    @Shadow public NonNullList<ItemStack> items;
    @Shadow public Player player;
    @Shadow public abstract void setChanged();

    @Inject(method = "removeFromSelected", at = @At("HEAD"), cancellable = true)
    private void xero$preventKnifeDrop(boolean wholeStack, CallbackInfoReturnable<ItemStack> cir) {
        Inventory inventory = (Inventory) (Object) this;
        if (KnifeSkinRules.blocksPlayerInventoryAction(player, inventory.getSelected())) {
            cir.setReturnValue(ItemStack.EMPTY);
        }
    }

    @Inject(method = "add(Lnet/minecraft/world/item/ItemStack;)Z", at = @At("HEAD"), cancellable = true)
    private void xero$addSizedItem(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (stack.isEmpty() || !Config.INSTANCE.itemGridEnabled.get()) return;
        DeltaPackAutoEquipService.PickupTransaction pickup = null;
        if (player instanceof ServerPlayer serverPlayer
            && PlayerLayoutSlotRules.enabled(serverPlayer)) {
            pickup = DeltaPackAutoEquipService.prepareCarrierPickup(serverPlayer, stack);
            if (pickup.preparation() == DeltaPackAutoEquipService.PickupPreparation.BLOCKED) {
                ModNetwork.sendTranslatedNoticePlain(serverPlayer,
                    "storage.xero_delta.ground_pack_contents_no_space");
                cir.setReturnValue(false);
                return;
            }
        }
        if (player instanceof ServerPlayer serverPlayer
            && PlayerLayoutSlotRules.enabled(serverPlayer)
            && serverPlayer.containerMenu != serverPlayer.inventoryMenu) {
            // External container transfers must skip the 1x1 hotbar/pocket
            // path; route the whole stack through equipped Delta carriers.
            boolean changed = DeltaQuickMoveService.moveIntoPlayerDelta(serverPlayer, stack);
            xero$finishCarrierPickup(pickup, stack);
            cir.setReturnValue(changed);
            return;
        }

        boolean changed = xero$insertSizedItem(stack);
        xero$finishCarrierPickup(pickup, stack);
        cir.setReturnValue(changed);
    }

    @Unique
    private static void xero$finishCarrierPickup(
        DeltaPackAutoEquipService.PickupTransaction pickup, ItemStack remainder) {
        if (pickup == null) return;
        if (pickup.preparation() == DeltaPackAutoEquipService.PickupPreparation.READY
            && remainder != null && !remainder.isEmpty()) {
            pickup.rollback();
        } else {
            pickup.commit();
        }
    }

    @Inject(method = "placeItemBackInInventory(Lnet/minecraft/world/item/ItemStack;Z)V",
        at = @At("HEAD"), cancellable = true)
    private void xero$placeSizedItemBack(ItemStack stack, boolean sendPacket, CallbackInfo ci) {
        if (stack.isEmpty() || !Config.INSTANCE.itemGridEnabled.get()) return;

        List<ItemStack> before = List.of();
        if (sendPacket && player instanceof ServerPlayer) {
            before = new ArrayList<>(Inventory.INVENTORY_SIZE);
            for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
                before.add(items.get(slot).copy());
            }
        }

        boolean changed = xero$insertSizedItem(stack);
        if (!stack.isEmpty()) player.drop(stack, false);
        if (changed && sendPacket && player instanceof ServerPlayer serverPlayer) {
            for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
                if (xero$sameStack(before.get(slot), items.get(slot))) continue;
                serverPlayer.connection.send(new ClientboundContainerSetSlotPacket(
                    -2, 0, slot, items.get(slot).copy()));
            }
        }
        ci.cancel();
    }

    @Unique
    private boolean xero$insertSizedItem(ItemStack stack) {

        boolean changed = false;
        changed |= xero$stackIntoExisting(stack);
        while (!stack.isEmpty()) {
            long placement = xero$findInventoryPlacement(stack);
            if (placement < 0) break;
            int move = Math.min(stack.getCount(), stack.getMaxStackSize());
            ItemStack placed = stack.copyWithCount(move);
            GridBackingStore.setRotated(placed, (placement & 1L) != 0L);
            items.set((int) (placement >>> 1), placed);
            stack.shrink(move);
            changed = true;
        }

        if (changed) setChanged();
        return changed;
    }

    @Unique
    private boolean xero$stackIntoExisting(ItemStack stack) {
        boolean changed = false;
        for (int i = 0; i < Inventory.INVENTORY_SIZE && !stack.isEmpty(); i++) {
            if (player instanceof ServerPlayer serverPlayer
                && !PlayerLayoutSlotRules.canPlace(serverPlayer, i, stack)) continue;
            ItemStack target = items.get(i);
            if (target.isEmpty() || !GridBackingStore.isSameItemIgnoringRotation(target, stack)) continue;
            if (i >= Inventory.getSelectionSize()) {
                int x = i % 9;
                int y = (i - Inventory.getSelectionSize()) / 9;
                if (xero$anchorAt(false, x, y) != i) continue;
            }
            int limit = Math.min(target.getMaxStackSize(), stack.getMaxStackSize());
            int move = Math.min(stack.getCount(), limit - target.getCount());
            if (move <= 0) continue;
            target.grow(move);
            stack.shrink(move);
            changed = true;
        }
        return changed;
    }

    @Unique
    private long xero$findInventoryPlacement(ItemStack stack) {
        boolean preferredRotated = GridBackingStore.isRotated(stack);
        boolean[] rotations = preferredRotated ? new boolean[]{true, false} : new boolean[]{false, true};
        for (boolean rotated : rotations) {
            ItemSize size = GridBackingStore.orientedSize(stack, rotated);
            if (rotated != preferredRotated && size.equals(GridBackingStore.orientedSize(stack, preferredRotated))) continue;
            // Vanilla pickup can use an empty hotbar slot before falling back
            // to the three-row main inventory. The hotbar branch above is
            // intentionally independent from the main-inventory footprint.
            for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
                if (!items.get(slot).isEmpty()) continue;
                if (player instanceof ServerPlayer serverPlayer
                    && !PlayerLayoutSlotRules.canPlace(serverPlayer, slot, stack)) continue;
                if (xero$canPlaceAt(slot, size)) return ((long) slot << 1) | (rotated ? 1L : 0L);
            }
        }
        return -1L;
    }

    @Unique
    private boolean xero$canPlaceAt(int slot, ItemSize size) {
        // The hotbar is vanilla storage: every slot is an independent 1x1
        // cell, even when the item itself has a larger backpack footprint.
        // Do not let a 2x2 item in one hotbar slot reserve its neighbours or
        // reject pickup into another empty hotbar slot.
        if (slot < Inventory.getSelectionSize()) return true;

        int x = slot % 9;
        int y = (slot - Inventory.getSelectionSize()) / 9;
        int maxHeight = 3;
        if (x + size.width() > 9 || size.height() > maxHeight
            || y + size.height() > maxHeight) return false;
        for (int yy = y; yy < y + size.height(); yy++) {
            for (int xx = x; xx < x + size.width(); xx++) {
                if (xero$anchorAt(false, xx, yy) >= 0) return false;
            }
        }
        return true;
    }

    @Unique
    private int xero$anchorAt(boolean hotbar, int x, int y) {
        int start = hotbar ? 0 : Inventory.getSelectionSize();
        int end = hotbar ? Inventory.getSelectionSize() : Inventory.INVENTORY_SIZE;
        for (int slot = start; slot < end; slot++) {
            ItemStack stack = items.get(slot);
            if (stack.isEmpty()) continue;
            ItemSize size = GridBackingStore.sizeOfStored(stack);
            int ax = hotbar ? slot : slot % 9;
            int ay = hotbar ? 0 : (slot - Inventory.getSelectionSize()) / 9;
            if (x >= ax && x < ax + size.width() && y >= ay && y < ay + size.height()) return slot;
        }
        return -1;
    }

    @Unique
    private static boolean xero$sameStack(ItemStack first, ItemStack second) {
        if (first.isEmpty() || second.isEmpty()) return first.isEmpty() && second.isEmpty();
        return first.getCount() == second.getCount()
            && ItemStack.isSameItemSameComponents(first, second);
    }

}
