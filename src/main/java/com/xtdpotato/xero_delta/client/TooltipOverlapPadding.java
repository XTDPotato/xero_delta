package com.xtdpotato.xero_delta.client;

import java.util.List;

/** Calculates padding requested by inline tooltip visuals that draw outside their declared row. */
public final class TooltipOverlapPadding {
    public static final Padding NONE = new Padding(0, 0, 0);

    private TooltipOverlapPadding() {
    }

    public record Metric(int width, int height) {
    }

    public record Padding(int left, int top, int bottom) {
    }

    public static Padding resolve(List<Metric> precedingComponents, int titleHeight) {
        int left = 0;
        int top = 0;
        int bottom = 0;
        for (Metric metric : precedingComponents) {
            if (metric.width() >= 0) continue;

            // Inline model components use a negative width to stay out of the
            // tooltip width calculation while drawing into the title row.
            int visualSpan = Math.max(16, -metric.width() - 6);
            int candidateLeft = Math.max(0, -metric.width() - 2);
            int candidateTop = 2;
            int declaredLead = Math.max(0, metric.height()) + 2;
            int candidateBottom = Math.max(0,
                visualSpan + 5 - (declaredLead + Math.max(0, titleHeight) + candidateTop));
            left = Math.max(left, candidateLeft);
            top = Math.max(top, candidateTop);
            bottom = Math.max(bottom, candidateBottom);
        }
        return left == 0 && top == 0 && bottom == 0 ? NONE : new Padding(left, top, bottom);
    }
}
