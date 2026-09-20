package com.xtdpotato.xero_delta.trading;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TradingSourceAddressTest {
    private static final String FINGERPRINT = "35f65b7f-47a2-3f4e-9bf0-58530cbbe12a";

    @Test
    void roundTripsInventoryBackpackAddressWithFingerprint() {
        TradingSourceAddress expected = TradingSourceAddress.inventory(7, 31, FINGERPRINT);

        assertEquals(expected, TradingSourceAddress.parse(expected.encode()));
    }

    @Test
    void roundTripsCurioBackpackAddressWithFingerprint() {
        TradingSourceAddress expected = TradingSourceAddress.curio("back", 0, 18, FINGERPRINT);

        assertEquals(expected, TradingSourceAddress.parse(expected.encode()));
    }

    @Test
    void acceptsLegacySlotOnlyAddresses() {
        assertEquals(TradingSourceAddress.inventory(2, 9, ""),
            TradingSourceAddress.parse("bag|inv|2|9"));
        assertEquals(TradingSourceAddress.curio("back", 1, 4, ""),
            TradingSourceAddress.parse("bag|curio|back|1|4"));
    }

    @Test
    void roundTripsAccessoriesCarrierAddress() {
        TradingSourceAddress expected =
            TradingSourceAddress.curio("@accessories", 2, 14, FINGERPRINT);
        assertEquals(expected, TradingSourceAddress.parse(expected.encode()));
    }

    @Test
    void roundTripsTravelersAttachmentAddress() {
        TradingSourceAddress expected =
            TradingSourceAddress.curio("@travelers_attachment", 0, 8, FINGERPRINT);
        assertEquals(expected, TradingSourceAddress.parse(expected.encode()));
    }

    @Test
    void rejectsMalformedOrAmbiguousAddresses() {
        assertNull(TradingSourceAddress.parse("bag|inv|-1|4|" + FINGERPRINT));
        assertNull(TradingSourceAddress.parse("bag|curio|back|bad|4|" + FINGERPRINT));
        assertNull(TradingSourceAddress.parse("bag|curio|back|0|4|finger|print"));
    }
}
