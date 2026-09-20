package com.xtdpotato.xero_delta.client;

/** Stable squad-number colors shared by teammate HUDs and world markers. */
public final class TeamColorPalette {
    private static final int[] COLORS = {
        0xFF68D4AE, 0xFF6BA8FF, 0xFFFFD36A, 0xFFD5A7FF,
        0xFFFF9F68, 0xFF7DE0D0, 0xFFC1CAC8, 0xFFB4E36D
    };

    private TeamColorPalette() {
    }

    public static int colorForNumber(int number) {
        int index = Math.max(1, number) - 1;
        return COLORS[index % COLORS.length];
    }
}
