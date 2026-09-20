package com.xtdpotato.xero_delta.network;

import com.xtdpotato.xero_delta.XeroDelta;
import com.xtdpotato.xero_delta.block.PersonalWarehouseBlock;
import com.xtdpotato.xero_delta.data.PersonalWarehouseData;
import com.xtdpotato.xero_delta.menu.PersonalWarehouseMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record RenameWarehousePacket(String name) implements CustomPacketPayload {
    public static final Type<RenameWarehousePacket> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(XeroDelta.MOD_ID, "rename_warehouse"));
    public static final StreamCodec<FriendlyByteBuf, RenameWarehousePacket> STREAM_CODEC =
        StreamCodec.of((buffer, packet) -> buffer.writeUtf(packet.name,
                PersonalWarehouseData.MAX_NAME_LENGTH * 4),
            buffer -> new RenameWarehousePacket(buffer.readUtf(
                PersonalWarehouseData.MAX_NAME_LENGTH * 4)));

    @Override public Type<RenameWarehousePacket> type() { return TYPE; }

    public static void handle(RenameWarehousePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                || !(player.containerMenu instanceof PersonalWarehouseMenu menu)) return;
            PersonalWarehouseData.get(player.server).rename(player.getUUID(), packet.name);
            PersonalWarehouseBlock.open(player, menu.category());
        });
    }
}
