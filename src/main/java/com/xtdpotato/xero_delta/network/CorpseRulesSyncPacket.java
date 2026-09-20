package com.xtdpotato.xero_delta.network;

import com.xtdpotato.xero_delta.XeroDelta;
import com.xtdpotato.xero_delta.client.CorpseRulesClientState;
import com.xtdpotato.xero_delta.data.CorpseRules;
import com.xtdpotato.xero_delta.data.CorpseRulesData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record CorpseRulesSyncPacket(CorpseRules.Settings settings,
                                    boolean editable) implements CustomPacketPayload {
    public static final Type<CorpseRulesSyncPacket> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(XeroDelta.MOD_ID, "corpse_rules_sync"));
    public static final StreamCodec<FriendlyByteBuf, CorpseRulesSyncPacket> STREAM_CODEC =
        StreamCodec.of((buffer, packet) -> {
            CorpseRulesPacketCodec.write(buffer, packet.settings);
            buffer.writeBoolean(packet.editable);
        }, buffer -> new CorpseRulesSyncPacket(CorpseRulesPacketCodec.read(buffer),
            buffer.readBoolean()));

    public static CorpseRulesSyncPacket from(ServerPlayer player) {
        CorpseRulesData rules = CorpseRulesData.get(player.server);
        return new CorpseRulesSyncPacket(rules.settings(), player.hasPermissions(2));
    }

    public static void handle(CorpseRulesSyncPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> CorpseRulesClientState.INSTANCE.update(packet));
    }

    @Override public Type<CorpseRulesSyncPacket> type() { return TYPE; }
}
