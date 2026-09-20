package com.xtdpotato.xero_delta.grid;

import com.xtdpotato.xero_delta.XeroDelta;
import com.xtdpotato.xero_delta.block.PersonalWarehouseBlock;
import com.xtdpotato.xero_delta.data.ModDataStorage;
import com.xtdpotato.xero_delta.data.KnifeSkinRules;
import com.xtdpotato.xero_delta.data.PersonalWarehouseData;
import com.xtdpotato.xero_delta.data.WarehouseCategory;
import com.xtdpotato.xero_delta.data.ItemSize;
import com.xtdpotato.xero_delta.data.PlayerLayoutSlotRules;
import com.xtdpotato.xero_delta.data.SafetyBoxAccessData;
import com.xtdpotato.xero_delta.item.SafetyBoxItem;
import com.xtdpotato.xero_delta.menu.PersonalWarehouseContainer;
import com.xtdpotato.xero_delta.menu.PersonalWarehouseMenu;
import com.xtdpotato.xero_delta.network.ModNetwork;
import com.xtdpotato.xero_delta.network.PlayerEquipmentSync;
import com.xtdpotato.xero_delta.trading.TradingInventorySource;
import com.xtdpotato.xero_delta.trading.TradingInventorySources;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Transactional transfer from any Delta/trading source into a warehouse bin. */
public final class WarehouseTransferService {
    private WarehouseTransferService() {
    }

    /** Stores purchased chunks in the owner's main warehouse; returns only undelivered items. */
    public static ItemStack storePurchase(ServerPlayer player, ItemStack purchased) {
        ItemStack remaining = purchased.copy();
        if (remaining.isEmpty() || !PersonalWarehouseBlock.isNearby(player)) return remaining;
        PersonalWarehouseMenu open = player.containerMenu instanceof PersonalWarehouseMenu menu ? menu : null;
        PersonalWarehouseMenu destination = targetMenu(player, open, WarehouseCategory.MAIN);
        remaining = storePurchase(destination, remaining);
        if (open != null) {
            ContainerGridHelper.invalidate(open);
            open.broadcastChanges();
            ContainerGridHelper.synchronizeStorageMenu(open);
        }
        return remaining;
    }

    static ItemStack storePurchase(PersonalWarehouseMenu destination, ItemStack purchased) {
        ItemStack remaining = purchased.copy();
        while (!remaining.isEmpty()) {
            int amount = Math.min(remaining.getCount(), Math.max(1, remaining.getMaxStackSize()));
            if (tryInsert(destination, insertsFor(remaining.copyWithCount(amount))) == null) break;
            remaining.shrink(amount);
        }
        return remaining;
    }

    public static boolean moveSource(ServerPlayer player, String sourceId) {
        return moveSource(player, sourceId, "", -1, false);
    }

    public static boolean moveSource(ServerPlayer player, String sourceId,
                                     String requestedCategory) {
        return moveSource(player, sourceId, requestedCategory, -1, false);
    }

    public static boolean moveSource(ServerPlayer player, String sourceId,
                                     String requestedCategory, int targetSlot,
                                     boolean rotated) {
        if (player == null || !(player.containerMenu instanceof PersonalWarehouseMenu openMenu)) {
            return false;
        }
        TradingInventorySource source = TradingInventorySources.find(player, sourceId);
        if (source == null) return full(player);

        ItemStack original = source.peek();
        if (KnifeSkinRules.locked(original)) return false;
        WarehouseCategory category = requestedCategory == null || requestedCategory.isBlank()
            ? openMenu.category() : WarehouseCategory.byId(requestedCategory);
        if (original.isEmpty() || !category.accepts(original)) return typeMismatch(player);

        ItemStack simulated = source.extract(original.getCount(), true);
        if (!sameCompleteStack(original, simulated)) return full(player);

        List<ItemStack> inserts = insertsFor(original);
        if (inserts.stream().anyMatch(stack -> !category.accepts(stack))) {
            return typeMismatch(player);
        }
        PersonalWarehouseMenu targetMenu = targetMenu(player, openMenu, category);
        Transaction transaction = tryInsert(targetMenu, inserts, targetSlot, rotated);
        if (transaction == null) return full(player);

        ItemStack extracted = ItemStack.EMPTY;
        try {
            extracted = source.extract(original.getCount(), false);
            if (!sameCompleteStack(original, extracted)) {
                returnToSourceOrPlayer(player, source, extracted);
                restore(targetMenu, transaction);
                return full(player);
            }

            ContainerGridHelper.invalidate(openMenu);
            openMenu.broadcastChanges();
            player.inventoryMenu.broadcastChanges();
            ContainerGridHelper.synchronizeStorageMenu(openMenu);
            syncEquipmentSource(player, sourceId);
            if (category != openMenu.category()) {
                PersonalWarehouseBlock.open(player, category);
            }
            return true;
        } catch (RuntimeException exception) {
            returnToSourceOrPlayer(player, source, extracted);
            restore(targetMenu, transaction);
            XeroDelta.LOGGER.error("Unable to move source {} into warehouse category {}",
                sourceId, category.id(), exception);
            return full(player);
        }
    }

