package com.xtdpotato.xero_delta.client;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.xtdpotato.xero_delta.data.ConfigPaths;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** One global position and scale shared by the status-effect HUD in every world. */
public final class StatusEffectHudState {
    private static final Gson GSON = new Gson();
    private static final Path FILE = ConfigPaths.file("status_effect_hud.json");
    private static boolean loaded;
    private static boolean manualPosition;
    private static int x;
    private static int y;
    private static int scalePercent = 100;
    private static int opacityPercent = 100;
    private static boolean manualInventoryEffectPosition;
    private static int inventoryEffectX;
    private static int inventoryEffectY;
    private static int inventoryEffectScalePercent = 100;
    private static int inventoryEffectOpacityPercent = 100;
    private static int inventoryLayoutScalePercent = 150;
    private static int inventoryLabelScalePercent = 100;
    private static int inventoryLayoutMarginPercent;
    private static boolean manualItemDetailPosition;
    private static int itemDetailX;
    private static int itemDetailY;
    private static int itemDetailWidth = 180;
    private static int itemDetailHeight = 90;
    private static boolean itemDetailAutoSize = true;
    private static boolean itemDetailAutoWidth = true;
    private static boolean itemDetailAutoHeight = true;
    private static int itemDetailScalePercent = 100;
    private static boolean manualHealthPosition;
    private static int healthX;
    private static int healthY;
    private static int healthScalePercent = 100;
    private static int healthOpacityPercent = 100;
    private static boolean manualIntroPosition;
    private static int introX;
    private static int introY;
    private static int introScalePercent = 100;
    private static int introOpacityPercent = 100;
    private static boolean manualStaminaPosition;
    private static int staminaX;
    private static int staminaY;
    private static int staminaScalePercent = 100;
    private static int staminaOpacityPercent = 100;
    private static boolean manualDownedPosition;
    private static int downedX;
    private static int downedY;
    private static int downedScalePercent = 100;
    private static int downedOpacityPercent = 100;
    private static boolean manualRescuePosition;
    private static int rescueX;
    private static int rescueY;
    private static int rescueScalePercent = 100;
    private static int rescueOpacityPercent = 100;
    private static boolean manualContextPosition;
    private static int contextX;
    private static int contextY;
    private static int contextScalePercent = 100;
    private static int contextOpacityPercent = 100;
    private static int effectIntroFadeInDurationMs = 200;
    private static int effectIntroHoldDurationMs = 1_000;
    private static int effectIntroFadeOutDurationMs = 250;
    private static int healthAnimationDurationMs = 200;
    private static boolean snapX = true;
    private static boolean snapY = true;
    private static boolean manualPanelPosition;
    private static int panelX;
    private static int panelY;
    private static boolean panelCollapsed;
    private static int editorScreenWidth;
    private static int editorScreenHeight;

    private StatusEffectHudState() {
    }

    public static int[] position(int defaultX, int defaultY, int screenWidth, int screenHeight,
                                 int hudWidth, int hudHeight) {
        load();
        int resolvedX = manualPosition ? x : defaultX;
        int resolvedY = manualPosition ? y : defaultY;
        return new int[]{
            clamp(resolvedX, 2, Math.max(2, screenWidth - hudWidth - 2)),
            clamp(resolvedY, 2, Math.max(2, screenHeight - hudHeight - 2))
        };
    }

    public static void setPosition(int nextX, int nextY) {
        load();
        manualPosition = true;
        x = nextX;
        y = nextY;
    }

    public static void resetPosition() {
        load();
        manualPosition = false;
    }

    public static boolean effectsManualPosition() { load(); return manualPosition; }
    public static int effectsX() { load(); return x; }
    public static int effectsY() { load(); return y; }

    public static int[] inventoryEffectPosition(int defaultX, int defaultY,
                                                int screenWidth, int screenHeight,
                                                int effectWidth, int effectHeight) {
        load();
        int resolvedX = manualInventoryEffectPosition ? inventoryEffectX : defaultX;
        int resolvedY = manualInventoryEffectPosition ? inventoryEffectY : defaultY;
        return new int[]{
            clamp(resolvedX, 2, Math.max(2, screenWidth - effectWidth - 2)),
            clamp(resolvedY, 2, Math.max(2, screenHeight - effectHeight - 2))
        };
    }

    public static void setInventoryEffectPosition(int nextX, int nextY) {
        load();
        manualInventoryEffectPosition = true;
        inventoryEffectX = nextX;
        inventoryEffectY = nextY;
    }

    public static void resetInventoryEffectPosition() {
        load();
        manualInventoryEffectPosition = false;
    }

    public static boolean inventoryEffectsManualPosition() {
        load();
        return manualInventoryEffectPosition;
    }

    public static int inventoryEffectsX() { load(); return inventoryEffectX; }
    public static int inventoryEffectsY() { load(); return inventoryEffectY; }

    public static int inventoryEffectScalePercent() {
        load();
        return inventoryEffectScalePercent;
    }

    public static float inventoryEffectScale() {
        return inventoryEffectScalePercent() / 100.0F;
    }

    public static void adjustInventoryEffectScale(int direction) {
        load();
        inventoryEffectScalePercent = clamp(inventoryEffectScalePercent
            + Integer.signum(direction) * 10, 50, 200);
    }
    public static void setInventoryEffectScalePercent(int value) {
        load(); inventoryEffectScalePercent = clamp(value, 50, 200);
    }

    public static int inventoryEffectOpacityPercent() {
        load();
        return inventoryEffectOpacityPercent;
    }

    public static float inventoryEffectOpacity() {
        return inventoryEffectOpacityPercent() / 100.0F;
    }

    public static void adjustInventoryEffectOpacity(int direction) {
        load();
        inventoryEffectOpacityPercent = clamp(inventoryEffectOpacityPercent
            + Integer.signum(direction) * 10, 10, 100);
    }
    public static void setInventoryEffectOpacityPercent(int value) { load(); inventoryEffectOpacityPercent = clamp(value, 10, 100); }

    public static int inventoryLayoutScalePercent() {
        load();
        return inventoryLayoutScalePercent;
    }

    public static float inventoryLayoutScale() {
        return inventoryLayoutScalePercent() / 100.0F;
    }

    public static void adjustInventoryLayoutScale(int direction) {
        load();
        inventoryLayoutScalePercent = clamp(inventoryLayoutScalePercent
            + Integer.signum(direction) * 5, 60, 500);
    }

    public static void setInventoryLayoutScalePercent(int value) {
        load();
        inventoryLayoutScalePercent = clamp(value, 60, 500);
    }

    public static int inventoryLabelScalePercent() {
        load();
        return inventoryLabelScalePercent;
    }

