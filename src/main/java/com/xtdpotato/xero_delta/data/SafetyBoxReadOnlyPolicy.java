package com.xtdpotato.xero_delta.data;

/** Pure action policy for expired safety boxes: removal is allowed, insertion is not. */
public final class SafetyBoxReadOnlyPolicy {
    private SafetyBoxReadOnlyPolicy() {}

    public static boolean allows(int action, boolean carriedEmpty, boolean shiftToInventory) {
        return switch (action) {
            case 0 -> carriedEmpty || shiftToInventory;
            case 3, 4 -> carriedEmpty;
            case 5 -> true;
            default -> false;
        };
    }
}
