package com.xtdpotato.xero_delta.item;

/** Balance values and deterministic timing for the outdoor medical kit. */
public final class OutdoorMedicalKitRules {
    public static final int MAX_DURABILITY = 350;
    public static final int STARTUP_TICKS = 80;
    public static final int WOUND_DURABILITY = 25;
    public static final int PAIN_RELIEF_DURABILITY = 25;
    public static final int PAIN_RELIEF_TICKS = 20 * 30;
    public static final float HEAL_PER_SECOND = 20.0F;
    public static final int HEAL_INTERVAL_TICKS = 20;

    public static float healingPerTick() {
        return HEAL_PER_SECOND / HEAL_INTERVAL_TICKS;
    }

    private OutdoorMedicalKitRules() {
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
