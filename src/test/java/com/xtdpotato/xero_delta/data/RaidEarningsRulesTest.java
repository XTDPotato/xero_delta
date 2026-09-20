package com.xtdpotato.xero_delta.data;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RaidEarningsRulesTest {
    @Test
    void addsWithoutTouchingTheWalletRange() {
        assertEquals(1_500L, RaidEarningsRules.add(1_000L, 500L));
        assertEquals(1_000L, RaidEarningsRules.add(1_000L, -1L));
    }

    @Test
    void saturatesWithoutLongOverflow() {
        assertEquals(RaidEarningsRules.MAX_EARNINGS,
            RaidEarningsRules.add(RaidEarningsRules.MAX_EARNINGS - 2L, Long.MAX_VALUE));
        assertEquals(RaidEarningsRules.MAX_EARNINGS,
            RaidEarningsRules.add(Long.MAX_VALUE, 1L));
    }
}