    public static boolean moveWarehouseSlot(ServerPlayer player, int sourceSlot,
                                            String requestedCategory) {
        if (player == null || !(player.containerMenu instanceof PersonalWarehouseMenu openMenu)
            || sourceSlot < 0 || sourceSlot >= openMenu.warehouseSlots()) return false;
        WarehouseCategory category = WarehouseCategory.byId(requestedCategory);
        if (category == openMenu.category()) return false;

        Slot source = openMenu.slots.get(sourceSlot);
        ItemStack original = source.getItem().copy();
        if (KnifeSkinRules.locked(original)) return false;
        if (original.isEmpty() || !category.accepts(original)) return typeMismatch(player);
        List<ItemStack> inserts = insertsFor(original);
        if (inserts.stream().anyMatch(stack -> !category.accepts(stack))) {
            return typeMismatch(player);
        }

        PersonalWarehouseMenu targetMenu = targetMenu(player, openMenu, category);
        Transaction transaction = tryInsert(targetMenu, inserts);
        if (transaction == null) return full(player);
        boolean removed = false;
        try {
            source.set(ItemStack.EMPTY);
            source.setChanged();
            removed = true;
            ContainerGridHelper.invalidate(openMenu);
            openMenu.broadcastChanges();
            ContainerGridHelper.synchronizeStorageMenu(openMenu);
            PersonalWarehouseBlock.open(player, category);
            return true;
        } catch (RuntimeException exception) {
            if (removed) {
                source.set(original.copy());
                source.setChanged();
            }
            restore(targetMenu, transaction);
            XeroDelta.LOGGER.error("Unable to move warehouse slot {} from {} to {}",
                sourceSlot, openMenu.category().id(), category.id(), exception);
            return full(player);
        }
    }

    public static boolean moveWarehouseSlotToSafetyBox(ServerPlayer player,
                                                       int sourceSlot,
                                                       int targetCell,
                                                       boolean rotated) {
        if (player == null || !PlayerLayoutSlotRules.enabled(player)
            || !(player.containerMenu instanceof PersonalWarehouseMenu menu)
            || sourceSlot < 0 || sourceSlot >= menu.warehouseSlots()
            || targetCell < -1) return false;

        ContainerGridHelper.invalidate(menu);
        ContainerGridHelper.prepare(menu, ModDataStorage::getCachedSizeFor);
        Slot requested = menu.slots.get(sourceSlot);
        Slot anchor = ContainerGridHelper.footprintAnchorFor(
            menu, requested, ModDataStorage::getCachedSizeFor);
        if (anchor == null || anchor.index < 0 || anchor.index >= menu.warehouseSlots()) {
            anchor = requested;
        }
        ItemStack original = anchor.getItem().copy();
        if (KnifeSkinRules.locked(original)) return false;
        if (original.isEmpty() || GridBackingStore.isBlocked(original)) return false;

        final Slot source = anchor;
        final boolean[] moved = {false};
        CuriosApi.getCuriosInventory(player).ifPresent(curios -> {
            var handler = curios.getStacksHandler("safety_box").orElse(null);
            if (handler == null || handler.getSlots() <= 0
                || !curios.isSlotActive("safety_box", 0)) return;
            ItemStack equipped = handler.getStacks().getStackInSlot(0);
            if (!(equipped.getItem() instanceof SafetyBoxItem box)
                || targetCell >= box.getGridWidth() * box.getGridHeight()) return;
            String itemId = equipped.getItemHolder().getKey().location().toString();
            if (!SafetyBoxAccessData.get(player.server).isUnlocked(
                player.getUUID(), itemId, System.currentTimeMillis())) {
                ModNetwork.sendTranslatedNotice(player,
                    "safety_box.xero_delta.expired_read_only");
                return;
            }

            ItemStack trialBox = equipped.copy();
            GridBackingStore trial = new GridBackingStore(
                trialBox, box.getGridWidth(), box.getGridHeight());
            if (targetCell < 0) {
                if (!autoInsertSafetyContents(trial, original)) {
                    if (DeltaPackTransferService.payload(original) != null) {
                        ModNetwork.sendTranslatedNoticePlain(player,
                            "storage.xero_delta.internal_contents_no_space");
                    }
                    return;
                }
            } else {
                int x = targetCell % box.getGridWidth();
                int y = targetCell / box.getGridWidth();
                if (trial.canStackAt(x, y, original)) {
                    ItemStack remaining = original.copy();
                    if (!trial.stackInto(x, y, remaining)
                        || !remaining.isEmpty()) return;
                } else if (trial.canPlace(x, y, original, rotated, Set.of())) {
                    DeltaPackTransferService.Payload payload =
                        DeltaPackTransferService.payload(original);
                    ItemStack placed = payload == null ? original : payload.emptyCarrier();
                    if (!trial.place(x, y, placed, rotated)) return;
                    if (payload != null
                        && !DeltaPackTransferService.insertContents(trial,
                            payload.contents())) {
                        if (!payload.contents().isEmpty()) {
                            ModNetwork.sendTranslatedNoticePlain(player,
                                "storage.xero_delta.internal_contents_no_space");
                        }
                        return;
                    }
                } else {
                    return;
                }
            }

            try {
                source.set(ItemStack.EMPTY);
                source.setChanged();
                curios.setEquippedCurio("safety_box", 0, trialBox);
                handler.update();
                moved[0] = true;
            } catch (RuntimeException exception) {
                source.set(original.copy());
                source.setChanged();
                try {
                    curios.setEquippedCurio("safety_box", 0, equipped.copy());
                    handler.update();
                } catch (RuntimeException restoreException) {
                    exception.addSuppressed(restoreException);
                }
                XeroDelta.LOGGER.error(
                    "Unable to move warehouse slot {} into safety box cell {}",
                    source.index, targetCell, exception);
            }
        });

        if (!moved[0]) return false;
        ContainerGridHelper.invalidate(menu);
        player.getInventory().setChanged();
        menu.broadcastChanges();
        player.inventoryMenu.broadcastChanges();
        ContainerGridHelper.synchronizeStorageMenu(menu);
        return true;
    }

