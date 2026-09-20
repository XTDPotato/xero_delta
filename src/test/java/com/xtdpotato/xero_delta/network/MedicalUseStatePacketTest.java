package com.xtdpotato.xero_delta.network;

import com.xtdpotato.xero_delta.item.ModItems;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MedicalUseStatePacketTest {
    @Test
    void roundTripsReservedSourceAndExactStackComponents() {
        var stack = ModItems.BATTLEFIELD_MEDICAL_KIT.get().getDefaultInstance();
        stack.setDamageValue(50);
        var sent = MedicalUseStatePacket.active(stack, 20, 100, "player|4");
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(),
            RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
        try {
            MedicalUseStatePacket.STREAM_CODEC.encode(buffer, sent);
            var received = MedicalUseStatePacket.STREAM_CODEC.decode(buffer);
            assertEquals(sent.sourceId(), received.sourceId());
            assertEquals(20, received.remainingTicks());
            assertTrue(ItemStack.isSameItemSameComponents(stack, received.displayStack()));
            assertFalse(buffer.isReadable());
            MedicalUseStatePacket.STREAM_CODEC.encode(buffer, MedicalUseStatePacket.inactive());
            var inactive = MedicalUseStatePacket.STREAM_CODEC.decode(buffer);
            assertFalse(inactive.active());
            assertTrue(inactive.displayStack().isEmpty());
        } finally {
            buffer.release();
        }
    }
}
