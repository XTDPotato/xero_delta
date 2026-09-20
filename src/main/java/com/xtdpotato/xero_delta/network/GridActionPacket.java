package com.xtdpotato.xero_delta.network;

import com.xtdpotato.xero_delta.XeroDelta;
import com.xtdpotato.xero_delta.Config;
import com.xtdpotato.xero_delta.ModDataComponents;
import com.xtdpotato.xero_delta.data.ItemSize;
import com.xtdpotato.xero_delta.data.ModDataStorage;
import com.xtdpotato.xero_delta.data.SafetyBoxAccessData;
import com.xtdpotato.xero_delta.data.SafetyBoxReadOnlyPolicy;
import com.xtdpotato.xero_delta.data.ServerGridCarryState;
import com.xtdpotato.xero_delta.data.ServerGridRotationState;
import com.xtdpotato.xero_delta.data.ServerGridCarryState.ContainerOrigin;
import com.xtdpotato.xero_delta.grid.ContainerGridHelper;
import com.xtdpotato.xero_delta.grid.DeltaPackTransferService;
import com.xtdpotato.xero_delta.grid.GridBackingStore;
import com.xtdpotato.xero_delta.grid.GridOriginFillPlan;
import com.xtdpotato.xero_delta.item.SafetyBoxItem;
import com.xtdpotato.xero_delta.screen.SafetyBoxLayoutPack;
import com.xtdpotato.xero_delta.tag.ModTags;
import net.minecraft.network.FriendlyByteBuf;

