package com.xtdpotato.xero_delta.network;

import com.xtdpotato.xero_delta.XeroDelta;
import com.xtdpotato.xero_delta.data.LootSearchManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Requests that one still-hidden item becomes the next search target. */
public record LootSearchHoverPacket(int containerId, int slotId) implements CustomPacketPayload {
    public static final Type<LootSearchHoverPacket> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(XeroDelta.MOD_ID, "loot_search_hover"));
    public static final StreamCodec<FriendlyByteBuf, LootSearchHoverPacket> STREAM_CODEC =
        StreamCodec.of((buffer, packet) -> {
            buffer.writeVarInt(packet.containerId);
            buffer.writeVarInt(packet.slotId);
        }, buffer -> new LootSearchHoverPacket(buffer.readVarInt(), buffer.readVarInt()));

    @Override
    public Type<LootSearchHoverPacket> type() {
        return TYPE;
    }

    public static void handle(LootSearchHoverPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                LootSearchManager.prioritizeNext(player, packet.containerId, packet.slotId);
            }
        });
    }
}
