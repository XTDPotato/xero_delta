package com.xtdpotato.xero_delta.client;

import com.xtdpotato.xero_delta.grid.GridBackingStore;
import com.xtdpotato.xero_delta.grid.GridWidget;
import net.minecraft.world.item.ItemStack;

public class SafetyBoxState {
    public static GridWidget gridWidget;
    public static GridBackingStore gridStore;
    public static String menuKey;
    public static ItemStack equippedBox;
    public static int headerX, headerY, headerW, headerH;
    public static boolean dragging;
    public static int dragStartX, dragStartY;
    public static int dragOrigOffX, dragOrigOffY;

    // Snapshot for server-synced grid state
    public static SafetyBoxSnapshot currentSnapshot;

    public static void overwriteSnapshot(SafetyBoxSnapshot snapshot) {
        currentSnapshot = snapshot;
    }

    public static void reset() {
        gridWidget = null;
        gridStore = null;
        menuKey = null;
        equippedBox = ItemStack.EMPTY;
        dragging = false;
        currentSnapshot = null;
    }
}