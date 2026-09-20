package com.xtdpotato.xero_delta.data;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FirstJoinGuidePolicyTest {
    @Test
    void showsForAdministratorWhenWorldHasNoConfiguration() {
        assertTrue(FirstJoinGuidePolicy.shouldShow(true, false, false, false));
    }

    @Test
    void doesNotShowToPlayersWithoutConfigurationPermission() {
        assertFalse(FirstJoinGuidePolicy.shouldShow(false, false, false, false));
    }

    @Test
    void anyExistingConfigurationSuppressesTheGuide() {
        assertFalse(FirstJoinGuidePolicy.shouldShow(true, true, false, false));
        assertFalse(FirstJoinGuidePolicy.shouldShow(true, false, true, false));
        assertFalse(FirstJoinGuidePolicy.shouldShow(true, false, false, true));
    }
}