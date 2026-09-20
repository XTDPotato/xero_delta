package com.xtdpotato.xero_delta.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TaczLoadoutTextTest {
    @Test
    void extractsAmmoResourceId() {
        assertEquals("tacz:9x39mm", TaczLoadoutText.ammunitionTokenFromDescriptor(
            "gun_data{ammo_id=tacz:9x39mm, magazine=20}"));
    }

    @Test
    void extractsCaliberText() {
        assertEquals("5.56X45MM", TaczLoadoutText.ammunitionNameFromDescriptor(
            "default_bullet, caliber 5.56x45mm, speed=0.9"));
    }

    @Test
    void ignoresDescriptorsWithoutAmmoInformation() {
        assertEquals("", TaczLoadoutText.ammunitionNameFromDescriptor(
            "gun_data{fire_mode=semi, magazine=20}"));
    }
}
