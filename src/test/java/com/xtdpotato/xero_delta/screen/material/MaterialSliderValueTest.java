package com.xtdpotato.xero_delta.screen.material;

import com.xtdpotato.xero_delta.screen.material.MaterialSliderValue;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MaterialSliderValueTest {
    @Test
    void integerStepsNeverDisplayDecimalSuffix() {
        double value = MaterialSliderValue.quantize(0.0D, 20.0D, 1.0D, 6.72D);
        assertEquals(7.0D, value);
        assertEquals("7", MaterialSliderValue.format(1.0D, value));
    }

    @Test
    void fractionalStepsKeepOnlyRequiredPrecision() {
        double value = MaterialSliderValue.quantize(0.5D, 3.0D, 0.05D,
            1.2999999999999998D);
        assertEquals(1.3D, value);
        assertEquals("1.3", MaterialSliderValue.format(0.05D, value));
    }

    @Test
    void negativeRangesRemainAlignedToStep() {
        double value = MaterialSliderValue.quantize(-100.0D, 100.0D, 1.0D,
            -28.51D);
        assertEquals(-29.0D, value);
        assertEquals("-29", MaterialSliderValue.format(1.0D, value));
    }
}

