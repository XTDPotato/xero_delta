package com.xtdpotato.xero_delta.network;

import com.xtdpotato.xero_delta.menu.CorpseMenu;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InventorySourceQuickMovePacketTest {
    @Test
    void onlyWornCorpseCarriersUseTheExplicitQuickMoveException() {
        assertEquals(CorpseMenu.CHEST_RIG_SLOT,
            InventorySourceQuickMovePacket.corpseCarrierSlot("container|10"));
        assertEquals(CorpseMenu.BACKPACK_SLOT,
            InventorySourceQuickMovePacket.corpseCarrierSlot("container|11"));
        for (String source : new String[]{null, "", "container|12", "container|-1",
            "container|010", "container|10|1", "curio_slot|backpack|0", "corpse_storage|1|10|0"}) {
            assertEquals(-1, InventorySourceQuickMovePacket.corpseCarrierSlot(source));
        }
    }
}
