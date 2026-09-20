package com.xtdpotato.xero_delta.network;

import com.xtdpotato.xero_delta.XeroDelta;
import com.xtdpotato.xero_delta.trading.TradingMarketService;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Creative virtual listing packet that preserves modded item data components. */
public record CreativeListingPacket(ItemStack stack, int amount, long price,
                                    int durationDays) implements CustomPacketPayload {
    public static final Type<CreativeListingPacket> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(XeroDelta.MOD_ID, "creative_listing"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CreativeListingPacket> STREAM_CODEC =
        StreamCodec.of((buf, packet) -> {
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buf,
                packet.stack == null || packet.stack.isEmpty() ? ItemStack.EMPTY : packet.stack.copyWithCount(1));
            buf.writeVarInt(Math.max(1, packet.amount));
            buf.writeLong(packet.price);
            buf.writeVarInt(Math.max(1, packet.durationDays));
        }, buf -> new CreativeListingPacket(ItemStack.OPTIONAL_STREAM_CODEC.decode(buf),
            buf.readVarInt(), buf.readLong(), buf.readVarInt()));

    public static void handle(CreativeListingPacket packet, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        context.enqueueWork(() -> {
            TradingMarketService.Result result = TradingMarketService.creativeList(player,
                packet.stack, packet.amount, packet.price, packet.durationDays);
            PacketDistributor.sendToPlayer(player,
                TradingSyncPacket.snapshot(player, result.message(), result.success(), result.value()));
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