    public static float inventoryLabelScale() {
        return inventoryLabelScalePercent() / 100.0F;
    }

    public static void adjustInventoryLabelScale(int direction) {
        load();
        inventoryLabelScalePercent = clamp(inventoryLabelScalePercent
            + Integer.signum(direction) * 5, 50, 160);
    }

    public static void setInventoryLabelScalePercent(int value) {
        load();
        inventoryLabelScalePercent = clamp(value, 50, 160);
    }

    public static int inventoryLayoutMarginPercent() {
        load();
        return inventoryLayoutMarginPercent;
    }

    public static int inventoryLayoutMargin() {
        return inventoryLayoutMarginPercent() * 2;
    }

    public static void adjustInventoryLayoutMargin(int direction) {
        load();
        inventoryLayoutMarginPercent = clamp(inventoryLayoutMarginPercent
            + Integer.signum(direction) * 5, 0, 40);
    }

    public static void setInventoryLayoutMarginPercent(int value) {
        load();
        inventoryLayoutMarginPercent = clamp(value, 0, 40);
    }

    public static int[] itemDetailPosition(int defaultX, int defaultY,
                                           int screenWidth, int screenHeight,
                                           int detailWidth, int detailHeight) {
        load();
        int resolvedX = manualItemDetailPosition ? itemDetailX : defaultX;
        int resolvedY = manualItemDetailPosition ? itemDetailY : defaultY;
        return new int[]{
            clamp(resolvedX, 2, Math.max(2, screenWidth - detailWidth - 2)),
            clamp(resolvedY, 2, Math.max(2, screenHeight - detailHeight - 2))
        };
    }

    public static void setItemDetailPosition(int nextX, int nextY) {
        load();
        manualItemDetailPosition = true;
        itemDetailX = nextX;
        itemDetailY = nextY;
    }

    public static void resetItemDetailPosition() {
        load();
        manualItemDetailPosition = false;
    }

    public static int itemDetailWidth() {
        load();
        return itemDetailWidth;
    }

    public static boolean itemDetailAutoSize() {
        load();
        return itemDetailAutoWidth && itemDetailAutoHeight;
    }

    public static void setItemDetailAutoSize(boolean automatic) {
        load();
        itemDetailAutoSize = automatic;
        itemDetailAutoWidth = automatic;
        itemDetailAutoHeight = automatic;
    }

    public static boolean itemDetailAutoWidth() {
        load();
        return itemDetailAutoWidth;
    }

    public static boolean itemDetailAutoHeight() {
        load();
        return itemDetailAutoHeight;
    }

    public static void setItemDetailAutoWidth(boolean automatic) {
        load();
        itemDetailAutoWidth = automatic;
        itemDetailAutoSize = itemDetailAutoWidth && itemDetailAutoHeight;
    }

    public static void setItemDetailAutoHeight(boolean automatic) {
        load();
        itemDetailAutoHeight = automatic;
        itemDetailAutoSize = itemDetailAutoWidth && itemDetailAutoHeight;
    }

    public static void setManualItemDetailSize(int width, int height) {
        load();
        itemDetailWidth = ItemDetailLayout.clampWidth(width);
        itemDetailHeight = ItemDetailLayout.clampHeight(height);
        itemDetailAutoSize = false;
        itemDetailAutoWidth = false;
        itemDetailAutoHeight = false;
    }

    public static void adjustItemDetailWidth(int direction) {
        load();
        itemDetailWidth = ItemDetailLayout.clampWidth(
            itemDetailWidth + Integer.signum(direction) * 5);
        itemDetailAutoSize = false;
        itemDetailAutoWidth = false;
    }

    public static void setItemDetailWidth(int value) {
        load();
        itemDetailWidth = ItemDetailLayout.clampWidth(value);
        itemDetailAutoSize = false;
        itemDetailAutoWidth = false;
    }

    public static int itemDetailHeight() {
        load();
        return itemDetailHeight;
    }

    public static void adjustItemDetailHeight(int direction) {
        load();
        itemDetailHeight = ItemDetailLayout.clampHeight(
            itemDetailHeight + Integer.signum(direction) * 5);
        itemDetailAutoSize = false;
        itemDetailAutoHeight = false;
    }

    public static void setItemDetailHeight(int value) {
        load();
        itemDetailHeight = ItemDetailLayout.clampHeight(value);
        itemDetailAutoSize = false;
        itemDetailAutoHeight = false;
    }

    public static int itemDetailScalePercent() {
        load();
        return itemDetailScalePercent;
    }

    public static float itemDetailScale() {
        return itemDetailScalePercent() / 100.0F;
    }

    public static void adjustItemDetailScale(int direction) {
        load();
        itemDetailScalePercent = clamp(itemDetailScalePercent
            + Integer.signum(direction) * 5, 50, 200);
    }

    public static void setItemDetailScalePercent(int value) {
        load();
        itemDetailScalePercent = clamp(value, 50, 200);
    }

    public static int[] healthPosition(int defaultX, int defaultY, int screenWidth, int screenHeight,
                                       int hudWidth, int hudHeight) {
        load();
        int resolvedX = manualHealthPosition ? healthX : defaultX;
        int resolvedY = manualHealthPosition ? healthY : defaultY;
        return new int[]{
            clamp(resolvedX, 2, Math.max(2, screenWidth - hudWidth - 2)),
            clamp(resolvedY, 2, Math.max(2, screenHeight - hudHeight - 2))
        };
    }

    public static void setHealthPosition(int nextX, int nextY) {
        load();
        manualHealthPosition = true;
        healthX = nextX;
        healthY = nextY;
    }

    public static void resetHealthPosition() {
        load();
        manualHealthPosition = false;
    }
    public static int[] introPosition(int defaultX, int defaultY, int screenWidth, int screenHeight,
                                      int hudWidth, int hudHeight) {
        load();
        int resolvedX = manualIntroPosition ? introX : defaultX;
        int resolvedY = manualIntroPosition ? introY : defaultY;
        return new int[]{
            clamp(resolvedX, 2, Math.max(2, screenWidth - hudWidth - 2)),
            clamp(resolvedY, 2, Math.max(2, screenHeight - hudHeight - 2))
        };
    }

    public static void setIntroPosition(int nextX, int nextY) {
        load();
        manualIntroPosition = true;
        introX = nextX;
        introY = nextY;
    }

    public static void resetIntroPosition() { load(); manualIntroPosition = false; }
    public static int introScalePercent() { load(); return introScalePercent; }
    public static float introScale() { return introScalePercent() / 100.0F; }
    public static void adjustIntroScale(int direction) {
        load();
        introScalePercent = clamp(introScalePercent + Integer.signum(direction) * 10, 50, 200);
    }
    public static void setIntroScalePercent(int value) { load(); introScalePercent = clamp(value, 50, 200); }

