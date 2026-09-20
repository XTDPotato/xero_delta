package com.xtdpotato.xero_delta.data;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ComponentRuleKeyTest {
    @Test
    void canonicalizesEquivalentComponentEntriesRegardlessOfOrder() {
        String first = rawKey("tacz:ammo", "{minecraft:custom_data=>{AmmoId:\"wemql_r:58x42\"}, minecraft:max_stack_size=>60}");
        String second = rawKey("tacz:ammo", "{minecraft:max_stack_size=>60, minecraft:custom_data=>{AmmoId:\"wemql_r:58x42\"}}");

        assertEquals(ComponentRuleKeys.canonicalizeRuleKey(first), ComponentRuleKeys.canonicalizeRuleKey(second));
    }

    private static String rawKey(String itemId, String components) {
        String encoded = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(components.getBytes(StandardCharsets.UTF_8));
        return itemId + "{v2:" + encoded + "}";
    }
}
