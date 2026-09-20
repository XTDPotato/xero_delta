package com.xtdpotato.xero_delta.client;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SafetyBoxAccessClientStateTest {
    @Test
    void basicBoxIsPermanentWithoutServerSnapshotEntry() {
        SafetyBoxAccessClientState.INSTANCE.update(Map.of());

        assertTrue(SafetyBoxAccessClientState.INSTANCE.isUnlocked("xero_delta:safety_box_2x1"));
        assertEquals(Long.MAX_VALUE,
            SafetyBoxAccessClientState.INSTANCE.expiresAt("xero_delta:safety_box_2x1"));
    }
}