    public static int introOpacityPercent() { load(); return introOpacityPercent; }
    public static float introOpacity() { return introOpacityPercent() / 100.0F; }
    public static void adjustIntroOpacity(int direction) {
        load();
        introOpacityPercent = clamp(introOpacityPercent + Integer.signum(direction) * 10, 10, 100);
    }
    public static void setIntroOpacityPercent(int value) { load(); introOpacityPercent = clamp(value, 10, 100); }
    public static int[] staminaPosition(int defaultX, int defaultY, int screenWidth, int screenHeight,
                                        int hudWidth, int hudHeight) {
        load();
        int resolvedX = manualStaminaPosition ? staminaX : defaultX;
        int resolvedY = manualStaminaPosition ? staminaY : defaultY;
        return new int[]{
            clamp(resolvedX, 2, Math.max(2, screenWidth - hudWidth - 2)),
            clamp(resolvedY, 2, Math.max(2, screenHeight - hudHeight - 2))
        };
    }

    public static void setStaminaPosition(int nextX, int nextY) {
        load();
        manualStaminaPosition = true;
        staminaX = nextX;
        staminaY = nextY;
    }

    public static void resetStaminaPosition() { load(); manualStaminaPosition = false; }
    public static int staminaScalePercent() { load(); return staminaScalePercent; }
    public static float staminaScale() { return staminaScalePercent() / 100.0F; }
    public static void adjustStaminaScale(int direction) {
        load();
        staminaScalePercent = clamp(staminaScalePercent + Integer.signum(direction) * 10, 50, 200);
    }
    public static void setStaminaScalePercent(int value) { load(); staminaScalePercent = clamp(value, 50, 200); }

    public static int staminaOpacityPercent() { load(); return staminaOpacityPercent; }
    public static float staminaOpacity() { return staminaOpacityPercent() / 100.0F; }
    public static void adjustStaminaOpacity(int direction) {
        load();
        staminaOpacityPercent = clamp(staminaOpacityPercent + Integer.signum(direction) * 10, 10, 100);
    }
    public static void setStaminaOpacityPercent(int value) { load(); staminaOpacityPercent = clamp(value, 10, 100); }
    public static int[] downedPosition(int defaultX, int defaultY, int screenWidth, int screenHeight,
                                       int hudWidth, int hudHeight) {
        load();
        int resolvedX = manualDownedPosition ? downedX : defaultX;
        int resolvedY = manualDownedPosition ? downedY : defaultY;
        return new int[]{
            clamp(resolvedX, 2, Math.max(2, screenWidth - hudWidth - 2)),
            clamp(resolvedY, 2, Math.max(2, screenHeight - hudHeight - 2))
        };
    }

    public static void setDownedPosition(int nextX, int nextY) {
        load();
        manualDownedPosition = true;
        downedX = nextX;
        downedY = nextY;
    }

    public static void resetDownedPosition() { load(); manualDownedPosition = false; }
    public static int downedScalePercent() { load(); return downedScalePercent; }
    public static float downedScale() { return downedScalePercent() / 100.0F; }
    public static void adjustDownedScale(int direction) {
        load();
        downedScalePercent = clamp(downedScalePercent + Integer.signum(direction) * 10, 50, 200);
    }
    public static void setDownedScalePercent(int value) { load(); downedScalePercent = clamp(value, 50, 200); }
    public static int downedOpacityPercent() { load(); return downedOpacityPercent; }
    public static float downedOpacity() { return downedOpacityPercent() / 100.0F; }
    public static void adjustDownedOpacity(int direction) {
        load();
        downedOpacityPercent = clamp(downedOpacityPercent + Integer.signum(direction) * 10, 10, 100);
    }
    public static void setDownedOpacityPercent(int value) { load(); downedOpacityPercent = clamp(value, 10, 100); }
    public static int[] rescuePosition(int defaultX, int defaultY, int screenWidth, int screenHeight,
                                       int hudWidth, int hudHeight) {
        load();
        int resolvedX = manualRescuePosition ? rescueX : defaultX;
        int resolvedY = manualRescuePosition ? rescueY : defaultY;
        return new int[]{
            clamp(resolvedX, 2, Math.max(2, screenWidth - hudWidth - 2)),
            clamp(resolvedY, 2, Math.max(2, screenHeight - hudHeight - 2))
        };
    }

    public static void setRescuePosition(int nextX, int nextY) {
        load();
        manualRescuePosition = true;
        rescueX = nextX;
        rescueY = nextY;
    }

    public static void resetRescuePosition() { load(); manualRescuePosition = false; }
    public static int rescueScalePercent() { load(); return rescueScalePercent; }
    public static float rescueScale() { return rescueScalePercent() / 100.0F; }
    public static void adjustRescueScale(int direction) {
        load();
        rescueScalePercent = clamp(rescueScalePercent + Integer.signum(direction) * 10, 50, 200);
    }
    public static void setRescueScalePercent(int value) { load(); rescueScalePercent = clamp(value, 50, 200); }
    public static int rescueOpacityPercent() { load(); return rescueOpacityPercent; }
    public static float rescueOpacity() { return rescueOpacityPercent() / 100.0F; }
    public static void adjustRescueOpacity(int direction) {
        load();
        rescueOpacityPercent = clamp(rescueOpacityPercent + Integer.signum(direction) * 10, 10, 100);
    }
    public static void setRescueOpacityPercent(int value) { load(); rescueOpacityPercent = clamp(value, 10, 100); }
    public static int[] contextPosition(int defaultX, int defaultY, int screenWidth, int screenHeight,
                                        int hudWidth, int hudHeight) {
        load();
        int resolvedX = manualContextPosition ? contextX : defaultX;
        int resolvedY = manualContextPosition ? contextY : defaultY;
        return new int[]{
            clamp(resolvedX, 2, Math.max(2, screenWidth - hudWidth - 2)),
            clamp(resolvedY, 2, Math.max(2, screenHeight - hudHeight - 2))
        };
    }
    public static void setContextPosition(int nextX, int nextY) {
        load();
        manualContextPosition = true;
        contextX = nextX;
        contextY = nextY;
    }
    public static void resetContextPosition() { load(); manualContextPosition = false; }

