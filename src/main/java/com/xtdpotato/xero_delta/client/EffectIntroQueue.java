package com.xtdpotato.xero_delta.client;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/** Deterministic FIFO gate that keeps new effects out of the permanent HUD until their intro ends. */
public final class EffectIntroQueue<K> {
    public static final long DEFAULT_FADE_IN_MS = 200L;
    public static final long DEFAULT_HOLD_MS = 1_000L;
    public static final long DEFAULT_FADE_OUT_MS = 250L;
    public static final long DURATION_MS = DEFAULT_FADE_IN_MS + DEFAULT_HOLD_MS
        + DEFAULT_FADE_OUT_MS;
    private final LinkedHashSet<K> observed = new LinkedHashSet<>();
    private final LinkedHashSet<K> withheld = new LinkedHashSet<>();
    private final ArrayDeque<K> pending = new ArrayDeque<>();
    private boolean initialized;
    private K active;
    private long activeSince;

    public Frame<K> update(Collection<K> current, long now) {
        return update(current, now, DURATION_MS);
    }

    public Frame<K> update(Collection<K> current, long now, long durationMs) {
        long safeDuration = Math.max(1L, durationMs);
        LinkedHashSet<K> currentSet = new LinkedHashSet<>(current);
        if (!initialized) {
            initialized = true;
            observed.addAll(currentSet);
            return frame(now, safeDuration);
        }

        if (active != null
            && (!currentSet.contains(active) || now - activeSince >= safeDuration)) {
            withheld.remove(active);
            active = null;
        }
        observed.retainAll(currentSet);
        withheld.retainAll(currentSet);
        pending.removeIf(key -> !currentSet.contains(key));

        for (K key : currentSet) {
            if (observed.add(key)) {
                pending.addLast(key);
                withheld.add(key);
            }
        }
        if (active == null) {
            while (!pending.isEmpty()) {
                K candidate = pending.removeFirst();
                if (!currentSet.contains(candidate)) continue;
                active = candidate;
                activeSince = now;
                break;
            }
        }
        return frame(now, safeDuration);
    }

    public Frame<K> update(Collection<K> current, long now, long fadeInMs,
                           long holdMs, long fadeOutMs) {
        return update(current, now, totalDuration(fadeInMs, holdMs, fadeOutMs));
    }

    public void reset() {
        initialized = false;
        observed.clear();
        withheld.clear();
        pending.clear();
        active = null;
        activeSince = 0L;
    }

    private Frame<K> frame(long now, long durationMs) {
        float progress = active == null ? 0.0F
            : Math.max(0.0F, Math.min(1.0F, (now - activeSince) / (float) durationMs));
        return new Frame<>(active, progress, Set.copyOf(withheld));
    }

    public static float alpha(float progress) {
        return alpha(progress, DEFAULT_FADE_IN_MS, DEFAULT_HOLD_MS, DEFAULT_FADE_OUT_MS);
    }

    public static float alpha(float progress, long fadeInMs, long holdMs, long fadeOutMs) {
        float p = Math.max(0.0F, Math.min(1.0F, progress));
        long fadeIn = Math.max(1L, fadeInMs);
        long hold = Math.max(0L, holdMs);
        long fadeOut = Math.max(1L, fadeOutMs);
        float elapsed = p * (fadeIn + hold + fadeOut);
        if (elapsed < fadeIn) return elapsed / fadeIn;
        if (elapsed < fadeIn + hold) return 1.0F;
        return Math.max(0.0F, 1.0F - (elapsed - fadeIn - hold) / fadeOut);
    }

    public static float scale(float progress) {
        return scale(progress, DEFAULT_FADE_IN_MS, DEFAULT_HOLD_MS, DEFAULT_FADE_OUT_MS);
    }

    public static float scale(float progress, long fadeInMs, long holdMs, long fadeOutMs) {
        float p = Math.max(0.0F, Math.min(1.0F, progress));
        long fadeIn = Math.max(1L, fadeInMs);
        long hold = Math.max(0L, holdMs);
        long fadeOut = Math.max(1L, fadeOutMs);
        float elapsed = p * (fadeIn + hold + fadeOut);
        if (elapsed < fadeIn + hold) return 1.0F;
        float fadeProgress = Math.min(1.0F, (elapsed - fadeIn - hold) / fadeOut);
        return 1.0F - 0.65F * fadeProgress;
    }

    public static long totalDuration(long fadeInMs, long holdMs, long fadeOutMs) {
        return Math.max(1L, fadeInMs) + Math.max(0L, holdMs) + Math.max(1L, fadeOutMs);
    }

    public record Frame<K>(K active, float progress, Set<K> withheld) {
        public boolean hasActive() {
            return active != null;
        }
    }
}
