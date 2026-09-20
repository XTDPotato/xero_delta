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

public record WarehouseSlotCategoryTransferPacket(int sourceSlot, String category)
    implements CustomPacketPayload {
    public static final Type<WarehouseSlotCategoryTransferPacket> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(XeroDelta.MOD_ID,
            "warehouse_slot_category_transfer"));
    public static final StreamCodec<FriendlyByteBuf, WarehouseSlotCategoryTransferPacket>
        STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, WarehouseSlotCategoryTransferPacket::sourceSlot,
            ByteBufCodecs.STRING_UTF8, WarehouseSlotCategoryTransferPacket::category,
            WarehouseSlotCategoryTransferPacket::new);

    @Override
    public Type<WarehouseSlotCategoryTransferPacket> type() {
        return TYPE;
    }

    public static void handle(WarehouseSlotCategoryTransferPacket packet,
                              IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                WarehouseTransferService.moveWarehouseSlot(
                    player, packet.sourceSlot, packet.category);
            }
        });
    }
}