import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import top.theillusivec4.curios.api.CuriosApi;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record GridActionPacket(int action, int gridX, int gridY, boolean shift, boolean rotated, int containerIndex) implements CustomPacketPayload {

    public GridActionPacket(int action, int gridX, int gridY, boolean shift, boolean rotated) {
        this(action, gridX, gridY, shift, rotated, 0);
    }

    public static final int PICKUP = 0;
    public static final int PLACE = 1;
    public static final int SWAP = 2;
    public static final int RIGHT_CLICK = 3;
    public static final int SPLIT = 4;
    public static final int DOUBLE_COLLECT = 5;
    public static final int RETURN_ORIGIN = 6;
    public static final int COMPLETE_INVENTORY_SWAP = 7;

    public static final Type<GridActionPacket> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(XeroDelta.MOD_ID, "grid_action"));

    public static final StreamCodec<FriendlyByteBuf, GridActionPacket> STREAM_CODEC = StreamCodec.of(
        (buf, p) -> { buf.writeByte(p.action); buf.writeInt(p.gridX); buf.writeInt(p.gridY); buf.writeBoolean(p.shift); buf.writeBoolean(p.rotated); buf.writeVarInt(p.containerIndex); },
        buf -> new GridActionPacket(buf.readByte(), buf.readInt(), buf.readInt(), buf.readBoolean(), buf.readBoolean(), buf.readVarInt())
    );

    @Override public Type<GridActionPacket> type() { return TYPE; }

    public static void handleServer(GridActionPacket p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            if (p.containerIndex() < 0) return;
            ItemStack box = findBox(player);
            if (box.isEmpty() || !(box.getItem() instanceof SafetyBoxItem sbi)) return;
            ModDataStorage.get(player.serverLevel());

            int gw = sbi.getGridWidth(), gh = sbi.getGridHeight();
            if (p.containerIndex() > 0) {
                SafetyBoxLayoutPack.LayoutData layout = SafetyBoxLayoutPack.getLayoutForBox(
                    box.getItemHolder().getKey().location().toString());
                if (p.containerIndex() >= layout.containers.size()) return;
                SafetyBoxLayoutPack.GridContainerData container = layout.containers.get(p.containerIndex());
                gw = container.columns;
                gh = container.rows;
            }
            GridBackingStore store = new GridBackingStore(box, gw, gh, p.containerIndex());

            ItemStack carried = player.containerMenu.getCarried().copy();
            boolean unlocked = SafetyBoxAccessData.get(player.server).isUnlocked(
                player.getUUID(), box.getItemHolder().getKey().location().toString(),
                System.currentTimeMillis());
            if (!unlocked && !SafetyBoxReadOnlyPolicy.allows(
                p.action(), carried.isEmpty(), p.shift())) {
                ModNetwork.sendTranslatedNotice(player,
                    "safety_box.xero_delta.expired_read_only");
                player.containerMenu.broadcastChanges();
                ModNetwork.sendToClient(player, new GridSyncPacket(store.getAllItems(), gw, gh,
                    carried, p.containerIndex()));
                return;
            }

            boolean changed = false;
            boolean forceSync = false;
            switch (p.action) {
                case PICKUP -> {
                    int anchor = store.findAnchorIndexAt(p.gridX, p.gridY);
                    ItemStack source = anchor >= 0 ? store.getItemRaw(anchor % gw, anchor / gw) : ItemStack.EMPTY;
                    ItemSize sourceFootprint = source.isEmpty() ? new ItemSize(1, 1) : GridBackingStore.sizeOfStored(source);
                    ItemStack taken = store.remove(p.gridX, p.gridY);
                    if (taken.isEmpty()) break;
                    if (p.shift) {
                        ServerGridCarryState.clearAll(player);
                        player.getInventory().add(taken);
                        if (!taken.isEmpty()) player.drop(taken, false);
                    } else {
                        if (anchor >= 0) {
                            ServerGridCarryState.rememberSafetyBox(player, boxId(box), p.containerIndex(),
                                anchor % gw, anchor / gw,
                                sourceFootprint, GridBackingStore.isRotated(taken));
                        }
                        player.containerMenu.setCarried(taken);
                    }
                    changed = true;
                }
                case PLACE -> {
                    if (carried.isEmpty()) break;
                    if (isBlocked(carried)) break;
                    // The client sends the already-resolved top-left anchor. Do
                    // not treat it as a hover cell and center the footprint a
                    // second time; that shifts even-sized items up/left.
                    GridBackingStore.PlacementResult resolved = store.resolveExactPlacement(
                        p.gridX, p.gridY, carried, p.rotated, java.util.Set.of());
                    if (resolved.status() == GridBackingStore.PlacementStatus.CAN_STACK
                        && store.stackInto(resolved.x(), resolved.y(), carried)) {
                        player.containerMenu.setCarried(carried);
                        if (carried.isEmpty()) ServerGridCarryState.clearAll(player);
                        changed = true;
                    } else if (resolved.status() == GridBackingStore.PlacementStatus.CAN_PLACE
                        && placeWithUnpackedContents(player, store, resolved, carried)) {
                        player.containerMenu.setCarried(ItemStack.EMPTY);
                        ServerGridCarryState.clearAll(player);
                        changed = true;
                    } else if (resolved.status() == GridBackingStore.PlacementStatus.CAN_SWAP
                        && resolved.blockers().size() == 1) {
                        var blockers = resolved.blockers();
                        ServerGridCarryState.SafetyBoxOrigin origin = validSafetyBoxOrigin(player, box);
                        boolean swapped = origin != null && origin.containerIndex() == p.containerIndex()
                            && swapIntoSafetyBoxOrigin(player, store, carried, resolved, origin);
                        if (!swapped) {
                            ContainerOrigin containerOrigin = validContainerOrigin(player);
                            swapped = containerOrigin != null
                                && swapIntoContainerOrigin(player, store, carried, resolved, containerOrigin);
                        }
                        if (!swapped && blockers.size() == 1) {
                            swapped = swapSingleToCursor(player, store, carried, resolved, blockers.iterator().next());
                        }
                        if (swapped) changed = true;
                        else forceSync = true;
                    } else if (resolved.status() == GridBackingStore.PlacementStatus.CAN_SWAP) {
                        forceSync = true;
                    }
                }
                case SWAP -> {
                    if (carried.isEmpty()) break;
                    if (isBlocked(carried)) break;
                    // The client sends the already-resolved top-left anchor. Do
                    // not treat it as a hover cell and center the footprint a
                    // second time; that shifts even-sized items up/left.
                    GridBackingStore.PlacementResult resolved = store.resolveExactPlacement(
                        p.gridX, p.gridY, carried, p.rotated, java.util.Set.of());
                    if (resolved.status() == GridBackingStore.PlacementStatus.CAN_STACK
                        && store.stackInto(resolved.x(), resolved.y(), carried)) {
                        player.containerMenu.setCarried(carried);
                        if (carried.isEmpty()) ServerGridCarryState.clearAll(player);
                        changed = true;
                    } else if (resolved.status() == GridBackingStore.PlacementStatus.CAN_SWAP
                        && resolved.blockers().size() == 1) {
                        var blockers = resolved.blockers();
                        ServerGridCarryState.SafetyBoxOrigin origin = validSafetyBoxOrigin(player, box);
                        boolean swapped = origin != null && origin.containerIndex() == p.containerIndex()
                            && swapIntoSafetyBoxOrigin(player, store, carried, resolved, origin);
                        if (!swapped) {
                            ContainerOrigin containerOrigin = validContainerOrigin(player);
                            swapped = containerOrigin != null
                                && swapIntoContainerOrigin(player, store, carried, resolved, containerOrigin);
                        }
                        if (!swapped && blockers.size() == 1) {
                            swapped = swapSingleToCursor(player, store, carried, resolved, blockers.iterator().next());
                        }
                        if (swapped) changed = true;
                        else forceSync = true;
                    } else if (resolved.status() == GridBackingStore.PlacementStatus.CAN_SWAP) {
                        forceSync = true;
                    }
                }
                case RIGHT_CLICK -> {
                    int anchor = store.findAnchorIndexAt(p.gridX, p.gridY);
                    ItemStack cur = anchor >= 0 ? store.getItemRaw(anchor % gw, anchor / gw).copy() : ItemStack.EMPTY;
                    if (cur.isEmpty() && !carried.isEmpty()) {
                        if (isBlocked(carried)) break;
                        ItemStack single = carried.copyWithCount(1);
                        GridBackingStore.PlacementResult resolved = store.resolveExactPlacement(
                            p.gridX, p.gridY, single, p.rotated, java.util.Set.of());
                        if (resolved.status() == GridBackingStore.PlacementStatus.CAN_PLACE
                            && placeWithUnpackedContents(player, store, resolved, single)) {
                            carried.shrink(1);
                            player.containerMenu.setCarried(carried);
                            changed = true;
                        }
                    } else if (!cur.isEmpty() && carried.isEmpty() && cur.getCount() > 1) {
                        int half = cur.getCount() / 2;
                        ItemStack split = store.splitStackToCursor(anchor, half);
                        if (split.isEmpty()) break;
                        ServerGridCarryState.clearAll(player);
                        player.containerMenu.setCarried(split);
                        changed = true;
                    }
                }
                case SPLIT -> {
                    // Never overwrite a server-side cursor stack. A stale
                    // client can otherwise turn the existing carried item into
                    // an apparent world drop when the split response arrives.
                    if (!carried.isEmpty()) {
                        forceSync = true;
                        break;
                    }
                    int anchor = store.findAnchorIndexAt(p.gridX, p.gridY);
                    if (anchor < 0) break;
                    ItemStack cur = store.getItemRaw(anchor % gw, anchor / gw).copy();
                    if (cur.isEmpty() || cur.getCount() <= 1) break;
                    int half = cur.getCount() / 2;
                    ItemStack split = store.splitStackToCursor(anchor, half);
                    if (split.isEmpty()) break;
                    ServerGridCarryState.clearAll(player);
                    player.containerMenu.setCarried(split);
                    changed = true;
                }
                case DOUBLE_COLLECT -> {
                    ItemStack matcher = carried.isEmpty() ? store.getStackAtIncludingFootprint(p.gridX, p.gridY) : carried;
                    if (matcher.isEmpty()) break;

                    ItemStack cursor = carried.copy();
                    if (cursor.isEmpty()) {
                        int anchor = store.findAnchorIndexAt(p.gridX, p.gridY);
                        ItemStack source = anchor >= 0 ? store.getItemRaw(anchor % gw, anchor / gw) : ItemStack.EMPTY;
                        ItemSize sourceFootprint = source.isEmpty() ? new ItemSize(1, 1) : GridBackingStore.sizeOfStored(source);
                        ItemStack taken = store.remove(p.gridX, p.gridY);
                        if (taken.isEmpty()) break;
                        cursor = taken;
                        if (anchor >= 0) {
                            ServerGridCarryState.rememberSafetyBox(player, boxId(box), p.containerIndex(),
                                anchor % gw, anchor / gw,
                                sourceFootprint, GridBackingStore.isRotated(taken));
                        }
                        changed = true;
                    }

                    List<Integer> anchors = matchingAnchors(store, matcher);
                    for (int anchor : anchors) {
                        if (cursor.getCount() >= cursor.getMaxStackSize()) break;
                        ItemStack stack = store.removeAnchor(anchor);
                        if (stack.isEmpty()) continue;
                        int move = Math.min(stack.getCount(), cursor.getMaxStackSize() - cursor.getCount());
                        cursor.grow(move);
                        stack.shrink(move);
                        changed = true;
                        if (!stack.isEmpty()) {
                            store.placeDirect(anchor % gw, anchor / gw, stack);
                            store.save();
                            break;
                        }
                    }

                    changed |= collectMatchingFromAccessibleSlots(player, cursor);
                    player.containerMenu.setCarried(cursor);
                }
                case RETURN_ORIGIN -> {
                    ServerGridCarryState.SafetyBoxOrigin origin = validSafetyBoxOrigin(player, box);
                    if (origin == null || origin.containerIndex() != p.containerIndex() || carried.isEmpty()) break;
                    if (store.place(origin.x(), origin.y(), carried, origin.rotated())) {
                        player.containerMenu.setCarried(ItemStack.EMPTY);
                        ServerGridCarryState.clearAll(player);
                        changed = true;
                    }
                }
                case COMPLETE_INVENTORY_SWAP -> {
                    ServerGridCarryState.SafetyBoxOrigin origin = validSafetyBoxOrigin(player, box);
                    if (origin == null || origin.containerIndex() != p.containerIndex()
                        || origin.x() != p.gridX() || origin.y() != p.gridY()) break;
                    if (carried.isEmpty()) {
                        ServerGridCarryState.clearAll(player);
                        break;
                    }
                    if (isBlocked(carried)) {
                        ServerGridCarryState.clearAll(player);
                        break;
                    }
                    boolean filled = fillSafetyBoxOrigin(store, carried, origin);
                    ServerGridCarryState.clearAll(player);
                    if (filled) {
                        player.containerMenu.setCarried(carried.isEmpty() ? ItemStack.EMPTY : carried);
                        changed = true;
                    }
                }
            }

            if (changed || forceSync) {
                player.containerMenu.broadcastChanges();
                List<ItemStack> snapshot = store.getAllItems();
                ModNetwork.sendToClient(player, new GridSyncPacket(snapshot, gw, gh,
                    player.containerMenu.getCarried().copy(), p.containerIndex()));
            }
        });
    }

    private static ItemStack findBox(ServerPlayer player) {
        try {
            var o = CuriosApi.getCuriosInventory(player);
            if (o.isPresent()) {
                var r = o.get().findFirstCurio(s -> s.is(ModTags.SAFETY_BOX));
                if (r.isPresent()) return r.get().stack();
            }
        } catch (Exception ignored) {}
        return ItemStack.EMPTY;
    }

    private static boolean isBlocked(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (stack.is(ModTags.SAFETY_BOX)) return true;
        return Config.INSTANCE.isBlacklisted(stack);
    }

    private static UUID boxId(ItemStack box) {
        return box.get(ModDataComponents.BOX_UUID.get());
    }

    private static ServerGridCarryState.SafetyBoxOrigin validSafetyBoxOrigin(ServerPlayer player, ItemStack box) {
        ServerGridCarryState.SafetyBoxOrigin origin = ServerGridCarryState.safetyBoxOrigin(player);
        if (origin != null && Objects.equals(origin.boxId(), boxId(box))) return origin;
        if (origin != null) ServerGridCarryState.clearAll(player);
        return null;
    }

    private static boolean placeWithUnpackedContents(ServerPlayer player,
                                                     GridBackingStore store,
                                                     GridBackingStore.PlacementResult placement,
                                                     ItemStack carried) {
        List<ItemStack> snapshot = store.getAllItems();
        DeltaPackTransferService.Payload payload = DeltaPackTransferService.payload(carried);
        ItemStack placed = payload == null ? carried : payload.emptyCarrier();
        if (!store.place(placement.x(), placement.y(), placed, placement.rotated())) {
            store.restoreAll(snapshot);
            return false;
        }
        if (payload != null
            && !DeltaPackTransferService.insertContents(store, payload.contents())) {
            store.restoreAll(snapshot);
            notifyInternalContentsNoSpace(player, payload);
            return false;
        }
        return true;
    }

    private static boolean swapIntoSafetyBoxOrigin(ServerPlayer player, GridBackingStore store, ItemStack carried,
                                                   GridBackingStore.PlacementResult target,
                                                   ServerGridCarryState.SafetyBoxOrigin origin) {
        int displacedArea = 0;
        for (int blocker : target.blockers()) {
            ItemStack blockerStack = store.getItemRaw(blocker % store.getWidth(), blocker / store.getWidth());
            ItemSize blockerSize = GridBackingStore.sizeOfStored(blockerStack);
            displacedArea += blockerSize.width() * blockerSize.height();
            if (!ContainerGridHelper.canRefillOrigin(origin.footprint(), displacedArea)) return false;
        }
        List<ItemStack> snapshot = store.getAllItems();
        DeltaPackTransferService.Payload payload =
            DeltaPackTransferService.payload(carried);
        List<ItemStack> displaced = new ArrayList<>();
        for (int blocker : target.blockers()) {
            ItemStack removed = store.removeAnchor(blocker);
            if (!removed.isEmpty()) displaced.add(removed);
        }
        ItemStack placed = payload == null ? carried : payload.emptyCarrier();
        if (displaced.isEmpty() || !store.place(
            target.x(), target.y(), placed, target.rotated())) {
            store.restoreAll(snapshot);
            return false;
        }

        displaced.sort(Comparator.comparingInt(GridActionPacket::stackArea).reversed());
        int maxX = origin.x() + origin.footprint().width();
        int maxY = origin.y() + origin.footprint().height();
        for (ItemStack stack : displaced) {
            GridBackingStore.PlacementResult placement = store.findPlacementWithin(stack, GridBackingStore.isRotated(stack),
                origin.x(), origin.y(), maxX, maxY);
            if (placement.status() != GridBackingStore.PlacementStatus.CAN_PLACE
                || !store.place(placement.x(), placement.y(), stack, placement.rotated())) {
                store.restoreAll(snapshot);
                return false;
            }
        }
        if (payload != null
            && !DeltaPackTransferService.insertContents(store, payload.contents())) {
            store.restoreAll(snapshot);
            notifyInternalContentsNoSpace(player, payload);
            return false;
        }

        player.containerMenu.setCarried(ItemStack.EMPTY);
        ServerGridCarryState.clearAll(player);
        return true;
    }

    private static boolean swapSingleToCursor(ServerPlayer player, GridBackingStore store, ItemStack carried,
                                              GridBackingStore.PlacementResult target, int blocker) {
        List<ItemStack> snapshot = store.getAllItems();
        ItemStack displaced = store.removeAnchor(blocker);
        if (displaced.isEmpty()) return false;
        DeltaPackTransferService.Payload payload =
            DeltaPackTransferService.payload(carried);
        ItemStack placed = payload == null ? carried : payload.emptyCarrier();
        if (!store.place(target.x(), target.y(), placed, target.rotated())) {
            store.restoreAll(snapshot);
            return false;
        }
        if (payload != null
            && !DeltaPackTransferService.insertContents(store, payload.contents())) {
            store.restoreAll(snapshot);
            notifyInternalContentsNoSpace(player, payload);
            return false;
        }
        player.containerMenu.setCarried(displaced);
        ServerGridCarryState.clearAll(player);
        return true;
    }

    /** Atomically swaps a safety-box placement with multiple items from the player's current grid menu. */
    private static boolean swapIntoContainerOrigin(ServerPlayer player, GridBackingStore store, ItemStack carried,
                                                   GridBackingStore.PlacementResult target,
                                                   ContainerOrigin origin) {
        var menu = origin.menu();
        if (menu != player.containerMenu || origin.slotIndex() < 0 || origin.slotIndex() >= menu.slots.size()
            || target.x() < 0 || target.y() < 0
            || target.x() >= store.getWidth() || target.y() >= store.getHeight()) return false;

        Slot originAnchor = menu.slots.get(origin.slotIndex());
        Set<Slot> originCells = ContainerGridHelper.footprintCells(menu, originAnchor, origin.footprint());
        if (originCells.size() != origin.footprint().width() * origin.footprint().height()) return false;
        for (Slot cell : originCells) if (!cell.mayPlace(carried)) return false;
        for (int blocker : target.blockers()) {
            if (blocker < 0 || blocker >= store.getSize()) return false;
        }

        int displacedArea = 0;
        for (int blocker : target.blockers()) {
            ItemStack blockerStack = store.getItemRaw(blocker % store.getWidth(), blocker / store.getWidth());
            ItemSize blockerSize = GridBackingStore.sizeOfStored(blockerStack);
            displacedArea += blockerSize.width() * blockerSize.height();
            if (!ContainerGridHelper.canRefillOrigin(origin.footprint(), displacedArea)) return false;
        }

        List<ItemStack> storeSnapshot = store.getAllItems();
        List<ItemStack> menuSnapshot = new ArrayList<>(menu.slots.size());
        for (Slot slot : menu.slots) menuSnapshot.add(slot.getItem().copy());
        DeltaPackTransferService.Payload payload =
            DeltaPackTransferService.payload(carried);
        List<ItemStack> displaced = new ArrayList<>();
        for (int blocker : target.blockers()) {
            ItemStack removed = store.removeAnchor(blocker);
            if (!removed.isEmpty()) displaced.add(removed);
        }
        ItemStack placed = payload == null ? carried : payload.emptyCarrier();
        if (displaced.isEmpty() || !store.place(
            target.x(), target.y(), placed, target.rotated())) {
            store.restoreAll(storeSnapshot);
            return false;
        }

        ContainerGridHelper.invalidate(menu);
        ContainerGridHelper.prepare(menu, ModDataStorage::getCachedSizeFor);
        displaced.sort(Comparator.comparingInt(GridActionPacket::stackArea).reversed());
        for (ItemStack stack : displaced) {
            ContainerGridHelper.PlacementResult placement = ContainerGridHelper.findPlacementWithin(
                menu, originCells, stack, GridBackingStore.isRotated(stack), ModDataStorage::getCachedSizeFor);
            if (placement.status() != GridBackingStore.PlacementStatus.CAN_PLACE || placement.anchor() == null) {
                store.restoreAll(storeSnapshot);
                restoreMenu(menu, menuSnapshot);
                return false;
            }
            ItemStack relocated = stack.copy();
            GridBackingStore.setRotated(relocated, placement.rotated());
            placement.anchor().set(relocated);
            placement.anchor().setChanged();
            ContainerGridHelper.invalidate(menu);
            ContainerGridHelper.prepare(menu, ModDataStorage::getCachedSizeFor);
        }
        if (payload != null
            && !DeltaPackTransferService.insertContents(store, payload.contents())) {
            store.restoreAll(storeSnapshot);
            restoreMenu(menu, menuSnapshot);
            notifyInternalContentsNoSpace(player, payload);
            return false;
        }

        player.containerMenu.setCarried(ItemStack.EMPTY);
        ServerGridCarryState.clearAll(player);
        for (Slot slot : menu.slots) slot.setChanged();
        menu.broadcastChanges();
        ContainerGridHelper.synchronizeStorageMenu(menu);
        return true;
    }

    private static ContainerOrigin validContainerOrigin(ServerPlayer player) {
        ContainerOrigin origin = ServerGridCarryState.containerOrigin(player);
        if (origin == null || origin.menu() != player.containerMenu
            || origin.slotIndex() < 0 || origin.slotIndex() >= player.containerMenu.slots.size()) {
            if (origin != null) ServerGridCarryState.clearAll(player);
            return null;
        }
        return origin;
    }

    private static void restoreMenu(net.minecraft.world.inventory.AbstractContainerMenu menu,
                                    List<ItemStack> snapshot) {
        for (int index = 0; index < menu.slots.size() && index < snapshot.size(); index++) {
            menu.slots.get(index).set(snapshot.get(index).copy());
            menu.slots.get(index).setChanged();
        }
        ContainerGridHelper.invalidate(menu);
    }

    private static boolean fillSafetyBoxOrigin(GridBackingStore store, ItemStack carried,
                                               ServerGridCarryState.SafetyBoxOrigin origin) {
        int maxX = origin.x() + origin.footprint().width();
        int maxY = origin.y() + origin.footprint().height();
        if (origin.x() < 0 || origin.y() < 0 || maxX > store.getWidth() || maxY > store.getHeight()) {
            return false;
        }

        List<ItemStack> snapshot = store.getAllItems();
        ItemSize size = GridBackingStore.sizeOfStored(carried);
        if (size.width() == 1 && size.height() == 1
            && carried.getCount() > 1 && origin.footprint().width() * origin.footprint().height() > 1) {
            List<GridOriginFillPlan.Cell> cells = GridOriginFillPlan.unitCells(
                origin.x(), origin.y(), origin.footprint(), carried.getCount());
            for (GridOriginFillPlan.Cell cell : cells) {
                ItemStack single = carried.copyWithCount(1);
                GridBackingStore.setRotated(single, false);
                if (!store.place(cell.x(), cell.y(), single, false)) {
                    store.restoreAll(snapshot);
                    return false;
                }
            }
            carried.shrink(cells.size());
            return !cells.isEmpty();
        }

        GridBackingStore.PlacementResult placement = store.findPlacementWithin(carried,
            GridBackingStore.isRotated(carried), origin.x(), origin.y(), maxX, maxY);
        if (placement.status() != GridBackingStore.PlacementStatus.CAN_PLACE
            || !store.place(placement.x(), placement.y(), carried, placement.rotated())) {
            store.restoreAll(snapshot);
            return false;
        }
        carried.setCount(0);
        return true;
    }

    private static int stackArea(ItemStack stack) {
        ItemSize size = GridBackingStore.sizeOfStored(stack);
        return size.width() * size.height();
    }

    private static void notifyInternalContentsNoSpace(
        ServerPlayer player, DeltaPackTransferService.Payload payload) {
        if (payload != null && !payload.contents().isEmpty()) {
            ModNetwork.sendTranslatedNoticePlain(player,
                "storage.xero_delta.internal_contents_no_space");
        }
    }

    private static List<Integer> matchingAnchors(GridBackingStore store, ItemStack matcher) {
        List<Integer> anchors = new ArrayList<>();
        for (int i = 0; i < store.getSize(); i++) {
            ItemStack stack = store.getItemRaw(i % store.getWidth(), i / store.getWidth());
            if (isSameItemIgnoringRotation(stack, matcher)) {
                anchors.add(i);
            }
        }
        return anchors;
    }

    private static boolean collectMatchingFromAccessibleSlots(ServerPlayer player, ItemStack cursor) {
        if (cursor.isEmpty()) return false;
        boolean moved = false;

        for (Slot slot : player.containerMenu.slots) {
            if (cursor.getCount() >= cursor.getMaxStackSize()) break;
            if (!slot.mayPickup(player)) continue;
            ItemStack target = slot.getItem();
            if (!isSameItemIgnoringRotation(target, cursor)) continue;
            int move = Math.min(target.getCount(), cursor.getMaxStackSize() - cursor.getCount());
            if (move <= 0) continue;
            cursor.grow(move);
            target.shrink(move);
            if (target.isEmpty()) slot.set(ItemStack.EMPTY);
            slot.setChanged();
            moved = true;
        }

        if (moved) {
            player.containerMenu.broadcastChanges();
        }
        return moved;
    }

    private static boolean isSameItemIgnoringRotation(ItemStack first, ItemStack second) {
        return GridBackingStore.isSameItemIgnoringRotation(first, second);
    }
}
