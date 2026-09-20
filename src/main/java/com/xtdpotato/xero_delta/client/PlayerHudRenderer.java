package com.xtdpotato.xero_delta.client;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

/** Delta-style percentage health panel rendered at its independently configurable HUD position. */
public final class PlayerHudRenderer {
    private static final int WIDTH = 226;
    private static final int HEIGHT = 48;
    private static final int FACE = 30;
    private static final int BAR_WIDTH = 178;
    private static final int BAR_HEIGHT = 6;
    private static final int EQUIPMENT_ICON_SIZE = 12;
    private static final int EQUIPMENT_ENTRY_GAP = 4;
    private static final float DAMAGE_TRAIL_SECONDS = 0.50F;
    private static final long DAMAGE_TRAIL_HOLD_MS = 120L;
    private static final HealthValueAnimator HEALTH_ANIMATOR = new HealthValueAnimator();
    private static float animatedHealth = -1.0F;
    private static float damageTrail = -1.0F;
    private static long lastFrameMs;
    private static long damageTrailHoldUntil;

    private PlayerHudRenderer() {
    }

    public static void render(GuiGraphics graphics, Minecraft minecraft) {
        renderLowHealthEdges(graphics, minecraft);
        renderAtConfiguredPosition(graphics, minecraft, false);
    }

    private static void renderLowHealthEdges(GuiGraphics graphics, Minecraft minecraft) {
        var player = minecraft.player;
        if (player == null || player.isSpectator() || !player.isAlive()) return;
        float fraction = HealthHudMath.currentFraction(player.getHealth(), player.getMaxHealth(),
            PlayerStatusClientState.INSTANCE.healthPenalty());
        float opacity = HealthHudMath.lowHealthEdgeOpacity(fraction);
        if (opacity <= 0.0F) return;
        int width = minecraft.getWindow().getGuiScaledWidth();
        int height = minecraft.getWindow().getGuiScaledHeight();
        int depth = Math.max(1, Math.round(Math.min(width, height) * 0.20F));
        // Disjoint rings avoid stacking alpha at the corners above the 80% cap.
        for (int inset = 0; inset < depth; inset++) {
            float fade = 1.0F - (float) inset / depth;
            int color = Math.round(255 * opacity * fade * fade) << 24;
            int right = width - inset;
            int bottom = height - inset;
            graphics.fill(inset, inset, right, inset + 1, color);
            graphics.fill(inset, bottom - 1, right, bottom, color);
            graphics.fill(inset, inset + 1, inset + 1, bottom - 1, color);
            graphics.fill(right - 1, inset + 1, right, bottom - 1, color);
        }
    }

    public static StatusEffectHudRenderer.Bounds renderPreview(GuiGraphics graphics,
                                                                 Minecraft minecraft) {
        return renderAtConfiguredPosition(graphics, minecraft, true);
    }

    /** Returns the occupied health-panel rectangle without drawing it. */
    public static StatusEffectHudRenderer.Bounds bounds(Minecraft minecraft) {
        if (minecraft.player == null || (minecraft.player.isSpectator()
            && !DownedClientState.INSTANCE.downed())) {
            return StatusEffectHudRenderer.Bounds.EMPTY;
        }
        float scale = StatusEffectHudState.healthScale();
        int scaledWidth = Math.round(WIDTH * scale);
        int scaledHeight = Math.round(HEIGHT * scale);
        int screenWidth = minecraft.getWindow().getGuiScaledWidth();
        int screenHeight = minecraft.getWindow().getGuiScaledHeight();
        int[] position = StatusEffectHudState.healthPosition(8, screenHeight - scaledHeight - 5,
            screenWidth, screenHeight, scaledWidth, scaledHeight);
        return new StatusEffectHudRenderer.Bounds(position[0], position[1], scaledWidth, scaledHeight);
    }

