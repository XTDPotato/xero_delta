package com.xtdpotato.xero_delta.data;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MedicalShortcutRulesTest {
    @Test
    void defaultHoldTimeIsFifteenTicks() {
        assertEquals(15, MedicalShortcutRules.holdTicks(0.75D));
        assertEquals(1, MedicalShortcutRules.holdTicks(0.0D));
    }

    @Test
    void applicableSurgeryWinsWhileInjured() {
        int surgery = MedicalShortcutRules.candidateScore(
            true, true, false, true, true, false, 0);
        int health = MedicalShortcutRules.candidateScore(
            true, true, false, false, false, true, 10);
        assertTrue(surgery < health);
    }

    @Test
    void rememberedItemWinsWithinTheCurrentTreatmentCategory() {
        int rememberedSurgery = MedicalShortcutRules.candidateScore(
            true, true, true, true, true, false, 5);
        int otherSurgery = MedicalShortcutRules.candidateScore(
            true, true, false, true, true, false, 0);
        int rememberedHealth = MedicalShortcutRules.candidateScore(
            false, true, true, false, false, true, 10);
        int otherHealth = MedicalShortcutRules.candidateScore(
            false, true, false, false, false, true, 0);
        assertTrue(rememberedSurgery < otherSurgery);
        assertTrue(rememberedHealth < otherHealth);
    }

    @Test
    void surgeryStillWinsOverRememberedNonSurgicalTreatmentWhileInjured() {
        int surgery = MedicalShortcutRules.candidateScore(
            true, true, false, true, true, false, 0);
        int rememberedFirstAid = MedicalShortcutRules.candidateScore(
            true, true, true, false, true, true, 0);
        assertTrue(surgery < rememberedFirstAid);
    }

    @Test
    void surgeryIsNeverRecommendedWithoutAnApplicableInjury() {
        assertEquals(Integer.MAX_VALUE, MedicalShortcutRules.candidateScore(
            false, true, true, true, false, false, 0));
    }
}
