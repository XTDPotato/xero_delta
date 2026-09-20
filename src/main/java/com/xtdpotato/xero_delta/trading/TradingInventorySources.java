package com.xtdpotato.xero_delta.trading;

import com.xtdpotato.xero_delta.ModDataComponents;
import com.xtdpotato.xero_delta.data.ModDataStorage;
import com.xtdpotato.xero_delta.data.PlayerLayoutInventoryPolicy;
import com.xtdpotato.xero_delta.data.PlayerLayoutSlotRules;
import com.xtdpotato.xero_delta.entity.CorpseEntity;
import com.xtdpotato.xero_delta.grid.GridBackingStore;
import com.xtdpotato.xero_delta.item.DeltaPackItem;
import com.xtdpotato.xero_delta.item.SafetyBoxItem;
import com.xtdpotato.xero_delta.menu.CorpseMenu;
import com.xtdpotato.xero_delta.menu.PersonalWarehouseMenu;
import com.xtdpotato.xero_delta.screen.SafetyBoxLayoutPack;
import com.xtdpotato.xero_delta.tag.ModTags;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Capability-first inventory discovery for vanilla and backpack mods. */
public final class TradingInventorySources {
    private static final int MAX_VISIBLE_SOURCES = 512;
    private static final String ACCESSORIES_IDENTIFIER = "@accessories";
    private static final String TRAVELERS_ATTACHMENT_IDENTIFIER = "@travelers_attachment";
    private static final String INVENTORY_GROUP = "inventory";
    private static final String CURIO_SLOT_PREFIX = "curio|";

    private TradingInventorySources() {
    }

    public static List<TradingInventorySource> list(net.minecraft.server.level.ServerPlayer player) {
        return discover(player).sources();
    }

    public static TradingInventorySource.Catalog catalog(net.minecraft.server.level.ServerPlayer player) {
        Discovery discovery = discover(player);
        Map<String, Integer> totals = new HashMap<>();
        for (TradingInventorySource source : discovery.sources()) {
            ItemStack stack = source.peek();
            if (stack.isEmpty() || !source.sellable()) continue;
            totals.merge(ModDataStorage.getKey(stack), stack.getCount(),
                (first, second) -> Math.min(99_999, first + second));
        }
        List<TradingInventorySource.View> views = discovery.sources().stream().map(source -> {
            ItemStack stack = source.peek();
            boolean sellable = source.sellable();
            return new TradingInventorySource.View(source.id(), source.label(), source.groupId(), stack,
                sellable ? totals.getOrDefault(ModDataStorage.getKey(stack), stack.getCount()) : stack.getCount(),
                sellable, sellable ? "" : source.blockedReason());
        }).filter(view -> !view.stack().isEmpty()).toList();
        return new TradingInventorySource.Catalog(views, discovery.groups());
    }

