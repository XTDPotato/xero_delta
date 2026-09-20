package com.xtdpotato.xero_delta.network;

import com.xtdpotato.xero_delta.Config;
import com.xtdpotato.xero_delta.XeroDelta;
import com.xtdpotato.xero_delta.data.ModDataStorage;
import com.xtdpotato.xero_delta.data.PlayerLayoutSlotRules;
import com.xtdpotato.xero_delta.data.SafetyBoxAccessData;
import com.xtdpotato.xero_delta.data.ServerGridCarryState;
import com.xtdpotato.xero_delta.grid.GridBackingStore;
import com.xtdpotato.xero_delta.grid.DeltaPackTransferService;
import com.xtdpotato.xero_delta.grid.DeltaQuickMoveService;
import com.xtdpotato.xero_delta.item.DeltaPackItem;
import com.xtdpotato.xero_delta.item.SafetyBoxItem;
import com.xtdpotato.xero_delta.tag.ModTags;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import top.theillusivec4.curios.api.CuriosApi;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Server-authoritative shortcut operations for the custom Delta storage
 * surfaces which have no vanilla Slot instances.
 */
public record EquippedStorageShortcutPacket(String identifier, int action, int cell)
    implements CustomPacketPayload {
    public static final int INSERT_CARRIED = 0;
    public static final int SORT = 1;
    public static final int DROP_ONE = 2;
    public static final int DROP_STACK = 3;
    public static final int QUICK_MOVE = 4;

    private static final Set<String> STORAGE_SLOTS = Set.of(
        "chest_rig", "pockets", "backpack", "card_holder", "safety_box");

    public static final Type<EquippedStorageShortcutPacket> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(XeroDelta.MOD_ID, "equipped_storage_shortcut"));
    public static final StreamCodec<FriendlyByteBuf, EquippedStorageShortcutPacket> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, EquippedStorageShortcutPacket::identifier,
            ByteBufCodecs.VAR_INT, EquippedStorageShortcutPacket::action,
            ByteBufCodecs.VAR_INT, EquippedStorageShortcutPacket::cell,
            EquippedStorageShortcutPacket::new);

    @Override
    public Type<EquippedStorageShortcutPacket> type() {
        return TYPE;
    }

    public static void handle(EquippedStorageShortcutPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                || !PlayerLayoutSlotRules.enabled(player)
                || !STORAGE_SLOTS.contains(packet.identifier)
                || packet.action < INSERT_CARRIED || packet.action > QUICK_MOVE
                || packet.cell < -1) return;

            if ("pockets".equals(packet.identifier)) {
                if (packet.action == QUICK_MOVE) quickMoveFromPocket(player, packet.cell);
                return;
            }

            var curiosOptional = CuriosApi.getCuriosInventory(player);
            if (curiosOptional.isEmpty()) return;
            var curios = curiosOptional.get();
            var slotHandler = curios.getStacksHandler(packet.identifier).orElse(null);
            if (slotHandler == null || slotHandler.getSlots() <= 0
                || !curios.isSlotActive(packet.identifier, 0)) return;
            ItemStack carrier = slotHandler.getStacks().getStackInSlot(0);
            if (carrier.isEmpty()) {
                if (packet.action == INSERT_CARRIED) noSpace(player);
                return;
            }

            boolean safetyBox = "safety_box".equals(packet.identifier);
            GridBackingStore grid = gridStore(player, packet.identifier, carrier, safetyBox,
                packet.action == INSERT_CARRIED);
            if (safetyBox && packet.action == INSERT_CARRIED && grid == null) return;
            IItemHandler capability = grid == null
                ? carrier.getCapability(Capabilities.ItemHandler.ITEM) : null;

            Operation result;
            if (grid != null) {
                result = applyGrid(player, packet, grid);
            } else if (capability != null && capability.getSlots() > 0) {
                result = applyCapability(player, packet, capability);
            } else {
                result = packet.action == INSERT_CARRIED ? Operation.NO_SPACE : Operation.NONE;
            }

            if (result == Operation.NO_SPACE) {
                noSpace(player);
                return;
            }
            if (result == Operation.NO_MOVE_SPACE) {
                noMoveSpace(player);
                return;
            }
            if (result == Operation.INTERNAL_CONTENTS_NO_SPACE) {
                internalContentsNoSpace(player);
                return;
            }
            if (result != Operation.CHANGED) return;

            curios.setEquippedCurio(packet.identifier, 0, carrier.copy());
            slotHandler.update();
            player.getInventory().setChanged();
            player.inventoryMenu.broadcastChanges();
            if (player.containerMenu != player.inventoryMenu) player.containerMenu.broadcastChanges();
            if (safetyBox && grid != null) {
                ModNetwork.sendToClient(player, new GridSyncPacket(
                    grid.getAllItems(), grid.getWidth(), grid.getHeight(),
                    player.containerMenu.getCarried().copy(), 0));
            }
        });
    }

    private static void quickMoveFromPocket(ServerPlayer player, int inventoryIndex) {
        if (player.containerMenu != player.inventoryMenu
            || inventoryIndex < 4 || inventoryIndex > 8) return;
        ItemStack source = player.getInventory().getItem(inventoryIndex);
        if (source.isEmpty()) return;
        if (!DeltaQuickMoveService.moveFromPlayerStorage(player, source, "pockets")) {
            noMoveSpace(player);
            return;
        }
        if (source.isEmpty()) {
            player.getInventory().setItem(inventoryIndex, ItemStack.EMPTY);
        }
        player.getInventory().setChanged();
        player.inventoryMenu.broadcastChanges();
    }

    private static GridBackingStore gridStore(ServerPlayer player, String identifier,
                                              ItemStack carrier, boolean safetyBox,
                                              boolean inserting) {
        if (safetyBox) {
            if (!(carrier.getItem() instanceof SafetyBoxItem box)) return null;
            if (inserting) {
                String itemId = carrier.getItemHolder().getKey().location().toString();
                if (!SafetyBoxAccessData.get(player.server).isUnlocked(
                    player.getUUID(), itemId, System.currentTimeMillis())) {
                    ModNetwork.sendTranslatedNotice(player,
                        "safety_box.xero_delta.expired_read_only");
                    return null;
                }
            }
            return new GridBackingStore(carrier, box.getGridWidth(), box.getGridHeight());
        }
        if (!(carrier.getItem() instanceof DeltaPackItem pack)
            || !identifier.equals(pack.slotIdentifier())) return null;
        return new GridBackingStore(carrier, pack.gridWidth(), pack.gridHeight(), 0,
            stack -> GridBackingStore.isBlockedInEquippedStorage(identifier, stack));
    }

    private static Operation applyGrid(ServerPlayer player,
                                       EquippedStorageShortcutPacket packet,
                                       GridBackingStore store) {
        return switch (packet.action) {
            case INSERT_CARRIED -> insertCarriedIntoGrid(player, packet.identifier, store);
            case SORT -> sortGrid(store, packet.identifier);
            case DROP_ONE, DROP_STACK -> dropFromGrid(
                player, store, packet.cell, packet.action == DROP_STACK);
            case QUICK_MOVE -> quickMoveFromGrid(player, packet.identifier, store, packet.cell);
            default -> Operation.NONE;
        };
    }

    private static Operation quickMoveFromGrid(ServerPlayer player, String identifier,
                                               GridBackingStore store, int cell) {
        if (cell < 0 || cell >= store.getSize()) return Operation.NONE;
        int anchor = store.findAnchorIndexAt(cell % store.getWidth(), cell / store.getWidth());
        if (anchor < 0) return Operation.NONE;
        ItemStack removed = store.removeAnchor(anchor);
        if (removed.isEmpty()) return Operation.NONE;
        ItemStack remainder = removed.copy();
        boolean moved = DeltaQuickMoveService.moveFromPlayerStorage(
            player, remainder, identifier);
        if (!remainder.isEmpty()) {
            store.placeDirect(anchor % store.getWidth(), anchor / store.getWidth(), remainder);
            store.save();
        }
        return moved ? Operation.CHANGED : Operation.NO_MOVE_SPACE;
    }

    private static Operation insertCarriedIntoGrid(ServerPlayer player, String identifier,
                                                   GridBackingStore store) {
        ItemStack carried = player.containerMenu.getCarried();
        if (carried.isEmpty() || blocked(identifier, carried)) return Operation.NO_SPACE;

        ItemStack trialCarrier = store.getBoxStack().copy();
        GridBackingStore trial = new GridBackingStore(trialCarrier,
            store.getWidth(), store.getHeight(), store.getContainerIndex(),
            blockedPredicate(identifier));
        DeltaPackTransferService.Payload payload =
            DeltaPackTransferService.payload(carried);
        ItemStack remainder = payload == null
            ? carried.copy() : payload.emptyCarrier().copy();
        insertIntoExistingStacks(trial, remainder);
        if (!remainder.isEmpty()) {
            GridBackingStore.PlacementResult placement = trial.findFreePlacement(remainder);
            if (placement.status() != GridBackingStore.PlacementStatus.CAN_PLACE
                || !trial.place(placement.x(), placement.y(), remainder, placement.rotated())) {
                return Operation.NO_SPACE;
            }
            remainder.setCount(0);
        }
        if (payload != null
            && !DeltaPackTransferService.insertContents(trial, payload.contents())) {
            return Operation.INTERNAL_CONTENTS_NO_SPACE;
        }

        store.restoreAll(trial.getAllItems());
        player.containerMenu.setCarried(ItemStack.EMPTY);
        ServerGridCarryState.clearAll(player);
        return Operation.CHANGED;
    }

    private static Operation sortGrid(GridBackingStore store, String identifier) {
        List<ItemStack> contents = compact(store.getAllItems());
        contents.sort(storageComparator());

        ItemStack trialCarrier = store.getBoxStack().copy();
        GridBackingStore trial = new GridBackingStore(trialCarrier,
            store.getWidth(), store.getHeight(), store.getContainerIndex(),
            blockedPredicate(identifier));
        trial.restoreAll(List.of());
        for (ItemStack stack : contents) {
            ItemStack remainder = stack.copy();
            insertIntoExistingStacks(trial, remainder);
            if (remainder.isEmpty()) continue;
            GridBackingStore.PlacementResult placement = trial.findFreePlacement(remainder);
            if (placement.status() != GridBackingStore.PlacementStatus.CAN_PLACE
                || !trial.place(placement.x(), placement.y(), remainder, placement.rotated())) {
                return Operation.NONE;
            }
        }
        store.restoreAll(trial.getAllItems());
        return Operation.CHANGED;
    }

    private static Operation dropFromGrid(ServerPlayer player, GridBackingStore store,
                                          int cell, boolean wholeStack) {
        if (cell < 0 || cell >= store.getSize()) return Operation.NONE;
        int anchor = store.findAnchorIndexAt(cell % store.getWidth(), cell / store.getWidth());
        if (anchor < 0) return Operation.NONE;
        ItemStack removed = store.removeAnchor(anchor);
        if (removed.isEmpty()) return Operation.NONE;
        ItemStack dropped;
        if (wholeStack || removed.getCount() <= 1) {
            dropped = removed;
        } else {
            dropped = removed.split(1);
            store.placeDirect(anchor % store.getWidth(), anchor / store.getWidth(), removed);
            store.save();
        }
        player.drop(dropped, false);
        return Operation.CHANGED;
    }

    private static Operation applyCapability(ServerPlayer player,
                                             EquippedStorageShortcutPacket packet,
                                             IItemHandler handler) {
        return switch (packet.action) {
            case INSERT_CARRIED -> insertCarriedIntoCapability(player, packet.identifier, handler);
            case SORT -> sortCapability(handler);
            case DROP_ONE, DROP_STACK -> dropFromCapability(
                player, handler, packet.cell, packet.action == DROP_STACK);
            case QUICK_MOVE -> quickMoveFromCapability(
                player, packet.identifier, handler, packet.cell);
            default -> Operation.NONE;
        };
    }

    private static Operation quickMoveFromCapability(ServerPlayer player, String identifier,
                                                     IItemHandler handler, int slot) {
        if (slot < 0 || slot >= handler.getSlots()
            || !player.containerMenu.getCarried().isEmpty()) return Operation.NONE;
        ItemStack extracted = handler.extractItem(slot, Integer.MAX_VALUE, false);
        if (extracted.isEmpty()) return Operation.NONE;
        ItemStack remainder = extracted.copy();
        boolean moved = DeltaQuickMoveService.moveFromPlayerStorage(
            player, remainder, identifier);
        if (!remainder.isEmpty()) {
            ItemStack notRestored = handler.insertItem(slot, remainder, false);
            for (int candidate = 0;
                 candidate < handler.getSlots() && !notRestored.isEmpty(); candidate++) {
                if (candidate != slot) {
                    notRestored = handler.insertItem(candidate, notRestored, false);
                }
            }
            if (!notRestored.isEmpty()) {
                player.containerMenu.setCarried(notRestored);
                moved = true;
            }
        }
        return moved ? Operation.CHANGED : Operation.NO_MOVE_SPACE;
    }

    private static Operation insertCarriedIntoCapability(ServerPlayer player, String identifier,
                                                         IItemHandler handler) {
        ItemStack carried = player.containerMenu.getCarried();
        if (carried.isEmpty() || blocked(identifier, carried)) return Operation.NO_SPACE;
        DeltaPackTransferService.Payload payload =
            DeltaPackTransferService.payload(carried);
        if (payload != null) {
            if (!(handler instanceof IItemHandlerModifiable modifiable)) {
                return Operation.NO_SPACE;
            }
            List<ItemStack> snapshot = new ArrayList<>(handler.getSlots());
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                snapshot.add(handler.getStackInSlot(slot).copy());
            }
            List<ItemStack> transfer = new ArrayList<>();
            transfer.add(payload.emptyCarrier().copy());
            for (ItemStack content : payload.contents()) transfer.add(content.copy());
            for (int transferIndex = 0; transferIndex < transfer.size(); transferIndex++) {
                ItemStack stack = transfer.get(transferIndex);
                ItemStack remainder = stack;
                for (int slot = 0; slot < handler.getSlots() && !remainder.isEmpty(); slot++) {
                    remainder = handler.insertItem(slot, remainder, false);
                }
                if (!remainder.isEmpty()) {
                    restoreCapability(modifiable, snapshot);
                    return transferIndex == 0
                        ? Operation.NO_SPACE : Operation.INTERNAL_CONTENTS_NO_SPACE;
                }
            }
            player.containerMenu.setCarried(ItemStack.EMPTY);
            ServerGridCarryState.clearAll(player);
            return Operation.CHANGED;
        }
        ItemStack simulated = carried.copy();
        for (int slot = 0; slot < handler.getSlots() && !simulated.isEmpty(); slot++) {
            simulated = handler.insertItem(slot, simulated, true);
        }
        if (!simulated.isEmpty()) return Operation.NO_SPACE;

        ItemStack remainder = carried.copy();
        for (int slot = 0; slot < handler.getSlots() && !remainder.isEmpty(); slot++) {
            remainder = handler.insertItem(slot, remainder, false);
        }
        if (!remainder.isEmpty()) return Operation.NO_SPACE;
        player.containerMenu.setCarried(ItemStack.EMPTY);
        ServerGridCarryState.clearAll(player);
        return Operation.CHANGED;
    }

    private static Operation sortCapability(IItemHandler handler) {
        if (!(handler instanceof IItemHandlerModifiable modifiable)) return Operation.NONE;
        List<ItemStack> snapshot = new ArrayList<>(handler.getSlots());
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            snapshot.add(handler.getStackInSlot(slot).copy());
        }
        List<ItemStack> contents = compact(snapshot);
        contents.sort(storageComparator());
        try {
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                modifiable.setStackInSlot(slot, ItemStack.EMPTY);
            }
            for (ItemStack stack : contents) {
                ItemStack remainder = stack.copy();
                for (int slot = 0; slot < handler.getSlots() && !remainder.isEmpty(); slot++) {
                    remainder = handler.insertItem(slot, remainder, false);
                }
                if (!remainder.isEmpty()) {
                    restoreCapability(modifiable, snapshot);
                    return Operation.NONE;
                }
            }
            return Operation.CHANGED;
        } catch (RuntimeException exception) {
            restoreCapability(modifiable, snapshot);
            XeroDelta.LOGGER.warn("Unable to sort equipped capability storage", exception);
            return Operation.NONE;
        }
    }

    private static Operation dropFromCapability(ServerPlayer player, IItemHandler handler,
                                                int slot, boolean wholeStack) {
        if (slot < 0 || slot >= handler.getSlots()) return Operation.NONE;
        ItemStack current = handler.getStackInSlot(slot);
        if (current.isEmpty()) return Operation.NONE;
        ItemStack dropped = handler.extractItem(slot,
            wholeStack ? Integer.MAX_VALUE : 1, false);
        if (dropped.isEmpty()) return Operation.NONE;
        player.drop(dropped, false);
        return Operation.CHANGED;
    }

    private static void restoreCapability(IItemHandlerModifiable handler,
                                          List<ItemStack> snapshot) {
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            handler.setStackInSlot(slot,
                slot < snapshot.size() ? snapshot.get(slot).copy() : ItemStack.EMPTY);
        }
    }

    private static void insertIntoExistingStacks(GridBackingStore store,
                                                 ItemStack remainder) {
        for (int index = 0; index < store.getSize() && !remainder.isEmpty(); index++) {
            int x = index % store.getWidth();
            int y = index / store.getWidth();
            if (store.canStackAt(x, y, remainder)) store.stackInto(x, y, remainder);
        }
    }

    private static List<ItemStack> compact(List<ItemStack> source) {
        List<ItemStack> result = new ArrayList<>();
        for (ItemStack original : source) {
            if (original.isEmpty()) continue;
            ItemStack remainder = original.copy();
            for (ItemStack target : result) {
                if (!GridBackingStore.isSameItemIgnoringRotation(target, remainder)) continue;
                int limit = Math.min(target.getMaxStackSize(), remainder.getMaxStackSize());
                int moved = Math.min(remainder.getCount(), limit - target.getCount());
                if (moved > 0) {
                    target.grow(moved);
                    remainder.shrink(moved);
                }
                if (remainder.isEmpty()) break;
            }
            if (!remainder.isEmpty()) result.add(remainder);
        }
        return result;
    }

    private static Comparator<ItemStack> storageComparator() {
        return Comparator.comparingInt(EquippedStorageShortcutPacket::area).reversed()
            .thenComparing(stack -> BuiltInRegistries.ITEM.getKey(stack.getItem()).toString())
            .thenComparing(stack -> stack.getHoverName().getString())
            .thenComparing(Comparator.comparingInt(ItemStack::getCount).reversed());
    }

    private static int area(ItemStack stack) {
        var size = ModDataStorage.getCachedSizeFor(stack);
        return size.width() * size.height();
    }

    private static Predicate<ItemStack> blockedPredicate(String identifier) {
        return "safety_box".equals(identifier)
            ? GridBackingStore::isBlocked
            : stack -> GridBackingStore.isBlockedInEquippedStorage(identifier, stack);
    }

    private static boolean blocked(String identifier, ItemStack stack) {
        if ("safety_box".equals(identifier)) {
            return stack.isEmpty() || stack.is(ModTags.SAFETY_BOX)
                || Config.INSTANCE.isBlacklisted(stack);
        }
        return GridBackingStore.isBlockedInEquippedStorage(identifier, stack);
    }

    private static void noSpace(ServerPlayer player) {
        ModNetwork.sendTranslatedNoticePlain(player,
            "storage.xero_delta.insufficient_space");
    }

    private static void noMoveSpace(ServerPlayer player) {
        ModNetwork.sendTranslatedNoticePlain(player,
            "storage.xero_delta.no_space_move");
    }

    private static void internalContentsNoSpace(ServerPlayer player) {
        ModNetwork.sendTranslatedNoticePlain(player,
            "storage.xero_delta.internal_contents_no_space");
    }

    private enum Operation {
        NONE,
        CHANGED,
        NO_SPACE,
        NO_MOVE_SPACE,
        INTERNAL_CONTENTS_NO_SPACE
    }
}
