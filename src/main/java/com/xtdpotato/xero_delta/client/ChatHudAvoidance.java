package com.xtdpotato.xero_delta.client;

/** Calculates how far the lower-left chat stack must move above the health HUD. */
public final class ChatHudAvoidance {
    private static final int CHAT_BOTTOM_MARGIN = 40;
    private static final int HUD_GAP = 6;

    private ChatHudAvoidance() {
    }

    public static int offset(int screenHeight, int chatWidth, int chatHeight,
                             StatusEffectHudRenderer.Bounds hud) {
        if (hud == null || hud.width() <= 0 || hud.height() <= 0 || chatWidth <= 0
            || chatHeight <= 0 || hud.x() >= chatWidth || hud.x() + hud.width() <= 0) {
            return 0;
        }

        int chatBottom = screenHeight - CHAT_BOTTOM_MARGIN;
        int chatTop = chatBottom - chatHeight;
        if (hud.y() >= chatBottom || hud.y() + hud.height() <= chatTop) return 0;
        return Math.max(0, chatBottom - (hud.y() - HUD_GAP));
    }
}
