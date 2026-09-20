package com.xtdpotato.xero_delta.client;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class WeaponSlotTextureResolverTest {
    @Test
    void exactGunIdBeatsUnrelatedSlotTexture() {
        String descriptor = "components={gun_id=delta:ash12} tacz gunitem";
        int exact = WeaponSlotTextureResolver.score(descriptor,
            ResourceLocation.fromNamespaceAndPath("delta",
                "textures/gun/slot/ash12.png"));
        int unrelated = WeaponSlotTextureResolver.score(descriptor,
            ResourceLocation.fromNamespaceAndPath("delta",
                "textures/gun/slot/m4a1.png"));
        assertTrue(exact > unrelated);
        assertTrue(exact > 0);
    }
}
