package com.xtdpotato.xero_delta.data;

/** Pure compatibility rule for merging the two legacy change-access flags. */
final class FeatureAccessMigration {
    private FeatureAccessMigration() {}

    static boolean resolve(boolean hasUnifiedFlag, boolean unifiedFlag,
                           boolean legacyKnifeFlag, boolean legacySafetyBoxFlag) {
        return hasUnifiedFlag ? unifiedFlag : legacyKnifeFlag || legacySafetyBoxFlag;
    }
}
