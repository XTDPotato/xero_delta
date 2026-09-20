package com.xtdpotato.xero_delta.data;

/** Pure stamina constants and arithmetic shared by the server implementation and tests. */
public final class StaminaRules {
    public static final float DEFAULT_MAX = 180.0F;
    public static final int SPRINT_DURATION_SECONDS = 35;
    public static final int SPRINT_DURATION_TICKS = SPRINT_DURATION_SECONDS * 20;
    public static final float SPRINT_COST_PER_TICK = DEFAULT_MAX / SPRINT_DURATION_TICKS;
    public static final float SPRINT_LOCK_FRACTION = 0.0F;
    public static final float SPRINT_RESUME_FRACTION = 0.05F;
    public static final int EXHAUSTED_FOOD_LEVEL = 6;
    public static final int RECOVERED_FOOD_LEVEL = 12;
    public static final float JUMP_COST = 10.0F;
    public static final int REGEN_DELAY_TICKS = 40;
    public static final float REGEN_PER_TICK = 1.0F;

    private StaminaRules() {}

    public static float consume(float current, float amount) {
        return clamp(current - Math.max(0.0F, amount), 0.0F, Float.MAX_VALUE);
    }

    public static float regenerate(float current, float maximum) {
        return clamp(current + REGEN_PER_TICK, 0.0F, Math.max(1.0F, maximum));
    }

    public static boolean exhausted(float current) {
        return current <= 0.0001F;
    }

    public static boolean shouldLockSprint(float current, float maximum) {
        return exhausted(current);
    }

    public static boolean canResumeSprint(float current, float maximum) {
        float safeMaximum = Math.max(1.0F, maximum);
        return current > safeMaximum * SPRINT_RESUME_FRACTION + 0.0001F;
    }

    public static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