    private static StatusEffectHudRenderer.Bounds renderAtConfiguredPosition(
        GuiGraphics graphics, Minecraft minecraft, boolean preview) {
        if (minecraft.player == null || (!preview && minecraft.player.isSpectator()
            && !DownedClientState.INSTANCE.downed())) {
            return StatusEffectHudRenderer.Bounds.EMPTY;
        }
        float scale = StatusEffectHudState.healthScale();
        StatusEffectHudRenderer.Bounds bounds = bounds(minecraft);
        int[] position = new int[]{bounds.x(), bounds.y()};

        if (!preview) TeamHudRenderer.renderAbove(graphics, minecraft,
            position[0], position[1], scale);

        graphics.pose().pushPose();
        graphics.pose().translate(position[0], position[1], 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        drawHud(graphics, minecraft);
        graphics.pose().popPose();
        return bounds;
    }

    private static void drawHud(GuiGraphics graphics, Minecraft minecraft) {
        var player = minecraft.player;
        PlayerStatusClientState status = PlayerStatusClientState.INSTANCE;
        DownedClientState downed = DownedClientState.INSTANCE;
        double penalty = status.healthPenalty();

        int x = 0;
        int y = 0;

        int faceX = x + 5;
        int faceY = y + 9;
        graphics.fill(faceX - 1, faceY - 1, faceX + FACE + 1, faceY + FACE + 1, 0x6612191C);
        if (downed.redDown()) {
            drawEcgIcon(graphics, faceX, faceY, 0xFFFF342E);
        } else if (downed.yellowDown()) {
            drawRescueIcon(graphics, faceX, faceY, 0xFFFFC83D);
        } else {
            PlayerFaceRenderer.draw(graphics, player.getSkin(), faceX, faceY, FACE);
        }
        drawRescueRequestPulse(graphics, player.getUUID(), faceX, faceY, FACE);
        if (downed.downed()) {
            int progressY = faceY + FACE + 2;
            int progressColor = downed.redDown() ? 0xFFFF342E : 0xFFFFC83D;
            int progressWidth = Math.round(FACE * downed.progress());
            graphics.fill(faceX, progressY, faceX + FACE, progressY + 3, 0xFF221B1C);
            if (progressWidth > 0) {
                graphics.fill(faceX, progressY, faceX + progressWidth,
                    progressY + 3, progressColor);
            }
        }

        int contentX = faceX + FACE + 7;
        float target = HealthHudMath.currentFraction(
            player.getHealth(), player.getMaxHealth(), penalty);
        updateAnimation(target);
        int percent = Math.round(target * 100.0F);
        boolean critical = !downed.downed() && target < 0.20F;
        int healthColor = critical ? 0xFFFF4038 : 0xFFF0F3F2;
        if (!downed.downed()) {
            graphics.drawString(minecraft.font, percent + "/100", contentX, y + 5,
                healthColor, false);
            if (target < 0.50F) {
                drawBloodOverlay(graphics, x, y, WIDTH, HEIGHT,
                    1.0F - target / 0.50F);
            }
        }

        int barX = contentX;
        int barY = y + 18;
        if (!downed.downed()) {
            graphics.fill(barX, barY, barX + BAR_WIDTH, barY + BAR_HEIGHT, 0xFF1A2225);
            graphics.renderOutline(barX - 1, barY - 1, BAR_WIDTH + 2, BAR_HEIGHT + 2,
                0xFF69767A);

            int currentPixels = Math.round(BAR_WIDTH * animatedHealth);
            int damagePixels = Math.round(BAR_WIDTH * Math.max(animatedHealth, damageTrail));
            if (damagePixels > currentPixels) {
                graphics.fill(barX + currentPixels, barY,
                    barX + damagePixels, barY + BAR_HEIGHT, 0xFFE93D35);
            }
            if (currentPixels > 0) {
                graphics.fill(barX, barY, barX + currentPixels, barY + BAR_HEIGHT,
                    healthColor);
            }

            int availablePixels = HealthHudMath.availablePixels(BAR_WIDTH, penalty);
            if (availablePixels < BAR_WIDTH) {
                drawDashedLoss(graphics, barX + availablePixels, barY,
                    BAR_WIDTH - availablePixels, BAR_HEIGHT);
            }
        }

        ItemStack helmet = downed.downed()
            ? ItemStack.EMPTY : player.getItemBySlot(EquipmentSlot.HEAD);
        ItemStack chestplate = downed.downed()
            ? ItemStack.EMPTY : player.getItemBySlot(EquipmentSlot.CHEST);
        int helmetWidth = downed.downed() ? 0 : EQUIPMENT_ICON_SIZE;
        int chestplateWidth = downed.downed() ? 0 : EQUIPMENT_ICON_SIZE;
        int equipmentWidth = helmetWidth + chestplateWidth;
        if (helmetWidth > 0 && chestplateWidth > 0) {
            equipmentWidth += EQUIPMENT_ENTRY_GAP;
        }

        int rowRight = x + WIDTH - 5;
        int equipmentX = rowRight - equipmentWidth;

        int nextEquipmentX = equipmentX;
        if (!downed.downed()) {
            drawCompactEquipment(graphics, helmet, nextEquipmentX, y + 3, true);
            nextEquipmentX += helmetWidth + EQUIPMENT_ENTRY_GAP;
            drawCompactEquipment(graphics, chestplate, nextEquipmentX, y + 3, false);
        }

        int nameWidth = Math.max(0, rowRight - contentX);
        String name = player.getGameProfile().getName();
        if (minecraft.font.width(name) > nameWidth) {
            int ellipsisWidth = minecraft.font.width("...");
            name = nameWidth > ellipsisWidth
                ? minecraft.font.plainSubstrByWidth(name, nameWidth - ellipsisWidth) + "..."
                : "";
        }
        if (!name.isEmpty()) {
            graphics.drawString(minecraft.font, name, contentX,
                downed.downed() ? y + 18 : y + 32, 0xFFD8DEDC, false);
        }
    }
    static void drawEcgIcon(GuiGraphics graphics, int x, int y, int color) {
        int mid = y + FACE / 2;
        drawPixelLine(graphics, x + 3, mid, x + 8, mid, color);
        drawPixelLine(graphics, x + 8, mid, x + 11, y + 10, color);
        drawPixelLine(graphics, x + 11, y + 10, x + 14, y + 22, color);
        drawPixelLine(graphics, x + 14, y + 22, x + 18, y + 6, color);
        drawPixelLine(graphics, x + 18, y + 6, x + 21, mid, color);
        drawPixelLine(graphics, x + 21, mid, x + 27, mid, color);
    }

    static void drawRescueIcon(GuiGraphics graphics, int x, int y, int color) {
        int centerX = x + FACE / 2;
        int centerY = y + FACE / 2;
        drawPixelLine(graphics, centerX, y + 3, x + FACE - 4, centerY, color);
        drawPixelLine(graphics, x + FACE - 4, centerY, centerX, y + FACE - 4, color);
        drawPixelLine(graphics, centerX, y + FACE - 4, x + 3, centerY, color);
        drawPixelLine(graphics, x + 3, centerY, centerX, y + 3, color);
        graphics.fill(centerX - 2, y + 8, centerX + 3, y + FACE - 7, color);
        graphics.fill(x + 8, centerY - 2, x + FACE - 7, centerY + 3, color);
    }

    static void drawRescueRequestPulse(GuiGraphics graphics, UUID playerId,
                                       int x, int y, int size) {
        RescueRequestClientState.Sample sample = RescueRequestClientState.INSTANCE.sample(playerId);
        if (!sample.active()) return;
        float doubled = sample.progress() * 2.0F;
        float phase = doubled - (float) Math.floor(doubled);
        float fade = 1.0F - phase;
        int expansion = Math.round(phase * 9.0F);
        int alpha = Math.max(0, Math.min(255, Math.round(255.0F * fade * fade)));
        int rgb = sample.stage() == 2 ? 0x00FFC83D : 0x00FF342E;
        int color = (alpha << 24) | rgb;
        graphics.renderOutline(x - 1 - expansion, y - 1 - expansion,
            size + 2 + expansion * 2, size + 2 + expansion * 2, color);
    }
    private static void drawPixelLine(GuiGraphics graphics, int x0, int y0,
                                      int x1, int y1, int color) {
        int dx = Math.abs(x1 - x0);
        int sx = x0 < x1 ? 1 : -1;
        int dy = -Math.abs(y1 - y0);
        int sy = y0 < y1 ? 1 : -1;
        int error = dx + dy;
        while (true) {
            graphics.fill(x0, y0, x0 + 2, y0 + 2, color);
            if (x0 == x1 && y0 == y1) break;
            int doubled = error * 2;
            if (doubled >= dy) {
                error += dy;
                x0 += sx;
            }
            if (doubled <= dx) {
                error += dx;
                y0 += sy;
            }
        }
    }

    private static void drawBloodOverlay(GuiGraphics graphics, int x, int y,
                                         int width, int height, float severity) {
        int phase = (int) (Util.getMillis() / 90L);
        float strength = 0.45F + clamp01(severity) * 0.55F;
        for (int side = 0; side < 2; side++) {
            for (int i = 0; i < 10; i++) {
                int py = y + 3 + Math.floorMod(i * 13 + phase * (i % 3 + 1), height - 7);
                int reach = 18 + Math.floorMod(i * 19 + phase * 3, 45);
                int thickness = 1 + (i % 3 == 0 ? 1 : 0);
                int alpha = Math.max(30, Math.min(175,
                    Math.round((64 + Math.floorMod(i * 31 + phase * 7, 92)) * strength)));
                int color = (alpha << 24) | 0x009B1714;
                if (side == 0) {
                    graphics.fill(x - 8, py, x + reach, py + thickness, color);
                    graphics.fill(x - 3, py + thickness, x + Math.max(4, reach - 12),
                        py + thickness + 1, color);
                } else {
                    graphics.fill(x + width - reach, py, x + width + 12,
                        py + thickness, color);
                    graphics.fill(x + width - Math.max(4, reach - 12), py + thickness,
                        x + width + 5, py + thickness + 1, color);
                }
            }
        }
    }
    private static void updateAnimation(float target) {
        long now = Util.getMillis();
        float previousActual = HEALTH_ANIMATOR.actualTarget();
        boolean hadActual = HEALTH_ANIMATOR.initialized();
        boolean tookDamage = hadActual && target < previousActual - 0.0001F;
        animatedHealth = HEALTH_ANIMATOR.update(target, now,
            StatusEffectHudState.healthAnimationDurationMs());
        if (lastFrameMs == 0L) damageTrail = target;
        float deltaSeconds = Math.min(0.10F, Math.max(0.0F, (now - lastFrameMs) / 1000.0F));
        lastFrameMs = now;
        if (tookDamage) {
            damageTrail = Math.max(damageTrail, previousActual);
            damageTrailHoldUntil = now + DAMAGE_TRAIL_HOLD_MS;
        }
        if (target >= damageTrail) {
            damageTrail = animatedHealth;
        } else if (now >= damageTrailHoldUntil) {
            damageTrail = approach(damageTrail, animatedHealth,
                deltaSeconds / DAMAGE_TRAIL_SECONDS);
        }
        animatedHealth = clamp01(animatedHealth);
        damageTrail = clamp01(Math.max(animatedHealth, damageTrail));
    }

    private static float approach(float current, float target, float amount) {
        float alpha = Math.max(0.0F, Math.min(1.0F, amount));
        return current + (target - current) * alpha;
    }

    private static void drawCompactEquipment(GuiGraphics graphics, ItemStack stack,
                                              int x, int y, boolean helmet) {
        boolean missing = stack.isEmpty();
        int percent = missing ? 0 : durabilityPercent(stack);
        boolean broken = !missing && percent <= 0;
        int splitY = y + EQUIPMENT_ICON_SIZE
            - Math.round(EQUIPMENT_ICON_SIZE * percent / 100.0F);
        for (int py = 0; py < EQUIPMENT_ICON_SIZE; py++) {
            for (int px = 0; px < EQUIPMENT_ICON_SIZE; px++) {
                if (!(helmet ? helmetPixel(px, py) : chestPixel(px, py))) continue;
                int color = broken ? 0xFFE73D38
                    : py + y >= splitY ? 0xFFF0F3F2 : 0xFF657074;
                graphics.fill(x + px, y + py, x + px + 1, y + py + 1, color);
            }
        }
        if (missing || broken) {
            int crossColor = broken ? 0xFF641719 : 0xFF252D30;
            for (int offset = 1; offset < EQUIPMENT_ICON_SIZE - 1; offset++) {
                graphics.fill(x + offset, y + offset, x + offset + 2, y + offset + 2,
                    crossColor);
                graphics.fill(x + EQUIPMENT_ICON_SIZE - offset - 2, y + offset,
                    x + EQUIPMENT_ICON_SIZE - offset, y + offset + 2, crossColor);
            }
        }
    }

    private static boolean helmetPixel(int x, int y) {
        if (y == 1) return x >= 3 && x <= 8;
        if (y == 2) return x >= 2 && x <= 9;
        if (y >= 3 && y <= 6) return x >= 1 && x <= 10;
        if (y == 7) return x == 1 || x == 2 || x == 9 || x == 10;
        if (y == 8) return x == 2 || x == 9;
        return false;
    }

    private static boolean chestPixel(int x, int y) {
        if (y == 1) return (x >= 1 && x <= 3) || (x >= 8 && x <= 10);
        if (y == 2) return x != 5 && x != 6;
        if (y >= 3 && y <= 8) return x >= 1 && x <= 10;
        if (y == 9) return x >= 2 && x <= 9;
        if (y == 10) return x >= 3 && x <= 8;
        return false;
    }

    private static int durabilityPercent(ItemStack stack) {
        if (!stack.isDamageableItem() || stack.getMaxDamage() <= 0) return 100;
        int remaining = Math.max(0, stack.getMaxDamage() - stack.getDamageValue() - 1);
        return Math.max(0, Math.min(100,
            Math.round(remaining * 100.0F / Math.max(1, stack.getMaxDamage() - 1))));
    }

    private static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    private static void drawDashedLoss(GuiGraphics graphics, int x, int y,
                                       int width, int height) {
        graphics.fill(x, y, x + width, y + height, 0xCC351C1D);
        for (int offset = 0; offset < width; offset += 4) {
            int end = Math.min(width, offset + 2);
            graphics.fill(x + offset, y, x + end, y + 1, 0xFFFF7870);
            graphics.fill(x + offset, y + height - 1, x + end, y + height,
                0xFFFF7870);
        }
        graphics.fill(x, y, x + 1, y + height, 0xFFFF7870);
    }
}
