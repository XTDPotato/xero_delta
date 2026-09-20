package com.xtdpotato.xero_delta.network;

import com.xtdpotato.xero_delta.XeroDelta;
import com.xtdpotato.xero_delta.item.DeltaPackItem;
import com.xtdpotato.xero_delta.menu.GroundPackMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.item.ItemEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record OpenGroundPackPacket(int entityId) implements CustomPacketPayload {
    public static final Type<OpenGroundPackPacket> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(XeroDelta.MOD_ID, "open_ground_pack"));
    public static final StreamCodec<FriendlyByteBuf, OpenGroundPackPacket> STREAM_CODEC =
        StreamCodec.of((buffer, packet) -> buffer.writeVarInt(packet.entityId),
            buffer -> new OpenGroundPackPacket(buffer.readVarInt()));

    @Override public Type<OpenGroundPackPacket> type() { return TYPE; }

    public static void handle(OpenGroundPackPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                || !(player.level().getEntity(packet.entityId) instanceof ItemEntity entity)
                || !entity.isAlive() || player.distanceToSqr(entity) > 100.0D
                || !(entity.getItem().getItem() instanceof DeltaPackItem pack)
                || (!"chest_rig".equals(pack.slotIdentifier())
                    && !"backpack".equals(pack.slotIdentifier()))) return;

            entity.setNeverPickUp();
            Component title = Component.translatable(
                "screen.xero_delta.ground_pack." + pack.slotIdentifier());
            player.openMenu(new SimpleMenuProvider(
                (id, inventory, ignored) -> new GroundPackMenu(id, inventory, entity, pack),
                title), buffer -> {
                    buffer.writeVarInt(entity.getId());
                    buffer.writeUtf(pack.slotIdentifier(), 32);
                    buffer.writeVarInt(pack.gridWidth());
                    buffer.writeVarInt(pack.gridHeight());
                });
        });
    }
}
