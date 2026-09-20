package com.xtdpotato.xero_delta.data;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;

/** World-global scaling and death-retention rules for the Delta health system. */
public final class HealthSystemRulesData extends SavedData {
    public static final double DEFAULT_EFFECT_MULTIPLIER = 1.0D;
    private static final String DATA_NAME = "xero_delta_health_system_rules";

    private double effectMultiplier = DEFAULT_EFFECT_MULTIPLIER;
    private boolean retainEffectsAfterDeath;

    public static HealthSystemRulesData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
            new Factory<>(HealthSystemRulesData::new, HealthSystemRulesData::load), DATA_NAME);
    }

    public synchronized double effectMultiplier() {
        return effectMultiplier;
    }

    public synchronized boolean retainEffectsAfterDeath() {
        return retainEffectsAfterDeath;
    }

    public synchronized void setEffectMultiplier(double value) {
        double clamped = clampMultiplier(value);
        if (Double.compare(effectMultiplier, clamped) == 0) return;
        effectMultiplier = clamped;
        setDirty();
    }

    public synchronized void setRetainEffectsAfterDeath(boolean value) {
        if (retainEffectsAfterDeath == value) return;
        retainEffectsAfterDeath = value;
        setDirty();
    }

    @Override
    public synchronized @NotNull CompoundTag save(@NotNull CompoundTag tag,
                                                   HolderLookup.@NotNull Provider registries) {
        tag.putDouble("effectMultiplier", effectMultiplier);
        tag.putBoolean("retainEffectsAfterDeath", retainEffectsAfterDeath);
        return tag;
    }

    public static HealthSystemRulesData load(CompoundTag tag, HolderLookup.Provider registries) {
        HealthSystemRulesData data = new HealthSystemRulesData();
        if (tag.contains("effectMultiplier", Tag.TAG_ANY_NUMERIC)) {
            data.effectMultiplier = clampMultiplier(tag.getDouble("effectMultiplier"));
        }
        data.retainEffectsAfterDeath = tag.getBoolean("retainEffectsAfterDeath");
        return data;
    }

    private static double clampMultiplier(double value) {
        if (!Double.isFinite(value)) return DEFAULT_EFFECT_MULTIPLIER;
        return Math.max(0.0D, Math.min(10.0D, value));
    }
}