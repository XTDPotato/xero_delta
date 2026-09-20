package com.xtdpotato.xero_delta.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HealthValueAnimatorTest {
    @Test
    void completesEachHealthChangeWithinHalfASecond() {
        HealthValueAnimator animator = new HealthValueAnimator();
        assertEquals(0.10F, animator.update(0.10F, 0L, 1_000L), 0.0001F);
        assertEquals(0.10F, animator.update(0.14F, 100L, 1_000L), 0.0001F);
        assertEquals(0.12F, animator.update(0.14F, 350L, 1_000L), 0.0001F);
        assertEquals(0.14F, animator.update(0.14F, 600L, 1_000L), 0.0001F);
    }

    @Test
    void interruptionStartsFromPreviousActualHealthInsteadOfVisualValue() {
        HealthValueAnimator animator = new HealthValueAnimator();
        animator.update(0.10F, 0L, 500L);
        animator.update(0.14F, 100L, 500L);
        assertEquals(0.12F, animator.update(0.14F, 350L, 500L), 0.0001F);

        assertEquals(0.14F, animator.update(0.12F, 350L, 500L), 0.0001F);
        assertEquals(0.13F, animator.update(0.12F, 600L, 500L), 0.0001F);
        assertEquals(0.12F, animator.update(0.12F, 850L, 500L), 0.0001F);
    }

    @Test
    void newSnapshotsReplaceOldAnimationsWithoutAQueue() {
        HealthValueAnimator animator = new HealthValueAnimator();
        animator.update(0.30F, 0L, 500L);
        animator.update(0.80F, 50L, 500L);
        animator.update(0.40F, 100L, 500L);
        assertEquals(0.80F, animator.update(0.40F, 100L, 500L), 0.0001F);
        assertEquals(0.40F, animator.update(0.40F, 600L, 500L), 0.0001F);
    }
}
