package com.xtdpotato.xero_delta.compat;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

/** Optional client probe used to let Better Looting own F while loot is visible. */
public final class BetterLootingClientPriority {
    private static boolean initialized;
    private static Object core;
    private static Method isHudActive;
    private static Method getNearbyItems;

    private BetterLootingClientPriority() {}

    public static boolean hasPickupTarget() {
        initialize();
        if (core == null || isHudActive == null || getNearbyItems == null) return false;
        try {
            if (!(boolean) isHudActive.invoke(core)) return false;
            Object result = getNearbyItems.invoke(core);
            return result instanceof List<?> list && !list.isEmpty();
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return false;
        }
    }

    private static synchronized void initialize() {
        if (initialized) return;
        initialized = true;
        try {
            Class<?> type = Class.forName("com.mohuia.better_looting.client.Core");
            Field instance = type.getField("INSTANCE");
            core = instance.get(null);
            isHudActive = type.getMethod("isHudActive");
            getNearbyItems = type.getMethod("getNearbyItems");
        } catch (ReflectiveOperationException | LinkageError ignored) {
            core = null;
        }
    }
}
