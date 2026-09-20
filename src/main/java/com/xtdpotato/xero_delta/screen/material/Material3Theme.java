package com.xtdpotato.xero_delta.screen.material;

import com.xtdpotato.xero_delta.Config;
import com.xtdpotato.xero_delta.screen.ConfigThemeCatalog;
import com.xtdpotato.xero_delta.trading.TradingHtmlThemeParser;

/** Shared Material 3 design tokens for every Xero Delta screen. */
public final class Material3Theme {
    public static int BACKGROUND = 0xFF101411;
    public static int SURFACE = 0xFF171C19;
    public static int SURFACE_CONTAINER_LOW = 0xFF1B211D;
    public static int SURFACE_CONTAINER = 0xFF202722;
    public static int SURFACE_CONTAINER_HIGH = 0xFF2A322C;
    public static int SURFACE_CONTAINER_HIGHEST = 0xFF343D36;

    public static int PRIMARY = 0xFF8FD9B8;
    public static int ON_PRIMARY = 0xFF003827;
    public static int PRIMARY_CONTAINER = 0xFF24513E;
    public static int ON_PRIMARY_CONTAINER = 0xFFAAF4D3;
    public static int SECONDARY = 0xFFB4CCBE;
    public static int ON_SECONDARY = 0xFF20352B;
    public static int SECONDARY_CONTAINER = 0xFF364B40;
    public static int ON_SECONDARY_CONTAINER = 0xFFD0E8DA;

    public static int TEXT = 0xFFE1E3DF;
    public static int TEXT_MUTED = 0xFFC0C9C2;
    public static int OUTLINE = 0xFF89938C;
    public static int OUTLINE_VARIANT = 0xFF414943;
    public static int ERROR = 0xFFFFB4AB;
    public static int ON_ERROR = 0xFF690005;
    public static int ERROR_CONTAINER = 0xFF93000A;
    public static int SUCCESS = 0xFF8FD9B8;
    public static int WARNING = 0xFFFFD36A;
    public static final int SCRIM = 0x99000000;

    public static final int RADIUS_EXTRA_SMALL = 4;
    public static final int RADIUS_SMALL = 8;
    public static final int RADIUS_MEDIUM = 12;
    public static final int RADIUS_LARGE = 16;
    public static final int RADIUS_FULL = 999;

    public static final int STATE_HOVER_ALPHA = 20;
    public static final int STATE_FOCUS_ALPHA = 30;
    public static final int STATE_PRESS_ALPHA = 30;
    public static final int DISABLED_CONTENT_ALPHA = 97;
    public static final int DISABLED_CONTAINER_ALPHA = 31;

    private Material3Theme() {
    }

    public static void refreshFromConfig() {
        if (!Config.SPEC.isLoaded()) return;
        apply(ConfigThemeCatalog.theme(Config.INSTANCE.configTheme.get()));
    }

    public static void apply(TradingHtmlThemeParser.Theme theme) {
        if (theme == null) return;
        BACKGROUND = opaque(theme.background());
        SURFACE = opaque(mix(theme.background(), theme.panel(), 0.62F));
        SURFACE_CONTAINER_LOW = opaque(theme.panel());
        SURFACE_CONTAINER = opaque(mix(theme.panel(), theme.panelAlt(), 0.42F));
        SURFACE_CONTAINER_HIGH = opaque(theme.panelAlt());
        SURFACE_CONTAINER_HIGHEST = opaque(mix(theme.panelAlt(), theme.border(), 0.35F));

        PRIMARY = opaque(theme.accent());
        ON_PRIMARY = contrast(PRIMARY);
        PRIMARY_CONTAINER = opaque(mix(theme.panelAlt(), theme.accent(), 0.30F));
        ON_PRIMARY_CONTAINER = contrast(PRIMARY_CONTAINER);
        SECONDARY = opaque(mix(theme.muted(), theme.accent(), 0.22F));
        ON_SECONDARY = contrast(SECONDARY);
        SECONDARY_CONTAINER = opaque(mix(theme.panelAlt(), theme.accent(), 0.16F));
        ON_SECONDARY_CONTAINER = opaque(theme.text());

        TEXT = opaque(theme.text());
        TEXT_MUTED = opaque(theme.muted());
        OUTLINE = opaque(theme.border());
        OUTLINE_VARIANT = opaque(mix(theme.panelAlt(), theme.border(), 0.55F));
        ERROR = opaque(theme.danger());
        ON_ERROR = contrast(ERROR);
        ERROR_CONTAINER = opaque(mix(theme.panelAlt(), theme.danger(), 0.35F));
        SUCCESS = PRIMARY;
        WARNING = opaque(mix(0xFFFFC107, theme.accent(), 0.18F));
    }

    private static int opaque(int color) {
        return 0xFF000000 | color & 0x00FFFFFF;
    }

    private static int contrast(int color) {
        int r = color >>> 16 & 255;
        int g = color >>> 8 & 255;
        int b = color & 255;
        return r * 299 + g * 587 + b * 114 >= 150000 ? 0xFF101410 : 0xFFFFFFFF;
    }

    public static int alpha(int color, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (color & 0x00FFFFFF);
    }

    public static int stateLayer(int contentColor, int alpha) {
        return alpha(contentColor, alpha);
    }

    public static int mix(int from, int to, float progress) {
        float p = Math.max(0.0F, Math.min(1.0F, progress));
        int a = Math.round(((from >>> 24) & 255) + (((to >>> 24) & 255) - ((from >>> 24) & 255)) * p);
        int r = Math.round(((from >>> 16) & 255) + (((to >>> 16) & 255) - ((from >>> 16) & 255)) * p);
        int g = Math.round(((from >>> 8) & 255) + (((to >>> 8) & 255) - ((from >>> 8) & 255)) * p);
        int b = Math.round((from & 255) + ((to & 255) - (from & 255)) * p);
        return a << 24 | r << 16 | g << 8 | b;
    }
}


