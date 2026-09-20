package com.xtdpotato.xero_delta.client;

final class LockFadeCurve {
    static final long FADE_IN_NANOS = 500_000_000L;
    static final long HOLD_NANOS = 0L;
    static final long FADE_OUT_NANOS = 500_000_000L;
    static final long COOLDOWN_NANOS = 0L;
    static final long FADE_OUT_START_NANOS = FADE_IN_NANOS + HOLD_NANOS;
    static final long VISIBLE_END_NANOS = FADE_OUT_START_NANOS + FADE_OUT_NANOS;
    static final long DURATION_NANOS = VISIBLE_END_NANOS + COOLDOWN_NANOS;

    private LockFadeCurve() {
    }

    static float alpha(long elapsedNanos) {
        if (elapsedNanos <= 0 || elapsedNanos >= DURATION_NANOS) return 0.0F;
        if (elapsedNanos < FADE_IN_NANOS) {
            return elapsedNanos / (float) FADE_IN_NANOS;
        }
        if (elapsedNanos < FADE_OUT_START_NANOS) return 1.0F;
        if (elapsedNanos < VISIBLE_END_NANOS) {
            return 1.0F - (elapsedNanos - FADE_OUT_START_NANOS) / (float) FADE_OUT_NANOS;
        }
        return 0.0F;
    }
}
