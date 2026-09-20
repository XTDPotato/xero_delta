package com.xtdpotato.xero_delta.client;

/** Pure sizing rules shared by the item-detail overlay and its visual editor. */
public final class ItemDetailLayout {
    public static final int BASE_WIDTH = 180;
    public static final int MIN_MANUAL_WIDTH = 120;
    public static final int MIN_MANUAL_HEIGHT = 130;
    public static final int MAX_MANUAL_WIDTH = 360;
    public static final int MAX_MANUAL_HEIGHT = 300;

    public static final int HEADER_TEXT_HEIGHT = 9;
    public static final int HEADER_PADDING = 2;
    public static final int HEADER_HEIGHT = HEADER_TEXT_HEIGHT + HEADER_PADDING * 2;
    public static final int METADATA_HEIGHT = 24;
    public static final int PREVIEW_FIXED_HEIGHT = 21;
    public static final int PREVIEW_HEIGHT = 104;
    public static final int ITEM_CELL_SIZE = 48;
    public static final int ACTION_HEIGHT = 18;
    public static final int ACTION_ROW_SPAN = 22;
    private static final int FOOTER_PADDING = 10;
    private static final int SCREEN_MARGIN = 16;

    private ItemDetailLayout() {
    }

    public static int adaptiveHeight(int actionRows) {
        return HEADER_HEIGHT + PREVIEW_HEIGHT
            + Math.max(1, actionRows) * ACTION_ROW_SPAN + FOOTER_PADDING;
    }

    public static int itemExtent(int cells) {
        return Math.max(1, cells) * ITEM_CELL_SIZE;
    }

    public static int previewSectionHeight(int itemRows) {
        return PREVIEW_HEIGHT + itemExtent(itemRows) - ITEM_CELL_SIZE;
    }

    /** Keep a visible information viewport below the fixed preview and action buttons. */
    public static int minimumContentHeight(int actionRows) {
        return adaptiveHeight(actionRows) + 34;
    }

    public static int resolveWidth(boolean automatic, int configuredWidth,
                                   int contentMinimum, int screenWidth) {
        return resolveWidth(automatic, configuredWidth, contentMinimum, 1.0F, screenWidth);
    }

    public static int resolveWidth(boolean automatic, int configuredWidth,
                                   int contentMinimum, float scale, int screenWidth) {
        int requested = automatic
            ? Math.max(MIN_MANUAL_WIDTH, contentMinimum)
            : clampWidth(configuredWidth);
        int scaled = Math.max(1, Math.round(requested * scale));
        return fitToScreen(automatic ? Math.max(requested, scaled) : scaled, screenWidth);
    }

    public static int resolveHeight(boolean automatic, int configuredHeight,
                                    int actionRows, int screenHeight) {
        return resolveHeight(automatic, configuredHeight, actionRows, 0, screenHeight);
    }

    public static int resolveHeight(boolean automatic, int configuredHeight,
                                    int actionRows, int extraContentHeight, int screenHeight) {
        return resolveHeight(automatic, configuredHeight, actionRows, extraContentHeight,
            1.0F, screenHeight);
    }

    public static int resolveHeight(boolean automatic, int configuredHeight,
                                    int actionRows, int extraContentHeight,
                                    float scale, int screenHeight) {
        int requested = automatic
            ? adaptiveHeight(actionRows) + Math.max(0, extraContentHeight)
            : clampHeight(configuredHeight);
        int scaled = Math.max(1, Math.round(requested * scale));
        // The setting resizes the card, not its fonts or buttons.
        return fitToScreen(automatic ? Math.max(requested, scaled) : scaled, screenHeight);
    }

    public static int contentHeight(int actionRows, int informationHeight, boolean split) {
        return contentHeight(actionRows, informationHeight, split, 1);
    }

    public static int contentHeight(int actionRows, int informationHeight, boolean split,
                                    int itemRows) {
        int base = split ? HEADER_HEIGHT + PREVIEW_HEIGHT + 60
            : adaptiveHeight(actionRows) + Math.max(0, informationHeight);
        return base + previewSectionHeight(itemRows) - PREVIEW_HEIGHT;
    }

    /** Reserve controls first; only overflowing regions scroll, never the whole card. */
    public static Sections sections(int cardHeight, int actionRows, int informationHeight,
                                    boolean split, int itemRows) {
        int previewContent = previewSectionHeight(itemRows) - METADATA_HEIGHT - 1;
        int actionsContent = METADATA_HEIGHT
            + (split ? 59 : Math.max(1, actionRows) * ACTION_ROW_SPAN + 4);
        int infoContent = split ? 0 : Math.max(0, informationHeight) + 5;
        int body = Math.max(0, cardHeight - HEADER_HEIGHT - 2);
        int previewReserve = Math.min(32, body / 2);
        int infoReserve = infoContent > 5 ? Math.min(24, body / 4) : 0;
        int actions = Math.min(actionsContent, body - previewReserve - infoReserve);
        int remaining = body - actions;
        int previewMinimum = Math.min(previewContent,
            Math.min(ITEM_CELL_SIZE, infoContent > 5 ? remaining / 2 : remaining));
        int info = Math.min(infoContent, Math.max(0, remaining - previewMinimum));
        return new Sections(remaining - info, actions, info,
            previewContent, actionsContent, infoContent);
    }

    public record Sections(int previewHeight, int actionsHeight, int infoHeight,
                           int previewContent, int actionsContent, int infoContent) {
        public int previewTop() { return HEADER_HEIGHT + 1; }
        public int previewFixedHeight() { return Math.min(PREVIEW_FIXED_HEIGHT, previewHeight); }
        public int previewImageHeight() { return previewHeight - previewFixedHeight(); }
        public int actionsTop() { return previewTop() + previewHeight; }
        public int infoTop() { return actionsTop() + actionsHeight; }
        public int previewMaxScroll() { return Math.max(0, previewContent - previewHeight); }
        public int actionsMaxScroll() { return Math.max(0, actionsContent - actionsHeight); }
        public int infoMaxScroll() { return Math.max(0, infoContent - infoHeight); }
        public int actionsOffset(int itemRows, double scroll) {
            int virtualTop = HEADER_HEIGHT + previewSectionHeight(itemRows) - METADATA_HEIGHT;
            return actionsTop() - virtualTop - (int) clampScroll(scroll, actionsContent, actionsHeight);
        }
    }

    public static int previewThumbWidth(int viewportWidth, int contentWidth) {
        return Math.min(viewportWidth, Math.max(8, viewportWidth * viewportWidth
            / Math.max(1, contentWidth)));
    }

    public static double previewScrollAt(double pointerX, int viewportWidth, int contentWidth) {
        int thumb = previewThumbWidth(viewportWidth, contentWidth);
        double ratio = (pointerX - thumb / 2.0) / Math.max(1, viewportWidth - thumb);
        return clampScroll(ratio * (contentWidth - viewportWidth), contentWidth, viewportWidth);
    }

    public static double clampScroll(double scroll, int contentHeight, int viewportHeight) {
        return Math.max(0, Math.min(Math.max(0, contentHeight - viewportHeight), scroll));
    }

    public static int clampWidth(int width) {
        return clamp(width, MIN_MANUAL_WIDTH, MAX_MANUAL_WIDTH);
    }

    public static int clampHeight(int height) {
        return clamp(height, MIN_MANUAL_HEIGHT, MAX_MANUAL_HEIGHT);
    }

    private static int fitToScreen(int value, int screenExtent) {
        return Math.max(1, Math.min(value, Math.max(1, screenExtent - SCREEN_MARGIN)));
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
