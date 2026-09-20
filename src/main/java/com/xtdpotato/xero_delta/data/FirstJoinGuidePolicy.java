package com.xtdpotato.xero_delta.data;

/** Pure decision policy for offering the world setup guide to a player. */
public final class FirstJoinGuidePolicy {
    private FirstJoinGuidePolicy() {
    }

    public static boolean shouldShow(boolean hasAdminPermission,
                                     boolean hasItemRules,
                                     boolean hasPriceRules,
                                     boolean layoutEnabled) {
        return hasAdminPermission && !hasItemRules && !hasPriceRules && !layoutEnabled;
    }
}