package com.xtdpotato.xero_delta.data;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

/** Persistent per-block overrides for whether opening requires loot searching. */
public final class LootSearchRulesData extends SavedData {
    private static final String DATA_NAME = "xero_delta_loot_search_rules";
    private final Map<String, Boolean> blockRules = new HashMap<>();

    public static LootSearchRulesData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
            new Factory<>(LootSearchRulesData::new, LootSearchRulesData::load), DATA_NAME);
    }

    public synchronized Boolean blockRule(ResourceKey<Level> dimension, BlockPos pos) {
        return blockRules.get(key(dimension, pos));
    }

    public synchronized void setBlockRule(ResourceKey<Level> dimension, BlockPos pos,
                                          boolean searchRequired) {
        blockRules.put(key(dimension, pos.immutable()), searchRequired);
        setDirty();
    }

    @Override
    public synchronized @NotNull CompoundTag save(@NotNull CompoundTag tag,
                                                   HolderLookup.@NotNull Provider registries) {
        CompoundTag rules = new CompoundTag();
        blockRules.forEach(rules::putBoolean);
        tag.put("BlockRules", rules);
        return tag;
    }

    public static LootSearchRulesData load(CompoundTag tag, HolderLookup.Provider registries) {
        LootSearchRulesData data = new LootSearchRulesData();
        CompoundTag rules = tag.getCompound("BlockRules");
        for (String key : rules.getAllKeys()) data.blockRules.put(key, rules.getBoolean(key));
        return data;
    }

    private static String key(ResourceKey<Level> dimension, BlockPos pos) {
        return dimension.location() + "|" + pos.getX() + "|" + pos.getY() + "|" + pos.getZ();
    }
}