    /** Repack the existing safety-box contents before accepting a selector drop. */
    private static boolean autoInsertSafetyContents(GridBackingStore trial,
                                                    ItemStack incoming) {
        List<ItemStack> all = new ArrayList<>();
        for (ItemStack stack : trial.getAllItems()) {
            if (!stack.isEmpty()) all.add(stack.copy());
        }
        DeltaPackTransferService.Payload payload =
            DeltaPackTransferService.payload(incoming);
        if (payload == null) {
            all.add(incoming.copy());
        } else {
            all.add(payload.emptyCarrier().copy());
            payload.contents().forEach(stack -> all.add(stack.copy()));
        }
        all.sort(Comparator.comparingInt(WarehouseTransferService::stackArea).reversed());
        trial.restoreAll(List.of());
        for (ItemStack stack : all) {
            if (!DeltaPackTransferService.insertContents(trial, List.of(stack))) return false;
        }
        return true;
    }

    private static int stackArea(ItemStack stack) {
        ItemSize size = GridBackingStore.sizeOfStored(stack);
        return size.width() * size.height();
    }

    private static PersonalWarehouseMenu targetMenu(ServerPlayer player,
                                                    PersonalWarehouseMenu openMenu,
                                                    WarehouseCategory category) {
        if (openMenu != null && category == openMenu.category()) return openMenu;
        PersonalWarehouseData data = PersonalWarehouseData.get(player.server);
        PersonalWarehouseData.Warehouse warehouse = data.warehouse(player.getUUID());
        PersonalWarehouseData.Bin bin = warehouse.bin(category);
        int[] used = new int[WarehouseCategory.values().length];
        int[] total = new int[WarehouseCategory.values().length];
        for (WarehouseCategory value : WarehouseCategory.values()) {
            used[value.ordinal()] = warehouse.usedCells(value);
            total[value.ordinal()] = warehouse.bin(value).capacity();
        }
        return new PersonalWarehouseMenu(-1, player.getInventory(),
            new PersonalWarehouseContainer(data, bin, category), bin.rows(),
            category, warehouse.name(), used, total);
    }

    private static List<ItemStack> insertsFor(ItemStack original) {
        DeltaPackTransferService.Payload payload = DeltaPackTransferService.payload(original);
        List<ItemStack> inserts = new ArrayList<>();
        if (payload == null) {
            inserts.add(original.copy());
        } else {
            inserts.add(payload.emptyCarrier().copy());
            payload.contents().forEach(stack -> inserts.add(stack.copy()));
        }
        return inserts;
    }

