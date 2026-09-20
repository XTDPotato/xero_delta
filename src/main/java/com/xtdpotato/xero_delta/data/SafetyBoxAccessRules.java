package com.xtdpotato.xero_delta.data;

/** Shared safety-box entitlement rules that do not depend on a loaded game registry. */
public final class SafetyBoxAccessRules {
    public static final String BASIC_BOX_ID = "xero_delta:safety_box_2x1";

    private SafetyBoxAccessRules() {}

    public static boolean isPermanent(String itemId) {
        return BASIC_BOX_ID.equals(itemId);
    }
}
