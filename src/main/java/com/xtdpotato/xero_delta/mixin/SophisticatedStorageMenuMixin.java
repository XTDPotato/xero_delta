package com.xtdpotato.xero_delta.mixin;

import com.xtdpotato.xero_delta.Config;
import com.xtdpotato.xero_delta.data.ContainerGridRules;
import com.xtdpotato.xero_delta.data.ItemSize;
import com.xtdpotato.xero_delta.data.ModDataStorage;
import com.xtdpotato.xero_delta.grid.ContainerGridHelper;
import com.xtdpotato.xero_delta.grid.ContainerGridNormalizer;
import com.xtdpotato.xero_delta.grid.DeltaQuickMoveService;
import com.xtdpotato.xero_delta.data.PlayerLayoutSlotRules;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * Optional integration for Sophisticated Core's private storage transfer
 * path. Its normal shift-click implementation does not call vanilla
 * moveItemStackTo, so large grid items would otherwise be treated as 1x1.
 */
@Pseudo
@Mixin(targets = "net.p3pp3rf1y.sophisticatedcore.common.gui.StorageContainerMenuBase", remap = false)
public abstract class SophisticatedStorageMenuMixin {
    @Inject(method = "mergeStackToStorage", at = @At("HEAD"), cancellable = true, remap = false)
    private void xero$mergeSizedStack(Slot source, ItemStack stack,
                                      CallbackInfoReturnable<Boolean> cir) {
        if (stack.isEmpty() || !Config.INSTANCE.itemGridEnabled.get()) return;
        AbstractContainerMenu menu = (AbstractContainerMenu) (Object) this;
        if (!ContainerGridRules.isScreenEnabled(menu.getClass().getName())) return;
        ContainerGridHelper.refresh(menu, ModDataStorage::getCachedSizeFor);
        Set<Slot> storageSlots = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Slot slot : menu.slots) {
            if (slot != source && ContainerGridHelper.isStorageInventorySlot(menu, slot)) storageSlots.add(slot);
        }
        if (storageSlots.isEmpty()) return;

        boolean hasLargeStorageItem = storageSlots.stream().anyMatch(slot -> {
            ItemStack stored = slot.getItem();
            if (stored.isEmpty()) return false;
            ItemSize storedSize = ModDataStorage.getCachedSizeFor(stored);
            return storedSize.width() > 1 || storedSize.height() > 1;
        });
        ItemSize sourceSize = ModDataStorage.getCachedSizeFor(stack);
        boolean largeSource = sourceSize.width() > 1 || sourceSize.height() > 1;
        // With no multi-cell item present, keep Sophisticated's native path so
        // its overflow/infinite/filter upgrades retain their exact behavior.
        if (!hasLargeStorageItem && !largeSource) return;

        boolean moved = ContainerGridHelper.transferIntoGrid(menu, stack, storageSlots, false,
            ModDataStorage::getCachedSizeFor);
        if (moved) {
            ContainerGridHelper.invalidate(menu);
            ContainerGridHelper.synchronizeStorageMenu(menu);
        }
        // Never fall back to Sophisticated's 1x1 merge for a storage range:
        // covered cells are physically empty and that fallback creates overlap.
        cir.setReturnValue(moved);
    }

    /** Move into player slots without treating covered grid cells as empty slots. */
    @Inject(method = "mergeStackToPlayersInventory", at = @At("HEAD"), cancellable = true, remap = false)
    private void xero$mergeSizedStackToPlayer(Slot source, ItemStack stack,
                                               CallbackInfoReturnable<Boolean> cir) {
        if (stack.isEmpty()) return;
        AbstractContainerMenu menu = (AbstractContainerMenu) (Object) this;
        ServerPlayer player = xero$serverPlayer(menu);
        if (player != null && PlayerLayoutSlotRules.enabled(player)) {
            cir.setReturnValue(DeltaQuickMoveService.moveIntoPlayerDelta(player, stack));
            return;
        }
        if (!Config.INSTANCE.itemGridEnabled.get()) return;
        if (!ContainerGridRules.isScreenEnabled(menu.getClass().getName())) return;

        ContainerGridHelper.refresh(menu, ModDataStorage::getCachedSizeFor);
        boolean moved = ContainerGridHelper.transferIntoPlayerInventory(menu, stack, true,
            ModDataStorage::getCachedSizeFor);
        // Never fall through to Sophisticated's physical-slot transfer. A
        // covered grid cell is empty in the backing inventory but unavailable
        // to quick move, so the native fallback can duplicate or overlap items.
        cir.setReturnValue(moved);
    }

    @Inject(method = "sort", at = @At("HEAD"), cancellable = true, remap = false)
    private void xero$sortLogicalGrid(CallbackInfo ci) {
        AbstractContainerMenu menu = (AbstractContainerMenu) (Object) this;
        ServerPlayer player = xero$serverPlayer(menu);
        if (player != null && Config.INSTANCE.itemGridEnabled.get()
            && ContainerGridRules.isScreenEnabled(menu.getClass().getName())) {
            // The native sorter treats every physical slot as an independent
            // 1x1 cell. Always consume the server-side call, even if a corrupt
            // layout cannot be repacked: sortAndRepack rolls back atomically.
            ContainerGridNormalizer.sortAndRepack(player, menu);
            ci.cancel();
        }
    }

    @Inject(method = "quickMoveStack", at = @At("RETURN"), remap = false)
    private void xero$validateAfterQuickMove(net.minecraft.world.entity.player.Player ignored, int slotIndex,
                                             CallbackInfoReturnable<ItemStack> cir) {
        AbstractContainerMenu menu = (AbstractContainerMenu) (Object) this;
        ServerPlayer player = xero$serverPlayer(menu);
        if (player != null && Config.INSTANCE.itemGridEnabled.get()
            && ContainerGridRules.isScreenEnabled(menu.getClass().getName())) {
            ContainerGridNormalizer.normalize(player, menu);
        }
    }

    @Unique
    private static ServerPlayer xero$serverPlayer(AbstractContainerMenu menu) {
        for (Slot slot : menu.slots) {
            if (slot.container instanceof Inventory inventory
                && inventory.player instanceof ServerPlayer player) return player;
        }
        return null;
    }
}
