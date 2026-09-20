package com.xtdpotato.xero_delta.network;

import com.xtdpotato.xero_delta.Config;
import com.xtdpotato.xero_delta.XeroDelta;
import com.xtdpotato.xero_delta.client.ClientDataCache;
import com.xtdpotato.xero_delta.data.BallisticArmorRules;
import com.xtdpotato.xero_delta.data.BulletArmorRulesData;
import com.xtdpotato.xero_delta.data.ContainerGridRules;
import com.xtdpotato.xero_delta.data.ModDataStorage;
import com.xtdpotato.xero_delta.data.ServerItemWeights;
import com.xtdpotato.xero_delta.trading.TradingItemEligibility;
import com.xtdpotato.xero_delta.trading.TradingUploadRulesData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record SyncDataPacket(Map<String, Long> prices, Map<String, Long> sizes,
                             Map<String, String> qualities, Map<String, Double> weights,
                             Set<String> autoPriceKeys, Set<String> autoSizeKeys,
                             Set<String> autoQualityKeys, boolean itemGridEnabled,
                             Map<String, Boolean> itemGridScreens,
                             Map<String, String> tradingUploadRules,
                             Map<String, List<Double>> bulletArmorRules)
    implements CustomPacketPayload {
    public static final Type<SyncDataPacket> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(XeroDelta.MOD_ID, "sync_data"));

    public SyncDataPacket(Map<String, Long> prices, Map<String, Long> sizes,
                          Map<String, String> qualities) {
        this(prices, sizes, qualities, Map.of(), Set.of(), Set.of(), Set.of(),
            Config.INSTANCE.itemGridEnabled.get(), ContainerGridRules.getAllRules(),
            Map.of(), Map.of());
    }

    public static SyncDataPacket from(ModDataStorage data, MinecraftServer server) {
        return new SyncDataPacket(data.getAllPrices(), data.getAllSizes(), data.getAllQualities(),
            ServerItemWeights.getAllWeights(), data.getAutoPriceKeys(),
            com.xtdpotato.xero_delta.data.ServerItemRules.getAutoSizeKeys(),
            com.xtdpotato.xero_delta.data.ServerItemRules.getAutoQualityKeys(),
            Config.INSTANCE.itemGridEnabled.get(), ContainerGridRules.getAllRules(),
            TradingUploadRulesData.get(server).rules(), BulletArmorRulesData.get(server).rules());
    }

    public static final StreamCodec<FriendlyByteBuf, SyncDataPacket> STREAM_CODEC = StreamCodec.of(
        (buf, packet) -> {
            writeLongMap(buf, packet.prices);
            writeLongMap(buf, packet.sizes);
            buf.writeVarInt(packet.qualities.size());
            packet.qualities.forEach((key, value) -> {
                buf.writeUtf(key);
                buf.writeUtf(value);
            });
            buf.writeVarInt(packet.weights.size());
            packet.weights.forEach((key, value) -> {
                buf.writeUtf(key);
                buf.writeDouble(value);
            });
            writeStringSet(buf, packet.autoPriceKeys);
            writeStringSet(buf, packet.autoSizeKeys);
            writeStringSet(buf, packet.autoQualityKeys);
            buf.writeBoolean(packet.itemGridEnabled);
            writeBooleanMap(buf, packet.itemGridScreens);
            writeStringMap(buf, packet.tradingUploadRules);
            buf.writeVarInt(packet.bulletArmorRules.size());
            packet.bulletArmorRules.forEach((key, values) -> {
                buf.writeUtf(key);
                for (int index = 0; index < BallisticArmorRules.TIER_COUNT; index++) {
                    buf.writeDouble(values.get(index));
                }
            });
        },
        buf -> {
            Map<String, Long> prices = readLongMap(buf);
            Map<String, Long> sizes = readLongMap(buf);
            Map<String, String> qualities = new HashMap<>();
            int qualityCount = buf.readVarInt();
            for (int i = 0; i < qualityCount; i++) qualities.put(buf.readUtf(), buf.readUtf());
            Map<String, Double> weights = new HashMap<>();
            int weightCount = buf.readVarInt();
            for (int i = 0; i < weightCount; i++) weights.put(buf.readUtf(), buf.readDouble());
            Set<String> autoPriceKeys = readStringSet(buf);
            Set<String> autoSizeKeys = readStringSet(buf);
            Set<String> autoQualityKeys = readStringSet(buf);
            boolean itemGridEnabled = buf.readBoolean();
            Map<String, Boolean> itemGridScreens = readBooleanMap(buf);
            Map<String, String> tradingUploadRules = readStringMap(buf);
            Map<String, List<Double>> bulletArmorRules = new HashMap<>();
            int bulletRuleCount = buf.readVarInt();
            for (int i = 0; i < bulletRuleCount; i++) {
                String key = buf.readUtf();
                List<Double> values = new ArrayList<>(BallisticArmorRules.TIER_COUNT);
                for (int tier = 0; tier < BallisticArmorRules.TIER_COUNT; tier++) {
                    values.add(buf.readDouble());
                }
                bulletArmorRules.put(key, List.copyOf(values));
            }
            return new SyncDataPacket(prices, sizes, qualities, weights,
                autoPriceKeys, autoSizeKeys, autoQualityKeys, itemGridEnabled,
                itemGridScreens, tradingUploadRules, bulletArmorRules);
        }
    );

    private static void writeLongMap(FriendlyByteBuf buf, Map<String, Long> values) {
        buf.writeVarInt(values.size());
        values.forEach((key, value) -> {
            buf.writeUtf(key);
            buf.writeLong(value);
        });
    }

    private static Map<String, Long> readLongMap(FriendlyByteBuf buf) {
        Map<String, Long> values = new HashMap<>();
        int count = buf.readVarInt();
        for (int i = 0; i < count; i++) values.put(buf.readUtf(), buf.readLong());
        return values;
    }

    private static void writeStringSet(FriendlyByteBuf buf, Set<String> values) {
        buf.writeVarInt(values.size());
        values.forEach(buf::writeUtf);
    }

    private static Set<String> readStringSet(FriendlyByteBuf buf) {
        Set<String> values = new HashSet<>();
        int count = buf.readVarInt();
        for (int i = 0; i < count; i++) values.add(buf.readUtf());
        return values;
    }

    private static void writeBooleanMap(FriendlyByteBuf buf, Map<String, Boolean> values) {
        buf.writeVarInt(values.size());
        values.forEach((key, value) -> {
            buf.writeUtf(key);
            buf.writeBoolean(value);
        });
    }

    private static Map<String, Boolean> readBooleanMap(FriendlyByteBuf buf) {
        Map<String, Boolean> values = new HashMap<>();
        int count = buf.readVarInt();
        for (int i = 0; i < count; i++) values.put(buf.readUtf(), buf.readBoolean());
        return values;
    }
    private static void writeStringMap(FriendlyByteBuf buf, Map<String, String> values) {
        buf.writeVarInt(values.size());
        values.forEach((key, value) -> {
            buf.writeUtf(key);
            buf.writeUtf(value == null ? "" : value);
        });
    }

    private static Map<String, String> readStringMap(FriendlyByteBuf buf) {
        Map<String, String> values = new HashMap<>();
        int count = buf.readVarInt();
        for (int i = 0; i < count; i++) values.put(buf.readUtf(), buf.readUtf());
        return values;
    }

    @Override
    public Type<SyncDataPacket> type() {
        return TYPE;
    }

    public static void handle(SyncDataPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            ClientDataCache.INSTANCE.update(packet.prices, packet.sizes, packet.qualities,
                packet.weights, packet.autoPriceKeys, packet.autoSizeKeys,
                packet.autoQualityKeys, packet.itemGridEnabled, packet.itemGridScreens);
            TradingItemEligibility.updateClientRules(packet.tradingUploadRules);
            BallisticArmorRules.updateClientRules(packet.bulletArmorRules);
        });
    }
}