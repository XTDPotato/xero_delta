package com.xtdpotato.xero_delta.data;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WarehouseNameRulesTest {
    @Test
    void removesControlCharactersAndTrimsWhitespace() {
        assertEquals("MainVault", WarehouseNameRules.sanitize(
            "  Main\nVault\u0000  ", 24));
    }

    @Test
    void limitsNamesByCodePoints() {
        assertEquals(24, WarehouseNameRules.sanitize("x".repeat(80), 24).length());
    }

    @Test
    void nullAndNonPositiveLimitsProduceEmptyName() {
        assertEquals("", WarehouseNameRules.sanitize(null, 24));
        assertEquals("", WarehouseNameRules.sanitize("warehouse", 0));
    }
}
