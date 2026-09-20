package com.xtdpotato.xero_delta.data;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SafetyBoxItemPolicyTest {
    @Test
    void allowsArmorAndHelmetRepairKitsOnlyFromThisMod() {
        assertTrue(SafetyBoxItemPolicy.isExplicitlyAllowedId(
            "xero_delta:advanced_armor_repair_combo"));
        assertTrue(SafetyBoxItemPolicy.isExplicitlyAllowedId(
            "xero_delta:homemade_helmet_repair_kit"));
        assertFalse(SafetyBoxItemPolicy.isExplicitlyAllowedId(
            "xero_delta:dar_assault_chest_rig"));
        assertFalse(SafetyBoxItemPolicy.isExplicitlyAllowedId(
            "other_mod:standard_armor_repair_kit"));
    }
}
