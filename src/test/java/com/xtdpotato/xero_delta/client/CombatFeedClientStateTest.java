package com.xtdpotato.xero_delta.client;

import com.xtdpotato.xero_delta.network.CombatFeedPacket;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CombatFeedClientStateTest {
    @Test
    void entriesExpireAfterSixSeconds() {
        CombatFeedClientState state = CombatFeedClientState.INSTANCE;
        state.clear();
        state.add(new CombatFeedPacket(CombatFeedPacket.RESCUED, "A", "B",
            true, true, false, ItemStack.EMPTY));
        long created = state.activeEntries().getFirst().createdAtMs();
        assertEquals(1, state.activeEntries(created + 5_999L).size());
        assertEquals(0, state.activeEntries(created + 6_000L).size());
    }
}
