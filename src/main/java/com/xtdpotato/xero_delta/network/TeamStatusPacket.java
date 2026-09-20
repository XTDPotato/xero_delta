package com.xtdpotato.xero_delta.network;

import com.xtdpotato.xero_delta.XeroDelta;
import com.xtdpotato.xero_delta.client.TeamStatusClientState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Online FTB team member health and Delta downed-state snapshot. */
public record TeamStatusPacket(List<Entry> members) implements CustomPacketPayload {
    public static final Type<TeamStatusPacket> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(XeroDelta.MOD_ID, "team_status"));
    public static final StreamCodec<FriendlyByteBuf, TeamStatusPacket> STREAM_CODEC = StreamCodec.of(
        TeamStatusPacket::write, TeamStatusPacket::read);

    public TeamStatusPacket {
        members = members == null ? List.of() : List.copyOf(members);
    }

    private static void write(FriendlyByteBuf buffer, TeamStatusPacket packet) {
        buffer.writeVarInt(packet.members.size());
        for (Entry entry : packet.members) {
            buffer.writeUUID(entry.playerId);
            buffer.writeUtf(entry.name, 64);
            buffer.writeUtf(entry.dimension, 128);
            buffer.writeDouble(entry.x);
            buffer.writeDouble(entry.y);
            buffer.writeDouble(entry.z);
            buffer.writeByte(entry.teamNumber);
            buffer.writeVarInt(entry.teamColor);
            buffer.writeFloat(entry.health);
            buffer.writeFloat(entry.maxHealth);
            buffer.writeBoolean(entry.weakness);
            buffer.writeByte(entry.stage);
            buffer.writeVarInt(entry.remainingTicks);
            buffer.writeVarInt(entry.durationTicks);
            buffer.writeVarInt(entry.rescueTicks);
            buffer.writeVarInt(entry.rescueDurationTicks);
            buffer.writeBoolean(entry.carried);
            buffer.writeBoolean(entry.carrying);
            buffer.writeBoolean(entry.rescuing);
            buffer.writeBoolean(entry.beingRescued);
            buffer.writeByte(entry.carryPhase);
            buffer.writeVarInt(entry.carryTicks);
        }
    }

    private static TeamStatusPacket read(FriendlyByteBuf buffer) {
        int count = Math.min(64, Math.max(0, buffer.readVarInt()));
        List<Entry> members = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            members.add(new Entry(buffer.readUUID(), buffer.readUtf(64), buffer.readUtf(128),
                buffer.readDouble(), buffer.readDouble(), buffer.readDouble(), buffer.readByte(),
                buffer.readVarInt(),
                buffer.readFloat(), buffer.readFloat(), buffer.readBoolean(), buffer.readByte(),
                buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(),
                buffer.readBoolean(), buffer.readBoolean(), buffer.readBoolean(), buffer.readBoolean(),
                buffer.readByte(), buffer.readVarInt()));
        }
        return new TeamStatusPacket(members);
    }

    @Override public Type<TeamStatusPacket> type() { return TYPE; }

    public static void handle(TeamStatusPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> TeamStatusClientState.INSTANCE.update(packet.members));
    }

    public record Entry(UUID playerId, String name, String dimension, double x, double y, double z,
                        byte teamNumber, int teamColor, float health, float maxHealth,
                        boolean weakness, byte stage, int remainingTicks, int durationTicks,
                        int rescueTicks, int rescueDurationTicks, boolean carried,
                        boolean carrying, boolean rescuing, boolean beingRescued,
                        byte carryPhase, int carryTicks) {
        public float healthFraction() {
            return maxHealth <= 0.0F ? 0.0F : Math.max(0.0F, Math.min(1.0F, health / maxHealth));
        }

        public float downedProgress() {
            return durationTicks <= 0 ? 0.0F
                : Math.max(0.0F, Math.min(1.0F, remainingTicks / (float) durationTicks));
        }
    }
}
