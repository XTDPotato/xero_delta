package com.xtdpotato.xero_delta.network;

import com.xtdpotato.xero_delta.Config;
import com.xtdpotato.xero_delta.XeroDelta;
import com.xtdpotato.xero_delta.data.ContainerGridRules;
import com.xtdpotato.xero_delta.data.ModDataStorage;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ItemGridConfigPacket(int action, String menuKey, boolean enabled) implements CustomPacketPayload {
    public static final int SET_GLOBAL = 0;
    public static final int SET_SCREEN = 1;
    public static final int RESET_SCREENS = 2;

    public static final Type<ItemGridConfigPacket> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(XeroDelta.MOD_ID, "item_grid_config"));

    public static final StreamCodec<FriendlyByteBuf, ItemGridConfigPacket> STREAM_CODEC = StreamCodec.of(
        (buf, p) -> {
            buf.writeByte(p.action);
            buf.writeUtf(p.menuKey == null ? "" : p.menuKey);
            buf.writeBoolean(p.enabled);
        },
        buf -> new ItemGridConfigPacket(buf.readByte(), buf.readUtf(), buf.readBoolean())
    );

    @Override
    public Type<ItemGridConfigPacket> type() {
        return TYPE;
    }

    public static void handle(ItemGridConfigPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer sp)) return;
            switch (packet.action) {
                case SET_GLOBAL -> {
                    Config.INSTANCE.itemGridEnabled.set(packet.enabled);
                    Config.SPEC.save();
                }
                case SET_SCREEN -> ContainerGridRules.setScreenEnabled(packet.menuKey, packet.enabled);
                case RESET_SCREENS -> ContainerGridRules.reset();
                default -> {
                    return;
                }
            }
            var data = ModDataStorage.get(sp.serverLevel());
            var sync = SyncDataPacket.from(data, sp.server);
            for (ServerPlayer player : sp.server.getPlayerList().getPlayers()) {
                ModNetwork.sendSyncToPlayer(player, sync);
            }
        });
    }
}
