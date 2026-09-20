package com.xtdpotato.xero_delta.grid;

import com.xtdpotato.xero_delta.XeroDelta;
import com.xtdpotato.xero_delta.data.ModDataStorage;
import com.xtdpotato.xero_delta.data.LootSearchManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public final class ContainerGridNormalizer {
    private static final ThreadLocal<Set<AbstractContainerMenu>> NORMALIZING =
        ThreadLocal.withInitial(() -> Collections.newSetFromMap(new IdentityHashMap<>()));

    private ContainerGridNormalizer() {
    }

    public static void normalize(ServerPlayer player, AbstractContainerMenu menu) {
        if (player == null || menu == null || !NORMALIZING.get().add(menu)) return;
        try {
            normalizeInternal(player, menu);
        } finally {
            NORMALIZING.get().remove(menu);
        }
    }

    /**
     * Replaces third-party 1x1 sorting with an all-or-nothing logical grid sort.
     * Covered physical slots are never exposed to the sorter as free cells.
     */
    public static boolean sortAndRepack(ServerPlayer player, AbstractContainerMenu menu) {
        if (player == null || menu == null || !NORMALIZING.get().add(menu)) return false;
        try {
            ContainerGridHelper.refresh(menu, ModDataStorage::getCachedSizeFor);
            Map<String, List<Slot>> groups = storageGroups(menu, false);
            if (groups.isEmpty()) return false;
            for (List<Slot> slots : groups.values()) {
                slots.sort((first, second) -> ContainerGridHelper.compareGridOrder(menu, first, second));
                if (!sortGroupTransaction(menu, slots)) return false;
            }
            menu.broadcastChanges();
            ContainerGridHelper.synchronizeStorageMenu(menu);
            LootSearchManager.contentsReordered(player, menu);
            return true;
        } catch (RuntimeException exception) {
            XeroDelta.LOGGER.error("Unable to sort logical grid storage {}",
                menu.getClass().getName(), exception);
            return false;
        } finally {
            NORMALIZING.get().remove(menu);
        }
    }

    /**
     * Replaces Inventory Sorter's physical-slot compaction for the logical
     * group under the clicked slot. The return value means the third-party
     * operation must be cancelled, including when an atomic repack fails.
     */
    public static boolean handleInventorySorter(ServerPlayer player, AbstractContainerMenu menu, Slot origin) {
        if (origin == null) return false;
        return handleExternalSort(player, menu, SortScope.ORIGIN, origin);
    }

    /**
     * Replaces SophisticatedSorter's player/container sort only when that
     * scope contains at least one multi-cell item.
     */
    public static boolean handleSophisticatedSorter(ServerPlayer player, AbstractContainerMenu menu,
                                                     boolean playerInventory) {
        return handleExternalSort(player, menu,
            playerInventory ? SortScope.PLAYER_MAIN : SortScope.CONTAINER, null);
    }

    private static boolean handleExternalSort(ServerPlayer player, AbstractContainerMenu menu,
                                              SortScope scope, Slot origin) {
        if (player == null || menu == null || !NORMALIZING.get().add(menu)) return false;
        boolean mustCancel = false;
        Map<Slot, ItemStack> snapshot = new LinkedHashMap<>();
        try {
            ContainerGridHelper.refresh(menu, ModDataStorage::getCachedSizeFor);
            String originGroup = null;
            if (scope == SortScope.ORIGIN) {
                var originInfo = ContainerGridHelper.gridSlotInfo(menu, origin);
                if (originInfo == null || !originInfo.adapted()) return false;
                originGroup = originInfo.group();
            }

            Map<String, List<Slot>> selectedGroups = new LinkedHashMap<>();
            for (var entry : storageGroups(menu, true).entrySet()) {
                List<Slot> slots = entry.getValue();
                boolean selected = switch (scope) {
                    case ORIGIN -> entry.getKey().equals(originGroup);
                    case PLAYER_MAIN -> slots.stream().anyMatch(ContainerGridHelper::isPlayerMainInventorySlot);
                    case CONTAINER -> slots.stream().anyMatch(slot -> !(slot.container instanceof Inventory));
                };
                if (selected) selectedGroups.put(entry.getKey(), slots);
            }
            if (selectedGroups.isEmpty()) return false;

            // Every adapted grid group is owned by the logical packing rules,
            // even while it currently contains only 1x1 items. Letting the
            // third-party sorter compact those physical cells can cross pack
            // regions and makes the next large item overlap covered cells.
            mustCancel = true;

            for (List<Slot> slots : selectedGroups.values()) {
                slots.sort((first, second) -> ContainerGridHelper.compareGridOrder(menu, first, second));
                for (Slot slot : slots) snapshot.putIfAbsent(slot, slot.getItem().copy());
            }
            for (List<Slot> slots : selectedGroups.values()) {
                if (!sortGroupTransaction(menu, slots)) {
                    restoreSnapshot(snapshot);
                    ContainerGridHelper.invalidate(menu);
                    XeroDelta.LOGGER.warn("Rejected third-party physical sort because the logical grid no longer fits {}",
                        menu.getClass().getName());
                    return true;
                }
            }
            menu.broadcastChanges();
            ContainerGridHelper.synchronizeStorageMenu(menu);
            LootSearchManager.contentsReordered(player, menu);
            return true;
        } catch (RuntimeException exception) {
            if (!snapshot.isEmpty()) {
                restoreSnapshot(snapshot);
                ContainerGridHelper.invalidate(menu);
            }
            XeroDelta.LOGGER.error("Unable to handle third-party logical grid sort for {}",
                menu.getClass().getName(), exception);
            return mustCancel;
        } finally {
            NORMALIZING.get().remove(menu);
        }
    }

    /**
     * Consumes ClientSort's physical slot permutation when it contains a
     * multi-cell item and repacks the same logical scope transactionally.
     *
     * @return {@code true} when ClientSort must skip its native slot writes;
     *         {@code false} when the mapping only contains vanilla 1x1 items.
     */
    public static boolean handleClientSort(ServerPlayer player, AbstractContainerMenu menu, int[] slotMapping) {
        if (player == null || menu == null || slotMapping == null || slotMapping.length == 0) return false;
        if ((slotMapping.length & 1) != 0 || !NORMALIZING.get().add(menu)) return true;
        try {
            ContainerGridHelper.refresh(menu, ModDataStorage::getCachedSizeFor);
            Map<Integer, ItemStack> snapshot = new TreeMap<>();
            for (int index = 0; index < menu.slots.size(); index++) {
                snapshot.put(index, menu.slots.get(index).getItem().copy());
            }

            List<ClientSortMapping> mappings = new ArrayList<>(slotMapping.length / 2);
            Set<Integer> sourceIds = new LinkedHashSet<>();
            Set<Integer> destinationIds = new LinkedHashSet<>();
            boolean hasLargeItem = false;
            for (int index = 0; index < slotMapping.length; index += 2) {
                int sourceId = slotMapping[index];
                int destinationId = slotMapping[index + 1];
                if (sourceId < 0 || sourceId >= menu.slots.size()
                    || destinationId < 0 || destinationId >= menu.slots.size()) {
                    XeroDelta.LOGGER.warn("Ignoring invalid ClientSort grid mapping {} -> {} for {}",
                        sourceId, destinationId, menu.getClass().getName());
                    return true;
                }
                sourceIds.add(sourceId);
                destinationIds.add(destinationId);
                ItemStack sourceStack = snapshot.get(sourceId);
                if (!sourceStack.isEmpty() && area(sourceStack) > 1) hasLargeItem = true;
                mappings.add(new ClientSortMapping(sourceId, destinationId));
            }
            if (!hasLargeItem) return false;

            // ClientSort produces a permutation of one complete screen scope.
            // Anything else is unsafe for multi-cell storage, so consume it
            // without partially applying the third-party physical writes.
            if (sourceIds.size() != mappings.size() || destinationIds.size() != mappings.size()
                || !sourceIds.equals(destinationIds)) {
                XeroDelta.LOGGER.warn("Rejected non-permutation ClientSort grid mapping for {}",
                    menu.getClass().getName());
                return true;
            }

            List<Slot> destinations = new ArrayList<>(mappings.size());
            String group = null;
            for (ClientSortMapping mapping : mappings) {
                Slot destination = menu.slots.get(mapping.destinationId());
                if (!ContainerGridHelper.isGridSlotEnabled(menu, destination)) {
                    XeroDelta.LOGGER.warn("Rejected mixed grid/non-grid ClientSort scope for {}",
                        menu.getClass().getName());
                    return true;
                }
                var info = ContainerGridHelper.gridSlotInfo(menu, destination);
                if (info == null || !info.adapted()) return true;
                if (group == null) group = info.group();
                else if (!group.equals(info.group())) {
                    XeroDelta.LOGGER.warn("Rejected multi-group ClientSort scope for {}",
                        menu.getClass().getName());
                    return true;
                }
                destinations.add(destination);
            }

            mappings.sort((first, second) -> ContainerGridHelper.compareGridOrder(menu,
                menu.slots.get(first.destinationId()), menu.slots.get(second.destinationId())));
            List<ItemStack> contents = new ArrayList<>();
            for (ClientSortMapping mapping : mappings) {
                ItemStack stack = snapshot.get(mapping.sourceId());
                if (!stack.isEmpty()) contents.add(stack.copy());
            }
            contents = compact(contents);
            // Large-first packing prevents fragmentation. List.sort is stable,
            // so ClientSort's selected order is retained among equal-area items.
            contents.sort(Comparator.comparingInt(ContainerGridNormalizer::area).reversed());

            boolean changed = repackGroupTransaction(menu, destinations, contents);
            if (changed) {
                menu.broadcastChanges();
                ContainerGridHelper.synchronizeStorageMenu(menu);
            } else {
                XeroDelta.LOGGER.warn("ClientSort grid repack could not fit the complete scope for {}",
                    menu.getClass().getName());
            }
            return true;
        } catch (RuntimeException exception) {
            XeroDelta.LOGGER.error("Unable to handle ClientSort logical grid mapping for {}",
                menu.getClass().getName(), exception);
            return true;
        } finally {
            NORMALIZING.get().remove(menu);
        }
    }

    private static boolean sortGroupTransaction(AbstractContainerMenu menu, List<Slot> slots) {
        List<ItemStack> contents = compact(slots.stream().map(Slot::getItem)
            .filter(stack -> !stack.isEmpty()).map(ItemStack::copy).toList());
        contents.sort(Comparator.comparingInt(ContainerGridNormalizer::area).reversed()
            .thenComparing(ContainerGridNormalizer::sortKey)
            .thenComparing(Comparator.comparingInt(ItemStack::getCount).reversed()));
        return repackGroupTransaction(menu, slots, contents);
    }

    private static boolean repackGroupTransaction(AbstractContainerMenu menu, List<Slot> slots,
                                                   List<ItemStack> contents) {
        List<ItemStack> snapshot = slots.stream().map(Slot::getItem).map(ItemStack::copy).toList();
        Set<Slot> selected = Collections.newSetFromMap(new IdentityHashMap<>());
        selected.addAll(slots);
        Map<GridPackingPlan.Cell, Slot> cells = new LinkedHashMap<>();
        for (Slot slot : slots) {
            var info = ContainerGridHelper.gridSlotInfo(menu, slot);
            if (info == null || !info.adapted()) return false;
            Slot occupyingAnchor = ContainerGridHelper.footprintAnchorFor(menu, slot,
                ModDataStorage::getCachedSizeFor);
            if (occupyingAnchor != null && !selected.contains(occupyingAnchor)) continue;
            Slot duplicate = cells.putIfAbsent(new GridPackingPlan.Cell(info.column(), info.row()), slot);
            if (duplicate != null) return false;
        }
        List<GridPackingPlan.Entry> entries = new ArrayList<>();
        for (int index = 0; index < contents.size(); index++) {
            ItemStack stack = contents.get(index);
            entries.add(new GridPackingPlan.Entry(index, ModDataStorage.getCachedSizeFor(stack),
                GridBackingStore.isRotated(stack)));
        }
        var plan = GridPackingPlan.pack(cells.keySet(), entries);
        if (plan.isEmpty()) return false;
        for (GridPackingPlan.Placement placement : plan.orElseThrow()) {
            ItemStack stack = contents.get(placement.index());
            for (GridPackingPlan.Cell cell : placement.cells()) {
                Slot target = cells.get(cell);
                if (target == null || !target.mayPlace(stack)) return false;
            }
        }
        try {
            for (Slot slot : slots) slot.set(ItemStack.EMPTY);
            for (GridPackingPlan.Placement placement : plan.orElseThrow()) {
                ItemStack stack = contents.get(placement.index());
                Slot anchor = cells.get(placement.anchor());
                if (anchor == null) throw new IllegalStateException("Grid sort plan has no physical anchor");
                GridBackingStore.setRotated(stack, placement.rotated());
                anchor.set(stack);
                anchor.setChanged();
            }
            ContainerGridHelper.invalidate(menu);
            for (Slot slot : slots) slot.setChanged();
            return true;
        } catch (RuntimeException exception) {
            restore(slots, snapshot);
            ContainerGridHelper.invalidate(menu);
            throw exception;
        }
    }

    private static String sortKey(ItemStack stack) {
        var id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id.getNamespace() + '\u0000' + stack.getHoverName().getString() + '\u0000' + id.getPath();
    }

    private static void normalizeInternal(ServerPlayer player, AbstractContainerMenu menu) {
        ContainerGridHelper.refresh(menu, ModDataStorage::getCachedSizeFor);
        Map<String, List<Slot>> groups = storageGroups(menu, true);
        if (groups.isEmpty()) return;
        boolean changed = false;
        List<ItemStack> overflow = new ArrayList<>();
        for (List<Slot> slots : groups.values()) {
            slots.sort((first, second) -> ContainerGridHelper.compareGridOrder(menu, first, second));
            if (isValid(menu, slots)) continue;
            List<ItemStack> snapshot = slots.stream().map(Slot::getItem).map(ItemStack::copy).toList();
            try {
                List<ItemStack> contents = compact(snapshot.stream()
                    .filter(stack -> !stack.isEmpty()).map(ItemStack::copy).toList());
                for (Slot slot : slots) slot.set(ItemStack.EMPTY);
                ContainerGridHelper.invalidate(menu);
                ContainerGridHelper.prepare(menu, ModDataStorage::getCachedSizeFor);

                contents.sort(Comparator.comparingInt(ContainerGridNormalizer::area).reversed());
                Set<Slot> allowed = new LinkedHashSet<>(slots);
                for (ItemStack stack : contents) {
                    var placement = ContainerGridHelper.findPlacementWithin(menu, allowed, stack,
                        GridBackingStore.isRotated(stack), ModDataStorage::getCachedSizeFor);
                    if (placement.status() == GridBackingStore.PlacementStatus.CAN_PLACE
                        && placement.anchor() != null) {
                        GridBackingStore.setRotated(stack, placement.rotated());
                        placement.anchor().set(stack);
                        placement.anchor().setChanged();
                        ContainerGridHelper.invalidate(menu);
                        ContainerGridHelper.prepare(menu, ModDataStorage::getCachedSizeFor);
                    } else {
                        overflow.add(stack);
                    }
                }
            } catch (RuntimeException exception) {
                restore(slots, snapshot);
                ContainerGridHelper.invalidate(menu);
                XeroDelta.LOGGER.error("Unable to normalize grid storage {}", menu.getClass().getName(), exception);
                continue;
            }
            for (Slot slot : slots) slot.setChanged();
            changed = true;
        }
        if (!overflow.isEmpty()) placeOverflow(player, menu, overflow);
        if (changed) {
            menu.broadcastChanges();
            ContainerGridHelper.synchronizeStorageMenu(menu);
        }
    }

    private static void restore(List<Slot> slots, List<ItemStack> snapshot) {
        for (int index = 0; index < slots.size() && index < snapshot.size(); index++) {
            slots.get(index).set(snapshot.get(index).copy());
            slots.get(index).setChanged();
        }
    }

    private static void restoreSnapshot(Map<Slot, ItemStack> snapshot) {
        snapshot.forEach((slot, stack) -> {
            slot.set(stack.copy());
            slot.setChanged();
        });
    }

    private static Map<String, List<Slot>> storageGroups(AbstractContainerMenu menu, boolean includePlayerMain) {
        Map<String, List<Slot>> groups = new LinkedHashMap<>();
        for (Slot slot : menu.slots) {
            if (!ContainerGridHelper.isGridSlotEnabled(menu, slot)) continue;
            // The hotbar remains vanilla 1x1 storage, while the three-row
            // player inventory participates in the grid just like a container.
            if (slot.container instanceof Inventory) {
                if (!includePlayerMain || !ContainerGridHelper.isPlayerMainInventorySlot(slot)) continue;
            } else if (!includePlayerMain && !ContainerGridHelper.isStorageInventorySlot(menu, slot)) {
                continue;
            }
            var info = ContainerGridHelper.gridSlotInfo(menu, slot);
            if (info != null) groups.computeIfAbsent(info.group(), ignored -> new ArrayList<>()).add(slot);
        }
        return groups;
    }

    private static boolean isValid(AbstractContainerMenu menu, List<Slot> slots) {
        Set<Slot> allowed = new LinkedHashSet<>(slots);
        Set<Slot> occupied = new LinkedHashSet<>();
        for (Slot anchor : slots) {
            ItemStack stack = anchor.getItem();
            if (stack.isEmpty()) continue;
            var size = ContainerGridHelper.orientedSize(stack, ModDataStorage::getCachedSizeFor);
            Set<Slot> footprint = ContainerGridHelper.footprintCells(menu, anchor, size);
            if (footprint.size() != size.width() * size.height() || !allowed.containsAll(footprint)) return false;
            for (Slot cell : footprint) if (!occupied.add(cell)) return false;
        }
        return true;
    }

    private static List<ItemStack> compact(List<ItemStack> source) {
        List<ItemStack> result = new ArrayList<>();
        for (ItemStack original : source) {
            ItemStack remaining = original.copy();
            for (ItemStack target : result) {
                if (!GridBackingStore.isSameItemIgnoringRotation(target, remaining)) continue;
                int limit = Math.min(target.getMaxStackSize(), remaining.getMaxStackSize());
                int moved = Math.min(remaining.getCount(), limit - target.getCount());
                if (moved > 0) {
                    target.grow(moved);
                    remaining.shrink(moved);
                }
                if (remaining.isEmpty()) break;
            }
            if (!remaining.isEmpty()) result.add(remaining);
        }
        return result;
    }

    private static void placeOverflow(ServerPlayer player, AbstractContainerMenu menu, List<ItemStack> overflow) {
        for (ItemStack stack : overflow) {
            // Use the inventory's normal insertion path so the hotbar is a
            // valid fallback when the three-row main inventory is full. The
            // Inventory mixin applies the same footprint rules and keeps the
            // hotbar/main regions separate for multi-cell items.
            ItemStack remainder = stack.copy();
            player.getInventory().add(remainder);
            if (!remainder.isEmpty()) {
                XeroDelta.LOGGER.warn("Dropping grid loot overflow {} for {}", stack, player.getScoreboardName());
                player.drop(remainder, false);
            }
        }
    }

    private static int area(ItemStack stack) {
        var size = ModDataStorage.getCachedSizeFor(stack);
        return size.width() * size.height();
    }

    private record ClientSortMapping(int sourceId, int destinationId) {
    }

    private enum SortScope {
        ORIGIN,
        PLAYER_MAIN,
        CONTAINER
    }
}