    public static boolean canSellStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return true;
        if (com.xtdpotato.xero_delta.data.KnifeSkinRules.locked(stack)) return false;
        try {
            List<ItemStack> contents = stack.getOrDefault(ModDataComponents.GRID_CONTENTS.get(), List.of());
            if (contents.stream().anyMatch(value -> value != null && !value.isEmpty())) return false;
            List<List<ItemStack>> containers =
                stack.getOrDefault(ModDataComponents.GRID_CONTAINERS.get(), List.of());
            if (containers.stream().flatMap(List::stream)
                .anyMatch(value -> value != null && !value.isEmpty())) return false;
        } catch (RuntimeException ignored) {
        }
        IItemHandler handler = stack.getCapability(Capabilities.ItemHandler.ITEM);
        if (handler == null) return true;
        try {
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                if (!handler.getStackInSlot(slot).isEmpty()) return false;
            }
        } catch (RuntimeException ignored) {
        }
        return true;
    }

    /** Inserts into capability-backed inventory/Curios/accessory backpacks without a hard mod dependency. */
    public static void insertIntoBackpacks(net.minecraft.server.level.ServerPlayer player,
                                           ItemStack remaining, ItemStack excludedCarrier) {
        if (player == null || remaining == null || remaining.isEmpty()) return;
        Set<ItemStack> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize() && !remaining.isEmpty(); slot++) {
            insertIntoBackpackCarrier(inventory.getItem(slot), remaining, excludedCarrier, visited);
        }
        CuriosApi.getCuriosInventory(player).ifPresent(curios -> {
            for (SlotResult slotResult : curios.findCurios(stack -> !stack.isEmpty())) {
                if (remaining.isEmpty()) break;
                insertIntoBackpackCarrier(slotResult.stack(), remaining, excludedCarrier, visited);
            }
        });
        for (ItemStack carrier : TradingOptionalBackpackIntegrations.accessoryStacks(player)) {
            if (remaining.isEmpty()) break;
            insertIntoBackpackCarrier(carrier, remaining, excludedCarrier, visited);
        }
        if (!remaining.isEmpty()) {
            insertIntoBackpackCarrier(
                TradingOptionalBackpackIntegrations.travelersAttachmentStack(player),
                remaining, excludedCarrier, visited);
        }
    }

    private static void insertIntoBackpackCarrier(ItemStack carrier, ItemStack remaining,
                                                  ItemStack excludedCarrier,
                                                  Set<ItemStack> visited) {
        if (carrier == null || carrier.isEmpty() || remaining.isEmpty()
            || carrier == excludedCarrier || carrier.is(ModTags.SAFETY_BOX)
            || !visited.add(carrier)) return;
        IItemHandler handler = carrier.getCapability(Capabilities.ItemHandler.ITEM);
        if (handler == null) return;
        try {
            for (int slot = 0; slot < handler.getSlots() && !remaining.isEmpty(); slot++) {
                int before = remaining.getCount();
                ItemStack remainder = handler.insertItem(slot, remaining.copy(), false);
                int after = remainder.isEmpty() ? 0 : Math.min(before, remainder.getCount());
                if (after < before) remaining.shrink(before - after);
            }
        } catch (RuntimeException ignored) {
            // A third-party backpack may invalidate its handler during a live equipment change.
        }
    }

    private static Discovery discover(net.minecraft.server.level.ServerPlayer player) {
        List<TradingInventorySource> result = new ArrayList<>();
        List<TradingInventorySource.Group> groups = new ArrayList<>();
        groups.add(new TradingInventorySource.Group(INVENTORY_GROUP, ItemStack.EMPTY, 1));
        List<ItemStack> equippedCarriers = new ArrayList<>();
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize() && result.size() < MAX_VISIBLE_SOURCES; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty()) continue;
            result.add(new PlayerSlotSource(inventory, slot));
            int carrierSlot = slot;
            if (isGridCarrier(stack)) {
                appendGridSources(result, groups, "bag|inv|" + carrierSlot,
                    (containerIndex, innerSlot, itemFingerprint) ->
                        TradingSourceAddress.inventory(carrierSlot, containerIndex,
                            innerSlot, itemFingerprint).encode(), stack,
                    "market.xero_delta.source.inventory_backpack",
                    address -> () -> syncGridCarrier(player, address));
            } else {
                appendBackpackSources(result, groups, "bag|inv|" + carrierSlot,
                    (innerSlot, itemFingerprint) ->
                        TradingSourceAddress.inventory(carrierSlot, innerSlot, itemFingerprint).encode(), stack,
                    "market.xero_delta.source.inventory_backpack");
            }
        }
        CuriosApi.getCuriosInventory(player).ifPresent(curios -> {
            for (SlotResult slotResult : curios.findCurios(stack -> !stack.isEmpty())) {
                if (result.size() >= MAX_VISIBLE_SOURCES) break;
                String identifier = slotResult.slotContext().identifier();
                int index = slotResult.slotContext().index();
                var stacksHandler = curios.getStacksHandler(identifier).orElse(null);
                IItemHandler curioStacks = stacksHandler == null
                    ? null : stacksHandler.getStacks();
                if (curioStacks != null && index >= 0 && index < curioStacks.getSlots()) {
                    String directId = sourceIdForCurioSlot(
                        identifier, index, slotResult.stack());
                    if (!directId.isBlank()) {
                        result.add(new CurioSlotSource(directId,
                            "market.xero_delta.source.curio_backpack", INVENTORY_GROUP,
                            curioStacks, index));
                    }
                }
                boolean appended = isGridCarrier(slotResult.stack())
                    ? appendGridSources(result, groups, "bag|curio|" + identifier + "|" + index,
                        (containerIndex, innerSlot, itemFingerprint) ->
                            TradingSourceAddress.curio(identifier, index, containerIndex,
                                innerSlot, itemFingerprint).encode(), slotResult.stack(),
                        "market.xero_delta.source.curio_backpack",
                        address -> () -> syncGridCarrier(player, address))
                    : appendBackpackSources(result, groups, "bag|curio|" + identifier + "|" + index,
                        (innerSlot, itemFingerprint) ->
                            TradingSourceAddress.curio(identifier, index, innerSlot, itemFingerprint).encode(),
                        slotResult.stack(), "market.xero_delta.source.curio_backpack");
                if (appended) {
                    equippedCarriers.add(slotResult.stack());
                }
            }
        });
        List<ItemStack> accessories = TradingOptionalBackpackIntegrations.accessoryStacks(player);
        for (int index = 0; index < accessories.size() && result.size() < MAX_VISIBLE_SOURCES; index++) {
            ItemStack carrier = accessories.get(index);
            if (containsIdenticalCarrier(equippedCarriers, carrier)) continue;
            int accessoryIndex = index;
            boolean appended = isGridCarrier(carrier)
                ? appendGridSources(result, groups,
                    "bag|curio|" + ACCESSORIES_IDENTIFIER + "|" + accessoryIndex,
                    (containerIndex, innerSlot, itemFingerprint) ->
                        TradingSourceAddress.curio(ACCESSORIES_IDENTIFIER, accessoryIndex,
                            containerIndex, innerSlot, itemFingerprint).encode(),
                    carrier, "market.xero_delta.source.curio_backpack",
                    address -> () -> syncGridCarrier(player, address))
                : appendBackpackSources(result, groups,
                    "bag|curio|" + ACCESSORIES_IDENTIFIER + "|" + accessoryIndex,
                    (innerSlot, itemFingerprint) ->
                        TradingSourceAddress.curio(ACCESSORIES_IDENTIFIER, accessoryIndex,
                            innerSlot, itemFingerprint).encode(),
                    carrier, "market.xero_delta.source.curio_backpack");
            if (appended) {
                equippedCarriers.add(carrier);
            }
        }
        ItemStack travelersCarrier =
            TradingOptionalBackpackIntegrations.travelersAttachmentStack(player);
        if (!travelersCarrier.isEmpty() && !containsIdenticalCarrier(equippedCarriers, travelersCarrier)) {
            if (isGridCarrier(travelersCarrier)) {
                appendGridSources(result, groups,
                    "bag|curio|" + TRAVELERS_ATTACHMENT_IDENTIFIER + "|0",
                    (containerIndex, innerSlot, itemFingerprint) ->
                        TradingSourceAddress.curio(TRAVELERS_ATTACHMENT_IDENTIFIER, 0,
                            containerIndex, innerSlot, itemFingerprint).encode(),
                    travelersCarrier, "market.xero_delta.source.curio_backpack",
                    address -> () -> syncGridCarrier(player, address));
            } else {
                appendBackpackSources(result, groups,
                    "bag|curio|" + TRAVELERS_ATTACHMENT_IDENTIFIER + "|0",
                    (innerSlot, itemFingerprint) ->
                        TradingSourceAddress.curio(TRAVELERS_ATTACHMENT_IDENTIFIER, 0,
                            innerSlot, itemFingerprint).encode(),
                    travelersCarrier, "market.xero_delta.source.curio_backpack");
            }
        }
        if (player.containerMenu instanceof PersonalWarehouseMenu warehouse) {
            String groupId = "warehouse|" + warehouse.category().id();
            boolean groupAdded = false;
            for (int slotIndex = 0; slotIndex < warehouse.warehouseSlots()
                && result.size() < MAX_VISIBLE_SOURCES; slotIndex++) {
                Slot slot = warehouse.slots.get(slotIndex);
                ItemStack stack = slot.getItem();
                if (stack.isEmpty()) continue;
                if (!groupAdded) {
                    groups.add(new TradingInventorySource.Group(groupId,
                        warehouse.category().icon(), groups.size()));
                    groupAdded = true;
                }
                result.add(new WarehouseSlotSource(sourceIdForWarehouse(
                    warehouse.category().id(), slotIndex, stack), groupId, slot));
            }
        }
        return new Discovery(List.copyOf(result), List.copyOf(groups));
    }

    public static TradingInventorySource find(net.minecraft.server.level.ServerPlayer player, String sourceId) {
        if (sourceId == null || sourceId.length() > 256) return null;
        if (sourceId.startsWith("player|")) {
            int slot = parseInt(sourceId.substring("player|".length()));
            return slot >= 0 && slot < player.getInventory().getContainerSize()
                ? new PlayerSlotSource(player.getInventory(), slot) : null;
        }
        if (sourceId.startsWith(CURIO_SLOT_PREFIX)) {
            return directCurioSource(player, sourceId);
        }
        if (sourceId.startsWith("warehouse|")) {
            return warehouseSource(player, sourceId);
        }
        TradingSourceAddress address = TradingSourceAddress.parse(sourceId);
        if (address == null) return null;
        ItemStack carrier;
        if (address.kind() == TradingSourceAddress.Kind.INVENTORY) {
            int carrierSlot = address.inventorySlot();
            if (carrierSlot < 0 || carrierSlot >= player.getInventory().getContainerSize()) return null;
            carrier = player.getInventory().getItem(carrierSlot);
        } else {
            carrier = switch (address.curioIdentifier()) {
                case ACCESSORIES_IDENTIFIER ->
                    TradingOptionalBackpackIntegrations.accessoryStack(player, address.curioIndex());
                case TRAVELERS_ATTACHMENT_IDENTIFIER ->
                    TradingOptionalBackpackIntegrations.travelersAttachmentStack(player);
                default -> CuriosApi.getCuriosInventory(player)
                    .flatMap(curios -> curios.findCurio(address.curioIdentifier(), address.curioIndex()))
                    .map(SlotResult::stack).orElse(ItemStack.EMPTY);
            };
        }
        TradingInventorySource exact = backpackSource(sourceId, carrier, address);
        if (exact != null) return exact;
        TradingInventorySource grid = gridSource(sourceId, player, carrier, address);
        if (grid != null) return grid;
        if (address.fingerprint().isBlank()) return null;

        // Capability-backed backpacks may rebuild or compact their handler when
        // their screen closes. Resolve the same component-bearing item again
        // instead of trusting the stale internal slot from the UI snapshot.
        for (TradingInventorySource source : list(player)) {
            if (!(source instanceof BackpackSlotSource) && !(source instanceof GridSlotSource)) continue;
            if (address.fingerprint().equals(fingerprint(source.peek()))) return source;
        }
        return null;
    }

    /** Splits into a nearby empty cell in the same storage before using vanilla inventory. */
    public static boolean splitStack(net.minecraft.server.level.ServerPlayer player,
                                     String sourceId, int requestedAmount) {
        if (!player.containerMenu.getCarried().isEmpty()) return false;
        if (sourceId != null && sourceId.startsWith("corpse_storage|")) {
            return splitCorpseStorageStack(player, sourceId, requestedAmount);
        }
        TradingInventorySource source = find(player, sourceId);
        if (source == null) return false;
        ItemStack current = source.peek();
        if (current.isEmpty() || current.getCount() <= 1) return false;
        int amount = Math.max(1, Math.min(current.getCount() - 1, requestedAmount));

        if (source instanceof BackpackSlotSource backpack) {
            boolean split = splitBackpackStack(player, sourceId, backpack, current, amount);
            if (split) player.containerMenu.broadcastChanges();
            return split;
        }

        if (source instanceof GridSlotSource grid) {
            boolean split = splitGridStack(player, grid, amount);
            if (split) player.containerMenu.broadcastChanges();
            return split;
        }

        Inventory inventory = player.getInventory();
        int original = source instanceof PlayerSlotSource playerSlot ? playerSlot.slot() : -1;
        int preferred = nearestPlayerDestination(player, current, original);
        if (preferred < 0) return false;

        ItemStack simulated = source.extract(amount, true);
        if (simulated.isEmpty() || simulated.getCount() != amount) return false;
        ItemStack part = source.extract(amount, false);
        if (part.isEmpty() || part.getCount() != amount) {
            restoreOrCarry(player, source, part);
            return false;
        }
        inventory.setItem(preferred, part);
        inventory.setChanged();
        player.containerMenu.broadcastChanges();
        return true;
    }

    private static int nearestPlayerDestination(
        net.minecraft.server.level.ServerPlayer player, ItemStack stack, int sourceSlot) {
        Inventory inventory = player.getInventory();
        int slots = Math.min(PlayerLayoutInventoryPolicy.INVENTORY_SIZE,
            inventory.getContainerSize());
        if (PlayerLayoutSlotRules.enabled(player)) {
            slots = Math.min(PlayerLayoutInventoryPolicy.HOTBAR_SIZE, slots);
        }
        for (int slot : splitDestinationOrder(slots, sourceSlot, 9)) {
            if (slot == sourceSlot || !inventory.getItem(slot).isEmpty()) continue;
            if (PlayerLayoutSlotRules.canPlace(player, slot, stack)) return slot;
        }
        return -1;
    }

    private static boolean splitGridStack(net.minecraft.server.level.ServerPlayer player,
                                          GridSlotSource source, int amount) {
        GridBackingStore store = splitGridStore(source);
        if (store == null) return false;
        List<ItemStack> contents = store.getAllItems();
        int sourceAnchor = matchingGridSlot(contents, source.address().innerSlot(),
            source.address().fingerprint());
        if (sourceAnchor < 0 || !store.splitStackToNearest(sourceAnchor, amount)) return false;
        if (source.sync() != null) source.sync().run();
        return true;
    }

    private static GridBackingStore splitGridStore(GridSlotSource source) {
        ItemStack carrier = source.carrier();
        int containerIndex = source.address().containerIndex();
        if (carrier.getItem() instanceof DeltaPackItem pack) {
            if (containerIndex != 0) return null;
            return new GridBackingStore(carrier, pack.gridWidth(), pack.gridHeight(), 0,
                stack -> GridBackingStore.isBlockedInEquippedStorage(
                    pack.slotIdentifier(), stack));
        }
        if (carrier.getItem() instanceof SafetyBoxItem box) {
            int width = box.getGridWidth();
            int height = box.getGridHeight();
            if (containerIndex > 0) {
                SafetyBoxLayoutPack.LayoutData layout = SafetyBoxLayoutPack.getLayoutForBox(
                    carrier.getItemHolder().getKey().location().toString());
                if (containerIndex >= layout.containers.size()) return null;
                SafetyBoxLayoutPack.GridContainerData container =
                    layout.containers.get(containerIndex);
                width = container.columns;
                height = container.rows;
            }
            return new GridBackingStore(carrier, width, height, containerIndex);
        }
        return null;
    }

    private static boolean splitBackpackStack(net.minecraft.server.level.ServerPlayer player,
                                              String sourceId, BackpackSlotSource source,
                                              ItemStack current, int amount) {
        IItemHandler handler = source.handler();
        int columns = splitStorageColumns(player, sourceId, handler.getSlots());
        for (int destination : splitDestinationOrder(
            handler.getSlots(), source.slot(), columns)) {
            if (!handler.getStackInSlot(destination).isEmpty()) continue;
            ItemStack requested = current.copyWithCount(amount);
            if (!handler.insertItem(destination, requested, true).isEmpty()) continue;
            ItemStack simulated = handler.extractItem(source.slot(), amount, true);
            if (simulated.isEmpty() || simulated.getCount() != amount) return false;

            ItemStack part = handler.extractItem(source.slot(), amount, false);
            if (part.isEmpty() || part.getCount() != amount) {
                restoreHandlerOrCarry(player, handler, source.slot(), part);
                return false;
            }
            ItemStack remainder = handler.insertItem(destination, part, false);
            if (remainder.isEmpty()) return true;

            // A third-party handler is allowed to disagree with its simulation.
            // Preserve every unaccepted item at the source, or on the cursor if
            // the handler refuses the rollback. Items already accepted by the
            // destination remain in a valid slot and are never dropped.
            restoreHandlerOrCarry(player, handler, source.slot(), remainder);
            return false;
        }
        return false;
    }

    private static void restoreOrCarry(net.minecraft.server.level.ServerPlayer player,
                                       TradingInventorySource source, ItemStack stack) {
        if (stack == null || stack.isEmpty() || source.restore(stack)) return;
        preserveOnCursor(player, stack);
    }

    private static void restoreHandlerOrCarry(
        net.minecraft.server.level.ServerPlayer player, IItemHandler handler,
        int sourceSlot, ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;
        ItemStack remainder = handler.insertItem(sourceSlot, stack.copy(), false);
        preserveOnCursor(player, remainder);
    }

    private static void preserveOnCursor(net.minecraft.server.level.ServerPlayer player,
                                         ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;
        ItemStack carried = player.containerMenu.getCarried();
        if (carried.isEmpty()) {
            player.containerMenu.setCarried(stack.copy());
            return;
        }
        if (ItemStack.isSameItemSameComponents(carried, stack)
            && carried.getCount() + stack.getCount() <= carried.getMaxStackSize()) {
            carried.grow(stack.getCount());
            player.containerMenu.setCarried(carried);
            return;
        }
        throw new IllegalStateException("Split rollback cursor unexpectedly occupied");
    }

    private static boolean splitCorpseStorageStack(
        net.minecraft.server.level.ServerPlayer player, String sourceId, int requestedAmount) {
        if (!(player.containerMenu instanceof CorpseMenu menu)) return false;
        String[] parts = sourceId.split("\\|", -1);
        if (parts.length != 4) return false;
        try {
            int corpseId = Integer.parseInt(parts[1]);
            int carrierSlot = Integer.parseInt(parts[2]);
            int sourceAnchor = Integer.parseInt(parts[3]);
            CorpseEntity corpse = menu.corpseEntity();
            if (corpse == null || menu.corpseEntityId() != corpseId || corpse.getId() != corpseId
                || player.distanceToSqr(corpse) > 64.0D) return false;
            GridBackingStore store = corpse.carrierStore(carrierSlot);
            if (store == null || sourceAnchor < 0 || sourceAnchor >= store.getSize()) return false;
            ItemStack current = store.getItemRaw(sourceAnchor % store.getWidth(),
                sourceAnchor / store.getWidth());
            if (current.isEmpty() || current.getCount() <= 1) return false;
            int amount = Math.max(1, Math.min(current.getCount() - 1, requestedAmount));
            if (!store.splitStackToNearest(sourceAnchor, amount)) return false;
            corpse.carrierContentsChanged(carrierSlot);
            menu.broadcastChanges();
            return true;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    static List<Integer> splitDestinationOrder(int slots, int sourceSlot, int columns) {
        return TradingSplitOrder.destinations(slots, sourceSlot, columns);
    }

    private static int splitStorageColumns(net.minecraft.server.level.ServerPlayer player,
                                           String sourceId, int slots) {
        TradingSourceAddress address = TradingSourceAddress.parse(sourceId);
        if (address == null) return Math.min(9, Math.max(1, slots));
        ItemStack carrier;
        if (address.kind() == TradingSourceAddress.Kind.INVENTORY) {
            carrier = address.inventorySlot() >= 0
                && address.inventorySlot() < player.getInventory().getContainerSize()
                ? player.getInventory().getItem(address.inventorySlot()) : ItemStack.EMPTY;
        } else {
            carrier = switch (address.curioIdentifier()) {
                case ACCESSORIES_IDENTIFIER ->
                    TradingOptionalBackpackIntegrations.accessoryStack(player, address.curioIndex());
                case TRAVELERS_ATTACHMENT_IDENTIFIER ->
                    TradingOptionalBackpackIntegrations.travelersAttachmentStack(player);
                default -> CuriosApi.getCuriosInventory(player)
                    .flatMap(curios -> curios.findCurio(
                        address.curioIdentifier(), address.curioIndex()))
                    .map(SlotResult::stack).orElse(ItemStack.EMPTY);
            };
        }
        if (carrier.getItem() instanceof DeltaPackItem pack) return pack.gridWidth();
        if (carrier.getItem() instanceof SafetyBoxItem box) return box.getGridWidth();
        return Math.min(9, Math.max(1, slots));
    }
    public static List<TradingInventorySource.View> views(net.minecraft.server.level.ServerPlayer player) {
        return catalog(player).sources();
    }

    public static List<TradingInventorySource.Group> groups(net.minecraft.server.level.ServerPlayer player) {
        return catalog(player).groups();
    }

    public static int countMatching(net.minecraft.server.level.ServerPlayer player, ItemStack sample) {
        if (sample.isEmpty() || !canSellStack(sample)) return 0;
        int total = 0;
        for (TradingInventorySource source : list(player)) {
            ItemStack stack = source.peek();
            if (!stack.isEmpty() && canSellStack(stack)
                && ItemStack.isSameItemSameComponents(sample, stack)) {
                total = Math.min(99_999, total + stack.getCount());
            }
        }
        return total;
    }

    public static ItemStack extractMatching(net.minecraft.server.level.ServerPlayer player, ItemStack sample,
                                            int amount, boolean simulate) {
        if (sample.isEmpty() || amount <= 0 || !canSellStack(sample)) return ItemStack.EMPTY;
        if (!simulate) return extractMatchingLive(player, sample, amount);
        int remaining = amount;
        int extracted = 0;
        for (TradingInventorySource source : list(player)) {
            ItemStack stack = source.peek();
            if (stack.isEmpty() || !canSellStack(stack)
                || !ItemStack.isSameItemSameComponents(sample, stack)) continue;
            int take = Math.min(remaining, stack.getCount());
            ItemStack part = source.extract(take, simulate);
            if (part.isEmpty() || !ItemStack.isSameItemSameComponents(sample, part)) continue;
            extracted += part.getCount();
            remaining -= part.getCount();
            if (remaining <= 0) break;
        }
        return extracted <= 0 ? ItemStack.EMPTY : sample.copyWithCount(extracted);
    }

    private static ItemStack extractMatchingLive(net.minecraft.server.level.ServerPlayer player,
                                                 ItemStack sample, int amount) {
        int remaining = amount;
        int extracted = 0;
        while (remaining > 0) {
            boolean progressed = false;
            for (TradingInventorySource source : list(player)) {
                ItemStack stack = source.peek();
                if (stack.isEmpty() || !canSellStack(stack)
                    || !ItemStack.isSameItemSameComponents(sample, stack)) continue;
                int take = Math.min(remaining, stack.getCount());
                ItemStack part = source.extract(take, false);
                if (part.isEmpty() || !ItemStack.isSameItemSameComponents(sample, part)) continue;
                extracted += part.getCount();
                remaining -= part.getCount();
                progressed = true;
                break;
            }
            if (!progressed) break;
        }
        return extracted <= 0 ? ItemStack.EMPTY : sample.copyWithCount(extracted);
    }

    private static boolean isGridCarrier(ItemStack carrier) {
        return carrier != null && !carrier.isEmpty()
            && (carrier.getItem() instanceof DeltaPackItem
                || carrier.getItem() instanceof SafetyBoxItem
                || carrier.has(ModDataComponents.GRID_CONTENTS.get())
                || carrier.has(ModDataComponents.GRID_CONTAINERS.get()));
    }

    private static boolean appendGridSources(List<TradingInventorySource> result,
                                             List<TradingInventorySource.Group> groups,
                                             String groupId, GridSourceIdFactory sourceIds,
                                             ItemStack carrier, String label,
                                             GridSyncFactory syncFactory) {
        boolean[] groupAdded = {false};
        List<ItemStack> primary = carrier.getOrDefault(ModDataComponents.GRID_CONTENTS.get(), List.of());
        appendGridContainerSources(result, groups, groupId, sourceIds, carrier, label,
            0, primary, groupAdded, syncFactory);
        List<List<ItemStack>> containers = carrier.getOrDefault(
            ModDataComponents.GRID_CONTAINERS.get(), List.of());
        for (int index = 0; index < containers.size() && result.size() < MAX_VISIBLE_SOURCES; index++) {
            appendGridContainerSources(result, groups, groupId, sourceIds, carrier, label,
                index + 1, containers.get(index), groupAdded, syncFactory);
        }
        return groupAdded[0];
    }

    private static void appendGridContainerSources(List<TradingInventorySource> result,
                                                   List<TradingInventorySource.Group> groups,
                                                   String groupId, GridSourceIdFactory sourceIds,
                                                   ItemStack carrier, String label,
                                                   int containerIndex, List<ItemStack> contents,
                                                   boolean[] groupAdded, GridSyncFactory syncFactory) {
        for (int slot = 0; slot < contents.size() && result.size() < MAX_VISIBLE_SOURCES; slot++) {
            ItemStack stack = contents.get(slot);
            if (stack == null || stack.isEmpty()) continue;
            if (!groupAdded[0]) {
                groups.add(new TradingInventorySource.Group(groupId, carrier, groups.size()));
                groupAdded[0] = true;
            }
            String sourceId = sourceIds.create(containerIndex, slot, fingerprint(stack));
            TradingSourceAddress address = TradingSourceAddress.parse(sourceId);
            if (address != null) {
                result.add(new GridSlotSource(sourceId, label, groupId, carrier, address,
                    syncFactory.create(address)));
            }
        }
    }

    private static boolean appendBackpackSources(List<TradingInventorySource> result,
                                                 List<TradingInventorySource.Group> groups,
                                                 String groupId,
                                                 SourceIdFactory sourceIds,
                                                 ItemStack carrier, String label) {
        IItemHandler handler = carrier.getCapability(Capabilities.ItemHandler.ITEM);
        if (handler == null) return false;
        boolean groupAdded = false;
        for (int slot = 0; slot < handler.getSlots() && result.size() < MAX_VISIBLE_SOURCES; slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (stack.isEmpty()) continue;
            if (!groupAdded) {
                groups.add(new TradingInventorySource.Group(groupId, carrier, groups.size()));
                groupAdded = true;
            }
            String sourceId = sourceIds.create(slot, fingerprint(stack));
            if (sourceId.isBlank()) continue;
            result.add(new BackpackSlotSource(sourceId, label, groupId, handler, slot));
        }
        return true;
    }

    private static TradingInventorySource gridSource(String sourceId,
                                                      net.minecraft.server.level.ServerPlayer player,
                                                      ItemStack carrier,
                                                      TradingSourceAddress address) {
        if (!isGridCarrier(carrier)) return null;
        List<ItemStack> contents = gridContents(carrier, address.containerIndex());
        int resolvedSlot = matchingGridSlot(contents, address.innerSlot(), address.fingerprint());
        if (resolvedSlot < 0) return null;
        return new GridSlotSource(sourceId, "market.xero_delta.source.backpack",
            groupId(address), carrier, address, () -> syncGridCarrier(player, address));
    }

    private static List<ItemStack> gridContents(ItemStack carrier, int containerIndex) {
        if (carrier == null || carrier.isEmpty() || containerIndex < 0) return List.of();
        if (containerIndex == 0) {
            return carrier.getOrDefault(ModDataComponents.GRID_CONTENTS.get(), List.of());
        }
        List<List<ItemStack>> containers = carrier.getOrDefault(
            ModDataComponents.GRID_CONTAINERS.get(), List.of());
        int index = containerIndex - 1;
        return index >= 0 && index < containers.size() ? containers.get(index) : List.of();
    }

    private static int matchingGridSlot(List<ItemStack> contents, int preferredSlot,
                                        String expectedFingerprint) {
        if (preferredSlot >= 0 && preferredSlot < contents.size()) {
            ItemStack stack = contents.get(preferredSlot);
            if (stack != null && !stack.isEmpty()
                && (expectedFingerprint.isBlank() || expectedFingerprint.equals(fingerprint(stack)))) {
                return preferredSlot;
            }
        }
        if (expectedFingerprint.isBlank()) return -1;
        for (int slot = 0; slot < contents.size(); slot++) {
            ItemStack stack = contents.get(slot);
            if (stack != null && !stack.isEmpty() && expectedFingerprint.equals(fingerprint(stack))) {
                return slot;
            }
        }
        return -1;
    }

    private static void writeGridSlot(ItemStack carrier, int containerIndex, int slot,
                                      ItemStack value) {
        List<ItemStack> contents = new ArrayList<>();
        for (ItemStack stack : gridContents(carrier, containerIndex)) {
            contents.add(stack == null ? ItemStack.EMPTY : stack.copy());
        }
        while (contents.size() <= slot) contents.add(ItemStack.EMPTY);
        contents.set(slot, value == null ? ItemStack.EMPTY : value.copy());
        if (containerIndex == 0) {
            carrier.set(ModDataComponents.GRID_CONTENTS.get(), contents);
            return;
        }
        List<List<ItemStack>> containers = new ArrayList<>();
        @SuppressWarnings("unchecked")
        List<List<ItemStack>> storedContainers = (List<List<ItemStack>>) (List<?>)
            carrier.getOrDefault(ModDataComponents.GRID_CONTAINERS.get(), List.of());
        for (List<ItemStack> old : storedContainers) {
            List<ItemStack> copy = new ArrayList<>();
            for (ItemStack stack : old) copy.add(stack == null ? ItemStack.EMPTY : stack.copy());
            containers.add(copy);
        }
        int index = containerIndex - 1;
        while (containers.size() <= index) containers.add(new ArrayList<>());
        containers.set(index, contents);
        carrier.set(ModDataComponents.GRID_CONTAINERS.get(), containers);
    }

    private static void syncGridCarrier(net.minecraft.server.level.ServerPlayer player,
                                        TradingSourceAddress address) {
        player.getInventory().setChanged();
        if (address.kind() == TradingSourceAddress.Kind.CURIO
            && !ACCESSORIES_IDENTIFIER.equals(address.curioIdentifier())
            && !TRAVELERS_ATTACHMENT_IDENTIFIER.equals(address.curioIdentifier())) {
            CuriosApi.getCuriosInventory(player).ifPresent(curios ->
                curios.getStacksHandler(address.curioIdentifier()).ifPresent(handler -> handler.update()));
        }
        player.inventoryMenu.broadcastChanges();
        if (player.containerMenu != player.inventoryMenu) player.containerMenu.broadcastChanges();
    }

    private static boolean containsIdenticalCarrier(List<ItemStack> carriers, ItemStack candidate) {
        if (candidate == null || candidate.isEmpty()) return false;
        for (ItemStack carrier : carriers) if (carrier == candidate) return true;
        return false;
    }

    private static TradingInventorySource backpackSource(String sourceId, ItemStack carrier,
                                                          TradingSourceAddress address) {
        if (carrier.isEmpty()) return null;
        IItemHandler handler = carrier.getCapability(Capabilities.ItemHandler.ITEM);
        if (handler == null) return null;
        int resolvedSlot = matchingSlot(handler, address.innerSlot(), address.fingerprint());
        return resolvedSlot < 0 ? null : new BackpackSlotSource(
            sourceId, "market.xero_delta.source.backpack", groupId(address), handler, resolvedSlot);
    }

    private static int matchingSlot(IItemHandler handler, int preferredSlot, String expectedFingerprint) {
        if (preferredSlot >= 0 && preferredSlot < handler.getSlots()) {
            ItemStack stack = handler.getStackInSlot(preferredSlot);
            if (!stack.isEmpty() && (expectedFingerprint.isBlank()
                || expectedFingerprint.equals(fingerprint(stack)))) return preferredSlot;
        }
        if (expectedFingerprint.isBlank()) return -1;
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            if (expectedFingerprint.equals(fingerprint(handler.getStackInSlot(slot)))) return slot;
        }
        return -1;
    }

    public static String sourceIdForCurio(String identifier, int curioIndex,
                                          int innerSlot, ItemStack stack) {
        return TradingSourceAddress.curio(
            identifier, curioIndex, innerSlot, fingerprint(stack)).encode();
    }

    public static String sourceIdForCurioSlot(String identifier, int curioIndex,
                                              ItemStack stack) {
        if (identifier == null || identifier.isBlank() || identifier.length() > 64
            || identifier.indexOf('|') >= 0 || curioIndex < 0) return "";
        String fingerprint = fingerprint(stack);
        return fingerprint.isBlank() ? ""
            : CURIO_SLOT_PREFIX + identifier + "|" + curioIndex + "|" + fingerprint;
    }

    public static String sourceIdForWarehouse(String category, int slot, ItemStack stack) {
        if (category == null || category.isBlank() || category.indexOf('|') >= 0 || slot < 0) return "";
        String value = fingerprint(stack);
        return value.isBlank() ? "" : "warehouse|" + category + "|" + slot + "|" + value;
    }

    private static TradingInventorySource warehouseSource(
        net.minecraft.server.level.ServerPlayer player, String sourceId) {
        if (!(player.containerMenu instanceof PersonalWarehouseMenu warehouse)) return null;
        String[] parts = sourceId.split("\\|", -1);
        if (parts.length != 4 || !warehouse.category().id().equals(parts[1])) return null;
        int slotIndex = parseInt(parts[2]);
        if (slotIndex < 0 || slotIndex >= warehouse.warehouseSlots()) return null;
        Slot slot = warehouse.slots.get(slotIndex);
        if (slot.getItem().isEmpty() || !parts[3].equals(fingerprint(slot.getItem()))) return null;
        return new WarehouseSlotSource(sourceId, "warehouse|" + parts[1], slot);
    }

    private static TradingInventorySource directCurioSource(
        net.minecraft.server.level.ServerPlayer player, String sourceId) {
        String[] parts = sourceId.split("\\|", -1);
        if (parts.length != 4 || !"curio".equals(parts[0])) return null;
        String identifier = parts[1];
        int index = parseInt(parts[2]);
        String expectedFingerprint = parts[3];
        if (identifier.isBlank() || identifier.length() > 64
            || index < 0 || expectedFingerprint.isBlank()) return null;
        return CuriosApi.getCuriosInventory(player).flatMap(curios ->
            curios.getStacksHandler(identifier).map(handler -> {
                IItemHandler stacks = handler.getStacks();
                if (index >= stacks.getSlots()
                    || !expectedFingerprint.equals(fingerprint(stacks.getStackInSlot(index)))) {
                    return null;
                }
                return (TradingInventorySource) new CurioSlotSource(sourceId,
                    "market.xero_delta.source.curio_backpack", INVENTORY_GROUP,
                    stacks, index);
            })).orElse(null);
    }

    private static String fingerprint(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "";
        return UUID.nameUUIDFromBytes(ModDataStorage.getKey(stack).getBytes(StandardCharsets.UTF_8)).toString();
    }

    private static int parseInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private static String groupId(TradingSourceAddress address) {
        return address.kind() == TradingSourceAddress.Kind.INVENTORY
            ? "bag|inv|" + address.inventorySlot()
            : "bag|curio|" + address.curioIdentifier() + "|" + address.curioIndex();
    }

    private record PlayerSlotSource(Inventory inventory, int slot) implements TradingInventorySource {
        @Override public String id() { return "player|" + slot; }
        @Override public String groupId() { return INVENTORY_GROUP; }
        @Override public String label() {
            return slot < 9 ? "market.xero_delta.source.hotbar"
                : "market.xero_delta.source.inventory";
        }
        @Override public ItemStack peek() { return inventory.getItem(slot).copy(); }
        @Override public boolean sellable() { return canSellStack(inventory.getItem(slot)); }

        @Override
        public ItemStack extract(int amount, boolean simulate) {
            ItemStack current = inventory.getItem(slot);
            if (com.xtdpotato.xero_delta.data.KnifeSkinRules.locked(current)) return ItemStack.EMPTY;
            if (current.isEmpty() || amount <= 0) return ItemStack.EMPTY;
            int taken = Math.min(amount, current.getCount());
            if (simulate) return current.copyWithCount(taken);
            return inventory.removeItem(slot, taken);
        }

        @Override
        public boolean restore(ItemStack stack) {
            if (stack == null || stack.isEmpty()) return true;
            ItemStack current = inventory.getItem(slot);
            if (current.isEmpty()) {
                inventory.setItem(slot, stack.copy());
                inventory.setChanged();
                return true;
            }
            if (!ItemStack.isSameItemSameComponents(current, stack)
                || current.getCount() + stack.getCount() > current.getMaxStackSize()) return false;
            current.grow(stack.getCount());
            inventory.setChanged();
            return true;
        }
    }

    private record WarehouseSlotSource(String id, String groupId, Slot slot)
        implements TradingInventorySource {
        @Override public String label() { return "market.xero_delta.source.warehouse"; }
        @Override public ItemStack peek() { return slot.getItem().copy(); }
        @Override public boolean sellable() { return canSellStack(slot.getItem()); }
        @Override public ItemStack extract(int amount, boolean simulate) {
            ItemStack current = slot.getItem();
            if (com.xtdpotato.xero_delta.data.KnifeSkinRules.locked(current)) return ItemStack.EMPTY;
            if (current.isEmpty() || amount <= 0) return ItemStack.EMPTY;
            int taken = Math.min(amount, current.getCount());
            if (simulate) return current.copyWithCount(taken);
            ItemStack result = slot.remove(taken);
            slot.setChanged();
            return result;
        }
        @Override public boolean restore(ItemStack stack) {
            if (stack == null || stack.isEmpty()) return true;
            ItemStack current = slot.getItem();
            if (current.isEmpty()) {
                slot.set(stack.copy());
                slot.setChanged();
                return true;
            }
            if (!ItemStack.isSameItemSameComponents(current, stack)
                || current.getCount() + stack.getCount() > current.getMaxStackSize()) return false;
            current.grow(stack.getCount());
            slot.setChanged();
            return true;
        }
    }

    private record BackpackSlotSource(String id, String label, String groupId, IItemHandler handler,
                                      int slot) implements TradingInventorySource {
        @Override public ItemStack peek() { return handler.getStackInSlot(slot).copy(); }
        @Override public boolean sellable() { return canSellStack(handler.getStackInSlot(slot)); }
        @Override public ItemStack extract(int amount, boolean simulate) {
            if (amount <= 0) return ItemStack.EMPTY;
            if (com.xtdpotato.xero_delta.data.KnifeSkinRules.locked(peek())) return ItemStack.EMPTY;
            return handler.extractItem(slot, amount, simulate);
        }
        @Override public boolean restore(ItemStack stack) {
            return stack == null || stack.isEmpty()
                || handler.insertItem(slot, stack.copy(), false).isEmpty();
        }
    }

    private record GridSlotSource(String id, String label, String groupId, ItemStack carrier,
                                  TradingSourceAddress address, Runnable sync)
        implements TradingInventorySource {
        @Override public ItemStack peek() {
            List<ItemStack> contents = gridContents(carrier, address.containerIndex());
            int slot = matchingGridSlot(contents, address.innerSlot(), address.fingerprint());
            return slot < 0 ? ItemStack.EMPTY : contents.get(slot).copy();
        }

        @Override public boolean sellable() {
            return canSellStack(peek());
        }

        @Override public ItemStack extract(int amount, boolean simulate) {
            if (amount <= 0) return ItemStack.EMPTY;
            List<ItemStack> contents = gridContents(carrier, address.containerIndex());
            int slot = matchingGridSlot(contents, address.innerSlot(), address.fingerprint());
            if (slot < 0) return ItemStack.EMPTY;
            ItemStack current = contents.get(slot);
            if (com.xtdpotato.xero_delta.data.KnifeSkinRules.locked(current)) return ItemStack.EMPTY;
            int taken = Math.min(amount, current.getCount());
            ItemStack extracted = current.copyWithCount(taken);
            if (simulate) return extracted;
            ItemStack remainder = current.getCount() <= taken
                ? ItemStack.EMPTY : current.copyWithCount(current.getCount() - taken);
            writeGridSlot(carrier, address.containerIndex(), slot, remainder);
            if (sync != null) sync.run();
            return extracted;
        }

        @Override public boolean restore(ItemStack stack) {
            if (stack == null || stack.isEmpty()) return true;
            List<ItemStack> contents = gridContents(carrier, address.containerIndex());
            int slot = matchingGridSlot(contents, address.innerSlot(), address.fingerprint());
            if (slot < 0) slot = Math.max(0, address.innerSlot());
            ItemStack current = slot < contents.size() ? contents.get(slot) : ItemStack.EMPTY;
            if (current.isEmpty()) {
                writeGridSlot(carrier, address.containerIndex(), slot, stack);
                if (sync != null) sync.run();
                return true;
            }
            if (!ItemStack.isSameItemSameComponents(current, stack)
                || current.getCount() + stack.getCount() > current.getMaxStackSize()) return false;
            writeGridSlot(carrier, address.containerIndex(), slot,
                current.copyWithCount(current.getCount() + stack.getCount()));
            if (sync != null) sync.run();
            return true;
        }
    }

    private record CurioSlotSource(String id, String label, String groupId,
                                   IItemHandler handler, int slot)
        implements TradingInventorySource {
        @Override public ItemStack peek() { return handler.getStackInSlot(slot).copy(); }
        @Override public boolean sellable() { return canSellStack(handler.getStackInSlot(slot)); }
        @Override public ItemStack extract(int amount, boolean simulate) {
            if (amount <= 0) return ItemStack.EMPTY;
            if (com.xtdpotato.xero_delta.data.KnifeSkinRules.locked(peek())) return ItemStack.EMPTY;
            return handler.extractItem(slot, amount, simulate);
        }
        @Override public boolean restore(ItemStack stack) {
            return stack == null || stack.isEmpty()
                || handler.insertItem(slot, stack.copy(), false).isEmpty();
        }
    }

    @FunctionalInterface
    private interface SourceIdFactory {
        String create(int innerSlot, String fingerprint);
    }

    @FunctionalInterface
    private interface GridSourceIdFactory {
        String create(int containerIndex, int innerSlot, String fingerprint);
    }

    @FunctionalInterface
    private interface GridSyncFactory {
        Runnable create(TradingSourceAddress address);
    }

    private record Discovery(List<TradingInventorySource> sources,
                             List<TradingInventorySource.Group> groups) {
    }
}
