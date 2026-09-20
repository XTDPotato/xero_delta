package com.xtdpotato.xero_delta.client;

import net.neoforged.fml.ModList;

import java.lang.reflect.Method;

/** Optional client bridge for cpw's Inventory Sorter. */
public final class InventorySorterCompat {
    private static final String MOD_ID = "inventorysorter";

    private InventorySorterCompat() {
    }

    /**
     * Custom Delta storage cells are not vanilla {@code Slot}s, so Inventory
     * Sorter's normal screen event cannot see them. Mirror its default middle
     * click action only while the mod and its sorting module are enabled.
     */
    public static boolean middleClickSortingEnabled() {
        if (!ModList.get().isLoaded(MOD_ID)) return false;
        try {
            Class<?> actionClass = Class.forName("cpw.mods.inventorysorter.Action");
            @SuppressWarnings({"rawtypes", "unchecked"})
            Object sort = Enum.valueOf((Class<? extends Enum>) actionClass.asSubclass(Enum.class), "SORT");
            Method active = actionClass.getMethod("isActive");
            Object value = active.invoke(sort);
            return !(value instanceof Boolean enabled) || enabled;
        } catch (ReflectiveOperationException | LinkageError ignored) {
            // Older releases use the same default binding but may not expose
            // Action#isActive. Loaded is the safest compatibility fallback.
            return true;
        }
    }
}
