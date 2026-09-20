package com.xtdpotato.xero_delta.data;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuiltinItemRuleCatalogTest {
    @Test
    void keepsEveryPainkillerAtOneCell() {
        assertEquals(ItemSize.ONE,
            BuiltinItemRuleCatalog.fixedSizeId("xero_delta:extended_release_painkillers"));
        assertEquals(ItemSize.ONE,
            BuiltinItemRuleCatalog.fixedSizeId("other_mod:military_painkiller"));
        assertNull(BuiltinItemRuleCatalog.fixedSizeId("xero_delta:field_first_aid_kit"));
    }

    @Test
    void keepsIronCompressionValuesEconomicallySeparated() {
        assertEquals(550L, MaterialValueDefaults.IRON_NUGGET);
        assertEquals(40_000L, MaterialValueDefaults.IRON_INGOT);
        assertEquals(360_000L, MaterialValueDefaults.IRON_BLOCK);
        assertEquals(50_000L, MaterialValueDefaults.GOLD_INGOT);
        assertEquals(10_000L, MaterialValueDefaults.COPPER_INGOT);
        assertEquals(26_666L, MaterialValueDefaults.RAW_IRON);
        assertEquals(33_333L, MaterialValueDefaults.RAW_GOLD);
        assertEquals(90_000L, MaterialValueDefaults.DIAMOND);
        assertEquals(10_000L, MaterialValueDefaults.AMETHYST_SHARD);
        assertEquals(160_000L, MaterialValueDefaults.TREASURE_MAP);
    }

    @Test
    void keepsDragonEggAtOneHundredMillion() {
        assertEquals(100_000_000L, MaterialValueDefaults.DRAGON_EGG);
    }

    @Test
    void keepsAuthoredMedicalAndRepairProductRules() {
        List<ProductRule> products = List.of(
            new ProductRule("tactical_quick_release_surgery_kit", 2, 1, "green", 9_300L),
            new ProductRule("advanced_armor_repair_combo", 2, 2, "red", 370_000L),
            new ProductRule("advanced_helmet_repair_combo", 2, 2, "red", 350_000L),
            new ProductRule("battlefield_medical_kit", 2, 2, "gold", 240_000L),
            new ProductRule("dek_field_surgery_kit", 3, 1, "purple", 30_000L),
            new ProductRule("field_first_aid_kit", 2, 1, "blue", 14_000L),
            new ProductRule("homemade_armor_repair_kit", 2, 1, "blue", 20_000L),
            new ProductRule("homemade_helmet_repair_kit", 2, 1, "blue", 16_000L),
            new ProductRule("outdoor_medical_kit", 3, 1, "purple", 79_000L),
            new ProductRule("precision_armor_repair_kit", 2, 2, "gold", 180_000L),
            new ProductRule("precision_helmet_repair_kit", 3, 1, "gold", 130_000L),
            new ProductRule("standard_armor_repair_kit", 3, 1, "purple", 74_000L),
            new ProductRule("standard_helmet_repair_kit", 2, 1, "purple", 60_000L)
        );

        for (ProductRule product : products) {
            BuiltinItemRuleCatalog.Rule actual = BuiltinItemRuleCatalog.explicitId(
                "xero_delta:" + product.path());
            assertEquals(new ItemSize(product.width(), product.height()), actual.size(), product.path());
            assertEquals(product.quality(), actual.quality(), product.path());
            assertEquals(product.price(), actual.price(), product.path());
            assertTrue(actual.stretchTexture(), product.path());
            assertTrue(actual.fixedAutomaticPrice(), product.path());
        }
    }

    private record ProductRule(String path, int width, int height, String quality, long price) {
    }
}
