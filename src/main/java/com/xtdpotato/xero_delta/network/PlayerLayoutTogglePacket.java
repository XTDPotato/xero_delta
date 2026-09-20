package com.xtdpotato.xero_delta.network;

import com.xtdpotato.xero_delta.ServerEvents;
import com.xtdpotato.xero_delta.XeroDelta;
import com.xtdpotato.xero_delta.data.PlayerLayoutRulesData;
import com.xtdpotato.xero_delta.data.PlayerLayoutSlotRules;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** F8 layout switch. The server remains authoritative and accepts it only from an operator/host. */
public record PlayerLayoutTogglePacket() implements CustomPacketPayload {
    public static final Type<PlayerLayoutTogglePacket> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(XeroDelta.MOD_ID, "player_layout_toggle"));
    public static final StreamCodec<FriendlyByteBuf, PlayerLayoutTogglePacket> STREAM_CODEC =
        StreamCodec.of((buffer, packet) -> { }, buffer -> new PlayerLayoutTogglePacket());

    @Override
    public Type<PlayerLayoutTogglePacket> type() {
        return TYPE;
    }

    public static void handle(PlayerLayoutTogglePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player) || !player.hasPermissions(2)) return;
            boolean enabled = !PlayerLayoutRulesData.get(player.server).enabled();
            PlayerLayoutRulesData.get(player.server).setEnabled(enabled);
            for (ServerPlayer online : player.server.getPlayerList().getPlayers()) {
                if (enabled) PlayerLayoutSlotRules.ejectDisabledMainInventory(online);
                ServerEvents.syncPlayerStatus(online, true);
            }
        });
    }
}
