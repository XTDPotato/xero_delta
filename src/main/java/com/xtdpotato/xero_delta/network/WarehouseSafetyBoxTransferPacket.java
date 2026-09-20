package com.xtdpotato.xero_delta.network;

import com.xtdpotato.xero_delta.XeroDelta;
import com.xtdpotato.xero_delta.grid.WarehouseTransferService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Atomic transfer from a warehouse anchor into the equipped safety box. */
public record WarehouseSafetyBoxTransferPacket(int sourceSlot, int targetCell,
                                               boolean rotated)
    implements CustomPacketPayload {
    public static final Type<WarehouseSafetyBoxTransferPacket> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(
            XeroDelta.MOD_ID, "warehouse_safety_box_transfer"));
    public static final StreamCodec<FriendlyByteBuf, WarehouseSafetyBoxTransferPacket>
        STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, WarehouseSafetyBoxTransferPacket::sourceSlot,
            ByteBufCodecs.VAR_INT, WarehouseSafetyBoxTransferPacket::targetCell,
            ByteBufCodecs.BOOL, WarehouseSafetyBoxTransferPacket::rotated,
            WarehouseSafetyBoxTransferPacket::new);

    @Override
    public Type<WarehouseSafetyBoxTransferPacket> type() {
        return TYPE;
    }

    public static void handle(WarehouseSafetyBoxTransferPacket packet,
                              IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                WarehouseTransferService.moveWarehouseSlotToSafetyBox(
                    player, packet.sourceSlot, packet.targetCell, packet.rotated);
            }
        });
    }
}
