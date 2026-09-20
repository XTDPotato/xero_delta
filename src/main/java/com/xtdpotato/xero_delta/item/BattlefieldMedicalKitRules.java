package com.xtdpotato.xero_delta.item;

/** Shared balance values and deterministic timing for the battlefield medical kit. */
public final class BattlefieldMedicalKitRules {
    public static final int MAX_DURABILITY = 800;
    public static final int STARTUP_TICKS = 70;
    public static final int WOUND_DURABILITY = 25;
    public static final int PAIN_RELIEF_DURABILITY = 25;
    public static final int PAIN_RELIEF_TICKS = 20 * 60;
    public static final float HEAL_PER_SECOND = 30.0F;
    public static final int HEAL_INTERVAL_TICKS = 20;

    public static float healingPerTick() {
        return HEAL_PER_SECOND / HEAL_INTERVAL_TICKS;
    }

    private BattlefieldMedicalKitRules() {
    }

    public static int estimatedDurationTicks(float health, float maximumHealth) {
        return estimatedDurationTicks(health, maximumHealth, maximumHealth);
    }

    public static int estimatedDurationTicks(float health, float maximumHealth, float baseline) {
        return STARTUP_TICKS + MedicalHealthRules.healingTicks(
            maximumHealth - health, baseline, MAX_DURABILITY, healingPerTick());
    }

    public static int healingPulseCount(float missingHealth) {
        return (int) Math.ceil(Math.max(0.0F, missingHealth) / HEAL_PER_SECOND);
    }

    public static int healingPulseTick(int pulseIndex) {
        return STARTUP_TICKS + (Math.max(0, pulseIndex) + 1) * HEAL_INTERVAL_TICKS;
    }
}
