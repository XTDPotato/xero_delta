package com.xtdpotato.xero_delta.network;

import com.xtdpotato.xero_delta.XeroDelta;
import com.xtdpotato.xero_delta.client.ItemDetailOverlay;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client acknowledgement for an exact-source detail action. */
public record ItemDetailActionResultPacket(boolean success)
    implements CustomPacketPayload {
    public static final Type<ItemDetailActionResultPacket> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(XeroDelta.MOD_ID,
            "item_detail_action_result"));
    public static final StreamCodec<FriendlyByteBuf, ItemDetailActionResultPacket> STREAM_CODEC =
        StreamCodec.composite(ByteBufCodecs.BOOL,
            ItemDetailActionResultPacket::success,
            ItemDetailActionResultPacket::new);

    @Override public Type<ItemDetailActionResultPacket> type() { return TYPE; }

    public static void handle(ItemDetailActionResultPacket packet,
                              IPayloadContext context) {
        context.enqueueWork(() -> ItemDetailOverlay.finishUse(packet.success));
    }
}
