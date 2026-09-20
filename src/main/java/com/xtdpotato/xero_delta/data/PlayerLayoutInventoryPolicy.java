package com.xtdpotato.xero_delta.data;

/** Pure slot-routing rules for the restricted Delta inventory layout. */
public final class PlayerLayoutInventoryPolicy {
    public static final int HOTBAR_SIZE = 9;
    public static final int INVENTORY_SIZE = 36;
    public static final int KNIFE_SLOT = 3;

    private PlayerLayoutInventoryPolicy() {}

    public static boolean isDisabledMainSlot(int inventoryIndex) {
        return inventoryIndex >= HOTBAR_SIZE && inventoryIndex < INVENTORY_SIZE;
    }

    public static boolean isEquipmentHotbarSlot(int inventoryIndex) {
        return inventoryIndex >= 0 && inventoryIndex < 4;
    }

    public static boolean isPocketHotbarSlot(int inventoryIndex) {
        return inventoryIndex >= 4 && inventoryIndex < HOTBAR_SIZE;
    }

    public static boolean isProtectedOnDeath(int inventoryIndex) {
        return inventoryIndex == KNIFE_SLOT;
    }

    public static boolean blocksQuickMove(boolean sourceIsPlayerInventory,
                                          boolean inventoryMenu) {
        return inventoryMenu;
    }
}
