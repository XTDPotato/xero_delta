package com.xtdpotato.xero_delta.client;

import net.minecraft.Util;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Independent, overlapping world-space damage-direction animation instances. */
public final class DamageDirectionClientState {
    public static final DamageDirectionClientState INSTANCE = new DamageDirectionClientState();
    public static final long DURATION_MS = 900L;
    private static final int MAX_EVENTS = 12;
    private final List<Hit> hits = new ArrayList<>();

    private DamageDirectionClientState() {
    }

    public synchronized void add(double directionX, double directionZ, float damage) {
        double length = Math.sqrt(directionX * directionX + directionZ * directionZ);
        if (!Double.isFinite(length) || length < 1.0E-4D) return;
        if (hits.size() >= MAX_EVENTS) hits.remove(0);
        hits.add(new Hit(directionX / length, directionZ / length,
            Math.max(0.0F, damage), Util.getMillis()));
    }

    public synchronized List<Sample> samples() {
        long now = Util.getMillis();
        List<Sample> result = new ArrayList<>(hits.size());
        Iterator<Hit> iterator = hits.iterator();
        while (iterator.hasNext()) {
            Hit hit = iterator.next();
            float progress = Math.max(0.0F,
                Math.min(1.0F, (now - hit.startedAtMs) / (float) DURATION_MS));
            if (progress >= 1.0F) {
                iterator.remove();
            } else {
                result.add(new Sample(hit.directionX, hit.directionZ,
                    hit.damage, progress));
            }
        }
        return List.copyOf(result);
    }

    private record Hit(double directionX, double directionZ,
                       float damage, long startedAtMs) {
    }

    public record Sample(double directionX, double directionZ,
                         float damage, float progress) {
    }
}
