package com.xtdpotato.xero_delta.data;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

class ServerItemRulesKeyTest {
    @Test
    void automaticIdRuleGetsItemPrefix() {
        assertEquals("item:minecraft:stone", ServerItemRules.automaticRuleKey("minecraft:stone"));
    }

    @Test
    void automaticDurabilityRuleKeepsDurabilityPrefix() {
        String key = "durability|minecraft:diamond_pickaxe|0..1_3";

        assertEquals(key, ServerItemRules.automaticRuleKey(key));
    }

    @Test
    void manualViewDropsLargeAutomaticRuleSetOnce() {
        Map<String, Long> rules = new HashMap<>();
        Set<String> automatic = new HashSet<>();
        for (int index = 0; index < 15_000; index++) {
            String key = "item:test:item_" + index;
            rules.put(key, (long) index);
            automatic.add(key);
        }
        rules.put("item:test:manual", 42L);

        Map<String, Long> manual = ServerItemRules.manualEntries(rules, automatic);

        assertEquals(Map.of("item:test:manual", 42L), manual);
    }
}
