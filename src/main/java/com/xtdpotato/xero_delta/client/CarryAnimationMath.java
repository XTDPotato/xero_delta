package com.xtdpotato.xero_delta.client;

/** Pure carry-animation timing and easing helpers. */
final class CarryAnimationMath {
    private CarryAnimationMath() {
    }

    static float progress(byte phase, float remainingTicks,
                          int actionDurationTicks, int animationDurationTicks) {
        if (actionDurationTicks <= 0 || animationDurationTicks <= 0) {
            return phase == 2 ? 1.0F : 0.0F;
        }
        float elapsedTicks = Math.max(0.0F, actionDurationTicks - remainingTicks);
        float animated = clamp01(elapsedTicks / animationDurationTicks);
        float linear = switch (phase) {
            case 1 -> animated;
            case 2 -> 1.0F;
            case 3 -> 1.0F - animated;
            default -> 0.0F;
        };
        return smootherStep(linear);
    }

    static float estimatedRemaining(int snapshotTicks, long elapsedMillis) {
        return Math.max(0.0F, snapshotTicks - Math.max(0L, elapsedMillis) / 50.0F);
    }

    static float smootherStep(float value) {
        float t = clamp01(value);
        return t * t * t * (t * (t * 6.0F - 15.0F) + 10.0F);
    }

    private static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }
}
