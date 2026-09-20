package com.xtdpotato.xero_delta.network;

import com.xtdpotato.xero_delta.XeroDelta;
import com.xtdpotato.xero_delta.data.PlayerFeatureAccessData;
import com.xtdpotato.xero_delta.data.PlayerLayoutSlotRules;
import com.xtdpotato.xero_delta.grid.GridBackingStore;
import com.xtdpotato.xero_delta.item.DeltaPackItem;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import top.theillusivec4.curios.api.CuriosApi;

import java.util.List;

/** Server-authoritative cursor swap for the named equipment slots in the Delta inventory. */
public record CurioSlotSwapPacket(String identifier, int slot) implements CustomPacketPayload {
    public static final Type<CurioSlotSwapPacket> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(XeroDelta.MOD_ID, "curio_slot_swap"));
    public static final StreamCodec<FriendlyByteBuf, CurioSlotSwapPacket> STREAM_CODEC =
        StreamCodec.of((buf, packet) -> {
            buf.writeUtf(packet.identifier, 64);
            buf.writeVarInt(packet.slot);
        }, buf -> new CurioSlotSwapPacket(buf.readUtf(64), buf.readVarInt()));

    @Override
    public Type<CurioSlotSwapPacket> type() {
        return TYPE;
    }

    public static void handle(CurioSlotSwapPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                || !PlayerLayoutSlotRules.enabled(player)
                || packet.identifier == null || packet.identifier.isBlank()
                || packet.identifier.length() > 64
                || "safety_box".equals(packet.identifier)
                || packet.slot < 0) return;
            if ("card_holder".equals(packet.identifier)
                && !PlayerFeatureAccessData.get(player.server)
                    .allowChangeBc(player)) return;
            CuriosApi.getCuriosInventory(player).ifPresent(curios -> {
                var handler = curios.getStacksHandler(packet.identifier).orElse(null);
                if (handler == null || packet.slot >= handler.getSlots()
                    || !curios.isSlotActive(packet.identifier, packet.slot)) return;
                AbstractContainerMenu cursorMenu = player.containerMenu;
                ItemStack carried = cursorMenu.getCarried();
                ItemStack equipped = handler.getStacks().getStackInSlot(packet.slot);
                if (carried.isEmpty()) {
                    if (equipped.isEmpty()) return;
                    curios.setEquippedCurio(packet.identifier, packet.slot, ItemStack.EMPTY);
                    cursorMenu.setCarried(equipped.copy());
                } else if ("backpack".equals(packet.identifier)
                    && carried.getCount() == 1
                    && carried.getItem() instanceof DeltaPackItem rig
                    && "chest_rig".equals(rig.slotIdentifier())) {
                    if (!moveChestRigIntoBackpack(player, cursorMenu, carried, equipped)) return;
                    handler.update();
                    player.inventoryMenu.broadcastChanges();
                    if (player.containerMenu != player.inventoryMenu) {
                        player.containerMenu.broadcastChanges();
                    }
                    return;
                } else {
                    if (carried.getCount() != 1
                        || !handler.getStacks().isItemValid(packet.slot, carried)) return;
                    curios.setEquippedCurio(packet.identifier, packet.slot, carried.copy());
                    cursorMenu.setCarried(equipped.copy());
                }
                handler.update();
                player.inventoryMenu.broadcastChanges();
                if (player.containerMenu != player.inventoryMenu) {
                    player.containerMenu.broadcastChanges();
                }
            });
        });
    }
    private static boolean moveChestRigIntoBackpack(ServerPlayer player,
                                                     AbstractContainerMenu cursorMenu,
                                                     ItemStack carried, ItemStack equipped) {
        if (!(equipped.getItem() instanceof DeltaPackItem backpack)
            || !"backpack".equals(backpack.slotIdentifier())
            || !(carried.getItem() instanceof DeltaPackItem rig)) return false;
        GridBackingStore rigStore = new GridBackingStore(carried, rig.gridWidth(), rig.gridHeight());
        List<ItemStack> rigContents = rigStore.getAllItems();
        ItemStack backpackCopy = equipped.copy();
        GridBackingStore trial = new GridBackingStore(backpackCopy,
            backpack.gridWidth(), backpack.gridHeight(), 0,
            stack -> GridBackingStore.isBlockedInEquippedStorage("backpack", stack));
        ItemStack emptyRig = carried.copy();
        new GridBackingStore(emptyRig, rig.gridWidth(), rig.gridHeight())
            .restoreAll(List.of());
        if (!insertFully(trial, emptyRig)) return false;
        for (ItemStack content : rigContents) {
            if (!content.isEmpty() && !insertFully(trial, content.copy())) return false;
        }
        GridBackingStore actual = new GridBackingStore(equipped,
            backpack.gridWidth(), backpack.gridHeight(), 0,
            stack -> GridBackingStore.isBlockedInEquippedStorage("backpack", stack));
        actual.restoreAll(trial.getAllItems());
        CuriosApi.getCuriosInventory(player).ifPresent(curios -> {
            curios.setEquippedCurio("chest_rig", 0, ItemStack.EMPTY);
            curios.getStacksHandler("chest_rig").ifPresent(handler -> handler.update());
        });
        cursorMenu.setCarried(ItemStack.EMPTY);
        return true;
    }

    private static boolean insertFully(GridBackingStore store, ItemStack stack) {
        if (stack.isEmpty()) return true;
        for (int i = 0; i < store.getSize() && !stack.isEmpty(); i++) {
            int x = i % store.getWidth();
            int y = i / store.getWidth();
            if (store.canStackAt(x, y, stack)) store.stackInto(x, y, stack);
        }
        if (stack.isEmpty()) return true;
        GridBackingStore.PlacementResult placement = store.findFreePlacement(stack);
        if (placement.status() != GridBackingStore.PlacementStatus.CAN_PLACE) return false;
        if (!store.place(placement.x(), placement.y(), stack, placement.rotated())) return false;
        stack.setCount(0);
        return true;
    }
}
