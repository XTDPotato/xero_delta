package com.xtdpotato.xero_delta.data;

import com.xtdpotato.xero_delta.network.SafetyBoxAccessPacket;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** World-persistent safety-box entitlements with real-time expirations. */
public final class SafetyBoxAccessData extends SavedData {
    private static final String DATA_NAME = "xero_delta_safety_box_access";
    public static final long PERMANENT = Long.MAX_VALUE;
    private final Map<UUID, Map<String, Long>> access = new HashMap<>();

    public static SafetyBoxAccessData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
            new Factory<>(SafetyBoxAccessData::new, SafetyBoxAccessData::load), DATA_NAME);
    }

    public static String basicBoxId() {
        return SafetyBoxAccessRules.BASIC_BOX_ID;
    }

    public synchronized boolean isUnlocked(UUID playerId, String itemId, long now) {
        if (SafetyBoxAccessRules.isPermanent(itemId)) return true;
        long expiresAt = access.getOrDefault(playerId, Map.of()).getOrDefault(itemId, 0L);
        return expiresAt == PERMANENT || expiresAt > now;
    }

    public synchronized void unlock(UUID playerId, String itemId, long expiresAt) {
        if (SafetyBoxAccessRules.isPermanent(itemId)) return;
        long now = System.currentTimeMillis();
        extend(playerId, itemId, Math.max(0L, expiresAt - now), now);
    }

    public synchronized void extend(UUID playerId, String itemId, long durationMillis, long now) {
        if (SafetyBoxAccessRules.isPermanent(itemId)) return;
        Map<String, Long> values = access.computeIfAbsent(playerId, ignored -> new HashMap<>());
        long current = values.getOrDefault(itemId, 0L);
        long requestedExpiresAt = durationMillis >= PERMANENT - now
            ? PERMANENT : now + Math.max(0L, durationMillis);
        values.put(itemId, SafetyBoxExpiry.merge(current, requestedExpiresAt, now, PERMANENT));
        setDirty();
    }

    public synchronized boolean lock(UUID playerId, String itemId) {
        if (SafetyBoxAccessRules.isPermanent(itemId)) return false;
        Map<String, Long> values = access.get(playerId);
        if (values == null || values.remove(itemId) == null) return false;
        if (values.isEmpty()) access.remove(playerId);
        setDirty();
        return true;
    }

    public synchronized Map<String, Long> snapshot(UUID playerId, long now) {
        Map<String, Long> values = new HashMap<>();
        values.put(basicBoxId(), PERMANENT);
        Map<String, Long> stored = access.get(playerId);
        if (stored == null) return Map.copyOf(values);
        boolean changed = stored.entrySet().removeIf(entry ->
            entry.getValue() != PERMANENT && entry.getValue() <= now);
        if (changed) setDirty();
        for (var entry : stored.entrySet()) values.put(entry.getKey(), entry.getValue());
        return Map.copyOf(values);
    }

    public synchronized boolean removeExpired(UUID playerId, long now) {
        Map<String, Long> values = access.get(playerId);
        if (values == null) return false;
        boolean changed = values.entrySet().removeIf(entry ->
            entry.getValue() != PERMANENT && entry.getValue() <= now);
        if (values.isEmpty()) access.remove(playerId);
        if (changed) setDirty();
        return changed;
    }

    public void sync(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new SafetyBoxAccessPacket(
            snapshot(player.getUUID(), System.currentTimeMillis())));
    }

    @Override
    public synchronized @NotNull CompoundTag save(@NotNull CompoundTag tag,
                                                   HolderLookup.@NotNull Provider registries) {
        ListTag players = new ListTag();
        for (var playerEntry : access.entrySet()) {
            CompoundTag playerTag = new CompoundTag();
            playerTag.putUUID("player", playerEntry.getKey());
            ListTag boxes = new ListTag();
            for (var boxEntry : playerEntry.getValue().entrySet()) {
                CompoundTag boxTag = new CompoundTag();
                boxTag.putString("item", boxEntry.getKey());
                boxTag.putLong("expiresAt", boxEntry.getValue());
                boxes.add(boxTag);
            }
            playerTag.put("boxes", boxes);
            players.add(playerTag);
        }
        tag.put("players", players);
        return tag;
    }

    public static SafetyBoxAccessData load(CompoundTag tag, HolderLookup.Provider registries) {
        SafetyBoxAccessData data = new SafetyBoxAccessData();
        ListTag players = tag.getList("players", CompoundTag.TAG_COMPOUND);
        for (int i = 0; i < players.size(); i++) {
            CompoundTag playerTag = players.getCompound(i);
            if (!playerTag.hasUUID("player")) continue;
            Map<String, Long> boxes = new HashMap<>();
            ListTag list = playerTag.getList("boxes", CompoundTag.TAG_COMPOUND);
            for (int j = 0; j < list.size(); j++) {
                CompoundTag boxTag = list.getCompound(j);
                String item = boxTag.getString("item");
                if (!item.isBlank()) boxes.merge(item, boxTag.getLong("expiresAt"), Math::max);
            }
            if (!boxes.isEmpty()) data.access.put(playerTag.getUUID("player"), boxes);
        }
        return data;
    }
}
