package com.xtdpotato.xero_delta.data;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;

/** World-global percentages for gray, unrecoverable health-cap regions. */
public final class HealthPenaltyRulesData extends SavedData {
    public static final double DEFAULT_CHEST_PERCENT = 10.0D;
    public static final double DEFAULT_YELLOW_RESCUE_PERCENT = 20.0D;
    public static final double MIN_PERCENT = 0.0D;
    public static final double MAX_PERCENT = 90.0D;
    private static final String DATA_NAME = "xero_delta_health_penalty_rules";

    private double chestPercent = DEFAULT_CHEST_PERCENT;
    private double yellowRescuePercent = DEFAULT_YELLOW_RESCUE_PERCENT;

    public static HealthPenaltyRulesData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
            new Factory<>(HealthPenaltyRulesData::new, HealthPenaltyRulesData::load),
            DATA_NAME);
    }

    public synchronized double chestPercent() {
        return chestPercent;
    }

    public synchronized double yellowRescuePercent() {
        return yellowRescuePercent;
    }

    public synchronized double chestFraction() {
        return chestPercent / 100.0D;
    }

    public synchronized double yellowRescueFraction() {
        return yellowRescuePercent / 100.0D;
    }

    public synchronized void setChestPercent(double value) {
        double clamped = clampPercent(value);
        if (Math.abs(chestPercent - clamped) < 0.000001D) return;
        chestPercent = clamped;
        setDirty();
    }

    public synchronized void setYellowRescuePercent(double value) {
        double clamped = clampPercent(value);
        if (Math.abs(yellowRescuePercent - clamped) < 0.000001D) return;
        yellowRescuePercent = clamped;
        setDirty();
    }

    @Override
    public synchronized @NotNull CompoundTag save(@NotNull CompoundTag tag,
                                                   HolderLookup.@NotNull Provider registries) {
        tag.putDouble("chestPercent", chestPercent);
        tag.putDouble("yellowRescuePercent", yellowRescuePercent);
        return tag;
    }

    public static HealthPenaltyRulesData load(CompoundTag tag,
                                               HolderLookup.Provider registries) {
        HealthPenaltyRulesData data = new HealthPenaltyRulesData();
        if (tag.contains("chestPercent", Tag.TAG_ANY_NUMERIC)) {
            data.chestPercent = clampPercent(tag.getDouble("chestPercent"));
        }
        if (tag.contains("yellowRescuePercent", Tag.TAG_ANY_NUMERIC)) {
            data.yellowRescuePercent = clampPercent(tag.getDouble("yellowRescuePercent"));
        }
        return data;
    }

    static double clampPercent(double value) {
        if (!Double.isFinite(value)) return MIN_PERCENT;
        return Math.max(MIN_PERCENT, Math.min(MAX_PERCENT, value));
    }
}