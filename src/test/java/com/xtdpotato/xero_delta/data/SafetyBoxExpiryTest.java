package com.xtdpotato.xero_delta.data;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SafetyBoxExpiryTest {
    private static final long DAY = 86_400_000L;

    @Test
    void addsNewDurationToRemainingTime() {
        long now = 1_000_000L;
        assertEquals(now + 15L * DAY,
            SafetyBoxExpiry.merge(now + 5L * DAY, now + 10L * DAY, now, Long.MAX_VALUE));
    }

    @Test
    void startsFromNowWhenPreviousUnlockExpired() {
        long now = 1_000_000L;
        assertEquals(now + 10L * DAY,
            SafetyBoxExpiry.merge(now - DAY, now + 10L * DAY, now, Long.MAX_VALUE));
    }

    @Test
    void permanentUnlockCannotBeShortened() {
        long now = 1_000_000L;
        assertEquals(Long.MAX_VALUE,
            SafetyBoxExpiry.merge(Long.MAX_VALUE, now + DAY, now, Long.MAX_VALUE));
    }
}
