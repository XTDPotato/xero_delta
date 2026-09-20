package com.xtdpotato.xero_delta.data;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeatureAccessMigrationTest {
    @Test
    void eitherLegacyPermissionEnablesUnifiedAccess() {
        assertTrue(FeatureAccessMigration.resolve(false, false, true, false));
        assertTrue(FeatureAccessMigration.resolve(false, false, false, true));
        assertFalse(FeatureAccessMigration.resolve(false, false, false, false));
    }

    @Test
    void explicitUnifiedFlagOverridesLegacyValues() {
        assertFalse(FeatureAccessMigration.resolve(true, false, true, true));
        assertTrue(FeatureAccessMigration.resolve(true, true, false, false));
    }
}
