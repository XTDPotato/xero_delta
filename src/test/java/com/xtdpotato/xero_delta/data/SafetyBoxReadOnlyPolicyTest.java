package com.xtdpotato.xero_delta.data;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SafetyBoxReadOnlyPolicyTest {
    @Test
    void expiredBoxAllowsOnlyRemovalActions() {
        assertTrue(SafetyBoxReadOnlyPolicy.allows(0, true, false));
        assertTrue(SafetyBoxReadOnlyPolicy.allows(0, false, true));
        assertTrue(SafetyBoxReadOnlyPolicy.allows(3, true, false));
        assertTrue(SafetyBoxReadOnlyPolicy.allows(4, true, false));
        assertTrue(SafetyBoxReadOnlyPolicy.allows(5, false, false));

        assertFalse(SafetyBoxReadOnlyPolicy.allows(0, false, false));
        assertFalse(SafetyBoxReadOnlyPolicy.allows(1, true, false));
        assertFalse(SafetyBoxReadOnlyPolicy.allows(2, true, false));
        assertFalse(SafetyBoxReadOnlyPolicy.allows(3, false, false));
        assertFalse(SafetyBoxReadOnlyPolicy.allows(4, false, false));
        assertFalse(SafetyBoxReadOnlyPolicy.allows(6, true, false));
        assertFalse(SafetyBoxReadOnlyPolicy.allows(7, true, false));
    }
}
