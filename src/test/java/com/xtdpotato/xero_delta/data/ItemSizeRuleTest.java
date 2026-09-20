package com.xtdpotato.xero_delta.data;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemSizeRuleTest {
    @Test
    void automaticSizingRotatesTextureWithoutStretchingIt() {
        ItemSizeRule rule = ItemSizeRule.automatic(new ItemSize(2, 3));

        assertTrue(rule.rotateTexture());
        assertFalse(rule.stretchTexture());
    }

    @Test
    void automaticTexturePolicyRoundTripsThroughPackedRule() {
        ItemSizeRule original = new ItemSizeRule(new ItemSize(2, 3), false, false, 2);

        ItemSizeRule restored = ItemSizeRule.unpack(original.pack());

        assertEquals(original, restored);
    }

    @Test
    void creativeSliderMaximumTenRoundTripsWithoutBeingClampedToNine() {
        ItemSizeRule original = new ItemSizeRule(new ItemSize(10, 10), true, true, 0);

        assertEquals(new ItemSize(10, 10), ItemSizeRule.unpack(original.pack()).size());
    }
}
