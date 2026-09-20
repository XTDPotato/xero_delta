package com.xtdpotato.xero_delta.item;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ConsumableProfileTest {
    @Test void everyReferenceItemHasConsistentCapacityAndDescription() {
        assertEquals(30, ConsumableProfile.all().size());
        ConsumableProfile.all().forEach((id, profile) -> {
            assertFalse(profile.description().isBlank(), id);
            assertTrue(profile.startupTicks() >= 50, id);
            assertTrue(profile.weight() > 0, id);
            assertEquals("启用时间", profile.effects().getFirst()[0]);
        });
    }
    @Test void registeredMedicinesHaveTheirReferenceCapacity() {
        for (var holder : ModItems.EXTRA_ITEMS) {
            var stack = holder.get().getDefaultInstance();
            if (!(stack.getItem() instanceof TimedUseItem)) continue;
            var profile = ConsumableProfile.get(stack);
            assertNotNull(profile);
            assertEquals(profile.capacity(), stack.getMaxDamage());
        }
    }
    @Test void catKeepsRemainingUsesUntilFourthTreatment() {
        ItemStack stack = ModItems.CAT_TOURNIQUET.get().getDefaultInstance();
        for (int use = 1; use <= 3; use++) {
            MedicalItem.consumeUse(stack);
            assertFalse(stack.isEmpty());
            assertEquals(use, stack.getDamageValue());
            assertEquals(1, stack.getCount());
        }
        MedicalItem.consumeUse(stack);
        assertTrue(stack.isEmpty());
    }
    @Test void surgicalKitsAndPainkillersUseIndividualDurations() {
        assertEquals(100, ModItems.DEK_FIELD_SURGERY_KIT.get().getUseDuration(
            ModItems.DEK_FIELD_SURGERY_KIT.get().getDefaultInstance(), null));
        assertEquals(200, ModItems.SIMPLE_SURGERY_KIT.get().getUseDuration(
            ModItems.SIMPLE_SURGERY_KIT.get().getDefaultInstance(), null));
        assertEquals(360, ConsumableProfile.get("dve_painkillers").effectSeconds());
        assertEquals(240, ConsumableProfile.get("bottled_antibiotics").effectSeconds());
        assertEquals(200, ConsumableProfile.get("extended_release_painkillers").effectSeconds());
    }
    @Test void everyRepairRequiresFourAndHalfSeconds() {
        ConsumableProfile.all().forEach((id, profile) -> {
            if (profile.kind() == ConsumableProfile.Kind.REPAIR) {
                assertEquals(90, profile.startupTicks());
                assertTrue(profile.repairCost() > 0);
                assertTrue(profile.repairCost() <= profile.capacity());
            }
        });
        var kit = ModItems.ADVANCED_ARMOR_REPAIR_COMBO.get();
        assertEquals(90, kit.getUseDuration(kit.getDefaultInstance(), null));
    }
    @Test void otherHealthKitsUseTheSameContinuousHealingUnits() {
        String[] ids = {"field_first_aid_kit","strong_injector","vehicle_first_aid_kit","simple_injector"};
        float[] rates = {20,12,6,5};
        for(int i=0;i<ids.length;i++) {
            var profile=ConsumableProfile.get(ids[i]);
            assertEquals(rates[i], profile.healPerSecond());
            assertEquals(rates[i]/5, MedicalHealthRules.toEntityHealth(profile.healPerSecond(),20),0.0001);
        }
    }
}
