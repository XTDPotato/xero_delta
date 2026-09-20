package com.xtdpotato.xero_delta.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RescueRequestClientStateTest {
    @Test
    void pulseProgressExpandsAndClamps() {
        assertEquals(0.0F, RescueRequestClientState.progress(-1L), 0.001F);
        assertEquals(0.5F, RescueRequestClientState.progress(600L), 0.001F);
        assertEquals(1.0F, RescueRequestClientState.progress(1_200L), 0.001F);
        assertEquals(1.0F, RescueRequestClientState.progress(1_500L), 0.001F);
    }
}
