package com.xtdpotato.xero_delta.data;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;

/** World-global optional Delta-style inventory layout rule. */
public final class PlayerLayoutRulesData extends SavedData {
    private static final String DATA_NAME = "xero_delta_player_layout";
    private boolean enabled = true;

    public static PlayerLayoutRulesData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
            new Factory<>(PlayerLayoutRulesData::new, PlayerLayoutRulesData::load), DATA_NAME);
    }

    public synchronized boolean enabled() { return enabled; }

    public synchronized void setEnabled(boolean value) {
        if (enabled == value) return;
        enabled = value;
        setDirty();
    }

    @Override
    public synchronized @NotNull CompoundTag save(@NotNull CompoundTag tag,
                                                   HolderLookup.@NotNull Provider registries) {
        tag.putBoolean("enabled", enabled);
        return tag;
    }

    public static PlayerLayoutRulesData load(CompoundTag tag, HolderLookup.Provider registries) {
        PlayerLayoutRulesData data = new PlayerLayoutRulesData();
        data.enabled = !tag.contains("enabled") || tag.getBoolean("enabled");
        return data;
    }
}
