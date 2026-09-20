package com.xtdpotato.xero_delta.data;

import com.xtdpotato.xero_delta.network.StaminaStatePacket;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/** Server-authoritative stamina state for the optional Delta inventory layout. */
public final class PlayerStaminaManager {
    private static final String ROOT = "xero_delta_stamina";
    private static final String CURRENT = "current";
    private static final String MAXIMUM = "maximum";
    private static final String REGEN_DELAY = "regen_delay";
    private static final String SPRINT_LOCKED = "sprint_locked";
    private static final String FOOD_LIMITED = "food_limited";
    private static final String RAPID_RECOVERY = "rapid_recovery_ticks";
    private static final String RAPID_AMOUNT = "rapid_recovery_amount";

    private PlayerStaminaManager() {}

    public static void tick(ServerPlayer player) {
        CompoundTag data = data(player);
        float maximum = maximum(data);
        float before = current(data, maximum);
        int delay = Math.max(0, data.getInt(REGEN_DELAY));
        boolean beforeLocked = data.getBoolean(SPRINT_LOCKED);
        boolean sprintLocked = beforeLocked || StaminaRules.shouldLockSprint(before, maximum);
        float after = before;

        if (!PlayerLayoutSlotRules.enabled(player)) {
            after = maximum;
            delay = 0;
            sprintLocked = false;
        } else {
            boolean sprinting = player.isSprinting();
            if (sprintLocked) {
                if (StaminaRules.canResumeSprint(before, maximum)) {
                    sprintLocked = false;
                } else {
                    if (sprinting) player.setSprinting(false);
                    sprinting = false;
                }
            }
            if (sprinting) {
                after = StaminaRules.consume(before, StaminaRules.SPRINT_COST_PER_TICK);
                delay = StaminaRules.REGEN_DELAY_TICKS;
                if (StaminaRules.shouldLockSprint(after, maximum)) {
                    sprintLocked = true;
                    player.setSprinting(false);
                }
            } else if (delay > 0) {
                delay--;
                if (delay == 0) after = StaminaRules.regenerate(before, maximum);
            } else {
                after = StaminaRules.regenerate(before, maximum);
            }
        }

        int recoveryTicks = data.getInt(RAPID_RECOVERY);
        if (recoveryTicks > 0) {
            after = StaminaRules.clamp(after + data.getFloat(RAPID_AMOUNT), 0, maximum);
            data.putInt(RAPID_RECOVERY, recoveryTicks - 1);
            if (StaminaRules.canResumeSprint(after, maximum)) sprintLocked = false;
        }
        data.putFloat(CURRENT, after);
        data.putFloat(MAXIMUM, maximum);
        data.putInt(REGEN_DELAY, delay);
        data.putBoolean(SPRINT_LOCKED, sprintLocked);
        updateFoodLimit(player, data, after, maximum,
            PlayerLayoutSlotRules.enabled(player));
        if (Math.abs(after - before) > 0.0001F || sprintLocked != beforeLocked) sync(player);
    }
    public static void consumeJump(ServerPlayer player) {
        if (!PlayerLayoutSlotRules.enabled(player)) return;
        CompoundTag data = data(player);
        float maximum = maximum(data);
        float before = current(data, maximum);
        float after = StaminaRules.consume(before, StaminaRules.JUMP_COST);
        boolean beforeLocked = data.getBoolean(SPRINT_LOCKED);
        boolean sprintLocked = beforeLocked || StaminaRules.shouldLockSprint(after, maximum);
        if (sprintLocked) player.setSprinting(false);
        data.putFloat(CURRENT, after);
        data.putInt(REGEN_DELAY, StaminaRules.REGEN_DELAY_TICKS);
        data.putBoolean(SPRINT_LOCKED, sprintLocked);
        updateFoodLimit(player, data, after, maximum, true);
        if (Math.abs(after - before) > 0.0001F || sprintLocked != beforeLocked) sync(player);
    }

    public static void beginRapidRecovery(ServerPlayer player, int durationTicks) {
        CompoundTag data = data(player);
        int ticks = Math.max(1, durationTicks);
        data.putInt(RAPID_RECOVERY, ticks);
        data.putFloat(RAPID_AMOUNT, maximum(data) / ticks);
    }

