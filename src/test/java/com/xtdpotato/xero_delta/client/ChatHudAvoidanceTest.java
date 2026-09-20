package com.xtdpotato.xero_delta.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChatHudAvoidanceTest {
    @Test
    void movesLowerLeftChatAboveTheDefaultHealthPanel() {
        assertEquals(19, ChatHudAvoidance.offset(240, 320, 108,
            new StatusEffectHudRenderer.Bounds(8, 187, 226, 48)));
    }

    @Test
    void ignoresHudOutsideTheChatColumnOrVerticalStack() {
        assertEquals(0, ChatHudAvoidance.offset(240, 180, 108,
            new StatusEffectHudRenderer.Bounds(200, 187, 226, 48)));
        assertEquals(0, ChatHudAvoidance.offset(240, 320, 108,
            new StatusEffectHudRenderer.Bounds(8, 20, 226, 40)));
    }
}
