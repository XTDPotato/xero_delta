package com.xtdpotato.xero_delta.network;

import com.xtdpotato.xero_delta.ModDataComponents;
import com.xtdpotato.xero_delta.XeroDelta;
import com.xtdpotato.xero_delta.data.ModDataStorage;
import com.xtdpotato.xero_delta.data.PlayerLayoutSlotRules;
import com.xtdpotato.xero_delta.data.ServerGridCarryState;
import com.xtdpotato.xero_delta.grid.ContainerGridHelper;
import com.xtdpotato.xero_delta.grid.GridBackingStore;
import com.xtdpotato.xero_delta.trading.TradingInventorySource;
import com.xtdpotato.xero_delta.trading.TradingInventorySources;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import com.xtdpotato.xero_delta.menu.CorpseMenu;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/** Atomically moves a Delta-layout source into a native container slot. */
public record InventorySourceToMenuPacket(String sourceId, int containerId,
                                          int targetSlot, boolean rotated)
    implements CustomPacketPayload {
    public static final Type<InventorySourceToMenuPacket> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(XeroDelta.MOD_ID,
            "inventory_source_to_menu"));
    public static final StreamCodec<FriendlyByteBuf, InventorySourceToMenuPacket> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, InventorySourceToMenuPacket::sourceId,
            ByteBufCodecs.VAR_INT, InventorySourceToMenuPacket::containerId,
            ByteBufCodecs.VAR_INT, InventorySourceToMenuPacket::targetSlot,
            ByteBufCodecs.BOOL, InventorySourceToMenuPacket::rotated,
            InventorySourceToMenuPacket::new);

    @Override
    public Type<InventorySourceToMenuPacket> type() {
        return TYPE;
    }

    public static void handle(InventorySourceToMenuPacket packet,
                              IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                || !PlayerLayoutSlotRules.enabled(player)
                || packet.sourceId() == null || packet.sourceId().length() > 256
                || packet.containerId() != player.containerMenu.containerId
                || packet.targetSlot() < 0
                || packet.targetSlot() >= player.containerMenu.slots.size()
                || !player.containerMenu.getCarried().isEmpty()) return;

            AbstractContainerMenu menu = player.containerMenu;
            Slot target = menu.slots.get(packet.targetSlot());
            if (target == null || !target.isActive()) return;
            InventoryTransferSource.Handle source = InventoryTransferSource.find(
                player, packet.sourceId());
            ItemStack sample = source == null ? ItemStack.EMPTY : source.peek();
            if (sample.isEmpty()) return;

            if (menu instanceof CorpseMenu corpseMenu
                && packet.targetSlot() < CorpseMenu.RESERVED_CORPSE_SLOT) {
                placeInCorpseSlot(player, corpseMenu, target, source, sample);
                return;
            }

            if (!target.mayPlace(sample)) {
                noSpace(player);
                return;
            }

            ItemStack extracted = source.extract(sample.getCount());
            if (extracted.isEmpty()) return;
            ContainerGridHelper.refresh(menu, ModDataStorage::getCachedSizeFor);
            ContainerGridHelper.PlacementResult placement = null;
            if (ContainerGridHelper.isGridSlotEnabled(menu, target)) {
                placement = ContainerGridHelper.resolveExactPlacement(
                    menu, target, extracted, packet.rotated(), false,
                    ModDataStorage::getCachedSizeFor);
                if (!placement.isAccepted()
                    || (placement.status() == GridBackingStore.PlacementStatus.CAN_SWAP
                        && placement.blockers().size() != 1)) {
                    if (!source.restore(extracted)) menu.setCarried(extracted);
                    noSpace(player);
                    menu.broadcastChanges();
                    return;
                }
            }
            GridBackingStore.setRotated(extracted, packet.rotated());
            List<ItemStack> before = snapshot(menu);
            menu.setCarried(extracted);
            menu.clicked(placement == null ? target.index : placement.anchor().index,
                0, ClickType.PICKUP, player);

            if (unchanged(menu, before, extracted)) {
                ItemStack cursor = menu.getCarried().copy();
                menu.setCarried(ItemStack.EMPTY);
                if (!source.restore(cursor.isEmpty() ? extracted : cursor)) {
                    menu.setCarried(cursor.isEmpty() ? extracted : cursor);
                }
                noSpace(player);
            } else {
                ItemStack displaced = menu.getCarried().copy();
                menu.setCarried(ItemStack.EMPTY);
                if (!displaced.isEmpty() && !source.restore(displaced)) {
                    menu.setCarried(displaced);
                    noSpace(player);
                }
                ServerGridCarryState.clearAll(player);
            }
            menu.broadcastChanges();
        });
    }

    private static List<ItemStack> snapshot(AbstractContainerMenu menu) {
        List<ItemStack> result = new ArrayList<>(menu.slots.size());
        for (Slot slot : menu.slots) result.add(slot.getItem().copy());
        return result;
    }

    private static void placeInCorpseSlot(ServerPlayer player, CorpseMenu menu,
                                          Slot target,
                                          InventoryTransferSource.Handle source,
                                          ItemStack sample) {
        if (menu.corpseEntity() == null || !target.mayPlace(sample)) {
            noSpace(player);
            return;
        }
        ItemStack extracted = source.extract(sample.getCount());
        if (extracted.isEmpty()) return;
        ItemStack remainder = menu.corpseEntity().placeInPresentationSlot(
            target.getContainerSlot(), extracted);
        if (!remainder.isEmpty()) {
            if (!source.restore(remainder)) player.containerMenu.setCarried(remainder);
            noSpace(player);
            return;
        }
        ServerGridCarryState.clearAll(player);
        menu.broadcastChanges();
    }

    private static boolean unchanged(AbstractContainerMenu menu,
                                     List<ItemStack> before,
                                     ItemStack extracted) {
        for (int i = 0; i < menu.slots.size(); i++) {
            if (!same(before.get(i), menu.slots.get(i).getItem())) return false;
        }
        return same(menu.getCarried(), extracted);
    }

    private static boolean same(ItemStack first, ItemStack second) {
        return first.getCount() == second.getCount()
            && ItemStack.isSameItemSameComponents(stripRotation(first),
                stripRotation(second));
    }

    private static ItemStack stripRotation(ItemStack stack) {
        ItemStack copy = stack.copy();
        copy.remove(ModDataComponents.GRID_ROTATED.get());
        return copy;
    }

    private static void noSpace(ServerPlayer player) {
        ModNetwork.sendTranslatedNoticePlain(player,
            "storage.xero_delta.no_space_move");
    }
}
