package com.xtdpotato.xero_delta.data;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CorpseRulesTest {
    @Test
    void defaultsToFiveMinutes() {
        assertEquals(5, CorpseRules.DEFAULT_LIFETIME_MINUTES);
        assertEquals(6_000, CorpseRules.lifetimeTicks(5));
    }

    @Test
    void defaultsToConfiguredHostileFamiliesWithChestRigOnly() {
        assertTrue(CorpseRules.DEFAULT_MOB_ENTITY_IDS.contains("minecraft:zombie"));
        assertTrue(CorpseRules.DEFAULT_MOB_ENTITY_IDS.contains("minecraft:skeleton"));
        assertTrue(CorpseRules.DEFAULT_MOB_ENTITY_IDS.contains("minecraft:creeper"));
        assertTrue(CorpseRules.DEFAULT_GENERATE_CHEST_RIG);
        assertFalse(CorpseRules.DEFAULT_GENERATE_BACKPACK);
        assertEquals("xero_delta:dar_assault_chest_rig",
            CorpseRules.DEFAULT_CHEST_RIG_ID);
    }

    @Test
    void clampsLifetimeToSupportedRange() {
        assertEquals(CorpseRules.MIN_LIFETIME_MINUTES,
            CorpseRules.clampLifetimeMinutes(0));
        assertEquals(CorpseRules.MAX_LIFETIME_MINUTES,
            CorpseRules.clampLifetimeMinutes(Integer.MAX_VALUE));
    }

    @Test
    void convertsConfiguredMinutesToTicks() {
        assertEquals(1_200, CorpseRules.lifetimeTicks(1));
        assertEquals(1_728_000, CorpseRules.lifetimeTicks(1_440));
    }

    @Test
    void selectsCandidatesUsingProportionalWeights() {
        var candidates = List.of(
            new CorpseRules.WeightedCarrier("test:heavy", 2),
            new CorpseRules.WeightedCarrier("test:light", 1));

        assertEquals("test:heavy",
            CorpseRules.chooseWeighted(candidates, 0).itemId());
        assertEquals("test:heavy",
            CorpseRules.chooseWeighted(candidates, 1).itemId());
        assertEquals("test:light",
            CorpseRules.chooseWeighted(candidates, 2).itemId());
    }

    @Test
    void keepsCarrierListsIndependentPerEntity() {
        CorpseRules.Settings settings = CorpseRules.defaultSettings()
            .withCandidates("minecraft:zombie", false, List.of(
                new CorpseRules.WeightedCarrier("test:zombie_pack", 3)));

        assertEquals("test:zombie_pack",
            settings.candidates("minecraft:zombie", false).getFirst().itemId());
        assertTrue(settings.candidates("minecraft:skeleton", false).isEmpty());
    }
}
