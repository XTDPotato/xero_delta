package com.xtdpotato.xero_delta.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TradingCurrencyFormatterTest {
    @Test
    void keepsValuesBelowOneMillionExact() {
        assertEquals("0", TradingCurrencyFormatter.formatPrice(0L));
        assertEquals("1,000", TradingCurrencyFormatter.formatPrice(1_000L));
        assertEquals("999,999", TradingCurrencyFormatter.formatPrice(999_999L));
    }

    @Test
    void usesKFromOneMillionUntilOneHundredMillion() {
        assertEquals("1000K", TradingCurrencyFormatter.formatPrice(1_000_000L));
        assertEquals("1234K", TradingCurrencyFormatter.formatPrice(1_234_567L));
        assertEquals("9999K", TradingCurrencyFormatter.formatPrice(9_999_999L));
    }

    @Test
    void pricesUseMFromOneHundredMillionAndNeverUseB() {
        assertEquals("10000K", TradingCurrencyFormatter.formatPrice(10_000_000L));
        assertEquals("99999K", TradingCurrencyFormatter.formatPrice(99_999_999L));
        assertEquals("100M", TradingCurrencyFormatter.formatPrice(100_000_000L));
        assertEquals("999M", TradingCurrencyFormatter.formatPrice(999_999_999L));
        assertEquals("1000M", TradingCurrencyFormatter.formatPrice(1_000_000_000L));
    }

    @Test
    void playerBalanceCanUseBillions() {
        assertEquals("999M", TradingCurrencyFormatter.formatBalance(999_999_999L));
        assertEquals("1B", TradingCurrencyFormatter.formatBalance(1_000_000_000L));
        assertEquals("2B", TradingCurrencyFormatter.formatBalance(2_000_000_000L));
    }

    @Test
    void detailValuesAlwaysUseThreeDigitCommaGroups() {
        assertEquals("999,999", TradingCurrencyFormatter.formatDetailed(999_999L));
        assertEquals("1,000,000", TradingCurrencyFormatter.formatDetailed(1_000_000L));
        assertEquals("1,234,567,890", TradingCurrencyFormatter.formatDetailed(1_234_567_890L));
    }
}
