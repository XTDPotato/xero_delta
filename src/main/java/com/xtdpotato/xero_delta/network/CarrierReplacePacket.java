package com.xtdpotato.xero_delta.network;

import com.xtdpotato.xero_delta.XeroDelta;
import com.xtdpotato.xero_delta.compat.DeltaPackAutoEquipService;
import com.xtdpotato.xero_delta.data.PlayerLayoutSlotRules;
import com.xtdpotato.xero_delta.grid.ContainerGridHelper;
import com.xtdpotato.xero_delta.grid.DeltaPackTransferService;
import com.xtdpotato.xero_delta.grid.DeltaQuickMoveService;
import com.xtdpotato.xero_delta.grid.GridBackingStore;
import com.xtdpotato.xero_delta.item.DeltaPackItem;
import com.xtdpotato.xero_delta.trading.TradingSourceAddress;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import top.theillusivec4.curios.api.CuriosApi;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Atomic replacement of an equipped chest rig/backpack from any visible source. */
public record CarrierReplacePacket(String sourceId) implements CustomPacketPayload {
    public static final Type<CarrierReplacePacket> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(XeroDelta.MOD_ID, "carrier_replace"));
    public static final StreamCodec<FriendlyByteBuf, CarrierReplacePacket> STREAM_CODEC =
        StreamCodec.composite(ByteBufCodecs.STRING_UTF8, CarrierReplacePacket::sourceId,
            CarrierReplacePacket::new);

    @Override public Type<CarrierReplacePacket> type() { return TYPE; }

    public static void handle(CarrierReplacePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                || !PlayerLayoutSlotRules.enabled(player)
                || packet.sourceId == null || packet.sourceId.length() > 256) return;
            if (!replace(player, packet.sourceId)) {
                ModNetwork.sendTranslatedNoticePlain(player,
                    "storage.xero_delta.replace_no_space");
            }
        });
    }

    private static boolean replace(ServerPlayer player, String sourceId) {
        InventoryTransferSource.Handle source = InventoryTransferSource.find(player, sourceId);
        ItemStack incomingSample = source == null ? ItemStack.EMPTY : source.peek();
        if (incomingSample.isEmpty() || incomingSample.getCount() != 1
            || !(incomingSample.getItem() instanceof DeltaPackItem incoming)
            || (!"chest_rig".equals(incoming.slotIdentifier())
                && !"backpack".equals(incoming.slotIdentifier()))) return false;
        var curiosOptional = CuriosApi.getCuriosInventory(player);
        if (curiosOptional.isEmpty()) return false;
        var curios = curiosOptional.get();
        String identifier = incoming.slotIdentifier();
        var handler = curios.getStacksHandler(identifier).orElse(null);
        if (handler == null || handler.getSlots() <= 0
            || !curios.isSlotActive(identifier, 0)
            || sourceId.startsWith("curio|" + identifier + "|0")) return false;
        ItemStack equipped = handler.getStacks().getStackInSlot(0).copy();
        StateSnapshot snapshot = StateSnapshot.capture(player);
        boolean sourceInsideReplacedCarrier = isInsideEquippedCarrier(sourceId, identifier);
        ItemStack incomingStack = source.extract(1);
        if (incomingStack.isEmpty()) return false;
        if (equipped.isEmpty()) {
            curios.setEquippedCurio(identifier, 0, incomingStack.copy());
            handler.update();
            sync(player);
            return true;
        }
        // Resolve the old carrier again after extracting the incoming one. When
        // the replacement was stored inside this carrier, the pre-extraction
        // copy still contains it and would duplicate the whole nested payload.
        ItemStack extractedFromEquipped = handler.getStacks().getStackInSlot(0).copy();
        if (sourceInsideReplacedCarrier) equipped = extractedFromEquipped;
        if (!(equipped.getItem() instanceof DeltaPackItem oldPack)
            || !identifier.equals(oldPack.slotIdentifier())) {
            snapshot.restore(player);
            return false;
        }

        GridBackingStore replacement = new GridBackingStore(incomingStack,
            incoming.gridWidth(), incoming.gridHeight(), 0,
            stack -> GridBackingStore.isBlockedInEquippedStorage(identifier, stack));
        DeltaPackTransferService.Payload oldPayload =
            DeltaPackTransferService.payload(equipped);
        List<ItemStack> overflow = new ArrayList<>();
        ItemStack displaced = oldPayload == null
            ? equipped.copyWithCount(1) : oldPayload.emptyCarrier().copyWithCount(1);
        if (sourceInsideReplacedCarrier) {
            ItemStack remainder = displaced.copy();
            insertRemainder(replacement, remainder);
            if (!remainder.isEmpty()) overflow.add(remainder);
        }
        for (ItemStack oldItem : oldPayload == null ? List.<ItemStack>of() : oldPayload.contents()) {
            ItemStack remainder = oldItem.copy();
            insertRemainder(replacement, remainder);
            if (!remainder.isEmpty()) overflow.add(remainder);
        }

        if (!sourceInsideReplacedCarrier && !restoreDisplaced(player, source, displaced)) {
            snapshot.restore(player);
            return false;
        }
        curios.setEquippedCurio(identifier, 0, replacement.getBoxStack().copy());
        handler.update();

        for (ItemStack item : overflow) {
            String fallbackStorage = "chest_rig".equals(identifier)
                ? "backpack" : "chest_rig";
            if (!item.isEmpty()) {
                DeltaPackAutoEquipService.tryStoreInEquippedCarrier(
                    player, item, fallbackStorage);
            }
            if (!item.isEmpty() && player.containerMenu != player.inventoryMenu) {
                DeltaQuickMoveService.moveIntoExternalContainer(player, item);
            }
            if (!item.isEmpty()) {
                snapshot.restore(player);
                return false;
            }
        }
        sync(player);
        return true;
    }

    private static boolean isInsideEquippedCarrier(String sourceId, String identifier) {
        TradingSourceAddress address = TradingSourceAddress.parse(sourceId);
        return address != null
            && address.kind() == TradingSourceAddress.Kind.CURIO
            && address.curioIndex() == 0
            && identifier.equals(address.curioIdentifier());
    }

    private static boolean restoreDisplaced(ServerPlayer player,
                                             InventoryTransferSource.Handle source,
                                             ItemStack displaced) {
        if (displaced == null || displaced.isEmpty()) return true;
        if (source.restore(displaced.copy())) return true;
        ItemStack remainder = displaced.copy();
        if (player.getInventory().add(remainder)) {
            player.getInventory().setChanged();
            return true;
        }
        if (!remainder.isEmpty() && player.containerMenu != player.inventoryMenu
            && DeltaQuickMoveService.moveIntoExternalContainer(player, remainder)
            && remainder.isEmpty()) {
            return true;
        }
        return false;
    }
    private static void insertRemainder(GridBackingStore store, ItemStack remainder) {
        for (int index = 0; index < store.getSize() && !remainder.isEmpty(); index++) {
            int x = index % store.getWidth();
            int y = index / store.getWidth();
            if (store.canStackAt(x, y, remainder)) store.stackInto(x, y, remainder);
        }
        while (!remainder.isEmpty()) {
            var placement = store.findFreePlacement(remainder);
            if (placement.status() != GridBackingStore.PlacementStatus.CAN_PLACE) return;
            ItemStack placed = remainder.copy();
            if (!store.place(placement.x(), placement.y(), placed, placement.rotated())) return;
            remainder.setCount(0);
        }
    }

    private static void sync(ServerPlayer player) {
        player.getInventory().setChanged();
        player.inventoryMenu.broadcastChanges();
        if (player.containerMenu != player.inventoryMenu) player.containerMenu.broadcastChanges();
    }

    private record SlotState(Slot slot, ItemStack stack) {}
    private static final class StateSnapshot {
        private final List<ItemStack> inventory;
        private final List<SlotState> menuSlots;
        private final Map<String, ItemStack> curios;
        private final ItemStack carried;
        private StateSnapshot(List<ItemStack> inventory, List<SlotState> menuSlots,
                              Map<String, ItemStack> curios, ItemStack carried) {
            this.inventory = inventory; this.menuSlots = menuSlots;
            this.curios = curios; this.carried = carried;
        }
        static StateSnapshot capture(ServerPlayer player) {
            List<ItemStack> inventory = new ArrayList<>();
            for (int i = 0; i < player.getInventory().getContainerSize(); i++)
                inventory.add(player.getInventory().getItem(i).copy());
            List<SlotState> menu = new ArrayList<>();
            for (Slot slot : player.containerMenu.slots)
                menu.add(new SlotState(slot, slot.getItem().copy()));
            Map<String, ItemStack> curio = new LinkedHashMap<>();
            CuriosApi.getCuriosInventory(player).ifPresent(c -> {
                for (String id : List.of("chest_rig", "backpack", "safety_box", "card_holder")) {
                    var h = c.getStacksHandler(id).orElse(null);
                    if (h != null && h.getSlots() > 0) curio.put(id, h.getStacks().getStackInSlot(0).copy());
                }
            });
            return new StateSnapshot(inventory, menu, curio,
                player.containerMenu.getCarried().copy());
        }
        void restore(ServerPlayer player) {
            for (int i = 0; i < inventory.size(); i++)
                player.getInventory().setItem(i, inventory.get(i).copy());
            CuriosApi.getCuriosInventory(player).ifPresent(c -> {
                curios.forEach((id, stack) -> c.setEquippedCurio(id, 0, stack.copy()));
                curios.keySet().forEach(id -> c.getStacksHandler(id).ifPresent(h -> h.update()));
            });
            for (int i = 0; i < menuSlots.size(); i++)
                menuSlots.get(i).slot().set(menuSlots.get(i).stack().copy());
            player.containerMenu.setCarried(carried.copy());
            sync(player);
        }
    }
}
