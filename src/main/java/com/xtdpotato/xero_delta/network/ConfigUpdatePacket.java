package com.xtdpotato.xero_delta.network;

import com.xtdpotato.xero_delta.XeroDelta;
import com.xtdpotato.xero_delta.data.ModDataStorage;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ConfigUpdatePacket(String key, Long price, boolean remove) implements CustomPacketPayload {
    public static final Type<ConfigUpdatePacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(XeroDelta.MOD_ID, "config_update"));

    public static final StreamCodec<FriendlyByteBuf, ConfigUpdatePacket> STREAM_CODEC = StreamCodec.of(
        (buf, packet) -> {
            buf.writeUtf(packet.key);
            buf.writeBoolean(packet.price != null);
            if (packet.price != null) buf.writeLong(packet.price);
            buf.writeBoolean(packet.remove);
        },
        buf -> {
            String key = buf.readUtf();
            Long price = buf.readBoolean() ? buf.readLong() : null;
            boolean remove = buf.readBoolean();
            return new ConfigUpdatePacket(key, price, remove);
        }
    );

    @Override
    public Type<ConfigUpdatePacket> type() { return TYPE; }

    public static void handle(ConfigUpdatePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer sp) {
                var data = ModDataStorage.get(sp.serverLevel());
                if (packet.remove) data.removePrice(packet.key);
                else if (packet.price != null) data.setPrice(packet.key, packet.price);
                ModNetwork.sendSyncToPlayer(sp, SyncDataPacket.from(data, sp.server));
            }
        });
    }
}
