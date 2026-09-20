package com.xtdpotato.xero_delta.trading;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TradingPriceSpecTest {
    @Test
    void keepsPlainPositiveNumbersAsAbsolutePrices() {
        TradingPriceSpec spec = TradingPriceSpec.parse("30_000").orElseThrow();
        assertEquals(TradingPriceSpec.Mode.ABSOLUTE, spec.mode());
        assertEquals(30_000L, spec.resolve(8_000L, new Random(1L)));
    }

    @Test
    void addsSignedOffsetsToTheItemsOwnValue() {
        TradingPriceSpec positive = TradingPriceSpec.parse("+1000").orElseThrow();
        TradingPriceSpec negative = TradingPriceSpec.parse("-1200").orElseThrow();
        assertEquals(TradingPriceSpec.Mode.OFFSET, positive.mode());
        assertEquals(9_000L, positive.resolve(8_000L, new Random(1L)));
        assertEquals(6_800L, negative.resolve(8_000L, new Random(1L)));
    }

    @Test
    void resolvesInclusiveOffsetRangesAndNormalizesTheirOrder() {
        TradingPriceSpec spec = TradingPriceSpec.parse("2000..-1200").orElseThrow();
        assertEquals(-1_200L, spec.minimum());
        assertEquals(2_000L, spec.maximum());
        Random random = new Random(42L);
        for (int index = 0; index < 200; index++) {
            long price = spec.resolve(8_000L, random);
            assertTrue(price >= 6_800L && price <= 10_000L);
        }
    }

    @Test
    void rejectsMalformedOrOutOfRangePriceExpressions() {
        assertTrue(TradingPriceSpec.parse("").isEmpty());
        assertTrue(TradingPriceSpec.parse("abc").isEmpty());
        assertTrue(TradingPriceSpec.parse("1..").isEmpty());
        assertTrue(TradingPriceSpec.parse("0").isEmpty());
        assertTrue(TradingPriceSpec.parse("+2000000001").isEmpty());
    }

    @Test
    void clampsRelativePricesToTheCurrencyBounds() {
        TradingPriceSpec high = TradingPriceSpec.parse("+1000").orElseThrow();
        TradingPriceSpec low = TradingPriceSpec.parse("-2000000000").orElseThrow();
        assertEquals(TradingRules.MAX_CURRENCY,
            high.resolve(TradingRules.MAX_CURRENCY, new Random(1L)));
        assertEquals(1L, low.resolve(100L, new Random(1L)));
    }
}
