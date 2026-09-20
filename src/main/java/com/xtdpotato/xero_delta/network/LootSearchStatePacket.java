package com.xtdpotato.xero_delta.network;

import com.xtdpotato.xero_delta.XeroDelta;
import com.xtdpotato.xero_delta.client.LootSearchClientState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/** Authoritative visibility and timing snapshot for one open loot container. */
public record LootSearchStatePacket(int containerId, List<Integer> hiddenSlots,
                                    int currentSlot, int elapsedTicks, int durationTicks,
                                    List<Integer> teammateSearchingSlots)
    implements CustomPacketPayload {
    public static final Type<LootSearchStatePacket> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(XeroDelta.MOD_ID, "loot_search_state"));
    public static final StreamCodec<FriendlyByteBuf, LootSearchStatePacket> STREAM_CODEC =
        StreamCodec.of((buffer, packet) -> {
            buffer.writeVarInt(packet.containerId);
            buffer.writeVarInt(packet.hiddenSlots.size());
            for (int slotId : packet.hiddenSlots) buffer.writeVarInt(slotId);
            buffer.writeVarInt(packet.currentSlot + 1);
            buffer.writeVarInt(Math.max(0, packet.elapsedTicks));
            buffer.writeVarInt(Math.max(0, packet.durationTicks));
            buffer.writeVarInt(packet.teammateSearchingSlots.size());
            for (int slotId : packet.teammateSearchingSlots) buffer.writeVarInt(slotId);
        }, buffer -> {
            int containerId = buffer.readVarInt();
            int size = buffer.readVarInt();
            if (size < 0 || size > 16_384) {
                throw new IllegalArgumentException("Invalid loot-search slot count: " + size);
            }
            List<Integer> hidden = new ArrayList<>(size);
            for (int index = 0; index < size; index++) hidden.add(buffer.readVarInt());
            int current = buffer.readVarInt() - 1;
            int elapsed = buffer.readVarInt();
            int duration = buffer.readVarInt();
            int teammateCount = buffer.readVarInt();
            if (teammateCount < 0 || teammateCount > 16_384) {
                throw new IllegalArgumentException("Invalid teammate-search slot count: " + teammateCount);
            }
            List<Integer> teammateSlots = new ArrayList<>(teammateCount);
            for (int index = 0; index < teammateCount; index++) {
                teammateSlots.add(buffer.readVarInt());
            }
            return new LootSearchStatePacket(containerId, List.copyOf(hidden),
                current, elapsed, duration, List.copyOf(teammateSlots));
        });

    public LootSearchStatePacket {
        hiddenSlots = List.copyOf(hiddenSlots);
        teammateSearchingSlots = List.copyOf(teammateSearchingSlots);
    }

    @Override
    public Type<LootSearchStatePacket> type() {
        return TYPE;
    }

    public static void handle(LootSearchStatePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> LootSearchClientState.INSTANCE.apply(packet));
    }
}
