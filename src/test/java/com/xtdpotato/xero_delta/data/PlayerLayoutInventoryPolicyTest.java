package com.xtdpotato.xero_delta.data;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerLayoutInventoryPolicyTest {
    @Test
    void disablesOnlyTheTwentySevenMainInventorySlots() {
        assertFalse(PlayerLayoutInventoryPolicy.isDisabledMainSlot(8));
        assertTrue(PlayerLayoutInventoryPolicy.isDisabledMainSlot(9));
        assertTrue(PlayerLayoutInventoryPolicy.isDisabledMainSlot(35));
        assertFalse(PlayerLayoutInventoryPolicy.isDisabledMainSlot(36));
    }

    @Test
    void separatesEquipmentKeysOneToFourFromPocketKeysFiveToNine() {
        assertTrue(PlayerLayoutInventoryPolicy.isEquipmentHotbarSlot(0));
        assertTrue(PlayerLayoutInventoryPolicy.isEquipmentHotbarSlot(3));
        assertFalse(PlayerLayoutInventoryPolicy.isEquipmentHotbarSlot(4));
        assertFalse(PlayerLayoutInventoryPolicy.isPocketHotbarSlot(3));
        assertTrue(PlayerLayoutInventoryPolicy.isPocketHotbarSlot(4));
        assertTrue(PlayerLayoutInventoryPolicy.isPocketHotbarSlot(8));
        assertFalse(PlayerLayoutInventoryPolicy.isPocketHotbarSlot(9));
    }

    @Test
    void protectsOnlyTheFourthHudSlotOnDeath() {
        assertFalse(PlayerLayoutInventoryPolicy.isProtectedOnDeath(2));
        assertTrue(PlayerLayoutInventoryPolicy.isProtectedOnDeath(3));
        assertFalse(PlayerLayoutInventoryPolicy.isProtectedOnDeath(4));
    }

    @Test
    void allowsContainerQuickMoveIntoPlayerStorageButBlocksInventoryMenu() {
        assertFalse(PlayerLayoutInventoryPolicy.blocksQuickMove(false, false));
        assertTrue(PlayerLayoutInventoryPolicy.blocksQuickMove(true, true));
        assertFalse(PlayerLayoutInventoryPolicy.blocksQuickMove(true, false));
    }

    @Test
    void pocketsAcceptOnlyOneByOneItems() {
        assertTrue(PlayerLayoutSlotRules.isPocketSizeAllowed(new ItemSize(1, 1)));
        assertFalse(PlayerLayoutSlotRules.isPocketSizeAllowed(new ItemSize(1, 2)));
        assertFalse(PlayerLayoutSlotRules.isPocketSizeAllowed(new ItemSize(2, 1)));
        assertFalse(PlayerLayoutSlotRules.isPocketSizeAllowed(new ItemSize(2, 2)));
    }
}