    private static Transaction tryInsert(PersonalWarehouseMenu menu,
                                         List<ItemStack> inserts) {
        return tryInsert(menu, inserts, -1, false);
    }

    private static Transaction tryInsert(PersonalWarehouseMenu menu,
                                         List<ItemStack> inserts,
                                         int targetSlot, boolean rotated) {
        Transaction transaction = snapshot(menu);
        for (int index = 0; index < inserts.size(); index++) {
            ItemStack insert = inserts.get(index);
            boolean inserted = index == 0 && targetSlot >= 0
                ? insertFullyAt(menu, transaction.destination(), insert,
                    targetSlot, rotated)
                : insertFully(menu, transaction.destination(), insert);
            if (!inserted) {
                restore(menu, transaction);
                return null;
            }
        }
        return transaction;
    }

    private static boolean insertFullyAt(PersonalWarehouseMenu menu,
                                         Set<Slot> destination,
                                         ItemStack stack, int targetSlot,
                                         boolean rotated) {
        if (stack.isEmpty()) return true;
        if (targetSlot < 0 || targetSlot >= menu.warehouseSlots()) return false;
        ItemStack remaining = stack.copy();
        List<ItemStack> working = transactionWorking(menu, destination);
        ItemStack target = working.get(targetSlot);
        if (!target.isEmpty()) {
            if (!GridBackingStore.isSameItemIgnoringRotation(target, remaining)) return false;
            int limit = Math.min(target.getMaxStackSize(), remaining.getMaxStackSize());
            int move = Math.min(remaining.getCount(), limit - target.getCount());
            if (move <= 0) return false;
            target.grow(move);
            remaining.shrink(move);
        } else {
            ItemSize base = ModDataStorage.getCachedSizeFor(remaining);
            ItemSize size = rotated ? base.rotated() : base;
            int x = targetSlot % PersonalWarehouseData.COLUMNS;
            int y = targetSlot / PersonalWarehouseData.COLUMNS;
            if (x + size.width() > PersonalWarehouseData.COLUMNS
                || y + size.height() > menu.rows()
                || !fitsWarehouse(working, PersonalWarehouseData.COLUMNS,
                    x, y, size)) return false;
            int move = Math.min(remaining.getCount(),
                Math.min(remaining.getMaxStackSize(), 64));
            ItemStack placed = remaining.copyWithCount(move);
            GridBackingStore.setRotated(placed, rotated);
            working.set(targetSlot, placed);
            remaining.shrink(move);
        }
        applyWorking(menu, working);
        return remaining.isEmpty()
            || insertFully(menu, destination, remaining);
    }

    private static Transaction snapshot(PersonalWarehouseMenu menu) {
        List<Slot> slots = new ArrayList<>(menu.warehouseSlots());
        for (int index = 0; index < menu.warehouseSlots(); index++) {
            slots.add(menu.slots.get(index));
        }
        List<ItemStack> snapshot = slots.stream()
            .map(Slot::getItem).map(ItemStack::copy).toList();
        return new Transaction(slots, snapshot, new LinkedHashSet<>(slots));
    }

    private static boolean insertFully(PersonalWarehouseMenu menu, Set<Slot> destination,
                                       ItemStack stack) {
        if (stack.isEmpty()) return true;
        ItemStack remaining = stack.copy();
        List<ItemStack> working = transactionWorking(menu, destination);
        int columns = PersonalWarehouseData.COLUMNS;
        int rows = menu.rows();
        for (ItemStack target : working) {
            if (target.isEmpty()
                || !GridBackingStore.isSameItemIgnoringRotation(target, remaining)) continue;
            int limit = Math.min(target.getMaxStackSize(), remaining.getMaxStackSize());
            int move = Math.min(remaining.getCount(), limit - target.getCount());
            if (move > 0) {
                target.grow(move);
                remaining.shrink(move);
            }
        }
        while (!remaining.isEmpty()) {
            ItemSize base = ModDataStorage.getCachedSizeFor(remaining);
            boolean preferred = GridBackingStore.isRotated(remaining);
            boolean placed = false;
            boolean[] rotations = base.width() == base.height()
                ? new boolean[]{preferred} : new boolean[]{preferred, !preferred};
            for (boolean rotated : rotations) {
                ItemSize size = rotated ? base.rotated() : base;
                for (int y = 0; y + size.height() <= rows && !placed; y++) {
                    for (int x = 0; x + size.width() <= columns; x++) {
                        if (!fitsWarehouse(working, columns, x, y, size)) continue;
                        int index = y * columns + x;
                        int move = Math.min(remaining.getCount(),
                            Math.min(remaining.getMaxStackSize(), 64));
                        ItemStack placedStack = remaining.copyWithCount(move);
                        GridBackingStore.setRotated(placedStack, rotated);
                        working.set(index, placedStack);
                        remaining.shrink(move);
                        placed = true;
                    }
                }
                if (placed) break;
            }
            if (!placed) return false;
        }
        applyWorking(menu, working);
        return true;
    }

