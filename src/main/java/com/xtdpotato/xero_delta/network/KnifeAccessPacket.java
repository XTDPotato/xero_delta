package com.xtdpotato.xero_delta.network;

import com.xtdpotato.xero_delta.XeroDelta;
import com.xtdpotato.xero_delta.client.KnifeAccessClientState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public record KnifeAccessPacket(Set<String> unlocked, String selected,
                                Map<String, CompoundTag> stacks)
    implements CustomPacketPayload {
    public static final Type<KnifeAccessPacket> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(XeroDelta.MOD_ID, "knife_access"));
    public static final StreamCodec<FriendlyByteBuf, KnifeAccessPacket> STREAM_CODEC = StreamCodec.of(
        (buf, packet) -> {
            buf.writeVarInt(packet.unlocked.size());
            packet.unlocked.forEach(id -> buf.writeUtf(id, 256));
            buf.writeUtf(packet.selected, 256);
            buf.writeVarInt(packet.stacks.size());
            packet.stacks.forEach((id, stack) -> {
                buf.writeUtf(id, 256);
                buf.writeNbt(stack);
            });
        },
        buf -> {
            int size = Math.min(512, Math.max(0, buf.readVarInt()));
            Set<String> ids = new LinkedHashSet<>();
            for (int index = 0; index < size; index++) ids.add(buf.readUtf(256));
            String selected = buf.readUtf(256);
            int stackCount = Math.min(512, Math.max(0, buf.readVarInt()));
            Map<String, CompoundTag> stacks = new LinkedHashMap<>();
            for (int index = 0; index < stackCount; index++) {
                String id = buf.readUtf(256);
                CompoundTag stack = buf.readNbt();
                if (stack != null) stacks.put(id, stack);
            }
            return new KnifeAccessPacket(Set.copyOf(ids), selected, Map.copyOf(stacks));
        });

    @Override public Type<KnifeAccessPacket> type() { return TYPE; }

    public static void handle(KnifeAccessPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> KnifeAccessClientState.INSTANCE.update(
            packet.unlocked, packet.selected, packet.stacks));
    }
}
