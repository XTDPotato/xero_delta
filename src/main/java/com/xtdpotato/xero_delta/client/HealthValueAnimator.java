package com.xtdpotato.xero_delta.client;

/** Fixed-duration health snapshots that never queue or chase stale values. */
final class HealthValueAnimator {
    static final long MAX_DURATION_MS = 500L;
    private static final float CHANGE_EPSILON = 0.0001F;

    private boolean initialized;
    private float from;
    private float target;
    private long startedAtMs;
    private long durationMs = MAX_DURATION_MS;

    float update(float actualHealth, long nowMs, long requestedDurationMs) {
        float actual = clamp01(actualHealth);
        if (!initialized) {
            initialized = true;
            from = actual;
            target = actual;
            startedAtMs = nowMs;
            durationMs = boundedDuration(requestedDurationMs);
            return actual;
        }

        if (Math.abs(actual - target) > CHANGE_EPSILON) {
            // New health replaces the old animation and starts at the prior real value.
            from = target;
            target = actual;
            startedAtMs = nowMs;
            durationMs = boundedDuration(requestedDurationMs);
        }
        return valueAt(nowMs);
    }

    float actualTarget() {
        return target;
    }

    boolean initialized() {
        return initialized;
    }

    void reset() {
        initialized = false;
        from = 0.0F;
        target = 0.0F;
        startedAtMs = 0L;
        durationMs = MAX_DURATION_MS;
    }

    private float valueAt(long nowMs) {
        long elapsedMs = Math.max(0L, nowMs - startedAtMs);
        if (elapsedMs >= durationMs) return target;
        float progress = elapsedMs / (float) durationMs;
        return from + (target - from) * progress;
    }

    private static long boundedDuration(long requestedDurationMs) {
        return Math.max(1L, Math.min(MAX_DURATION_MS, requestedDurationMs));
    }

    private static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }
}
