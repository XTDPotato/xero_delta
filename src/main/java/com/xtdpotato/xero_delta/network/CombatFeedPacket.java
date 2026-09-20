package com.xtdpotato.xero_delta.network;

import com.xtdpotato.xero_delta.XeroDelta;
import com.xtdpotato.xero_delta.client.CombatFeedClientState;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** One short-lived, viewer-colored combat or rescue feed entry. */
public record CombatFeedPacket(byte eventType, String actorName, String targetName,
                               boolean actorAlly, boolean targetAlly,
                               boolean headshot, ItemStack weapon)
    implements CustomPacketPayload {
    public static final byte DOWNED = 0;
    public static final byte RESCUED = 1;
    public static final Type<CombatFeedPacket> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(XeroDelta.MOD_ID, "combat_feed"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CombatFeedPacket> STREAM_CODEC =
        StreamCodec.of(CombatFeedPacket::encode, CombatFeedPacket::decode);

    public CombatFeedPacket {
        actorName = actorName == null ? "" : actorName;
        targetName = targetName == null ? "" : targetName;
        weapon = weapon == null || weapon.isEmpty()
            ? ItemStack.EMPTY : weapon.copyWithCount(1);
    }

    private static void encode(RegistryFriendlyByteBuf buffer, CombatFeedPacket packet) {
        buffer.writeByte(packet.eventType);
        buffer.writeUtf(packet.actorName, 64);
        buffer.writeUtf(packet.targetName, 64);
        buffer.writeBoolean(packet.actorAlly);
        buffer.writeBoolean(packet.targetAlly);
        buffer.writeBoolean(packet.headshot);
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, packet.weapon);
    }

    private static CombatFeedPacket decode(RegistryFriendlyByteBuf buffer) {
        return new CombatFeedPacket(buffer.readByte(), buffer.readUtf(64),
            buffer.readUtf(64), buffer.readBoolean(), buffer.readBoolean(),
            buffer.readBoolean(), ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer));
    }

    @Override
    public Type<CombatFeedPacket> type() {
        return TYPE;
    }

    public static void handle(CombatFeedPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> CombatFeedClientState.INSTANCE.add(packet));
    }
}
