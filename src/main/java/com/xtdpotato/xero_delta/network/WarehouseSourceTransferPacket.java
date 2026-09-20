package com.xtdpotato.xero_delta.network;

import com.xtdpotato.xero_delta.XeroDelta;
import com.xtdpotato.xero_delta.grid.WarehouseTransferService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record WarehouseSourceTransferPacket(String sourceId, String category,
                                            int targetSlot, boolean rotated)
    implements CustomPacketPayload {
    public static final Type<WarehouseSourceTransferPacket> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(XeroDelta.MOD_ID, "warehouse_source_transfer"));
    public static final StreamCodec<FriendlyByteBuf, WarehouseSourceTransferPacket> STREAM_CODEC =
        StreamCodec.of((buffer, packet) -> {
            buffer.writeUtf(packet.sourceId, 256);
            buffer.writeUtf(packet.category, 32);
            buffer.writeVarInt(packet.targetSlot);
            buffer.writeBoolean(packet.rotated);
        }, buffer -> new WarehouseSourceTransferPacket(
            buffer.readUtf(256), buffer.readUtf(32),
            buffer.readVarInt(), buffer.readBoolean()));

    public WarehouseSourceTransferPacket(String sourceId) {
        this(sourceId, "", -1, false);
    }

    public WarehouseSourceTransferPacket(String sourceId, String category) {
        this(sourceId, category, -1, false);
    }

    @Override
    public Type<WarehouseSourceTransferPacket> type() {
        return TYPE;
    }

    public static void handle(WarehouseSourceTransferPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                WarehouseTransferService.moveSource(player, packet.sourceId, packet.category,
                    packet.targetSlot, packet.rotated);
            }
        });
    }
}
