package com.xtdpotato.xero_delta.network;

import com.xtdpotato.xero_delta.XeroDelta;
import com.xtdpotato.xero_delta.block.PersonalWarehouseBlock;
import com.xtdpotato.xero_delta.data.WarehouseCategory;
import com.xtdpotato.xero_delta.menu.PersonalWarehouseMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record OpenWarehouseCategoryPacket(String category) implements CustomPacketPayload {
    public static final Type<OpenWarehouseCategoryPacket> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(XeroDelta.MOD_ID, "open_warehouse_category"));
    public static final StreamCodec<FriendlyByteBuf, OpenWarehouseCategoryPacket> STREAM_CODEC =
        StreamCodec.of((buffer, packet) -> buffer.writeUtf(packet.category, 32),
            buffer -> new OpenWarehouseCategoryPacket(buffer.readUtf(32)));

    @Override public Type<OpenWarehouseCategoryPacket> type() { return TYPE; }

    public static void handle(OpenWarehouseCategoryPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player
                && player.containerMenu instanceof PersonalWarehouseMenu) {
                PersonalWarehouseBlock.open(player, WarehouseCategory.byId(packet.category));
            }
        });
    }
}
