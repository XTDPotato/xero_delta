package com.xtdpotato.xero_delta.network;

import com.xtdpotato.xero_delta.XeroDelta;
import com.xtdpotato.xero_delta.data.ModDataStorage;
import com.xtdpotato.xero_delta.data.ServerGridRotationState;
import com.xtdpotato.xero_delta.grid.GridBackingStore;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record CarriedRotationPacket(boolean rotated, boolean manualPriority) implements CustomPacketPayload {
    public static final Type<CarriedRotationPacket> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(XeroDelta.MOD_ID, "carried_rotation"));

    public static final StreamCodec<FriendlyByteBuf, CarriedRotationPacket> STREAM_CODEC = StreamCodec.of(
        (buffer, packet) -> {
            buffer.writeBoolean(packet.rotated);
            buffer.writeBoolean(packet.manualPriority);
        },
        buffer -> new CarriedRotationPacket(buffer.readBoolean(), buffer.readBoolean())
    );

    @Override
    public Type<CarriedRotationPacket> type() {
        return TYPE;
    }

    public static void handle(CarriedRotationPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            ItemStack carried = player.containerMenu.getCarried();
            if (carried.isEmpty()) {
                ServerGridRotationState.setManualPriority(player, false);
                return;
            }
            var size = ModDataStorage.getCachedSizeFor(carried);
            if (size.width() <= 1 && size.height() <= 1) {
                ServerGridRotationState.setManualPriority(player, false);
                return;
            }
            GridBackingStore.setRotated(carried, packet.rotated);
            player.containerMenu.setCarried(carried);
            ServerGridRotationState.setManualPriority(player, packet.manualPriority);
            player.containerMenu.broadcastChanges();
        });
    }
}
