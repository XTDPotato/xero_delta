package com.xtdpotato.xero_delta.data;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BallisticArmorRulesTest {
    @Test
    void followsQualityArmorWearMatrix() {
        assertEquals(0.60D, BallisticArmorRules.multiplier("gray", "green", null));
        assertEquals(0.20D, BallisticArmorRules.multiplier("gray", "red", null));
        assertEquals(0.70D, BallisticArmorRules.multiplier("green", "blue", null));
        assertEquals(0.30D, BallisticArmorRules.multiplier("green", "red", null));
        assertEquals(0.90D, BallisticArmorRules.multiplier("blue", "purple", null));
        assertEquals(0.50D, BallisticArmorRules.multiplier("blue", "gold", null));
        assertEquals(0.60D, BallisticArmorRules.multiplier("purple", "red", null));
        assertEquals(1.10D, BallisticArmorRules.multiplier("gold", "gray", null));
        assertEquals(1.20D, BallisticArmorRules.multiplier("red", "red", null));
    }

    @Test
    void convertsBaseDamageToPercentageDurabilityWear() {
        assertEquals(80, BallisticArmorRules.durabilityWear(400, 20.0F, 1.0D));
        assertEquals(48, BallisticArmorRules.durabilityWear(400, 20.0F, 0.60D));
        assertEquals(400, BallisticArmorRules.durabilityWear(400, 200.0F, 1.0D));
    }
}