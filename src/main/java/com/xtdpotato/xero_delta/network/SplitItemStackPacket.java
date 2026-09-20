package com.xtdpotato.xero_delta.network;

import com.xtdpotato.xero_delta.XeroDelta;
import com.xtdpotato.xero_delta.trading.TradingInventorySources;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Server-authoritative split request for a concrete inventory/backpack source. */
public record SplitItemStackPacket(String sourceId, int amount)
    implements CustomPacketPayload {
    public static final Type<SplitItemStackPacket> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(XeroDelta.MOD_ID, "split_item_stack"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SplitItemStackPacket> STREAM_CODEC =
        StreamCodec.of((buffer, packet) -> {
            buffer.writeUtf(packet.sourceId == null ? "" : packet.sourceId, 256);
            buffer.writeVarInt(Math.max(1, packet.amount));
        }, buffer -> new SplitItemStackPacket(buffer.readUtf(256), buffer.readVarInt()));

    public static void handle(SplitItemStackPacket packet, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        context.enqueueWork(() -> {
            // The detail-menu split writes to another concrete slot. Do not
            // start it while another item is already held by the cursor: that
            // cursor is the lossless rollback destination for hostile or
            // stateful third-party item handlers.
            if (!player.containerMenu.getCarried().isEmpty()) {
                player.containerMenu.broadcastChanges();
                ModNetwork.sendTranslatedNoticePlain(player,
                    "item_detail.xero_delta.split_no_space");
                return;
            }
            boolean split = TradingInventorySources.splitStack(
                player, packet.sourceId, packet.amount);
            if (!split) {
                ModNetwork.sendTranslatedNoticePlain(player,
                    "item_detail.xero_delta.split_no_space");
            }
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
