package com.xtdpotato.xero_delta.network;

import com.xtdpotato.xero_delta.XeroDelta;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Server→Client: full grid snapshot after every mutation.
 * Client overwrites its local snapshot with this data.
 */
public record GridSyncPacket(List<ItemStack> items, int gridWidth, int gridHeight, ItemStack carried, int containerIndex) implements CustomPacketPayload {

    public GridSyncPacket(List<ItemStack> items, int gridWidth, int gridHeight, ItemStack carried) {
        this(items, gridWidth, gridHeight, carried, 0);
    }

    public static final Type<GridSyncPacket> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(XeroDelta.MOD_ID, "grid_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, GridSyncPacket> STREAM_CODEC = StreamCodec.of(
        (buf, p) -> {
            buf.writeInt(p.gridWidth);
            buf.writeInt(p.gridHeight);
            buf.writeVarInt(p.containerIndex);
            buf.writeInt(p.items.size());
            for (ItemStack s : p.items) ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, s);
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, p.carried);
        },
        buf -> {
            int gw = buf.readInt();
            int gh = buf.readInt();
            int containerIndex = buf.readVarInt();
            int count = buf.readInt();
            List<ItemStack> items = new ArrayList<>();
            for (int i = 0; i < count; i++)
                items.add(ItemStack.OPTIONAL_STREAM_CODEC.decode(buf));
            return new GridSyncPacket(items, gw, gh, ItemStack.OPTIONAL_STREAM_CODEC.decode(buf), containerIndex);
        }
    );

    @Override
    public Type<GridSyncPacket> type() { return TYPE; }

    public static void handle(GridSyncPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            com.xtdpotato.xero_delta.client.XeroDeltaClient.onGridSync(
                packet.items, packet.gridWidth, packet.gridHeight, packet.carried, packet.containerIndex);
        });
    }
}
