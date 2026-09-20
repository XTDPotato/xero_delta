package com.xtdpotato.xero_delta.data;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaczCompatibilityRulesTest {
    @Test
    void onlyMatchesLrWorkshopMeleeWeapons() {
        assertTrue(TaczCompatibilityRules.isLrTacticalMeleeDescriptor(
            "lr_tactical:combat_knife melee"));
        assertTrue(TaczCompatibilityRules.isLrTacticalMeleeDescriptor(
            "lr tactical workshop katana melee"));
        assertFalse(TaczCompatibilityRules.isLrTacticalMeleeDescriptor(
            "lr_tactical:assault_rifle rifle"));
        assertFalse(TaczCompatibilityRules.isLrTacticalMeleeDescriptor(
            "other_pack:combat_knife melee"));
    }

    @Test
    void classifiesTaczPrimaryWeaponsAndHandguns() {
        assertTrue(TaczCompatibilityRules.isTaczGunDescriptor(
            "com.tacz.gunitem modern_kinetic_gun gun_id=tacz:ak47"));
        assertFalse(TaczCompatibilityRules.isHandgunDescriptor(
            "com.tacz.gunitem modern_kinetic_gun gun_id=tacz:ak47 assault_rifle"));
        assertTrue(TaczCompatibilityRules.isHandgunDescriptor(
            "com.tacz.gunitem modern_kinetic_gun gun_id=tacz:glock_17 pistol"));
        assertFalse(TaczCompatibilityRules.isTaczGunDescriptor("minecraft:crossbow"));
    }
}
