package com.xtdpotato.xero_delta.client;

/** Shared geometry for rendering and input in the creative rule editor. */
public final class CreativeRuleEditorLayout {
    public static final int WIDTH = 190;
    public static final int HEIGHT = 240;
    public static final int MIN_HEIGHT = 116;
    public static final int BODY_TOP = 76;
    public static final int FOOTER_HEIGHT = 30;

    public enum Tab {
        QUALITY("quality", 68), SIZE("size", 134), PRICE("price", 104);

        public final String key;
        public final int contentHeight;

        Tab(String key, int contentHeight) {
            this.key = key;
            this.contentHeight = contentHeight;
        }
    }

    private CreativeRuleEditorLayout() {}

    public static boolean eligible(boolean creativeScreen, boolean creativePlayer, boolean operator) {
        return creativeScreen && creativePlayer && operator;
    }

    public static int footerTop(int height) {
        return Math.max(BODY_TOP, height - FOOTER_HEIGHT);
    }

    public static int maxScroll(Tab tab, int height) {
        return Math.max(0, tab.contentHeight - Math.max(0, footerTop(height) - BODY_TOP));
    }

    public static int clampScroll(Tab tab, int height, int scroll) {
        return Math.max(0, Math.min(maxScroll(tab, height), scroll));
    }

    public static int sliderTop(int index) {
        return switch (index) {
            case 0 -> 4;
            case 1 -> 28;
            default -> 112;
        };
    }
}
