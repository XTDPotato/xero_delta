package com.xtdpotato.xero_delta.data;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InjuryImpactRuleTest {
    @Test
    void invalidDamageDoesNotCreateInjury() {
        assertEquals(0, InjuryImpactRule.points(0.0F, 0.5D));
        assertEquals(0, InjuryImpactRule.points(-2.0F, 0.5D));
        assertEquals(0, InjuryImpactRule.points(Float.NaN, 0.5D));
    }

    @Test
    void everyValidHitAddsAtLeastOnePoint() {
        assertEquals(1, InjuryImpactRule.points(0.1F, 0.0D));
    }

    @Test
    void strongHitsAreCappedAtTwentyPoints() {
        assertEquals(20, InjuryImpactRule.points(100.0F, 1.0D));
    }

    @Test
    void forceRollChangesTheResultWithoutExceedingBounds() {
        assertEquals(8, InjuryImpactRule.points(10.0F, 0.0D));
        assertEquals(20, InjuryImpactRule.points(10.0F, 1.0D));
    }
}
