package com.xtdpotato.xero_delta.client;

import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/** Gives HUD wheels hidden pointer input without opening a Screen or rotating the camera. */
public final class WheelMouseController {
    enum Owner { MARKER, MEDICAL, COMMAND }

    private static Owner owner;
    private static boolean restoreGrab;
    private static boolean cursorHidden;
    private static float confinementCenterX;
    private static float confinementCenterY;
    private static float confinementRadius;

    private WheelMouseController() {
    }

    static void open(Minecraft minecraft, Owner requestedOwner) {
        if (owner == requestedOwner) return;
        if (owner != null) return;
        owner = requestedOwner;
        restoreGrab = minecraft.mouseHandler.isMouseGrabbed();
        if (restoreGrab) minecraft.mouseHandler.releaseMouse();
        confinementCenterX = minecraft.getWindow().getGuiScaledWidth() * 0.5F;
        confinementCenterY = minecraft.getWindow().getGuiScaledHeight() * 0.5F;
        confinementRadius = Math.max(1.0F, Math.min(confinementCenterX, confinementCenterY) - 2.0F);
        keepHidden(minecraft);
    }

    static void keepHidden(Minecraft minecraft) {
        if (owner == null || minecraft.getWindow() == null || !minecraft.isWindowActive()
            || minecraft.screen != null) return;
        long window = minecraft.getWindow().getWindow();
        GLFW.glfwSetInputMode(window, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_HIDDEN);
        cursorHidden = true;
    }

    static void close(Minecraft minecraft, Owner requestedOwner) {
        if (owner != requestedOwner) return;
        owner = null;
        confinementRadius = 0.0F;
        if (restoreGrab && minecraft.screen == null && minecraft.isWindowActive()) {
            minecraft.mouseHandler.grabMouse();
        } else if (cursorHidden) {
            GLFW.glfwSetInputMode(minecraft.getWindow().getWindow(), GLFW.GLFW_CURSOR,
                GLFW.GLFW_CURSOR_NORMAL);
            cursorHidden = false;
        }
        cursorHidden = false;
        restoreGrab = false;
    }

    static float guiX(Minecraft minecraft) {
        return (float) (minecraft.mouseHandler.xpos()
            * minecraft.getWindow().getGuiScaledWidth()
            / minecraft.getWindow().getScreenWidth());
    }

    static float guiY(Minecraft minecraft) {
        return (float) (minecraft.mouseHandler.ypos()
            * minecraft.getWindow().getGuiScaledHeight()
            / minecraft.getWindow().getScreenHeight());
    }

    /**
     * Keeps the hidden hardware pointer inside a HUD wheel.  GLFW still
     * tracks a hidden cursor, so hiding it alone lets selection drift beyond
     * the wheel and even out of the game window.
     */
    static void confineToCircle(Minecraft minecraft, float centerX, float centerY,
                                float maximumRadius) {
        confinementCenterX = centerX;
        confinementCenterY = centerY;
        confinementRadius = maximumRadius;
        confineToConfiguredCircle(minecraft);
    }

    /**
     * Runs after every native cursor-move callback.  Render-time clamping
     * alone leaves one frame in which a fast hardware cursor can escape a
     * radial wheel, particularly on high-refresh displays.
     */
    public static void constrainOnMouseMove(Minecraft minecraft) {
        confineToConfiguredCircle(minecraft);
    }

    private static void confineToConfiguredCircle(Minecraft minecraft) {
        float centerX = confinementCenterX;
        float centerY = confinementCenterY;
        float maximumRadius = confinementRadius;
        if (owner == null || minecraft.getWindow() == null || maximumRadius <= 0.0F
            || !minecraft.isWindowActive() || minecraft.screen != null) return;
        int guiWidth = minecraft.getWindow().getGuiScaledWidth();
        int guiHeight = minecraft.getWindow().getGuiScaledHeight();
        int screenWidth = minecraft.getWindow().getScreenWidth();
        int screenHeight = minecraft.getWindow().getScreenHeight();
        if (guiWidth <= 0 || guiHeight <= 0 || screenWidth <= 0 || screenHeight <= 0) return;

        float offsetX = guiX(minecraft) - centerX;
        float offsetY = guiY(minecraft) - centerY;
        float distanceSquared = offsetX * offsetX + offsetY * offsetY;
        float maximumSquared = maximumRadius * maximumRadius;
        if (distanceSquared <= maximumSquared) return;

        float scale = maximumRadius / (float) Math.sqrt(distanceSquared);
        float clampedX = centerX + offsetX * scale;
        float clampedY = centerY + offsetY * scale;
        GLFW.glfwSetCursorPos(minecraft.getWindow().getWindow(),
            clampedX * screenWidth / guiWidth,
            clampedY * screenHeight / guiHeight);
    }

}
