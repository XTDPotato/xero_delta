package com.xtdpotato.xero_delta.network;

import com.xtdpotato.xero_delta.XeroDelta;
import com.xtdpotato.xero_delta.compat.BetterLootingPickupCompat;
import com.xtdpotato.xero_delta.compat.DeltaPackAutoEquipService;
import com.xtdpotato.xero_delta.data.PlayerLayoutSlotRules;
import com.xtdpotato.xero_delta.data.ServerGridCarryState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Set;

/** Atomically moves one source into an equipped Delta storage target. */
public record InventorySourceToEquippedStoragePacket(
    String sourceId, String identifier, int cell, boolean rotated)
    implements CustomPacketPayload {
    private static final Set<String> TARGETS = Set.of(
        "chest_rig", "backpack", "card_holder", "safety_box", "pockets");
    public static final Type<InventorySourceToEquippedStoragePacket> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(XeroDelta.MOD_ID,
            "inventory_source_to_equipped_storage"));
    public static final StreamCodec<FriendlyByteBuf, InventorySourceToEquippedStoragePacket>
        STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, InventorySourceToEquippedStoragePacket::sourceId,
            ByteBufCodecs.STRING_UTF8, InventorySourceToEquippedStoragePacket::identifier,
            ByteBufCodecs.VAR_INT, InventorySourceToEquippedStoragePacket::cell,
            ByteBufCodecs.BOOL, InventorySourceToEquippedStoragePacket::rotated,
            InventorySourceToEquippedStoragePacket::new);

    @Override public Type<InventorySourceToEquippedStoragePacket> type() { return TYPE; }

    public static void handle(InventorySourceToEquippedStoragePacket packet,
                              IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                || !PlayerLayoutSlotRules.enabled(player)
                || packet.sourceId == null || packet.sourceId.length() > 256
                || !TARGETS.contains(packet.identifier)
                || packet.cell < -1 || !player.containerMenu.getCarried().isEmpty()) return;
            InventoryTransferSource.Handle source =
                InventoryTransferSource.find(player, packet.sourceId);
            ItemStack sample = source == null ? ItemStack.EMPTY : source.peek();
            if (sample.isEmpty()) return;
            ItemStack extracted = source.extract(sample.getCount());
            if (extracted.isEmpty()) return;

            boolean changed;
            if ("pockets".equals(packet.identifier) && packet.cell >= 4) {
                changed = placeInExactPocket(player, packet.cell, extracted);
                player.containerMenu.setCarried(ItemStack.EMPTY);
            } else if (packet.cell >= 0 && !"pockets".equals(packet.identifier)) {
                player.containerMenu.setCarried(extracted);
                changed = EquippedStorageActionPacket.apply(
                    new EquippedStorageActionPacket(packet.identifier, packet.cell, 0,
                        packet.rotated), player);
            } else {
                ItemStack remainder = extracted.copy();
                changed = switch (packet.identifier) {
                    case "backpack", "chest_rig" ->
                        DeltaPackAutoEquipService.tryStoreInEquippedCarrier(
                            player, remainder, packet.identifier);
                    case "safety_box" ->
                        BetterLootingPickupCompat.storeInSafetyBox(player, remainder);
                    case "pockets" ->
                        BetterLootingPickupCompat.storeInPockets(player, remainder);
                    default -> false;
                };
                player.containerMenu.setCarried(remainder);
            }

            ItemStack cursor = player.containerMenu.getCarried().copy();
            if (!changed) {
                player.containerMenu.setCarried(ItemStack.EMPTY);
                if (!source.restore(extracted)) player.containerMenu.setCarried(extracted);
                noSpace(player);
            } else if (!cursor.isEmpty()) {
                player.containerMenu.setCarried(ItemStack.EMPTY);
                if (!source.restore(cursor)) {
                    player.containerMenu.setCarried(cursor);
                    noSpace(player);
                }
            } else {
                ServerGridCarryState.clearAll(player);
            }
            player.containerMenu.broadcastChanges();
            player.inventoryMenu.broadcastChanges();
        });
    }

    private static boolean placeInExactPocket(ServerPlayer player, int inventorySlot,
                                              ItemStack extracted) {
        if (inventorySlot < 4 || inventorySlot > 8 || extracted.isEmpty()
            || !PlayerLayoutSlotRules.canPlace(player, inventorySlot, extracted)) return false;
        ItemStack current = player.getInventory().getItem(inventorySlot);
        if (current.isEmpty()) {
            player.getInventory().setItem(inventorySlot, extracted.copy());
            player.getInventory().setChanged();
            extracted.setCount(0);
            return true;
        }
        if (!ItemStack.isSameItemSameComponents(current, extracted)) return false;
        int room = Math.max(0, current.getMaxStackSize() - current.getCount());
        if (room < extracted.getCount()) return false;
        current.grow(extracted.getCount());
        player.getInventory().setChanged();
        extracted.setCount(0);
        return true;
    }

    private static void noSpace(ServerPlayer player) {
        ModNetwork.sendTranslatedNoticePlain(player,
            "storage.xero_delta.no_space_move");
    }
}
