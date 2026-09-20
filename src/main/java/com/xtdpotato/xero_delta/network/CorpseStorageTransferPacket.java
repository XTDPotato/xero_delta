package com.xtdpotato.xero_delta.network;

import com.xtdpotato.xero_delta.XeroDelta;
import com.xtdpotato.xero_delta.entity.CorpseEntity;
import com.xtdpotato.xero_delta.grid.GridBackingStore;
import com.xtdpotato.xero_delta.menu.CorpseMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Set;

/** Atomically transfers an exact source into a corpse carrier grid cell. */
public record CorpseStorageTransferPacket(String sourceId, int corpseId,
                                          int carrierSlot, int cell,
                                          boolean rotated)
    implements CustomPacketPayload {
    public static final Type<CorpseStorageTransferPacket> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(XeroDelta.MOD_ID,
            "corpse_storage_transfer"));
    public static final StreamCodec<FriendlyByteBuf, CorpseStorageTransferPacket> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, CorpseStorageTransferPacket::sourceId,
            ByteBufCodecs.VAR_INT, CorpseStorageTransferPacket::corpseId,
            ByteBufCodecs.VAR_INT, CorpseStorageTransferPacket::carrierSlot,
            ByteBufCodecs.VAR_INT, CorpseStorageTransferPacket::cell,
            ByteBufCodecs.BOOL, CorpseStorageTransferPacket::rotated,
            CorpseStorageTransferPacket::new);

    @Override public Type<CorpseStorageTransferPacket> type() { return TYPE; }

    public static void handle(CorpseStorageTransferPacket packet,
                              IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                || !(player.containerMenu instanceof CorpseMenu menu)
                || menu.containerId != player.containerMenu.containerId
                || menu.corpseEntityId() != packet.corpseId
                || packet.sourceId == null || packet.sourceId.length() > 256
                || (!"cursor".equals(packet.sourceId)
                    && !player.containerMenu.getCarried().isEmpty())) return;
            CorpseEntity corpse = menu.corpseEntity();
            GridBackingStore store = corpse == null ? null
                : corpse.carrierStore(packet.carrierSlot);
            if (corpse == null || corpse.getId() != packet.corpseId
                || player.distanceToSqr(corpse) > 64.0D || store == null
                || packet.cell < 0 || packet.cell >= store.getSize()) return;
            InventoryTransferSource.Handle source = InventoryTransferSource.find(
                player, packet.sourceId);
            ItemStack sample = source == null ? ItemStack.EMPTY : source.peek();
            if (sample.isEmpty()) return;
            int sourceAnchor = sourceAnchor(packet.sourceId, packet.corpseId,
                packet.carrierSlot);
            Set<Integer> ignored = sourceAnchor < 0 ? Set.of() : Set.of(sourceAnchor);
            GridBackingStore.PlacementResult placement = store.resolveExactPlacement(
                packet.cell % store.getWidth(), packet.cell / store.getWidth(),
                sample, packet.rotated, ignored);
            if (placement.status() != GridBackingStore.PlacementStatus.CAN_PLACE
                && placement.status() != GridBackingStore.PlacementStatus.CAN_STACK) {
                noSpace(player);
                return;
            }
            ItemStack extracted = source.extract(sample.getCount());
            if (extracted.isEmpty()) return;
            store = corpse.carrierStore(packet.carrierSlot);
            if (store == null) {
                source.restore(extracted);
                return;
            }
            placement = store.resolveExactPlacement(placement.x(), placement.y(),
                extracted, placement.rotated(), Set.of());
            boolean placed;
            if (placement.status() == GridBackingStore.PlacementStatus.CAN_STACK) {
                placed = store.stackInto(placement.x(), placement.y(), extracted);
            } else if (placement.status() == GridBackingStore.PlacementStatus.CAN_PLACE) {
                placed = store.place(placement.x(), placement.y(), extracted,
                    placement.rotated());
                if (placed) extracted.setCount(0);
            } else {
                placed = false;
            }
            if (!placed || !extracted.isEmpty()) {
                if (!source.restore(extracted)) player.containerMenu.setCarried(extracted);
                noSpace(player);
                return;
            }
            corpse.carrierContentsChanged(packet.carrierSlot);
            menu.broadcastChanges();
        });
    }

    private static int sourceAnchor(String sourceId, int corpseId, int carrierSlot) {
        if (sourceId == null || !sourceId.startsWith("corpse_storage|")) return -1;
        String[] parts = sourceId.split("\\|", -1);
        if (parts.length != 4) return -1;
        try {
            return Integer.parseInt(parts[1]) == corpseId
                && Integer.parseInt(parts[2]) == carrierSlot
                ? Integer.parseInt(parts[3]) : -1;
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private static void noSpace(ServerPlayer player) {
        ModNetwork.sendTranslatedNoticePlain(player,
            "storage.xero_delta.no_space_move");
    }
}
