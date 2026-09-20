package com.xtdpotato.xero_delta.network;

import com.xtdpotato.xero_delta.XeroDelta;
import com.xtdpotato.xero_delta.client.MedicalUseClientState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record MedicalUseStatePacket(boolean active, String itemId,
                                    int remainingTicks, int durationTicks,
                                    String sourceId, ItemStack displayStack)
    implements CustomPacketPayload {
    public static final Type<MedicalUseStatePacket> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(XeroDelta.MOD_ID, "medical_use_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MedicalUseStatePacket> STREAM_CODEC =
        StreamCodec.of((buffer, packet) -> {
            buffer.writeBoolean(packet.active);
            buffer.writeUtf(packet.itemId, 128);
            buffer.writeVarInt(packet.remainingTicks);
            buffer.writeVarInt(packet.durationTicks);
            buffer.writeUtf(packet.sourceId, 256);
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, packet.displayStack);
        }, buffer -> new MedicalUseStatePacket(buffer.readBoolean(),
            buffer.readUtf(128), buffer.readVarInt(), buffer.readVarInt(),
            buffer.readUtf(256), ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer)));

    public static MedicalUseStatePacket active(ItemStack stack, int remaining, int duration) {
        return active(stack, remaining, duration, "");
    }

    public static MedicalUseStatePacket active(ItemStack stack, int remaining, int duration, String sourceId) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return new MedicalUseStatePacket(true, id == null ? "" : id.toString(),
            Math.max(0, remaining), Math.max(1, duration), sourceId, stack.copyWithCount(1));
    }

    public static MedicalUseStatePacket inactive() {
        return new MedicalUseStatePacket(false, "", 0, 1, "", ItemStack.EMPTY);
    }

    @Override
    public Type<MedicalUseStatePacket> type() {
        return TYPE;
    }

    public static void handle(MedicalUseStatePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> MedicalUseClientState.INSTANCE.update(packet));
    }
}
