package com.xtdpotato.xero_delta.network;

import com.xtdpotato.xero_delta.XeroDelta;
import com.xtdpotato.xero_delta.data.DownedManager;
import com.xtdpotato.xero_delta.entity.CorpseEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Opens a corpse/loot box from the dedicated F interaction after server validation. */
public record CorpseOpenPacket(int entityId) implements CustomPacketPayload {
    public static final Type<CorpseOpenPacket> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(XeroDelta.MOD_ID, "corpse_open"));
    public static final StreamCodec<FriendlyByteBuf, CorpseOpenPacket> STREAM_CODEC = StreamCodec.of(
        (buffer, packet) -> buffer.writeVarInt(packet.entityId),
        buffer -> new CorpseOpenPacket(buffer.readVarInt()));

    @Override public Type<CorpseOpenPacket> type() { return TYPE; }

    public static void handle(CorpseOpenPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                || DownedManager.interactionLocked(player)) return;
            if (!(player.serverLevel().getEntity(packet.entityId) instanceof CorpseEntity corpse)
                || corpse.carryingPlayer() != null
                || player.distanceToSqr(corpse) > 64.0D) return;
            ServerPlayer owner = corpse.ownerId() == null ? null
                : player.server.getPlayerList().getPlayer(corpse.ownerId());
            if (!corpse.isLootBox() && owner != null && DownedManager.isYellow(owner)
                && DownedManager.isTeammate(player, owner.getUUID())) return;
            corpse.openFromInteraction(player);
        });
    }
}
