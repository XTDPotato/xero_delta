package com.xtdpotato.xero_delta.client;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TooltipOverlapPaddingTest {
    @Test
    void ignoresOrdinaryTooltipRows() {
        assertEquals(TooltipOverlapPadding.NONE, TooltipOverlapPadding.resolve(List.of(
            new TooltipOverlapPadding.Metric(120, 10),
            new TooltipOverlapPadding.Metric(40, 20)
        ), 10));
    }

    @Test
    void reservesInlineModelSpaceFromNegativeWidthContract() {
        var padding = TooltipOverlapPadding.resolve(List.of(
            new TooltipOverlapPadding.Metric(-28, 4)
        ), 10);

        assertEquals(26, padding.left());
        assertEquals(2, padding.top());
        assertEquals(9, padding.bottom());
    }

    @Test
    void usesLargestInlineObstacleWithoutSummingOverlays() {
        var padding = TooltipOverlapPadding.resolve(List.of(
            new TooltipOverlapPadding.Metric(-20, 4),
            new TooltipOverlapPadding.Metric(-36, 5)
        ), 10);

        assertEquals(34, padding.left());
        assertEquals(2, padding.top());
        assertEquals(16, padding.bottom());
    }
}
