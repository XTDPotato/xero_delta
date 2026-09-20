package com.xtdpotato.xero_delta.screen.material;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Stable step quantization and display formatting without binary decimal tails. */
public final class MaterialSliderValue {
    private MaterialSliderValue() {
    }

    public static double quantize(double minimum, double maximum,
                                  double step, double raw) {
        double value = step <= 0.0D ? raw
            : minimum + Math.round((raw - minimum) / step) * step;
        value = Math.max(minimum, Math.min(maximum, value));
        return BigDecimal.valueOf(value)
            .setScale(decimalPlaces(step), RoundingMode.HALF_UP)
            .doubleValue();
    }

    public static String format(double step, double value) {
        return BigDecimal.valueOf(value)
            .setScale(decimalPlaces(step), RoundingMode.HALF_UP)
            .stripTrailingZeros()
            .toPlainString();
    }

    static int decimalPlaces(double step) {
        if (step <= 0.0D) return 6;
        return Math.max(0, Math.min(6,
            BigDecimal.valueOf(step).stripTrailingZeros().scale()));
    }
}


