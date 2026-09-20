package com.xtdpotato.xero_delta.client;

import com.xtdpotato.xero_delta.item.ModItems;
import com.xtdpotato.xero_delta.network.MedicalUseStatePacket;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class MedicalUseClientStateTest {
    @Test
    void reservedMedicineRemainsVisibleWithItsComponentsAndProgress() {
        var state = new MedicalUseClientState();
        var stack = ModItems.BATTLEFIELD_MEDICAL_KIT.get().getDefaultInstance();
        stack.setDamageValue(30);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("my kit"));
        state.update(MedicalUseStatePacket.active(stack, 60, 100, "player|4"));
        var entries = state.withReservedEntry(List.of());
        assertEquals(1, entries.size());
        assertTrue(state.isUsing(entries.getFirst()));
        assertEquals(30, entries.getFirst().stack().getDamageValue());
        assertEquals("my kit", entries.getFirst().stack().getHoverName().getString());
        assertEquals(0.4F, state.progress(), 0.001F);
        state.tick();
        assertEquals(0.41F, state.progress(), 0.001F);
        assertEquals(1, state.withReservedEntry(entries).size());
        state.cancelLocally();
        assertTrue(state.withReservedEntry(List.of()).isEmpty());
    }

    @Test
    void completionClearsReservedEntryAndHeldUseDoesNotInventAStorageSource() {
        var state = new MedicalUseClientState();
        var stack = ModItems.ELASTIC_BANDAGE.get().getDefaultInstance();
        state.update(MedicalUseStatePacket.active(stack, 5, 10, "player|4"));
        assertEquals(1, state.withReservedEntry(List.of()).size());
        state.update(MedicalUseStatePacket.inactive());
        assertTrue(state.withReservedEntry(List.of()).isEmpty());
        state.update(MedicalUseStatePacket.active(stack, 5, 10));
        assertTrue(state.withReservedEntry(List.of()).isEmpty());
        for (int index = 0; index < 20; index++) state.tick();
        assertEquals(1.0F, state.progress());
    }
}
