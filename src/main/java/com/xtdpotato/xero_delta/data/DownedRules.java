package com.xtdpotato.xero_delta.data;

/** Pure timing and transition rules for the Delta-style downed system. */
public final class DownedRules {
    public static final int RED_TICKS = 50 * 20;
    public static final int BOLT_SNIPER_RED_TICKS = 50 * 20;
    public static final int YELLOW_WINDOW_TICKS = 3 * 60 * 20;
    public static final int RED_RESCUE_TICKS = 15 * 20;
    public static final int YELLOW_RESCUE_TICKS = 15 * 20;
    public static final int RESCUE_HEARTBEAT_GRACE_TICKS = 10;
    public static final int CARRY_WINDUP_TICKS = 2 * 20;
    public static final int CARRY_DROP_TICKS = 2 * 20;
    public static final int CARRY_ANIMATION_TICKS = 20;
    public static final int RESCUE_REQUEST_COOLDOWN_TICKS = 10 * 20;
    public static final int ABANDON_HOLD_TICKS = 3 * 20;
    public static final float DAMAGE_TIMER_PERCENT_PER_HEALTH = 0.01F;
    /** Explosions are more dangerous to a player who is already red-downed. */
    public static final float RED_EXPLOSION_DAMAGE_MULTIPLIER = 2.0F;
    public static final double CLOSE_EXPLOSION_RADIUS = 2.0D;
    private static final double CLOSE_EXPLOSION_RADIUS_SQR =
        CLOSE_EXPLOSION_RADIUS * CLOSE_EXPLOSION_RADIUS;

    private DownedRules() {
    }

    public static int redDuration(boolean boltSniper) {
        return boltSniper ? BOLT_SNIPER_RED_TICKS : RED_TICKS;
    }

    public static int damageTimerPenalty(int durationTicks, float damage) {
        if (!Float.isFinite(damage) || damage <= 0.0F) return 0;
        return Math.max(1, Math.round(durationTicks * damage * DAMAGE_TIMER_PERCENT_PER_HEALTH));
    }

    public static int subtractDamageTime(int remainingTicks, int durationTicks, float damage) {
        return Math.max(0, remainingTicks - damageTimerPenalty(durationTicks, damage));
    }

    public static float redDownExplosionDamage(float damage) {
        if (!Float.isFinite(damage) || damage <= 0.0F) return 0.0F;
        return damage * RED_EXPLOSION_DAMAGE_MULTIPLIER;
    }

    public static boolean isCloseExplosion(double distanceSquared) {
        return Double.isFinite(distanceSquared)
            && distanceSquared <= CLOSE_EXPLOSION_RADIUS_SQR + 1.0E-6D;
    }

    public static float progress(int remainingTicks, int durationTicks) {
        if (durationTicks <= 0) return 0.0F;
        return Math.max(0.0F, Math.min(1.0F, remainingTicks / (float) durationTicks));
    }

    public static float abandonProgress(int heldTicks) {
        return progress(heldTicks, ABANDON_HOLD_TICKS);
    }

    public static boolean rescueHeartbeatFresh(long gameTime, long lastHeartbeatTime) {
        return gameTime - lastHeartbeatTime <= RESCUE_HEARTBEAT_GRACE_TICKS;
    }

    public static boolean canRequestRescue(long gameTime, long nextAllowedGameTime) {
        return gameTime >= nextAllowedGameTime;
    }

    public static long nextRescueRequestTime(long gameTime) {
        return gameTime + RESCUE_REQUEST_COOLDOWN_TICKS;
    }
}
