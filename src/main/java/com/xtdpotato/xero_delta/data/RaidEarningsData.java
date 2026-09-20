package com.xtdpotato.xero_delta.data;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** World-persistent currency earned during the current raid, separate from the wallet. */
public final class RaidEarningsData extends SavedData {
    private static final String DATA_NAME = "xero_delta_raid_earnings";
    private final Map<UUID, Long> earnings = new HashMap<>();

    public static RaidEarningsData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
            new Factory<>(RaidEarningsData::new, RaidEarningsData::load), DATA_NAME);
    }

    public synchronized long amount(UUID playerId) {
        return earnings.getOrDefault(playerId, 0L);
    }

    public synchronized long add(UUID playerId, long value) {
        if (value <= 0L) return amount(playerId);
        long current = amount(playerId);
        long next = RaidEarningsRules.add(current, value);
        if (next != current) {
            earnings.put(playerId, next);
            setDirty();
        }
        return next;
    }

    /** Removes and returns the current reward in one synchronized operation. */
    public synchronized long take(UUID playerId) {
        Long removed = earnings.remove(playerId);
        if (removed == null || removed <= 0L) return 0L;
        setDirty();
        return removed;
    }

    public synchronized void clear(UUID playerId) {
        if (earnings.remove(playerId) != null) setDirty();
    }


    @Override
    public synchronized @NotNull CompoundTag save(@NotNull CompoundTag tag,
                                                   HolderLookup.@NotNull Provider registries) {
        ListTag values = new ListTag();
        earnings.forEach((playerId, amount) -> {
            if (amount <= 0L) return;
            CompoundTag value = new CompoundTag();
            value.putUUID("player", playerId);
            value.putLong("amount", amount);
            values.add(value);
        });
        tag.put("earnings", values);
        return tag;
    }

    public static RaidEarningsData load(CompoundTag tag, HolderLookup.Provider registries) {
        RaidEarningsData data = new RaidEarningsData();
        for (var raw : tag.getList("earnings", 10)) {
            CompoundTag value = (CompoundTag) raw;
            if (!value.hasUUID("player")) continue;
            long amount = Math.max(0L, Math.min(RaidEarningsRules.MAX_EARNINGS, value.getLong("amount")));
            if (amount > 0L) data.earnings.put(value.getUUID("player"), amount);
        }
        return data;
    }
}
