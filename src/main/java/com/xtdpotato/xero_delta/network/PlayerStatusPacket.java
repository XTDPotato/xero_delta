package com.xtdpotato.xero_delta.network;

import com.xtdpotato.xero_delta.XeroDelta;
import com.xtdpotato.xero_delta.client.PlayerStatusClientState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record PlayerStatusPacket(long balance, double weightKg, double speedPenalty,
                                 double healthPenalty,
                                 boolean layoutEnabled, boolean allowChangeBc, boolean layoutClick,
                                 float head, float chest, float abdomen,
                                 float leftArm, float rightArm,
                                 float leftLeg, float rightLeg, float wholeBody) implements CustomPacketPayload {
    public static final Type<PlayerStatusPacket> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(XeroDelta.MOD_ID, "player_status"));

    public static final StreamCodec<FriendlyByteBuf, PlayerStatusPacket> STREAM_CODEC = StreamCodec.of(
        (buf, packet) -> {
            buf.writeLong(packet.balance);
            buf.writeDouble(packet.weightKg);
            buf.writeDouble(packet.speedPenalty);
            buf.writeDouble(packet.healthPenalty);
            buf.writeBoolean(packet.layoutEnabled);
            buf.writeBoolean(packet.allowChangeBc);
            buf.writeBoolean(packet.layoutClick);
            buf.writeFloat(packet.head);
            buf.writeFloat(packet.chest);
            buf.writeFloat(packet.abdomen);
            buf.writeFloat(packet.leftArm);
            buf.writeFloat(packet.rightArm);
            buf.writeFloat(packet.leftLeg);
            buf.writeFloat(packet.rightLeg);
            buf.writeFloat(packet.wholeBody);
        },
        buf -> new PlayerStatusPacket(buf.readLong(), buf.readDouble(), buf.readDouble(),
            buf.readDouble(), buf.readBoolean(), buf.readBoolean(), buf.readBoolean(),
            buf.readFloat(), buf.readFloat(), buf.readFloat(),
            buf.readFloat(), buf.readFloat(),
            buf.readFloat(), buf.readFloat(), buf.readFloat())
    );

    @Override
    public Type<PlayerStatusPacket> type() {
        return TYPE;
    }

    public static void handle(PlayerStatusPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> PlayerStatusClientState.INSTANCE.update(packet));
    }
}
