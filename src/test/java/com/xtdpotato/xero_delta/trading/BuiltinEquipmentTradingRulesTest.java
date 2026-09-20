package com.xtdpotato.xero_delta.trading;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuiltinEquipmentTradingRulesTest {
    @Test
    void deltaRigAndPackAreBlockedFromMarketByDefault() {
        assertFalse(BuiltinTradingUploadRules.defaultAllowed("xero_delta:dar_assault_chest_rig"));
        assertFalse(BuiltinTradingUploadRules.defaultAllowed("xero_delta:gto_heavy_tactical_pack"));
        assertTrue(BuiltinTradingUploadRules.defaultAllowed("minecraft:diamond"));
    }

    @Test
    void usesDedicatedMilitaryVendorRecycleValues() {
        assertEquals(59_159L,
            BuiltinRecycleValueCatalog.resolve("xero_delta:dar_assault_chest_rig", 243_000L));
        assertEquals(58_114L,
            BuiltinRecycleValueCatalog.resolve("xero_delta:gto_heavy_tactical_pack", 1_084_000L));
        assertEquals(42L, BuiltinRecycleValueCatalog.resolve("minecraft:stick", 42L));
    }
}