    public static void setMaximum(ServerPlayer player, float maximum) {
        float safeMaximum = StaminaRules.clamp(maximum, 1.0F, 100_000.0F);
        CompoundTag data = data(player);
        float current = current(data, maximum(data));
        float nextCurrent = Math.min(current, safeMaximum);
        data.putFloat(MAXIMUM, safeMaximum);
        data.putFloat(CURRENT, nextCurrent);
        if (StaminaRules.shouldLockSprint(nextCurrent, safeMaximum)) {
            data.putBoolean(SPRINT_LOCKED, true);
            player.setSprinting(false);
        } else if (StaminaRules.canResumeSprint(nextCurrent, safeMaximum)) {
            data.putBoolean(SPRINT_LOCKED, false);
        }
        updateFoodLimit(player, data, nextCurrent, safeMaximum,
            PlayerLayoutSlotRules.enabled(player));
        sync(player);
    }

    public static void initialize(ServerPlayer player) {
        CompoundTag data = data(player);
        float maximum = maximum(data);
        if (!data.contains(CURRENT)) data.putFloat(CURRENT, maximum);
        if (!data.contains(SPRINT_LOCKED)) data.putBoolean(SPRINT_LOCKED, false);
        if (!data.contains(FOOD_LIMITED)) data.putBoolean(FOOD_LIMITED, false);
        updateFoodLimit(player, data, current(data, maximum), maximum,
            PlayerLayoutSlotRules.enabled(player));
        sync(player);
    }

    public static void copyMaximumAfterDeath(ServerPlayer original, ServerPlayer replacement) {
        float maximum = maximum(data(original));
        CompoundTag target = data(replacement);
        target.putFloat(MAXIMUM, maximum);
        target.putFloat(CURRENT, maximum);
        target.putInt(REGEN_DELAY, 0);
        target.putBoolean(SPRINT_LOCKED, false);
        target.putBoolean(FOOD_LIMITED, false);
        sync(replacement);
    }

    public static Snapshot snapshot(ServerPlayer player) {
        CompoundTag data = data(player);
        float maximum = maximum(data);
        float current = current(data, maximum);
        return new Snapshot(current, maximum, data.getBoolean(SPRINT_LOCKED));
    }

    public static void sync(ServerPlayer player) {
        Snapshot value = snapshot(player);
        PacketDistributor.sendToPlayer(player,
            new StaminaStatePacket(value.current(), value.maximum(), value.exhausted()));
    }

    private static CompoundTag data(ServerPlayer player) {
        CompoundTag persistent = player.getPersistentData();
        if (!persistent.contains(ROOT)) persistent.put(ROOT, new CompoundTag());
        return persistent.getCompound(ROOT);
    }

    private static float maximum(CompoundTag data) {
        float stored = data.contains(MAXIMUM) ? data.getFloat(MAXIMUM) : StaminaRules.DEFAULT_MAX;
        return StaminaRules.clamp(stored, 1.0F, 100_000.0F);
    }

    private static float current(CompoundTag data, float maximum) {
        float stored = data.contains(CURRENT) ? data.getFloat(CURRENT) : maximum;
        return StaminaRules.clamp(stored, 0.0F, maximum);
    }

    private static void updateFoodLimit(ServerPlayer player, CompoundTag data, float current,
                                        float maximum, boolean staminaEnabled) {
        boolean foodLimited = data.getBoolean(FOOD_LIMITED);
        if (staminaEnabled && StaminaRules.exhausted(current)) {
            if (!foodLimited) {
                player.getFoodData().setFoodLevel(StaminaRules.EXHAUSTED_FOOD_LEVEL);
            }
            data.putBoolean(FOOD_LIMITED, true);
            return;
        }
        if (foodLimited && (!staminaEnabled || StaminaRules.canResumeSprint(current, maximum))) {
            player.getFoodData().setFoodLevel(StaminaRules.RECOVERED_FOOD_LEVEL);
            data.putBoolean(FOOD_LIMITED, false);
        }
    }

    public record Snapshot(float current, float maximum, boolean exhausted) {}
}
