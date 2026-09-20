package com.xtdpotato.xero_delta.grid;

import com.xtdpotato.xero_delta.ModDataComponents;
import com.xtdpotato.xero_delta.data.ModDataStorage;
import com.xtdpotato.xero_delta.item.DeltaPackItem;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Shared, server-side support for moving a Delta chest rig/backpack into
 * another grid.  The carrier itself is stored empty and its former contents
 * are inserted beside it.  Callers own the surrounding snapshot/rollback.
 */
public final class DeltaPackTransferService {
    public record Payload(ItemStack emptyCarrier, List<ItemStack> contents) {
    }

    private DeltaPackTransferService() {
    }

    public static Payload payload(ItemStack carried) {
        if (carried == null || carried.isEmpty() || carried.getCount() != 1
            || !(carried.getItem() instanceof DeltaPackItem pack)
            || (!"chest_rig".equals(pack.slotIdentifier())
                && !"backpack".equals(pack.slotIdentifier()))) {
            return null;
        }

        GridBackingStore source = new GridBackingStore(
            carried, pack.gridWidth(), pack.gridHeight(), 0,
            stack -> GridBackingStore.isBlockedInEquippedStorage(pack.slotIdentifier(), stack));
        List<ItemStack> contents = new ArrayList<>();
        for (ItemStack stack : source.getAllItems()) {
            if (!stack.isEmpty()) contents.add(stack.copy());
        }
        @SuppressWarnings("unchecked")
        List<List<ItemStack>> containers = (List<List<ItemStack>>) (List<?>)
            carried.getOrDefault(ModDataComponents.GRID_CONTAINERS.get(), List.of());
        for (List<ItemStack> container : containers) {
            for (ItemStack stack : container) {
                if (stack != null && !stack.isEmpty()) contents.add(stack.copy());
            }
        }
        contents.sort(Comparator.comparingInt(DeltaPackTransferService::area).reversed());
        if (contents.isEmpty()) return null;

        ItemStack emptyCarrier = carried.copy();
        emptyCarrier.set(ModDataComponents.GRID_CONTENTS.get(), List.of());
        emptyCarrier.set(ModDataComponents.GRID_CONTAINERS.get(), List.of());
        return new Payload(emptyCarrier, List.copyOf(contents));
    }

    public static boolean insertContents(GridBackingStore destination, List<ItemStack> contents) {
        for (ItemStack content : contents) {
            if (!insertFully(destination, content.copy())) return false;
        }
        return true;
    }

    public static boolean insertFully(GridBackingStore destination, ItemStack stack) {
        if (stack.isEmpty()) return true;
        for (int index = 0; index < destination.getSize() && !stack.isEmpty(); index++) {
            int x = index % destination.getWidth();
            int y = index / destination.getWidth();
            if (destination.canStackAt(x, y, stack)) destination.stackInto(x, y, stack);
        }
        while (!stack.isEmpty()) {
            GridBackingStore.PlacementResult placement = destination.findFreePlacement(stack);
            if (placement.status() != GridBackingStore.PlacementStatus.CAN_PLACE) return false;
            ItemStack placed = stack.copy();
            if (!destination.place(placement.x(), placement.y(), placed, placement.rotated())) return false;
            stack.setCount(0);
        }
        return true;
    }

    public static Set<Slot> destinationCells(AbstractContainerMenu menu, Slot target) {
        ContainerGridHelper.GridSlotInfo targetInfo = ContainerGridHelper.gridSlotInfo(menu, target);
        if (targetInfo == null) return Set.of();
        Set<Slot> result = new LinkedHashSet<>();
        for (Slot slot : menu.slots) {
            if (!slot.isActive() || !ContainerGridHelper.isGridSlotEnabled(menu, slot)) continue;
            ContainerGridHelper.GridSlotInfo info = ContainerGridHelper.gridSlotInfo(menu, slot);
            if (info != null && targetInfo.group().equals(info.group())) result.add(slot);
        }
        return result;
    }

    public static boolean insertContents(AbstractContainerMenu menu, Set<Slot> destination,
                                         List<ItemStack> contents) {
        for (ItemStack content : contents) {
            if (!insertFully(menu, destination, content.copy())) return false;
        }
        return true;
    }

    private static boolean insertFully(AbstractContainerMenu menu, Set<Slot> destination,
                                       ItemStack stack) {
        if (stack.isEmpty()) return true;
        List<Slot> ordered = new ArrayList<>(destination);
        ordered.sort((first, second) -> ContainerGridHelper.compareGridOrder(menu, first, second));

        for (Slot slot : ordered) {
            if (stack.isEmpty()) break;
            Slot anchor = ContainerGridHelper.footprintAnchorFor(
                menu, slot, ModDataStorage::getCachedSizeFor);
            if (anchor != null && anchor != slot) continue;
            ItemStack target = slot.getItem();
            if (!ContainerGridHelper.canStackInto(slot, target, stack)) continue;
            int before = stack.getCount();
            ItemStack insertion = stack.copy();
            ItemStack remainder = slot.safeInsert(insertion, before);
            int inserted = before - remainder.getCount();
            if (inserted > 0) stack.shrink(inserted);
        }

        while (!stack.isEmpty()) {
            ContainerGridHelper.invalidate(menu);
            ContainerGridHelper.prepare(menu, ModDataStorage::getCachedSizeFor);
            ContainerGridHelper.PlacementResult placement = ContainerGridHelper.findPlacementWithin(
                menu, destination, stack, GridBackingStore.isRotated(stack),
                ModDataStorage::getCachedSizeFor);
            if (placement.status() != GridBackingStore.PlacementStatus.CAN_PLACE
                || placement.anchor() == null) return false;
            Slot anchor = placement.anchor();
            int limit = Math.min(anchor.getMaxStackSize(stack), stack.getMaxStackSize());
            int move = Math.min(stack.getCount(), limit);
            if (move <= 0) return false;
            ItemStack placed = stack.copyWithCount(move);
            GridBackingStore.setRotated(placed, placement.rotated());
            anchor.set(placed);
            anchor.setChanged();
            stack.shrink(move);
        }
        ContainerGridHelper.invalidate(menu);
        ContainerGridHelper.prepare(menu, ModDataStorage::getCachedSizeFor);
        return true;
    }

    private static int area(ItemStack stack) {
        var size = GridBackingStore.sizeOfStored(stack);
        return size.width() * size.height();
    }
}