    /** Restores every Delta HUD control to the same defaults used by a fresh configuration. */
    public static void resetAllToDefaults() {
        load();
        manualPosition = false;
        x = 0;
        y = 0;
        scalePercent = 100;
        opacityPercent = 100;
        manualInventoryEffectPosition = false;
        inventoryEffectX = 0;
        inventoryEffectY = 0;
        inventoryEffectScalePercent = 100;
        inventoryEffectOpacityPercent = 100;
        inventoryLayoutScalePercent = 150;
        inventoryLabelScalePercent = 100;
        inventoryLayoutMarginPercent = 0;
        manualItemDetailPosition = false;
        itemDetailX = 0;
        itemDetailY = 0;
        itemDetailWidth = 180;
        itemDetailHeight = 90;
        itemDetailAutoSize = true;
        itemDetailAutoWidth = true;
        itemDetailAutoHeight = true;
        itemDetailScalePercent = 100;
        manualHealthPosition = false;
        healthX = 0;
        healthY = 0;
        healthScalePercent = 100;
        healthOpacityPercent = 100;
        manualIntroPosition = false;
        introX = 0;
        introY = 0;
        introScalePercent = 100;
        introOpacityPercent = 100;
        manualStaminaPosition = false;
        staminaX = 0;
        staminaY = 0;
        staminaScalePercent = 100;
        staminaOpacityPercent = 100;
        manualDownedPosition = false;
        downedX = 0;
        downedY = 0;
        downedScalePercent = 100;
        downedOpacityPercent = 100;
        manualRescuePosition = false;
        rescueX = 0;
        rescueY = 0;
        rescueScalePercent = 100;
        rescueOpacityPercent = 100;
        manualContextPosition = false;
        contextX = 0;
        contextY = 0;
        contextScalePercent = 100;
        contextOpacityPercent = 100;
        effectIntroFadeInDurationMs = 200;
        effectIntroHoldDurationMs = 1_000;
        effectIntroFadeOutDurationMs = 250;
        healthAnimationDurationMs = 200;
        snapX = true;
        snapY = true;
        manualPanelPosition = false;
        panelX = 0;
        panelY = 0;
        panelCollapsed = false;
        editorScreenWidth = 0;
        editorScreenHeight = 0;
    }
    public static int contextScalePercent() { load(); return contextScalePercent; }
    public static float contextScale() { return contextScalePercent() / 100.0F; }
    public static void adjustContextScale(int direction) {
        load();
        contextScalePercent = clamp(contextScalePercent + Integer.signum(direction) * 10, 50, 200);
    }
    public static void setContextScalePercent(int value) { load(); contextScalePercent = clamp(value, 50, 200); }
    public static int contextOpacityPercent() { load(); return contextOpacityPercent; }
    public static float contextOpacity() { return contextOpacityPercent() / 100.0F; }
    public static void adjustContextOpacity(int direction) {
        load();
        contextOpacityPercent = clamp(contextOpacityPercent + Integer.signum(direction) * 10, 10, 100);
    }
    public static void setContextOpacityPercent(int value) { load(); contextOpacityPercent = clamp(value, 10, 100); }
    public static int healthScalePercent() {
        load();
        return healthScalePercent;
    }

    public static float healthScale() {
        return healthScalePercent() / 100.0F;
    }

    public static void adjustHealthScale(int direction) {
        load();
        healthScalePercent = clamp(healthScalePercent + Integer.signum(direction) * 10, 50, 200);
    }
    public static void setHealthScalePercent(int value) { load(); healthScalePercent = clamp(value, 50, 200); }
    public static int healthOpacityPercent() { load(); return healthOpacityPercent; }
    public static float healthOpacity() { return healthOpacityPercent() / 100.0F; }
    public static void adjustHealthOpacity(int direction) {
        load();
        healthOpacityPercent = clamp(healthOpacityPercent + Integer.signum(direction) * 10, 10, 100);
    }
    public static void setHealthOpacityPercent(int value) { load(); healthOpacityPercent = clamp(value, 10, 100); }
    public static int effectAnimationDurationMs() { return effectIntroTotalDurationMs(); }
    public static int effectIntroFadeInDurationMs() { load(); return effectIntroFadeInDurationMs; }
    public static int effectIntroHoldDurationMs() { load(); return effectIntroHoldDurationMs; }
    public static int effectIntroFadeOutDurationMs() { load(); return effectIntroFadeOutDurationMs; }
    public static int effectIntroTotalDurationMs() {
        load();
        return effectIntroFadeInDurationMs + effectIntroHoldDurationMs
            + effectIntroFadeOutDurationMs;
    }

    public static int healthAnimationDurationMs() {
        load();
        return healthAnimationDurationMs;
    }

    public static float healthAnimationSeconds() {
        return healthAnimationDurationMs() / 1_000.0F;
    }

    public static void adjustEffectIntroFadeInDuration(int direction) {
        load();
        effectIntroFadeInDurationMs = clamp(effectIntroFadeInDurationMs
            + Integer.signum(direction) * 50, 50, 2_000);
    }

    public static void setEffectIntroFadeInDurationMs(int value) {
        load(); effectIntroFadeInDurationMs = clamp(value, 50, 2_000);
    }

    public static void adjustEffectIntroHoldDuration(int direction) {
        load();
        effectIntroHoldDurationMs = clamp(effectIntroHoldDurationMs
            + Integer.signum(direction) * 50, 100, 5_000);
    }

    public static void setEffectIntroHoldDurationMs(int value) {
        load(); effectIntroHoldDurationMs = clamp(value, 100, 5_000);
    }

    public static void adjustEffectIntroFadeOutDuration(int direction) {
        load();
        effectIntroFadeOutDurationMs = clamp(effectIntroFadeOutDurationMs
            + Integer.signum(direction) * 50, 50, 2_000);
    }

    public static void setEffectIntroFadeOutDurationMs(int value) {
        load(); effectIntroFadeOutDurationMs = clamp(value, 50, 2_000);
    }

    public static void adjustHealthAnimationDuration(int direction) {
        load();
        healthAnimationDurationMs = clamp(healthAnimationDurationMs
            + Integer.signum(direction) * 50, 50, 500);
    }
    public static void setHealthAnimationDurationMs(int value) {
        load(); healthAnimationDurationMs = clamp(value, 50, 500);
    }
    public static boolean snapX() { load(); return snapX; }
    public static boolean snapY() { load(); return snapY; }
    public static void setSnapX(boolean value) { load(); snapX = value; }
    public static void setSnapY(boolean value) { load(); snapY = value; }

    public static int[] panelPosition(int defaultX, int defaultY, int screenWidth, int screenHeight,
                                      int width, int height) {
        load();
        int resolvedX = manualPanelPosition ? panelX : defaultX;
        int resolvedY = manualPanelPosition ? panelY : defaultY;
        return new int[]{clamp(resolvedX, 2, Math.max(2, screenWidth - width - 2)),
            clamp(resolvedY, 2, Math.max(2, screenHeight - height - 2))};
    }

