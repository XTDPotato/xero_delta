package com.xtdpotato.xero_delta.network;

import com.xtdpotato.xero_delta.XeroDelta;
import com.xtdpotato.xero_delta.data.ModDataStorage;
import com.xtdpotato.xero_delta.data.ItemSizeRule;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.LinkedHashMap;
import java.util.Map;

/** Commits one creative-editor draft as a single server-side transaction. */
public record CreativeRuleBatchPacket(Map<String, String> qualities,
                                      Map<String, Long> prices,
                                      Map<String, Long> sizes)
    implements CustomPacketPayload {
    private static final int MAX_EDITS = 4096;
    public static final String REMOVE_QUALITY = "__remove__";
    public static final Type<CreativeRuleBatchPacket> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(XeroDelta.MOD_ID, "creative_rule_batch"));
    public static final StreamCodec<FriendlyByteBuf, CreativeRuleBatchPacket> STREAM_CODEC =
        StreamCodec.of(CreativeRuleBatchPacket::write, CreativeRuleBatchPacket::read);

    public CreativeRuleBatchPacket {
        qualities = Map.copyOf(qualities);
        prices = Map.copyOf(prices);
        sizes = Map.copyOf(sizes);
    }

    private static void write(FriendlyByteBuf buffer, CreativeRuleBatchPacket packet) {
        buffer.writeVarInt(packet.qualities.size());
        packet.qualities.forEach((key, quality) -> {
            buffer.writeUtf(key, 2048);
            buffer.writeUtf(quality, 32);
        });
        buffer.writeVarInt(packet.prices.size());
        packet.prices.forEach((key, price) -> {
            buffer.writeUtf(key, 2048);
            buffer.writeLong(price);
        });
        buffer.writeVarInt(packet.sizes.size());
        packet.sizes.forEach((key, size) -> {
            buffer.writeUtf(key, 2048);
            buffer.writeLong(size);
        });
    }

    private static CreativeRuleBatchPacket read(FriendlyByteBuf buffer) {
        int qualityCount = buffer.readVarInt();
        if (qualityCount < 0 || qualityCount > MAX_EDITS) {
            throw new IllegalArgumentException("Too many creative quality edits: " + qualityCount);
        }
        Map<String, String> qualities = new LinkedHashMap<>(qualityCount);
        for (int index = 0; index < qualityCount; index++) {
            qualities.put(buffer.readUtf(2048), buffer.readUtf(32));
        }
        int priceCount = buffer.readVarInt();
        if (priceCount < 0 || priceCount > MAX_EDITS) {
            throw new IllegalArgumentException("Too many creative price edits: " + priceCount);
        }
        Map<String, Long> prices = new LinkedHashMap<>(priceCount);
        for (int index = 0; index < priceCount; index++) {
            prices.put(buffer.readUtf(2048), buffer.readLong());
        }
        int sizeCount = buffer.readVarInt();
        if (sizeCount < 0 || sizeCount > MAX_EDITS) {
            throw new IllegalArgumentException("Too many creative size edits: " + sizeCount);
        }
        Map<String, Long> sizes = new LinkedHashMap<>(sizeCount);
        for (int index = 0; index < sizeCount; index++) {
            sizes.put(buffer.readUtf(2048), buffer.readLong());
        }
        return new CreativeRuleBatchPacket(qualities, prices, sizes);
    }

    @Override
    public Type<CreativeRuleBatchPacket> type() {
        return TYPE;
    }

    public static void handle(CreativeRuleBatchPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                || !player.isCreative() || !player.hasPermissions(2)
                || packet.qualities.size() > MAX_EDITS || packet.prices.size() > MAX_EDITS
                || packet.sizes.size() > MAX_EDITS) {
                return;
            }
            ModDataStorage data = ModDataStorage.get(player.serverLevel());
            int changed = 0;
            for (var entry : packet.qualities.entrySet()) {
                if (!validKey(entry.getKey())) continue;
                if (REMOVE_QUALITY.equals(entry.getValue())) {
                    data.removeQuality(entry.getKey());
                } else {
                    data.setQuality(entry.getKey(),
                        ModDataStorage.normalizeQuality(entry.getValue()));
                }
                changed++;
            }
            for (var entry : packet.prices.entrySet()) {
                if (!validKey(entry.getKey())
                    || entry.getValue() == null
                    || entry.getValue() < 0L
                    || entry.getValue() > 999_999_999L) continue;
                data.setPrice(entry.getKey(), entry.getValue());
                changed++;
            }
            for (var entry : packet.sizes.entrySet()) {
                if (!validKey(entry.getKey()) || entry.getValue() == null) continue;
                ItemSizeRule rule = ItemSizeRule.unpack(entry.getValue());
                if (rule.size().width() < 1 || rule.size().width() > 10
                    || rule.size().height() < 1 || rule.size().height() > 10) continue;
                data.setSize(entry.getKey(), rule.size(), rule.rotateTexture(),
                    rule.stretchTexture(), rule.proportionalScale());
                changed++;
            }
            if (changed <= 0) return;
            var sync = SyncDataPacket.from(data, player.server);
            for (ServerPlayer online : player.server.getPlayerList().getPlayers()) {
                PacketDistributor.sendToPlayer(online, sync);
            }
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                "creative_rules.xero_delta.saved", changed), true);
        });
    }

    private static boolean validKey(String key) {
        return key != null && !key.isBlank() && key.length() <= 2048;
    }
}
