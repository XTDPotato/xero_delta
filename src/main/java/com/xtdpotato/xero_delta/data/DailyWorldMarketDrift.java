package com.xtdpotato.xero_delta.data;

import com.xtdpotato.xero_delta.XeroDelta;
import com.xtdpotato.xero_delta.network.ModNetwork;
import com.xtdpotato.xero_delta.network.SyncDataPacket;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Applies a small, reproducible daily variation to prices calculated by the
 * automatic rule tool.  It deliberately never creates or overwrites manual
 * prices, qualities, or sizes.
 */
@EventBusSubscriber(modid = XeroDelta.MOD_ID)
public final class DailyWorldMarketDrift {
    private static final long TICKS_PER_DAY = 24_000L;
    private static final Map<MinecraftServer, State> STATES = new IdentityHashMap<>();

    private DailyWorldMarketDrift() {
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % 20 != 0 || server.getPlayerCount() == 0) return;

        long day = Math.floorDiv(server.overworld().getGameTime(), TICKS_PER_DAY);
        State state = STATES.computeIfAbsent(server, ignored -> new State());
        if (state.appliedDay == day) return;

        ModDataStorage data = ModDataStorage.get(server.overworld());
        Set<String> automaticKeys = data.getAutoPriceKeys();
        if (automaticKeys.isEmpty()) {
            state.appliedDay = day;
            state.baseline.clear();
            return;
        }
        if (!state.baseline.keySet().equals(automaticKeys)) {
            state.baseline.clear();
            Map<String, Long> current = data.getAllPrices();
            for (String key : automaticKeys) {
                Long price = current.get(key);
                if (price != null && price > 0L) state.baseline.put(key, price);
            }
        }

        Map<String, Long> varied = new HashMap<>();
        long seed = server.overworld().getSeed();
        for (var entry : state.baseline.entrySet()) {
            int percent = 88 + Math.floorMod(mix(seed ^ day ^ entry.getKey().hashCode()), 29);
            long value = Math.max(1L, Math.round(entry.getValue() * (percent / 100.0D)));
            varied.put(entry.getKey(), value);
        }
        if (varied.isEmpty()) {
            state.appliedDay = day;
            return;
        }

        data.setAutoPrices(varied);
        Map<String, String> qualities = new HashMap<>(
            AutomaticItemValuation.calculateQualities(data.getAllPrices()));
        for (String itemId : varied.keySet()) {
            ResourceLocation id = ResourceLocation.tryParse(itemId);
            if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) continue;
            BuiltinItemRuleCatalog.Rule fixed = BuiltinItemRuleCatalog.explicit(
                BuiltInRegistries.ITEM.get(id).getDefaultInstance());
            if (fixed != null) qualities.put(itemId, fixed.quality());
        }
        ServerItemRules.setAutoQualities(qualities);
        state.appliedDay = day;

        SyncDataPacket packet = SyncDataPacket.from(data, server);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ModNetwork.sendSyncToPlayer(player, packet);
        }
    }

    @SubscribeEvent
    public static void onStopped(ServerStoppedEvent event) {
        STATES.remove(event.getServer());
    }

    private static long mix(long value) {
        value = (value ^ (value >>> 33)) * 0xff51afd7ed558ccdl;
        value = (value ^ (value >>> 33)) * 0xc4ceb9fe1a85ec53l;
        return value ^ (value >>> 33);
    }

    private static final class State {
        private final Map<String, Long> baseline = new HashMap<>();
        private long appliedDay = Long.MIN_VALUE;
    }
}
