package com.xtdpotato.xero_delta.data.size.provider;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TaczItemSizeProviderTest {
    @Test
    void classifiesRepresentativeTaczItems() {
        assertSize(2, 1, "tacz:glock_17 pistol");
        assertSize(4, 2, "tacz:vector submachine_gun");
        assertSize(3, 2, "tacz:uzi submachine_gun");
        assertSize(5, 2, "tacz:ak47 assault_rifle");
        assertSize(5, 2, "tacz:scar_h battle_rifle");
        assertSize(5, 1, "tacz:m870 shotgun");
        assertSize(5, 1, "tacz:modern_kinetic_gun delta:db_12 shotgun");
        assertSize(5, 2, "tacz:s12k shotgun");
        assertSize(5, 1, "tacz:m700 sniper");
        assertSize(6, 2, "tacz:m95 sniper");
        assertSize(6, 2, "tacz:m82 anti_material sniper");
        assertSize(6, 2, "tacz:m107 anti_material sniper");
        assertSize(5, 2, "tacz:m249 light_machine_gun");
        assertSize(6, 2, "tacz:m250 light_machine_gun");
        assertSize(5, 2, "tacz:rpk light_machine_gun");
        assertSize(5, 2, "tacz:rpg rocket_launcher");
        assertSize(5, 1, "tacz:modern_kinetic_gun gun_id=delta:weapons/db_12");
        assertSize(6, 2, "tacz:modern_kinetic_gun gun_id=delta:weapons/m95 ammo=12_gauge extended_magazine");
        assertSize(5, 2, "tacz:modern_kinetic_gun gun_id=delta:weapons/m249 ammo=nato");
        assertSize(1, 1, "tacz:ammo_556 nato bullet");
        assertSize(2, 2, "tacz:drum_magazine");
        assertSize(1, 2, "tacz:extended_magazine");
    }

    @Test
    void classifiesLrTacticalWorkshopMeleeAsOneCell() {
        assertSize(1, 1, "tacz:modern_kinetic_gun lr_tactical:combat_knife melee");
    }

    @Test
    void randomCategorySizesAreStableForTheSameGunId() {
        var first = TaczItemSizeProvider.classifyDescriptor(
            "tacz:modern_kinetic_gun gun_id=delta:sniper/custom_alpha sniper").size();
        var second = TaczItemSizeProvider.classifyDescriptor(
            "different hover text tacz:modern_kinetic_gun gun_id=delta:sniper/custom_alpha sniper").size();
        assertEquals(first, second);
    }

    private static void assertSize(int width, int height, String descriptor) {
        var size = TaczItemSizeProvider.classifyDescriptor(descriptor).size();
        assertEquals(width, size.width());
        assertEquals(height, size.height());
    }
}
