package com.xtdpotato.xero_delta.network;

import com.xtdpotato.xero_delta.Config;
import com.xtdpotato.xero_delta.XeroDelta;
import com.xtdpotato.xero_delta.data.ItemSize;
import com.xtdpotato.xero_delta.data.PlayerLayoutSlotRules;
import com.xtdpotato.xero_delta.data.ServerGridCarryState;
import com.xtdpotato.xero_delta.data.SafetyBoxAccessData;
import com.xtdpotato.xero_delta.grid.DeltaPackTransferService;
import com.xtdpotato.xero_delta.grid.GridBackingStore;
import com.xtdpotato.xero_delta.item.DeltaPackItem;
import com.xtdpotato.xero_delta.item.SafetyBoxItem;
import com.xtdpotato.xero_delta.tag.ModTags;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import top.theillusivec4.curios.api.CuriosApi;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/** Server-authoritative click handling for an equipped chest-rig/backpack/card grid. */
public record EquippedStorageActionPacket(String identifier, int cell, int button,
                                          boolean rotated)
    implements CustomPacketPayload {
    private static final Set<String> STORAGE_SLOTS = Set.of(
        "chest_rig", "backpack", "card_holder", "safety_box");

    public static final Type<EquippedStorageActionPacket> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(XeroDelta.MOD_ID, "equipped_storage_action"));
    public static final StreamCodec<FriendlyByteBuf, EquippedStorageActionPacket> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, EquippedStorageActionPacket::identifier,
            ByteBufCodecs.VAR_INT, EquippedStorageActionPacket::cell,
            ByteBufCodecs.VAR_INT, EquippedStorageActionPacket::button,
            ByteBufCodecs.BOOL, EquippedStorageActionPacket::rotated,
            EquippedStorageActionPacket::new);

    @Override
    public Type<EquippedStorageActionPacket> type() {
        return TYPE;
    }

    public static void handle(EquippedStorageActionPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) apply(packet, player);
        });
    }

    /** Applies one storage click immediately on the server thread. */
    public static boolean apply(EquippedStorageActionPacket packet, ServerPlayer player) {
        if (player == null || !PlayerLayoutSlotRules.enabled(player)
            || !STORAGE_SLOTS.contains(packet.identifier)
            || packet.cell < 0 || packet.button < 0 || packet.button > 1) return false;
        final boolean[] result = {false};
        CuriosApi.getCuriosInventory(player).ifPresent(curios -> {
            var handler = curios.getStacksHandler(packet.identifier).orElse(null);
            if (handler == null || handler.getSlots() <= 0
                || !curios.isSlotActive(packet.identifier, 0)) return;
            ItemStack carrier = handler.getStacks().getStackInSlot(0);
            if (carrier.isEmpty()) return;
            boolean changed;
            if ("safety_box".equals(packet.identifier)
                && carrier.getItem() instanceof SafetyBoxItem box) {
                changed = clickSafetyBox(player, carrier, box, packet.cell,
                    packet.button, packet.rotated);
            } else if (carrier.getItem() instanceof DeltaPackItem pack) {
                changed = pack.slotIdentifier().equals(packet.identifier)
                    && clickDeltaPack(player, carrier, pack, packet.cell, packet.button,
                        packet.rotated);
            } else {
                changed = clickCapabilityStorage(player, carrier,
                    packet.cell, packet.button);
            }
            if (!changed) return;
            curios.setEquippedCurio(packet.identifier, 0, carrier.copy());
            handler.update();
            player.getInventory().setChanged();
            player.inventoryMenu.broadcastChanges();
            if (player.containerMenu != player.inventoryMenu) {
                player.containerMenu.broadcastChanges();
            }
            result[0] = true;
        });
        return result[0];
    }
    private static boolean clickSafetyBox(ServerPlayer player, ItemStack carrier,
                                          SafetyBoxItem box, int cell, int button,
                                          boolean preferredRotated) {
        int width = box.getGridWidth();
        int height = box.getGridHeight();
        if (cell >= width * height) return false;
        int x = cell % width;
        int y = cell / width;
        GridBackingStore store = new GridBackingStore(carrier, width, height);
        AbstractContainerMenu cursorMenu = player.containerMenu;
        ItemStack carried = cursorMenu.getCarried();

        if (!carried.isEmpty()) {
            String itemId = carrier.getItemHolder().getKey().location().toString();
            if (!SafetyBoxAccessData.get(player.server).isUnlocked(
                player.getUUID(), itemId, System.currentTimeMillis())) {
                ModNetwork.sendTranslatedNotice(player,
                    "safety_box.xero_delta.expired_read_only");
                return false;
            }
            if (blocked(carried)) return false;
        }

        if (button == 1) {
            if (carried.isEmpty()) {
                ServerGridCarryState.clearAll(player);
                int anchor = store.findAnchorIndexAt(x, y);
                if (anchor < 0) return false;
                ItemStack current = store.getItemRaw(anchor % width, anchor / width);
                if (current.getCount() == 1) {
                    ItemStack picked = store.removeAnchor(anchor);
                    if (picked.isEmpty()) return false;
                    cursorMenu.setCarried(picked);
                    return true;
                }
                int take = Math.max(1, (current.getCount() + 1) / 2);
                ItemStack picked = store.splitStackToCursor(anchor, take);
                if (picked.isEmpty()) return false;
                cursorMenu.setCarried(picked);
                return true;
            }
            ItemStack single = carried.copyWithCount(1);
            GridBackingStore.PlacementResult placement = store.resolveExactPlacement(
                x, y, single, preferredRotated, Set.of());
            if (placement.status() == GridBackingStore.PlacementStatus.CAN_STACK) {
                if (!store.stackInto(placement.x(), placement.y(), single)) return false;
            } else if (placement.status() == GridBackingStore.PlacementStatus.CAN_PLACE) {
                if (!placeWithUnpackedContents(player, store, placement, single)) return false;
            } else {
                return false;
            }
            carried.shrink(1);
            cursorMenu.setCarried(carried.isEmpty() ? ItemStack.EMPTY : carried);
            if (carried.isEmpty()) ServerGridCarryState.clearAll(player);
            return true;
        }

        if (carried.isEmpty()) {
            int anchor = store.findAnchorIndexAt(x, y);
            if (anchor < 0) return false;
            ItemStack removed = store.removeAnchor(anchor);
            if (removed.isEmpty()) return false;
            ServerGridCarryState.clearAll(player);
            cursorMenu.setCarried(removed);
            return true;
        }

        GridBackingStore.PlacementResult placement = store.resolveExactPlacement(
            x, y, carried, preferredRotated, Set.of());
        if (placement.status() == GridBackingStore.PlacementStatus.CAN_STACK) {
            if (!store.stackInto(placement.x(), placement.y(), carried)) return false;
            cursorMenu.setCarried(carried.isEmpty() ? ItemStack.EMPTY : carried);
            if (carried.isEmpty()) ServerGridCarryState.clearAll(player);
            return true;
        }
        if (placement.status() == GridBackingStore.PlacementStatus.CAN_PLACE) {
            if (!placeWithUnpackedContents(player, store, placement, carried)) return false;
            cursorMenu.setCarried(ItemStack.EMPTY);
            ServerGridCarryState.clearAll(player);
            return true;
        }
        if (placement.status() != GridBackingStore.PlacementStatus.CAN_SWAP
            || placement.blockers().size() != 1) return false;
        return swapSingleToCursor(player, store, carried, placement,
            placement.blockers().iterator().next());
    }

    private static boolean clickDeltaPack(ServerPlayer player, ItemStack carrier,
                                          DeltaPackItem pack, int cell, int button,
                                          boolean preferredRotated) {
        int width = pack.gridWidth();
        int height = pack.gridHeight();
        if (cell >= width * height) return false;
        int x = cell % width;
        int y = cell / width;
        GridBackingStore store = new GridBackingStore(carrier, width, height, 0,
            stack -> GridBackingStore.isBlockedInEquippedStorage(
                pack.slotIdentifier(), stack));
        AbstractContainerMenu cursorMenu = player.containerMenu;
        ItemStack carried = cursorMenu.getCarried();

        if (button == 1) {
            if (carried.isEmpty()) {
                ServerGridCarryState.clearAll(player);
                int anchor = store.findAnchorIndexAt(x, y);
                if (anchor < 0) return false;
                ItemStack current = store.getItemRaw(anchor % width, anchor / width);
                if (current.getCount() == 1) {
                    ItemStack picked = store.removeAnchor(anchor);
                    if (picked.isEmpty()) return false;
                    cursorMenu.setCarried(picked);
                    return true;
                }
                int take = Math.max(1, (current.getCount() + 1) / 2);
                ItemStack picked = store.splitStackToCursor(anchor, take);
                if (picked.isEmpty()) return false;
                cursorMenu.setCarried(picked);
                return true;
            }
            if (GridBackingStore.isBlockedInEquippedStorage(
                pack.slotIdentifier(), carried)) return false;
            ItemStack single = carried.copyWithCount(1);
            GridBackingStore.PlacementResult placement = store.resolveExactPlacement(
                x, y, single, preferredRotated, Set.of());
            if (placement.status() == GridBackingStore.PlacementStatus.CAN_STACK) {
                if (!store.stackInto(placement.x(), placement.y(), single)) return false;
            } else if (placement.status() == GridBackingStore.PlacementStatus.CAN_PLACE) {
                if (!fitsRegion(pack, placement.x(), placement.y(), single, placement.rotated())
                    || !placeWithUnpackedContents(player, store, placement, single)) return false;
            } else {
                return false;
            }
            carried.shrink(1);
            cursorMenu.setCarried(carried.isEmpty() ? ItemStack.EMPTY : carried);
            if (carried.isEmpty()) ServerGridCarryState.clearAll(player);
            return true;
        }

        if (carried.isEmpty()) {
            int anchor = store.findAnchorIndexAt(x, y);
            if (anchor < 0) return false;
            ItemStack source = store.getItemRaw(anchor % width, anchor / width);
            ItemSize footprint = GridBackingStore.sizeOfStored(source);
            ItemStack removed = store.removeAnchor(anchor);
            if (removed.isEmpty()) return false;
            ServerGridCarryState.rememberEquippedStorage(player, pack.slotIdentifier(),
                anchor % width, anchor / width, footprint, GridBackingStore.isRotated(removed));
            cursorMenu.setCarried(removed);
            return true;
        }

        if (GridBackingStore.isBlockedInEquippedStorage(
            pack.slotIdentifier(), carried)) return false;
        GridBackingStore.PlacementResult placement = store.resolveExactPlacement(
            x, y, carried, preferredRotated, Set.of());
        if (placement.status() == GridBackingStore.PlacementStatus.CAN_STACK) {
            if (!store.stackInto(placement.x(), placement.y(), carried)) return false;
            cursorMenu.setCarried(carried.isEmpty() ? ItemStack.EMPTY : carried);
            if (carried.isEmpty()) ServerGridCarryState.clearAll(player);
            return true;
        }
        if (!fitsRegion(pack, placement.x(), placement.y(), carried, placement.rotated())) return false;
        if (placement.status() == GridBackingStore.PlacementStatus.CAN_PLACE) {
            if (!placeWithUnpackedContents(player, store, placement, carried)) return false;
            cursorMenu.setCarried(ItemStack.EMPTY);
            ServerGridCarryState.clearAll(player);
            return true;
        }
        if (placement.status() != GridBackingStore.PlacementStatus.CAN_SWAP
            || placement.blockers().size() != 1) return false;
        ServerGridCarryState.EquippedStorageOrigin origin =
            ServerGridCarryState.equippedStorageOrigin(player);
        if (origin != null && swapIntoEquippedOrigin(player, store, pack.slotIdentifier(),
            carried, placement, origin)) return true;
        if (placement.blockers().size() != 1) return false;
        return swapSingleToCursor(player, store, carried, placement,
            placement.blockers().iterator().next());
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

    private static boolean swapIntoEquippedOrigin(ServerPlayer player,
                                                  GridBackingStore destination,
                                                  String destinationIdentifier,
                                                  ItemStack carried,
                                                  GridBackingStore.PlacementResult target,
                                                  ServerGridCarryState.EquippedStorageOrigin origin) {
        GridBackingStore source = resolveEquippedStore(player, origin);
        if (source == null) {
            ServerGridCarryState.clearEquippedStorage(player);
            return false;
        }
        boolean sameStore = origin.identifier().equals(destinationIdentifier);
        if (sameStore) source = destination;
        int maxX = origin.x() + origin.footprint().width();
        int maxY = origin.y() + origin.footprint().height();
        if (origin.x() < 0 || origin.y() < 0
            || maxX > source.getWidth() || maxY > source.getHeight()) return false;

        List<ItemStack> destinationSnapshot = destination.getAllItems();
        List<ItemStack> sourceSnapshot = sameStore ? destinationSnapshot : source.getAllItems();
        List<ItemStack> displaced = new ArrayList<>();
        for (int blocker : target.blockers()) {
            ItemStack removed = destination.removeAnchor(blocker);
            if (!removed.isEmpty()) displaced.add(removed);
        }
        DeltaPackTransferService.Payload payload = DeltaPackTransferService.payload(carried);
        ItemStack placed = payload == null ? carried : payload.emptyCarrier();
        if (displaced.isEmpty()
            || !destination.place(target.x(), target.y(), placed, target.rotated())) {
            destination.restoreAll(destinationSnapshot);
            return false;
        }

        displaced.sort(Comparator.comparingInt(EquippedStorageActionPacket::stackArea).reversed());
        for (ItemStack stack : displaced) {
            GridBackingStore.PlacementResult placement = source.findPlacementWithin(
                stack, GridBackingStore.isRotated(stack), origin.x(), origin.y(), maxX, maxY);
            if (placement.status() != GridBackingStore.PlacementStatus.CAN_PLACE
                || !source.place(placement.x(), placement.y(), stack, placement.rotated())) {
                destination.restoreAll(destinationSnapshot);
                if (!sameStore) source.restoreAll(sourceSnapshot);
                return false;
            }
        }
        if (payload != null
            && !DeltaPackTransferService.insertContents(destination, payload.contents())) {
            destination.restoreAll(destinationSnapshot);
            if (!sameStore) source.restoreAll(sourceSnapshot);
            notifyInternalContentsNoSpace(player, payload);
            return false;
        }

        player.containerMenu.setCarried(ItemStack.EMPTY);
        ServerGridCarryState.clearAll(player);
        updateEquippedStorage(player, origin.identifier());
        return true;
    }

    private static boolean swapSingleToCursor(ServerPlayer player, GridBackingStore store,
                                              ItemStack carried,
                                              GridBackingStore.PlacementResult target,
                                              int blocker) {
        List<ItemStack> snapshot = store.getAllItems();
        ItemStack displaced = store.removeAnchor(blocker);
        if (displaced.isEmpty()) return false;
        DeltaPackTransferService.Payload payload = DeltaPackTransferService.payload(carried);
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

    private static GridBackingStore resolveEquippedStore(
        ServerPlayer player, ServerGridCarryState.EquippedStorageOrigin origin) {
        final GridBackingStore[] result = {null};
        CuriosApi.getCuriosInventory(player).ifPresent(curios -> {
            var handler = curios.getStacksHandler(origin.identifier()).orElse(null);
            if (handler == null || handler.getSlots() <= 0
                || !curios.isSlotActive(origin.identifier(), 0)) return;
            ItemStack carrier = handler.getStacks().getStackInSlot(0);
            if (!(carrier.getItem() instanceof DeltaPackItem pack)
                || !origin.identifier().equals(pack.slotIdentifier())) return;
            result[0] = new GridBackingStore(carrier, pack.gridWidth(), pack.gridHeight(), 0,
                stack -> GridBackingStore.isBlockedInEquippedStorage(origin.identifier(), stack));
        });
        return result[0];
    }

    private static void notifyInternalContentsNoSpace(
        ServerPlayer player, DeltaPackTransferService.Payload payload) {
        if (payload != null && !payload.contents().isEmpty()) {
            ModNetwork.sendTranslatedNoticePlain(player,
                "storage.xero_delta.internal_contents_no_space");
        }
    }

    private static void updateEquippedStorage(ServerPlayer player, String identifier) {
        CuriosApi.getCuriosInventory(player).ifPresent(curios ->
            curios.getStacksHandler(identifier).ifPresent(handler -> handler.update()));
    }

    private static int stackArea(ItemStack stack) {
        ItemSize size = GridBackingStore.sizeOfStored(stack);
        return size.width() * size.height();
    }

    private static boolean clickCapabilityStorage(ServerPlayer player, ItemStack carrier,
                                                  int slot, int button) {
        IItemHandler storage = carrier.getCapability(Capabilities.ItemHandler.ITEM);
        if (storage == null || slot >= storage.getSlots()) return false;
        AbstractContainerMenu cursorMenu = player.containerMenu;
        ItemStack carried = cursorMenu.getCarried();
        if (button == 1) {
            if (carried.isEmpty()) {
                ItemStack current = storage.getStackInSlot(slot);
                if (current.isEmpty()) return false;
                int requested = Math.max(1, (current.getCount() + 1) / 2);
                ItemStack simulated = storage.extractItem(slot, requested, true);
                if (simulated.isEmpty() || simulated.getCount() != requested) return false;
                ItemStack extracted = storage.extractItem(slot, requested, false);
                if (extracted.isEmpty()) return false;
                cursorMenu.setCarried(extracted);
                return true;
            }
            if (blocked(carried)) return false;
            ItemStack single = carried.copyWithCount(1);
            ItemStack remainder = storage.insertItem(slot, single, false);
            if (!remainder.isEmpty()) return false;
            carried.shrink(1);
            cursorMenu.setCarried(carried.isEmpty() ? ItemStack.EMPTY : carried);
            return true;
        }

        if (carried.isEmpty()) {
            ItemStack extracted = storage.extractItem(slot, Integer.MAX_VALUE, false);
            if (extracted.isEmpty()) return false;
            cursorMenu.setCarried(extracted);
            return true;
        }
        if (blocked(carried)) return false;
        ItemStack remainder = storage.insertItem(slot, carried.copy(), false);
        if (remainder.getCount() < carried.getCount()) {
            cursorMenu.setCarried(remainder.isEmpty() ? ItemStack.EMPTY : remainder);
            return true;
        }

        ItemStack existing = storage.getStackInSlot(slot).copy();
        if (existing.isEmpty()) return false;
        ItemStack extracted = storage.extractItem(slot, Integer.MAX_VALUE, false);
        if (extracted.isEmpty()) return false;
        ItemStack swapRemainder = storage.insertItem(slot, carried.copy(), false);
        if (swapRemainder.isEmpty()) {
            cursorMenu.setCarried(extracted);
            return true;
        }
        storage.extractItem(slot, Integer.MAX_VALUE, false);
        storage.insertItem(slot, extracted, false);
        return false;
    }

    private static boolean fitsRegion(DeltaPackItem pack, int x, int y,
                                      ItemStack stack, boolean rotated) {
        if (x < 0 || y < 0 || stack.isEmpty()) return false;
        ItemSize size = GridBackingStore.orientedSize(stack, rotated);
        return pack.regions().stream().anyMatch(region ->
            region.contains(x, y, size.width(), size.height()));
    }

    private static boolean blocked(ItemStack stack) {
        return stack.isEmpty() || stack.is(ModTags.SAFETY_BOX)
            || Config.INSTANCE.isBlacklisted(stack);
    }

}
