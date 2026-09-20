package com.xtdpotato.xero_delta.client;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EffectIntroQueueTest {
    @Test
    void introAnimationUsesConfiguredDefaultPhases() {
        assertEquals(200L, EffectIntroQueue.DEFAULT_FADE_IN_MS);
        assertEquals(1_000L, EffectIntroQueue.DEFAULT_HOLD_MS);
        assertEquals(250L, EffectIntroQueue.DEFAULT_FADE_OUT_MS);
        assertEquals(1_450L, EffectIntroQueue.DURATION_MS);
    }

    @Test
    void initialSnapshotDoesNotReplayEveryExistingEffect() {
        EffectIntroQueue<String> queue = new EffectIntroQueue<>();
        var frame = queue.update(List.of("speed", "strength"), 0L);
        assertFalse(frame.hasActive());
        assertTrue(frame.withheld().isEmpty());
    }

    @Test
    void newEffectsStayHiddenUntilTheirAnimationCompletes() {
        EffectIntroQueue<String> queue = new EffectIntroQueue<>();
        queue.update(List.of("speed"), 0L);
        var start = queue.update(List.of("speed", "bleeding"), 100L);
        assertEquals("bleeding", start.active());
        assertTrue(start.withheld().contains("bleeding"));

        var beforeEnd = queue.update(List.of("speed", "bleeding"),
            100L + EffectIntroQueue.DURATION_MS - 1L);
        assertEquals("bleeding", beforeEnd.active());
        assertTrue(beforeEnd.withheld().contains("bleeding"));

        var ended = queue.update(List.of("speed", "bleeding"),
            100L + EffectIntroQueue.DURATION_MS);
        assertFalse(ended.hasActive());
        assertFalse(ended.withheld().contains("bleeding"));
    }

    @Test
    void stackedEffectsAnimateStrictlyInInsertionOrder() {
        EffectIntroQueue<String> queue = new EffectIntroQueue<>();
        queue.update(List.of(), 0L);
        var first = queue.update(List.of("wound", "weakness"), 100L);
        assertEquals("wound", first.active());
        assertEquals(2, first.withheld().size());

        var second = queue.update(List.of("wound", "weakness"),
            100L + EffectIntroQueue.DURATION_MS);
        assertEquals("weakness", second.active());
        assertFalse(second.withheld().contains("wound"));
        assertTrue(second.withheld().contains("weakness"));

        var complete = queue.update(List.of("wound", "weakness"),
            100L + EffectIntroQueue.DURATION_MS * 2L);
        assertFalse(complete.hasActive());
        assertTrue(complete.withheld().isEmpty());
    }

    @Test
    void removedPendingEffectIsSkipped() {
        EffectIntroQueue<String> queue = new EffectIntroQueue<>();
        queue.update(List.of(), 0L);
        queue.update(List.of("a", "b"), 10L);
        var frame = queue.update(List.of("a"), 10L + EffectIntroQueue.DURATION_MS);
        assertFalse(frame.hasActive());
        assertTrue(frame.withheld().isEmpty());
    }

    @Test
    void animationFadesInThenShrinksAndFadesOut() {
        assertEquals(0.0F, EffectIntroQueue.alpha(0.0F), 0.001F);
        float fadeInEnd = 200.0F / 1_450.0F;
        float fadeOutStart = 1_200.0F / 1_450.0F;
        assertEquals(1.0F, EffectIntroQueue.alpha(fadeInEnd), 0.001F);
        assertEquals(1.0F, EffectIntroQueue.scale(fadeOutStart), 0.001F);
        assertTrue(EffectIntroQueue.alpha(0.9F) < 1.0F);
        assertTrue(EffectIntroQueue.scale(0.9F) < 1.0F);
        assertEquals(0.0F, EffectIntroQueue.alpha(1.0F), 0.001F);
        assertEquals(0.35F, EffectIntroQueue.scale(1.0F), 0.001F);
    }
    @Test
    void customDurationControlsWhenTheQueuedEffectBecomesVisible() {
        EffectIntroQueue<String> queue = new EffectIntroQueue<>();
        queue.update(List.of(), 0L, 400L);
        queue.update(List.of("speed"), 100L, 400L);
        assertTrue(queue.update(List.of("speed"), 499L, 400L).hasActive());
        assertFalse(queue.update(List.of("speed"), 500L, 400L).hasActive());
    }

    @Test
    void independentPhasesControlFadeAndQueueDuration() {
        assertEquals(1_450L, EffectIntroQueue.totalDuration(200L, 1_000L, 250L));
        assertEquals(0.5F, EffectIntroQueue.alpha(0.1F, 200L, 600L, 200L), 0.001F);
        assertEquals(1.0F, EffectIntroQueue.alpha(0.5F, 200L, 600L, 200L), 0.001F);
        assertEquals(0.5F, EffectIntroQueue.alpha(0.9F, 200L, 600L, 200L), 0.001F);
    }
}