    public static void setPanelPosition(int nextX, int nextY) {
        load();
        manualPanelPosition = true;
        panelX = nextX;
        panelY = nextY;
    }

    public static boolean panelCollapsed() { load(); return panelCollapsed; }
    public static void setPanelCollapsed(boolean value) { load(); panelCollapsed = value; }
    public static int scalePercent() {
        load();
        return scalePercent;
    }

    public static float scale() {
        return scalePercent() / 100.0F;
    }

    public static void adjustScale(int direction) {
        load();
        scalePercent = clamp(scalePercent + Integer.signum(direction) * 10, 50, 200);
    }
    public static void setScalePercent(int value) { load(); scalePercent = clamp(value, 50, 200); }

    public static int opacityPercent() { load(); return opacityPercent; }
    public static float opacity() { return opacityPercent() / 100.0F; }
    public static void adjustOpacity(int direction) {
        load();
        opacityPercent = clamp(opacityPercent + Integer.signum(direction) * 10, 10, 100);
    }
    public static void setOpacityPercent(int value) { load(); opacityPercent = clamp(value, 10, 100); }


    /** Keeps customized controls proportional when GUI-scaled screen dimensions change. */
    public static boolean adaptToScreen(int screenWidth, int screenHeight) {
        load();
        if (screenWidth <= 0 || screenHeight <= 0) return false;
        if (editorScreenWidth <= 0 || editorScreenHeight <= 0) {
            editorScreenWidth = screenWidth;
            editorScreenHeight = screenHeight;
            return false;
        }
        if (editorScreenWidth == screenWidth && editorScreenHeight == screenHeight) return false;
        double ratioX = screenWidth / (double) editorScreenWidth;
        double ratioY = screenHeight / (double) editorScreenHeight;
        double sizeRatio = Math.min(ratioX, ratioY);
        if (manualPosition) { x = scaled(x, ratioX); y = scaled(y, ratioY); }
        if (manualInventoryEffectPosition) {
            inventoryEffectX = scaled(inventoryEffectX, ratioX);
            inventoryEffectY = scaled(inventoryEffectY, ratioY);
        }
        if (manualItemDetailPosition) {
            itemDetailX = scaled(itemDetailX, ratioX);
            itemDetailY = scaled(itemDetailY, ratioY);
        }
        if (manualHealthPosition) { healthX = scaled(healthX, ratioX); healthY = scaled(healthY, ratioY); }
        if (manualIntroPosition) { introX = scaled(introX, ratioX); introY = scaled(introY, ratioY); }
        if (manualStaminaPosition) { staminaX = scaled(staminaX, ratioX); staminaY = scaled(staminaY, ratioY); }
        if (manualDownedPosition) { downedX = scaled(downedX, ratioX); downedY = scaled(downedY, ratioY); }
        if (manualRescuePosition) { rescueX = scaled(rescueX, ratioX); rescueY = scaled(rescueY, ratioY); }
        if (manualContextPosition) { contextX = scaled(contextX, ratioX); contextY = scaled(contextY, ratioY); }
        if (manualPanelPosition) { panelX = scaled(panelX, ratioX); panelY = scaled(panelY, ratioY); }
        scalePercent = scaledPercent(scalePercent, sizeRatio, 50, 200);
        inventoryEffectScalePercent = scaledPercent(inventoryEffectScalePercent, sizeRatio, 50, 200);
        inventoryLayoutScalePercent = scaledPercent(inventoryLayoutScalePercent, sizeRatio, 60, 500);
        inventoryLabelScalePercent = scaledPercent(inventoryLabelScalePercent, sizeRatio, 50, 160);
        if (!itemDetailAutoWidth) {
            itemDetailWidth = ItemDetailLayout.clampWidth(scaled(itemDetailWidth, sizeRatio));
        }
        if (!itemDetailAutoHeight) {
            itemDetailHeight = ItemDetailLayout.clampHeight(scaled(itemDetailHeight, sizeRatio));
        }
        inventoryLayoutMarginPercent = scaledPercent(inventoryLayoutMarginPercent, sizeRatio, 0, 40);
        healthScalePercent = scaledPercent(healthScalePercent, sizeRatio, 50, 200);
        introScalePercent = scaledPercent(introScalePercent, sizeRatio, 50, 200);
        staminaScalePercent = scaledPercent(staminaScalePercent, sizeRatio, 50, 200);
        downedScalePercent = scaledPercent(downedScalePercent, sizeRatio, 50, 200);
        rescueScalePercent = scaledPercent(rescueScalePercent, sizeRatio, 50, 200);
        contextScalePercent = scaledPercent(contextScalePercent, sizeRatio, 50, 200);
        editorScreenWidth = screenWidth;
        editorScreenHeight = screenHeight;
        return true;
    }

    public static String editorSnapshot() {
        load();
        return GSON.toJson(new EditorState(
            manualPosition, x, y, scalePercent, opacityPercent,
            manualInventoryEffectPosition, inventoryEffectX, inventoryEffectY,
            inventoryEffectScalePercent, inventoryEffectOpacityPercent,
            inventoryLayoutScalePercent, inventoryLabelScalePercent,
            inventoryLayoutMarginPercent,
            manualItemDetailPosition, itemDetailX, itemDetailY, itemDetailWidth, itemDetailHeight,
            itemDetailAutoSize, itemDetailAutoWidth, itemDetailAutoHeight, itemDetailScalePercent,
            manualHealthPosition, healthX, healthY, healthScalePercent, healthOpacityPercent,
            manualIntroPosition, introX, introY, introScalePercent, introOpacityPercent,
            manualStaminaPosition, staminaX, staminaY, staminaScalePercent, staminaOpacityPercent,
            manualDownedPosition, downedX, downedY, downedScalePercent, downedOpacityPercent,
            manualRescuePosition, rescueX, rescueY, rescueScalePercent, rescueOpacityPercent,
            manualContextPosition, contextX, contextY, contextScalePercent, contextOpacityPercent,
            effectIntroFadeInDurationMs, effectIntroHoldDurationMs, effectIntroFadeOutDurationMs,
            healthAnimationDurationMs, snapX, snapY, manualPanelPosition, panelX, panelY,
            panelCollapsed, editorScreenWidth, editorScreenHeight));
    }

