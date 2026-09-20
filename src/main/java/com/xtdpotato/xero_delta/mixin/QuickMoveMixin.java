package com.xtdpotato.xero_delta.mixin;

import com.xtdpotato.xero_delta.Config;
import com.xtdpotato.xero_delta.data.ContainerGridRules;
import com.xtdpotato.xero_delta.data.ModDataStorage;
import com.xtdpotato.xero_delta.grid.ContainerGridHelper;
import com.xtdpotato.xero_delta.grid.GridBackingStore;
import com.xtdpotato.xero_delta.grid.DeltaQuickMoveService;
import com.xtdpotato.xero_delta.data.PlayerLayoutSlotRules;
import com.xtdpotato.xero_delta.data.KnifeSkinRules;
import com.xtdpotato.xero_delta.item.SafetyBoxItem;
import com.xtdpotato.xero_delta.tag.ModTags;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.theillusivec4.curios.api.CuriosApi;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

@Mixin(AbstractContainerMenu.class)
public class QuickMoveMixin {

    @Inject(method = "moveItemStackTo", at = @At("HEAD"), cancellable = true)
    private void onMoveItemStackTo(ItemStack stack, int start, int end, boolean rev, CallbackInfoReturnable<Boolean> cir) {
        if (stack.isEmpty()) return;
        var self = (AbstractContainerMenu)(Object) this;
        if (KnifeSkinRules.blocksPlayerInventoryAction(xero$menuPlayer(self), stack)) {
            cir.setReturnValue(false);
            return;
        }
        if (Config.INSTANCE.itemGridEnabled.get()
            && ContainerGridRules.isScreenEnabled(self.getClass().getName())) {
            ContainerGridHelper.refresh(self, ModDataStorage::getCachedSizeFor);
        }
        if (xero$tryMoveToSafetyBox(self, stack, start, end, cir)) return;
        if (xero$moveItemStackWithGrid(self, stack, start, end, rev, cir)) return;
    }

    private static boolean xero$tryMoveToSafetyBox(AbstractContainerMenu menu, ItemStack stack, int start, int end,
                                                    CallbackInfoReturnable<Boolean> cir) {
        boolean toPlayer = false;
        for (int i = start; i < end && i < menu.slots.size(); i++) {
            if (menu.slots.get(i).container instanceof Inventory) { toPlayer = true; break; }
        }
        if (!toPlayer) return false;

        ServerPlayer sp = null;
        for (Slot s : menu.slots) {
            if (s.container instanceof Inventory inv && inv.player instanceof ServerPlayer p) { sp = p; break; }
        }
        if (sp == null) return false;
        if (PlayerLayoutSlotRules.enabled(sp)) {
            boolean moved = DeltaQuickMoveService.moveIntoPlayerDelta(sp, stack);
            cir.setReturnValue(moved);
            if (!moved) {
                sp.displayClientMessage(Component.translatable(
                    "storage.xero_delta.no_space_move"), true);
            }
            return true;
        }
        if (!Config.INSTANCE.quickMoveEnabled.get()) return false;
        if (!ContainerGridHelper.canTransferIntoPlayerInventory(menu, stack, true,
            ModDataStorage::getCachedSizeFor)) return false;

        long val = ModDataStorage.get(sp.serverLevel()).getPriceFor(stack);
        int threshold = Config.INSTANCE.quickMoveValueThreshold.get();
        if (val < threshold) return false;

        if (stack.is(ModTags.SAFETY_BOX)) return false;
        if (Config.INSTANCE.isBlacklisted(stack)) return false;

        ItemStack boxStack = findSafetyBox(sp);
        if (boxStack.isEmpty() || !(boxStack.getItem() instanceof SafetyBoxItem sbi)) return false;

        GridBackingStore store = new GridBackingStore(boxStack, sbi.getGridWidth(), sbi.getGridHeight());

        GridBackingStore.PlacementResult placement = store.findFreePlacement(stack);
        if (placement.status() == GridBackingStore.PlacementStatus.CAN_PLACE) {
            ItemStack toMove = stack.copy();
            if (store.place(placement.x(), placement.y(), toMove, placement.rotated())) {
                stack.setCount(0);
                cir.setReturnValue(true);
                return true;
            }
        }
        return false;
    }

    private static Player xero$menuPlayer(AbstractContainerMenu menu) {
        for (Slot slot : menu.slots) {
            if (slot.container instanceof Inventory inventory) return inventory.player;
        }
        return null;
    }

    private static ItemStack findSafetyBox(ServerPlayer player) {
        try {
            var opt = CuriosApi.getCuriosInventory(player);
            if (opt.isPresent()) {
                var res = opt.get().findFirstCurio(s -> s.is(ModTags.SAFETY_BOX));
                if (res.isPresent()) return res.get().stack();
            }
        } catch (Exception ignored) {}
        return ItemStack.EMPTY;
    }