    private static void applyWorking(PersonalWarehouseMenu menu,
                                     List<ItemStack> working) {
        for (int index = 0; index < menu.warehouseSlots(); index++) {
            menu.slots.get(index).set(working.get(index).copy());
            menu.slots.get(index).setChanged();
        }
    }

    private static List<ItemStack> transactionWorking(PersonalWarehouseMenu menu,
                                                       Set<Slot> destination) {
        // A logical multi-cell item owns one anchor; covered physical slots must
        // never participate in the capacity simulation.  Third-party sorting
        // can leave a stale client/server snapshot in one of those cells.
        ContainerGridHelper.invalidate(menu);
        ContainerGridHelper.prepare(menu, ModDataStorage::getCachedSizeFor);
        List<ItemStack> working = new ArrayList<>(menu.warehouseSlots());
        for (int index = 0; index < menu.warehouseSlots(); index++) {
            Slot slot = menu.slots.get(index);
            ItemStack stack = slot.getItem();
            if (!stack.isEmpty()) {
                Slot anchor = ContainerGridHelper.footprintAnchorFor(
                    menu, slot, ModDataStorage::getCachedSizeFor);
                if (anchor != null && anchor != slot) {
                    working.add(ItemStack.EMPTY);
                    continue;
                }
            }
            working.add(stack.copy());
        }
        return working;
    }

    private static boolean fitsWarehouse(List<ItemStack> items, int columns,
                                         int x, int y, ItemSize size) {
        for (int yy = y; yy < y + size.height(); yy++) {
            for (int xx = x; xx < x + size.width(); xx++) {
                int cell = yy * columns + xx;
                if (cell < 0 || cell >= items.size() || !items.get(cell).isEmpty()) return false;
                for (int index = 0; index < items.size(); index++) {
                    ItemStack anchor = items.get(index);
                    if (anchor.isEmpty()) continue;
                    int ax = index % columns;
                    int ay = index / columns;
                    ItemSize stored = GridBackingStore.sizeOfStored(anchor);
                    if (xx >= ax && xx < ax + stored.width()
                        && yy >= ay && yy < ay + stored.height()) return false;
                }
            }
        }
        return true;
    }

    private static boolean sameCompleteStack(ItemStack expected, ItemStack actual) {
        return !actual.isEmpty() && actual.getCount() == expected.getCount()
            && ItemStack.isSameItemSameComponents(expected, actual);
    }

    private static void syncEquipmentSource(ServerPlayer player, String sourceId) {
        if ("player|39".equals(sourceId)) {
            PlayerEquipmentSync.sync(player, EquipmentSlot.HEAD);
        } else if ("player|38".equals(sourceId)) {
            PlayerEquipmentSync.sync(player, EquipmentSlot.CHEST);
        }
    }

    private static void returnToSourceOrPlayer(ServerPlayer player,
                                               TradingInventorySource source,
                                               ItemStack extracted) {
        if (extracted == null || extracted.isEmpty() || source.restore(extracted)) return;
        ItemStack remainder = extracted.copy();
        player.getInventory().add(remainder);
        if (!remainder.isEmpty()) player.drop(remainder, false);
    }

    private static void restore(PersonalWarehouseMenu menu, Transaction transaction) {
        for (int index = 0; index < transaction.slots().size(); index++) {
            transaction.slots().get(index).set(transaction.snapshot().get(index).copy());
            transaction.slots().get(index).setChanged();
        }
        ContainerGridHelper.invalidate(menu);
        menu.broadcastChanges();
        ContainerGridHelper.synchronizeStorageMenu(menu);
    }

    private static boolean full(ServerPlayer player) {
        ModNetwork.sendTranslatedNoticePlain(player,
            "warehouse.xero_delta.category_full");
        return false;
    }

    private static boolean typeMismatch(ServerPlayer player) {
        ModNetwork.sendTranslatedNoticePlain(player,
            "warehouse.xero_delta.type_mismatch");
        return false;
    }

    private record Transaction(List<Slot> slots, List<ItemStack> snapshot,
                               Set<Slot> destination) {
    }
}