    public static void restoreEditorSnapshot(String json) {
        load();
        if (json == null || json.isBlank()) return;
        try {
            EditorState state = GSON.fromJson(json, EditorState.class);
            if (state == null) return;
            manualPosition = state.manualPosition; x = state.x; y = state.y;
            scalePercent = state.scalePercent; opacityPercent = state.opacityPercent;
            manualInventoryEffectPosition = state.manualInventoryEffectPosition;
            inventoryEffectX = state.inventoryEffectX; inventoryEffectY = state.inventoryEffectY;
            inventoryEffectScalePercent = state.inventoryEffectScalePercent;
            inventoryEffectOpacityPercent = state.inventoryEffectOpacityPercent;
            inventoryLayoutScalePercent = clamp(state.inventoryLayoutScalePercent, 60, 500);
            inventoryLabelScalePercent = state.inventoryLabelScalePercent;
            inventoryLayoutMarginPercent = clamp(state.inventoryLayoutMarginPercent, 0, 40);
            manualItemDetailPosition = state.manualItemDetailPosition;
            itemDetailX = state.itemDetailX; itemDetailY = state.itemDetailY;
            itemDetailWidth = state.itemDetailWidth <= 0 ? 180
                : ItemDetailLayout.clampWidth(state.itemDetailWidth);
            itemDetailHeight = state.itemDetailHeight <= 0 ? 90
                : ItemDetailLayout.clampHeight(state.itemDetailHeight);
            itemDetailAutoSize = state.itemDetailAutoSize;
            itemDetailAutoWidth = state.itemDetailAutoWidth;
            itemDetailAutoHeight = state.itemDetailAutoHeight;
            itemDetailScalePercent = clamp(state.itemDetailScalePercent, 50, 200);
            manualHealthPosition = state.manualHealthPosition; healthX = state.healthX; healthY = state.healthY;
            healthScalePercent = state.healthScalePercent; healthOpacityPercent = state.healthOpacityPercent;
            manualIntroPosition = state.manualIntroPosition; introX = state.introX; introY = state.introY;
            introScalePercent = state.introScalePercent; introOpacityPercent = state.introOpacityPercent;
            manualStaminaPosition = state.manualStaminaPosition; staminaX = state.staminaX; staminaY = state.staminaY;
            staminaScalePercent = state.staminaScalePercent; staminaOpacityPercent = state.staminaOpacityPercent;
            manualDownedPosition = state.manualDownedPosition; downedX = state.downedX; downedY = state.downedY;
            downedScalePercent = state.downedScalePercent; downedOpacityPercent = state.downedOpacityPercent;
            manualRescuePosition = state.manualRescuePosition; rescueX = state.rescueX; rescueY = state.rescueY;
            rescueScalePercent = state.rescueScalePercent; rescueOpacityPercent = state.rescueOpacityPercent;
            manualContextPosition = state.manualContextPosition; contextX = state.contextX; contextY = state.contextY;
            contextScalePercent = state.contextScalePercent; contextOpacityPercent = state.contextOpacityPercent;
            effectIntroFadeInDurationMs = state.effectIntroFadeInDurationMs;
            effectIntroHoldDurationMs = state.effectIntroHoldDurationMs;
            effectIntroFadeOutDurationMs = state.effectIntroFadeOutDurationMs;
            healthAnimationDurationMs = state.healthAnimationDurationMs;
            snapX = state.snapX; snapY = state.snapY;
            manualPanelPosition = state.manualPanelPosition; panelX = state.panelX; panelY = state.panelY;
            panelCollapsed = state.panelCollapsed;
            editorScreenWidth = state.editorScreenWidth; editorScreenHeight = state.editorScreenHeight;
        } catch (Exception ignored) {
        }
    }
    public static void save() {
        load();
        try {
            Files.createDirectories(FILE.getParent());
            JsonObject root = new JsonObject();
            root.addProperty("manualPosition", manualPosition);
            root.addProperty("x", x);
            root.addProperty("y", y);
            root.addProperty("scalePercent", scalePercent);
            root.addProperty("opacityPercent", opacityPercent);
            root.addProperty("manualInventoryEffectPosition", manualInventoryEffectPosition);
            root.addProperty("inventoryEffectX", inventoryEffectX);
            root.addProperty("inventoryEffectY", inventoryEffectY);
            root.addProperty("inventoryEffectScalePercent", inventoryEffectScalePercent);
            root.addProperty("inventoryEffectOpacityPercent", inventoryEffectOpacityPercent);
            root.addProperty("inventoryLayoutScalePercent", inventoryLayoutScalePercent);
            root.addProperty("inventoryLabelScalePercent", inventoryLabelScalePercent);
            root.addProperty("inventoryLayoutMarginPercent", inventoryLayoutMarginPercent);
            root.addProperty("manualItemDetailPosition", manualItemDetailPosition);
            root.addProperty("itemDetailX", itemDetailX);
            root.addProperty("itemDetailY", itemDetailY);
            root.addProperty("itemDetailWidth", itemDetailWidth);
            root.addProperty("itemDetailHeight", itemDetailHeight);
            root.addProperty("itemDetailAutoSize", itemDetailAutoSize);
            root.addProperty("itemDetailAutoWidth", itemDetailAutoWidth);
            root.addProperty("itemDetailAutoHeight", itemDetailAutoHeight);
            root.addProperty("itemDetailScalePercent", itemDetailScalePercent);
            root.addProperty("manualHealthPosition", manualHealthPosition);
            root.addProperty("healthX", healthX);
            root.addProperty("healthY", healthY);
            root.addProperty("healthScalePercent", healthScalePercent);
            root.addProperty("healthOpacityPercent", healthOpacityPercent);
            root.addProperty("manualIntroPosition", manualIntroPosition);
            root.addProperty("introX", introX);
            root.addProperty("introY", introY);
            root.addProperty("introScalePercent", introScalePercent);
            root.addProperty("introOpacityPercent", introOpacityPercent);
            root.addProperty("manualStaminaPosition", manualStaminaPosition);
            root.addProperty("staminaX", staminaX);
            root.addProperty("staminaY", staminaY);
            root.addProperty("staminaScalePercent", staminaScalePercent);
            root.addProperty("staminaOpacityPercent", staminaOpacityPercent);
            root.addProperty("manualDownedPosition", manualDownedPosition);
            root.addProperty("downedX", downedX);
            root.addProperty("downedY", downedY);
            root.addProperty("downedScalePercent", downedScalePercent);
            root.addProperty("downedOpacityPercent", downedOpacityPercent);
            root.addProperty("manualRescuePosition", manualRescuePosition);
            root.addProperty("rescueX", rescueX);
            root.addProperty("rescueY", rescueY);
            root.addProperty("rescueScalePercent", rescueScalePercent);
            root.addProperty("rescueOpacityPercent", rescueOpacityPercent);
            root.addProperty("manualContextPosition", manualContextPosition);
            root.addProperty("contextX", contextX);
            root.addProperty("contextY", contextY);
            root.addProperty("contextScalePercent", contextScalePercent);
            root.addProperty("contextOpacityPercent", contextOpacityPercent);
            root.addProperty("effectIntroFadeInDurationMs", effectIntroFadeInDurationMs);
            root.addProperty("effectIntroHoldDurationMs", effectIntroHoldDurationMs);
            root.addProperty("effectIntroFadeOutDurationMs", effectIntroFadeOutDurationMs);
            root.addProperty("healthAnimationDurationMs", healthAnimationDurationMs);
            root.addProperty("snapX", snapX);
            root.addProperty("snapY", snapY);
            root.addProperty("manualPanelPosition", manualPanelPosition);
            root.addProperty("panelX", panelX);
            root.addProperty("panelY", panelY);
            root.addProperty("panelCollapsed", panelCollapsed);
            root.addProperty("editorScreenWidth", editorScreenWidth);
            root.addProperty("editorScreenHeight", editorScreenHeight);
            Files.writeString(FILE, GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (Exception ignored) {
        }
    }

    private static void load() {
        if (loaded) return;
        loaded = true;
        try {
            if (!Files.exists(FILE)) return;
            JsonObject root = JsonParser.parseString(
                Files.readString(FILE, StandardCharsets.UTF_8)).getAsJsonObject();
            manualPosition = root.has("manualPosition") && root.get("manualPosition").getAsBoolean();
            x = root.has("x") ? root.get("x").getAsInt() : 0;
            y = root.has("y") ? root.get("y").getAsInt() : 0;
            scalePercent = root.has("scalePercent")
                ? clamp(root.get("scalePercent").getAsInt(), 50, 200) : 100;
            opacityPercent = root.has("opacityPercent")
                ? clamp(root.get("opacityPercent").getAsInt(), 10, 100) : 100;
            manualInventoryEffectPosition = root.has("manualInventoryEffectPosition")
                && root.get("manualInventoryEffectPosition").getAsBoolean();
            inventoryEffectX = root.has("inventoryEffectX")
                ? root.get("inventoryEffectX").getAsInt() : 0;
            inventoryEffectY = root.has("inventoryEffectY")
                ? root.get("inventoryEffectY").getAsInt() : 0;
            inventoryEffectScalePercent = root.has("inventoryEffectScalePercent")
                ? clamp(root.get("inventoryEffectScalePercent").getAsInt(), 50, 200) : 100;
            inventoryEffectOpacityPercent = root.has("inventoryEffectOpacityPercent")
                ? clamp(root.get("inventoryEffectOpacityPercent").getAsInt(), 10, 100) : 100;
            inventoryLayoutScalePercent = root.has("inventoryLayoutScalePercent")
                ? clamp(root.get("inventoryLayoutScalePercent").getAsInt(), 60, 500) : 150;
            inventoryLabelScalePercent = root.has("inventoryLabelScalePercent")
                ? clamp(root.get("inventoryLabelScalePercent").getAsInt(), 50, 160) : 100;
            inventoryLayoutMarginPercent = root.has("inventoryLayoutMarginPercent")
                ? clamp(root.get("inventoryLayoutMarginPercent").getAsInt(), 0, 40) : 0;
            manualItemDetailPosition = root.has("manualItemDetailPosition")
                && root.get("manualItemDetailPosition").getAsBoolean();
            itemDetailX = root.has("itemDetailX") ? root.get("itemDetailX").getAsInt() : 0;
            itemDetailY = root.has("itemDetailY") ? root.get("itemDetailY").getAsInt() : 0;
            itemDetailWidth = root.has("itemDetailWidth")
                ? ItemDetailLayout.clampWidth(root.get("itemDetailWidth").getAsInt()) : 180;
            itemDetailHeight = root.has("itemDetailHeight")
                ? ItemDetailLayout.clampHeight(root.get("itemDetailHeight").getAsInt()) : 90;
            itemDetailAutoSize = !root.has("itemDetailAutoSize")
                || root.get("itemDetailAutoSize").getAsBoolean();
            boolean legacyAutoSize = itemDetailAutoSize;
            itemDetailAutoWidth = root.has("itemDetailAutoWidth")
                ? root.get("itemDetailAutoWidth").getAsBoolean() : legacyAutoSize;
            itemDetailAutoHeight = root.has("itemDetailAutoHeight")
                ? root.get("itemDetailAutoHeight").getAsBoolean() : legacyAutoSize;
            itemDetailScalePercent = root.has("itemDetailScalePercent")
                ? clamp(root.get("itemDetailScalePercent").getAsInt(), 50, 200) : 100;
            manualHealthPosition = root.has("manualHealthPosition")
                && root.get("manualHealthPosition").getAsBoolean();
            healthX = root.has("healthX") ? root.get("healthX").getAsInt() : 0;
            healthY = root.has("healthY") ? root.get("healthY").getAsInt() : 0;
            healthScalePercent = root.has("healthScalePercent")
                ? clamp(root.get("healthScalePercent").getAsInt(), 50, 200) : 100;
            healthOpacityPercent = root.has("healthOpacityPercent")
                ? clamp(root.get("healthOpacityPercent").getAsInt(), 10, 100) : 100;
            manualIntroPosition = root.has("manualIntroPosition")
                && root.get("manualIntroPosition").getAsBoolean();
            introX = root.has("introX") ? root.get("introX").getAsInt() : 0;
            introY = root.has("introY") ? root.get("introY").getAsInt() : 0;
            introScalePercent = root.has("introScalePercent")
                ? clamp(root.get("introScalePercent").getAsInt(), 50, 200) : 100;
            introOpacityPercent = root.has("introOpacityPercent")
                ? clamp(root.get("introOpacityPercent").getAsInt(), 10, 100) : 100;
            manualStaminaPosition = root.has("manualStaminaPosition")
                && root.get("manualStaminaPosition").getAsBoolean();
            staminaX = root.has("staminaX") ? root.get("staminaX").getAsInt() : 0;
            staminaY = root.has("staminaY") ? root.get("staminaY").getAsInt() : 0;
            staminaScalePercent = root.has("staminaScalePercent")
                ? clamp(root.get("staminaScalePercent").getAsInt(), 50, 200) : 100;
            staminaOpacityPercent = root.has("staminaOpacityPercent")
                ? clamp(root.get("staminaOpacityPercent").getAsInt(), 10, 100) : 100;
            manualDownedPosition = root.has("manualDownedPosition")
                && root.get("manualDownedPosition").getAsBoolean();
            downedX = root.has("downedX") ? root.get("downedX").getAsInt() : 0;
            downedY = root.has("downedY") ? root.get("downedY").getAsInt() : 0;
            downedScalePercent = root.has("downedScalePercent")
                ? clamp(root.get("downedScalePercent").getAsInt(), 50, 200) : 100;
            downedOpacityPercent = root.has("downedOpacityPercent")
                ? clamp(root.get("downedOpacityPercent").getAsInt(), 10, 100) : 100;
            manualRescuePosition = root.has("manualRescuePosition")
                && root.get("manualRescuePosition").getAsBoolean();
            rescueX = root.has("rescueX") ? root.get("rescueX").getAsInt() : 0;
            rescueY = root.has("rescueY") ? root.get("rescueY").getAsInt() : 0;
            rescueScalePercent = root.has("rescueScalePercent")
                ? clamp(root.get("rescueScalePercent").getAsInt(), 50, 200) : 100;
            rescueOpacityPercent = root.has("rescueOpacityPercent")
                ? clamp(root.get("rescueOpacityPercent").getAsInt(), 10, 100) : 100;
            manualContextPosition = root.has("manualContextPosition")
                && root.get("manualContextPosition").getAsBoolean();
            contextX = root.has("contextX") ? root.get("contextX").getAsInt() : 0;
            contextY = root.has("contextY") ? root.get("contextY").getAsInt() : 0;
            contextScalePercent = root.has("contextScalePercent")
                ? clamp(root.get("contextScalePercent").getAsInt(), 50, 200) : 100;
            contextOpacityPercent = root.has("contextOpacityPercent")
                ? clamp(root.get("contextOpacityPercent").getAsInt(), 10, 100) : 100;
            effectIntroFadeInDurationMs = root.has("effectIntroFadeInDurationMs")
                ? clamp(root.get("effectIntroFadeInDurationMs").getAsInt(), 50, 2_000) : 200;
            effectIntroHoldDurationMs = root.has("effectIntroHoldDurationMs")
                ? clamp(root.get("effectIntroHoldDurationMs").getAsInt(), 100, 5_000)
                : root.has("effectAnimationDurationMs")
                    ? clamp(root.get("effectAnimationDurationMs").getAsInt(), 100, 5_000)
                    : 1_000;
            effectIntroFadeOutDurationMs = root.has("effectIntroFadeOutDurationMs")
                ? clamp(root.get("effectIntroFadeOutDurationMs").getAsInt(), 50, 2_000) : 250;
            healthAnimationDurationMs = root.has("healthAnimationDurationMs")
                ? clamp(root.get("healthAnimationDurationMs").getAsInt(), 50, 500) : 200;
            snapX = !root.has("snapX") || root.get("snapX").getAsBoolean();
            snapY = !root.has("snapY") || root.get("snapY").getAsBoolean();
            manualPanelPosition = root.has("manualPanelPosition")
                && root.get("manualPanelPosition").getAsBoolean();
            panelX = root.has("panelX") ? root.get("panelX").getAsInt() : 0;
            panelY = root.has("panelY") ? root.get("panelY").getAsInt() : 0;
            panelCollapsed = root.has("panelCollapsed")
                && root.get("panelCollapsed").getAsBoolean();
            editorScreenWidth = root.has("editorScreenWidth")
                ? Math.max(0, root.get("editorScreenWidth").getAsInt()) : 0;
            editorScreenHeight = root.has("editorScreenHeight")
                ? Math.max(0, root.get("editorScreenHeight").getAsInt()) : 0;
        } catch (Exception ignored) {
            manualPosition = false;
            scalePercent = 100;
            opacityPercent = 100;
            inventoryLayoutScalePercent = 150;
            inventoryLabelScalePercent = 100;
            inventoryLayoutMarginPercent = 0;
            manualItemDetailPosition = false;
            itemDetailWidth = 180;
            itemDetailHeight = 90;
            itemDetailAutoSize = true;
            itemDetailAutoWidth = true;
            itemDetailAutoHeight = true;
            itemDetailScalePercent = 100;
            manualHealthPosition = false;
            healthScalePercent = 100;
            healthOpacityPercent = 100;
            manualIntroPosition = false;
            introScalePercent = 100;
            introOpacityPercent = 100;
            manualStaminaPosition = false;
            staminaScalePercent = 100;
            staminaOpacityPercent = 100;
            manualDownedPosition = false;
            downedScalePercent = 100;
            downedOpacityPercent = 100;
            manualRescuePosition = false;
            rescueScalePercent = 100;
            rescueOpacityPercent = 100;
            manualContextPosition = false;
            contextScalePercent = 100;
            contextOpacityPercent = 100;
            effectIntroFadeInDurationMs = 200;
            effectIntroHoldDurationMs = 1_000;
            effectIntroFadeOutDurationMs = 250;
            healthAnimationDurationMs = 200;
            snapX = true;
            snapY = true;
            manualPanelPosition = false;
            panelCollapsed = false;
            editorScreenWidth = 0;
            editorScreenHeight = 0;
        }
    }

    private static int scaled(int value, double ratio) {
        return (int) Math.round(value * ratio);
    }

    private static int scaledPercent(int value, double ratio, int minimum, int maximum) {
        return clamp((int) Math.round(value * ratio), minimum, maximum);
    }

    private record EditorState(
        boolean manualPosition, int x, int y, int scalePercent, int opacityPercent,
        boolean manualInventoryEffectPosition, int inventoryEffectX, int inventoryEffectY,
        int inventoryEffectScalePercent, int inventoryEffectOpacityPercent,
        int inventoryLayoutScalePercent, int inventoryLabelScalePercent,
        int inventoryLayoutMarginPercent,
        boolean manualItemDetailPosition, int itemDetailX, int itemDetailY,
        int itemDetailWidth, int itemDetailHeight, boolean itemDetailAutoSize,
        boolean itemDetailAutoWidth, boolean itemDetailAutoHeight, int itemDetailScalePercent,
        boolean manualHealthPosition, int healthX, int healthY, int healthScalePercent, int healthOpacityPercent,
        boolean manualIntroPosition, int introX, int introY, int introScalePercent, int introOpacityPercent,
        boolean manualStaminaPosition, int staminaX, int staminaY, int staminaScalePercent, int staminaOpacityPercent,
        boolean manualDownedPosition, int downedX, int downedY, int downedScalePercent, int downedOpacityPercent,
        boolean manualRescuePosition, int rescueX, int rescueY, int rescueScalePercent, int rescueOpacityPercent,
        boolean manualContextPosition, int contextX, int contextY, int contextScalePercent, int contextOpacityPercent,
        int effectIntroFadeInDurationMs, int effectIntroHoldDurationMs, int effectIntroFadeOutDurationMs,
        int healthAnimationDurationMs, boolean snapX, boolean snapY,
        boolean manualPanelPosition, int panelX, int panelY, boolean panelCollapsed,
        int editorScreenWidth, int editorScreenHeight) {
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