    private static boolean xero$moveItemStackWithGrid(AbstractContainerMenu menu, ItemStack stack, int start, int end,
                                                       boolean reversed, CallbackInfoReturnable<Boolean> cir) {
        if (!Config.INSTANCE.itemGridEnabled.get()) return false;
        if (!ContainerGridRules.isScreenEnabled(menu.getClass().getName())) return false;
        // Sophisticated has its own mergeStackToStorage path, intercepted by
        // SophisticatedStorageMenuMixin. Running both transfer engines can
        // place the stack back into player slots or evaluate stale occupancy.
        if (ContainerGridHelper.usesStorageAdapter(menu)) return false;
        boolean hasGridTarget = false;
        for (int slotIndex = start; slotIndex < end && slotIndex < menu.slots.size(); slotIndex++) {
            if (slotIndex >= 0 && ContainerGridHelper.isGridSlotEnabled(menu, menu.slots.get(slotIndex))) {
                hasGridTarget = true;
                break;
            }
        }
        if (!hasGridTarget) return false;

        boolean moved = false;
        moved |= xero$mergeIntoExisting(menu, stack, start, end, reversed);
        moved |= xero$placeIntoGrid(menu, stack, start, end, reversed);
        moved |= xero$placeIntoNonGrid(menu, stack, start, end, reversed);

        cir.setReturnValue(moved);
        return true;
    }

    private static boolean xero$mergeIntoExisting(AbstractContainerMenu menu, ItemStack stack, int start, int end,
                                                    boolean reversed) {
        boolean moved = false;
        int slotIndex = reversed ? end - 1 : start;
        while (!stack.isEmpty() && (reversed ? slotIndex >= start : slotIndex < end)) {
            if (slotIndex >= 0 && slotIndex < menu.slots.size()) {
                Slot slot = menu.slots.get(slotIndex);
                boolean usesGrid = ContainerGridHelper.isGridSlotEnabled(menu, slot);
                if (!usesGrid
                    && !ContainerGridHelper.isSafeNonGridTransferTarget(menu, slot)) {
                    slotIndex += reversed ? -1 : 1;
                    continue;
                }
                ItemStack target = slot.getItem();
                if (target.isEmpty()) {
                    slotIndex += reversed ? -1 : 1;
                    continue;
                }
                if (target != stack
                    && ContainerGridHelper.isSameGridItem(target, stack) && slot.mayPlace(stack)) {
                    int limit = Math.min(slot.getMaxStackSize(target), target.getMaxStackSize());
                    int move = Math.min(stack.getCount(), limit - target.getCount());
                    if (move > 0) {
                        ItemStack insertion = stack.copyWithCount(move);
                        GridBackingStore.setRotated(insertion, GridBackingStore.isRotated(target));
                        int inserted = xero$quickMoveSafeInsert(slot, insertion, move);
                        if (inserted > 0) {
                            stack.shrink(inserted);
                            moved = true;
                        }
                    }
                }
            }
            slotIndex += reversed ? -1 : 1;
        }
        return moved;
    }

    private static boolean xero$placeIntoGrid(AbstractContainerMenu menu, ItemStack stack, int start, int end,
                                               boolean reversed) {
        Set<Slot> allowed = Collections.newSetFromMap(new IdentityHashMap<>());
        for (int slotIndex = Math.max(0, start); slotIndex < end && slotIndex < menu.slots.size(); slotIndex++) {
            Slot slot = menu.slots.get(slotIndex);
            if (ContainerGridHelper.isGridSlotEnabled(menu, slot)) allowed.add(slot);
        }
        if (allowed.isEmpty()) return false;

        boolean moved = false;
        while (!stack.isEmpty()) {
            var placement = ContainerGridHelper.findQuickMovePlacement(menu, allowed, stack,
                GridBackingStore.isRotated(stack), reversed, ModDataStorage::getCachedSizeFor);
            Slot anchor = placement.anchor();
            if (placement.status() != GridBackingStore.PlacementStatus.CAN_PLACE || anchor == null) break;
            int move = Math.min(stack.getCount(), Math.min(anchor.getMaxStackSize(stack), stack.getMaxStackSize()));
            if (move <= 0) break;
            ItemStack placed = stack.copyWithCount(move);
            GridBackingStore.setRotated(placed, placement.rotated());
            int inserted = xero$quickMoveSafeInsert(anchor, placed, move);
            if (inserted <= 0) break;
            stack.shrink(inserted);
            moved = true;
            ContainerGridHelper.invalidate(menu);
            ContainerGridHelper.prepare(menu, ModDataStorage::getCachedSizeFor);
        }
        return moved;
    }

    private static boolean xero$placeIntoNonGrid(AbstractContainerMenu menu, ItemStack stack, int start, int end,
                                                  boolean reversed) {
        boolean moved = false;
        int slotIndex = reversed ? end - 1 : start;
        while (!stack.isEmpty() && (reversed ? slotIndex >= start : slotIndex < end)) {
            if (slotIndex >= 0 && slotIndex < menu.slots.size()) {
                Slot slot = menu.slots.get(slotIndex);
                boolean usesGrid = ContainerGridHelper.isGridSlotEnabled(menu, slot);
                boolean canPlace = !usesGrid && ContainerGridHelper.isSafeNonGridTransferTarget(menu, slot)
                    && slot.getItem().isEmpty() && slot.mayPlace(stack);
                if (canPlace) {
                    int move = Math.min(stack.getCount(), Math.min(slot.getMaxStackSize(stack), stack.getMaxStackSize()));
                    if (move > 0) {
                        int inserted = xero$quickMoveSafeInsert(slot, stack.copyWithCount(move), move);
                        if (inserted > 0) {
                            stack.shrink(inserted);
                            moved = true;
                        }
                    }
                }
            }
            slotIndex += reversed ? -1 : 1;
        }
        return moved;
    }

    private static int xero$quickMoveSafeInsert(Slot slot, ItemStack insertion, int limit) {
        int before = insertion.getCount();
        ItemStack remainder = slot.safeInsert(insertion, limit);
        int inserted = before - remainder.getCount();
        if (inserted > 0) slot.setChanged();
        return Math.max(0, inserted);
    }
}
