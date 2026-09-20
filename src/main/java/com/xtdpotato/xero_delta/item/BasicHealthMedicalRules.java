package com.xtdpotato.xero_delta.item;

/** Timing and healing budget for consumable health items other than durable kits. */
public final class BasicHealthMedicalRules {
    public static final double PULSE_SECONDS = 1.0D;
    public static final int SERVER_TICKS_PER_SECOND = 20;
    public static final int PULSE_INTERVAL_TICKS = secondsToServerTicks(PULSE_SECONDS);
    public static final float TOTAL_HEAL_FRACTION = 0.25F;
    public static final float HEAL_PER_SECOND_FRACTION = 0.10F;

    private BasicHealthMedicalRules() {
    }

    public static float totalHealing(float maximumHealth) {
        return Math.max(0.0F, maximumHealth) * TOTAL_HEAL_FRACTION;
    }

    public static float healingPerSecond(float maximumHealth) {
        return Math.max(0.0F, maximumHealth) * HEAL_PER_SECOND_FRACTION;
    }

    public static int durationTicks(float maximumHealth) {
        float perSecond = healingPerSecond(maximumHealth);
        if (perSecond <= 0.0F) return PULSE_INTERVAL_TICKS;
        int seconds = Math.max(1, (int) Math.ceil(totalHealing(maximumHealth) / perSecond));
        return seconds * PULSE_INTERVAL_TICKS;
    }

    public static int secondsToServerTicks(double seconds) {
        return Math.max(1, (int) Math.round(Math.max(0.0D, seconds)
            * SERVER_TICKS_PER_SECOND));
    }
}
