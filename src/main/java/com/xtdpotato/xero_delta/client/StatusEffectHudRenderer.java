package com.xtdpotato.xero_delta.client;

import com.xtdpotato.xero_delta.XeroDelta;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Scalable effect icons rendered above the hotbar. */
public final class StatusEffectHudRenderer {
    private static final int CELL = 26;
    private static final int ICON = 18;
    private static final int COLUMNS = 8;
    private static final EffectIntroQueue<EffectKey> INTRO_QUEUE = new EffectIntroQueue<>();
    private static final Map<EffectKey, Integer> MAX_DURATIONS = new LinkedHashMap<>();
    private static UUID animationPlayer;

    private StatusEffectHudRenderer() {
    }

    public static Bounds render(GuiGraphics graphics, Minecraft minecraft, int mouseX, int mouseY) {
        if (minecraft.player == null) {
            INTRO_QUEUE.reset();
            MAX_DURATIONS.clear();
            animationPlayer = null;
            return Bounds.EMPTY;
        }
        UUID playerId = minecraft.player.getUUID();
        if (!playerId.equals(animationPlayer)) {
            INTRO_QUEUE.reset();
            MAX_DURATIONS.clear();
            animationPlayer = playerId;
        }
        List<MobEffectInstance> effects = new ArrayList<>(minecraft.player.getActiveEffects());
        // Respect vanilla's icon visibility flag. Localized injury markers are server-side
        // state carriers and must not appear beside their derived fracture/pain effect.
        effects.removeIf(effect -> !effect.showIcon());
        effects.sort(Comparator
            .comparing((MobEffectInstance value) -> value.getEffect().value().isBeneficial()).reversed()
            .thenComparing(value -> value.getEffect().value().getDisplayName().getString()));
        Map<EffectKey, MobEffectInstance> current = new LinkedHashMap<>();
        for (MobEffectInstance effect : effects) {
            EffectKey key = effectKey(effect);
            current.put(key, effect);
            if (effect.getDuration() >= 0) {
                MAX_DURATIONS.merge(key, Math.max(1, effect.getDuration()), Math::max);
            }
        }
        MAX_DURATIONS.keySet().retainAll(current.keySet());
        EffectIntroQueue.Frame<EffectKey> intro = INTRO_QUEUE.update(current.keySet(), Util.getMillis(),
            StatusEffectHudState.effectIntroFadeInDurationMs(),
            StatusEffectHudState.effectIntroHoldDurationMs(),
            StatusEffectHudState.effectIntroFadeOutDurationMs());
        List<MobEffectInstance> visible = effects.stream()
            .filter(effect -> !intro.withheld().contains(effectKey(effect)))
            .toList();
        if (visible.isEmpty() && !intro.hasActive()) return Bounds.EMPTY;
        float scale = StatusEffectHudState.scale();
        int layoutCount = Math.max(1, visible.size());
        int columns = Math.min(COLUMNS, layoutCount);
        int rows = (layoutCount + COLUMNS - 1) / COLUMNS;
        int width = Math.round(columns * CELL * scale);
        int height = Math.round(rows * CELL * scale);
        int defaultX = (minecraft.getWindow().getGuiScaledWidth() - width) / 2;
        int defaultY = minecraft.getWindow().getGuiScaledHeight() - 49 - height;
        int[] position = StatusEffectHudState.position(defaultX, defaultY,
            minecraft.getWindow().getGuiScaledWidth(), minecraft.getWindow().getGuiScaledHeight(),
            width, height);
        if (!visible.isEmpty()) {
            drawEffects(graphics, minecraft, visible, position[0], position[1],
                scale, mouseX, mouseY);
        }
        if (intro.hasActive()) {
            MobEffectInstance animated = current.get(intro.active());
            if (animated != null) {
                float introScale = StatusEffectHudState.introScale();
                int introCell = Math.round(CELL * introScale);
                int defaultIntroX = Math.max(2, position[0] - introCell - 6);
                int defaultIntroY = position[1];
                int[] introPosition = StatusEffectHudState.introPosition(
                    defaultIntroX, defaultIntroY,
                    minecraft.getWindow().getGuiScaledWidth(),
                    minecraft.getWindow().getGuiScaledHeight(), introCell, introCell);
                renderIntro(graphics, minecraft, animated, introPosition[0], introPosition[1],
                    introScale, intro.progress());
            }
        }
        return new Bounds(position[0], position[1], width, height);
    }

    public static Bounds renderPreview(GuiGraphics graphics, Font font, int screenWidth, int screenHeight,
                                       int mouseX, int mouseY) {
        float scale = StatusEffectHudState.scale();
        int count = 7;
        int width = Math.round(count * CELL * scale);
        int height = Math.round(CELL * scale);
        int defaultX = (screenWidth - width) / 2;
        int defaultY = screenHeight - 49 - height;
        int[] position = StatusEffectHudState.position(defaultX, defaultY,
            screenWidth, screenHeight, width, height);
        graphics.pose().pushPose();
        graphics.pose().translate(position[0], position[1], 0);
        graphics.pose().scale(scale, scale, 1.0F);
        String[] labels = {"速", "力", "头", "胸", "臂", "腿", "全"};
        for (int index = 0; index < labels.length; index++) {
            int x = index * CELL;
            boolean beneficial = index < 2;
            drawCell(graphics, font, x, 0, beneficial ? 0xFF67D99F : 0xFFE56A62);
            graphics.drawCenteredString(font, labels[index], x + CELL / 2, 9,
                beneficial ? 0xFF9AF1BF : 0xFFFFA49D);
        }
        graphics.pose().popPose();
        return new Bounds(position[0], position[1], width, height);
    }

    public static Bounds renderIntroPreview(GuiGraphics graphics, Minecraft minecraft,
                                             int effectX, int effectY) {
        float scale = StatusEffectHudState.introScale();
        int size = Math.round(CELL * scale);
        int defaultX = Math.max(2, effectX - size - 6);
        int defaultY = effectY;
        int[] position = StatusEffectHudState.introPosition(defaultX, defaultY,
            minecraft.getWindow().getGuiScaledWidth(), minecraft.getWindow().getGuiScaledHeight(),
            size, size);
        renderIntro(graphics, minecraft,
            new MobEffectInstance(MobEffects.WEAKNESS, 200, 0),
            position[0], position[1], scale, 0.30F);
        return new Bounds(position[0], position[1], size, size);
    }

    private static void drawEffects(GuiGraphics graphics, Minecraft minecraft,
                                    List<MobEffectInstance> effects, int originX, int originY,
                                    float scale, int mouseX, int mouseY) {
        graphics.pose().pushPose();
        graphics.pose().translate(originX, originY, 0);
        graphics.pose().scale(scale, scale, 1.0F);
        for (int index = 0; index < effects.size(); index++) {
            MobEffectInstance instance = effects.get(index);
            int x = (index % COLUMNS) * CELL;
            int y = (index / COLUMNS) * CELL;
            boolean beneficial = instance.getEffect().value().isBeneficial();
            drawCell(graphics, minecraft.font, x, y, beneficial ? 0xFF67D99F : 0xFFE56A62);
            drawEffectIcon(graphics, minecraft, instance, x, y, 1.0F);
            drawDurationOverlay(graphics, instance, x, y);
            if (instance.getAmplifier() > 0) {
                String level = String.valueOf(instance.getAmplifier() + 1);
                int levelWidth = minecraft.font.width(level);
                int levelX = x + (CELL - levelWidth) / 2;
                int levelY = y + CELL - minecraft.font.lineHeight;
                graphics.fill(levelX - 2, levelY - 1, levelX + levelWidth + 2,
                    y + CELL - 1, 0xA8000000);
                graphics.drawString(minecraft.font, level, levelX, levelY,
                    0xFFFFFFFF, true);
            }
            int physicalX = originX + Math.round(x * scale);
            int physicalY = originY + Math.round(y * scale);
            int physicalCell = Math.round(CELL * scale);
            if (mouseX >= physicalX && mouseX < physicalX + physicalCell
                && mouseY >= physicalY && mouseY < physicalY + physicalCell) {
                graphics.pose().popPose();
                graphics.renderTooltip(minecraft.font, effectTooltip(instance), mouseX, mouseY);
                graphics.pose().pushPose();
                graphics.pose().translate(originX, originY, 0);
                graphics.pose().scale(scale, scale, 1.0F);
            }
        }
        graphics.pose().popPose();
    }

    private static void drawDurationOverlay(GuiGraphics graphics,
                                            MobEffectInstance instance, int x, int y) {
        if (instance.getDuration() < 0) return;
        int maximum = MAX_DURATIONS.getOrDefault(effectKey(instance),
            Math.max(1, instance.getDuration()));
        float remaining = Math.max(0.0F, Math.min(1.0F,
            instance.getDuration() / (float) maximum));
        int covered = Math.round((CELL - 2) * (1.0F - remaining));
        if (covered > 0) {
            graphics.fill(x + 1, y + 1, x + CELL - 1, y + 1 + covered,
                0x66FFFFFF);
        }
    }

    private static void renderIntro(GuiGraphics graphics, Minecraft minecraft,
                                    MobEffectInstance instance, int x, int y,
                                    float hudScale, float progress) {
        float alpha = EffectIntroQueue.alpha(progress,
            StatusEffectHudState.effectIntroFadeInDurationMs(),
            StatusEffectHudState.effectIntroHoldDurationMs(),
            StatusEffectHudState.effectIntroFadeOutDurationMs());
        float shrink = EffectIntroQueue.scale(progress,
            StatusEffectHudState.effectIntroFadeInDurationMs(),
            StatusEffectHudState.effectIntroHoldDurationMs(),
            StatusEffectHudState.effectIntroFadeOutDurationMs());
        int physicalCell = Math.round(CELL * hudScale);
        graphics.pose().pushPose();
        graphics.pose().translate(x + physicalCell / 2.0F, y + physicalCell / 2.0F, 0);
        graphics.pose().scale(hudScale * shrink, hudScale * shrink, 1.0F);
        int localX = -CELL / 2;
        int localY = -CELL / 2;
        boolean beneficial = instance.getEffect().value().isBeneficial();
        int border = beneficial ? 0xFF67D99F : 0xFFE56A62;
        graphics.fill(localX + 1, localY + 1, localX + CELL - 1, localY + CELL - 1,
            withAlpha(0xFF1A2326, alpha * 0.84F));
        graphics.fill(localX, localY, localX + CELL, localY + 1, withAlpha(border, alpha));
        graphics.fill(localX, localY + CELL - 1, localX + CELL, localY + CELL,
            withAlpha(border, alpha));
        graphics.fill(localX, localY, localX + 1, localY + CELL, withAlpha(border, alpha));
        graphics.fill(localX + CELL - 1, localY, localX + CELL, localY + CELL,
            withAlpha(border, alpha));
        drawEffectIcon(graphics, minecraft, instance, localX, localY, alpha);
        drawIntroName(graphics, minecraft, instance, alpha);
        graphics.pose().popPose();
    }

    private static void drawIntroName(GuiGraphics graphics, Minecraft minecraft,
                                      MobEffectInstance instance, float alpha) {
        String label = instance.getEffect().value().getDisplayName().getString();
        if (instance.getAmplifier() > 0) label += " " + (instance.getAmplifier() + 1);
        int maxWidth = 86;
        if (minecraft.font.width(label) > maxWidth) {
            label = minecraft.font.plainSubstrByWidth(label,
                Math.max(1, maxWidth - minecraft.font.width("..."))) + "...";
        }
        int textWidth = minecraft.font.width(label);
        int textX = -textWidth / 2;
        int textY = CELL / 2 + 4;
        graphics.fill(textX - 3, textY - 2, textX + textWidth + 3,
            textY + minecraft.font.lineHeight + 1, withAlpha(0xE0141C1F, alpha));
        graphics.drawString(minecraft.font, label, textX, textY,
            withAlpha(0xFFF4F7F6, alpha), true);
    }
    public static void drawEffectIcon(GuiGraphics graphics, Minecraft minecraft,
                                       MobEffectInstance instance, int x, int y, float alpha) {
        drawEffectIconScaled(graphics, minecraft, instance, x, y, CELL, alpha);
    }

    /** Draws an effect icon inside an exact-size cell so previews and HUDs stay aligned. */
    public static void drawEffectIconScaled(GuiGraphics graphics, Minecraft minecraft,
                                             MobEffectInstance instance, int x, int y,
                                             int cellSize, float alpha) {
        int size = Math.max(8, Math.min(cellSize, Math.round(ICON * cellSize / 24.0F)));
        int offset = Math.max(0, (cellSize - size) / 2);
        ResourceLocation id = BuiltInRegistries.MOB_EFFECT.getKey(instance.getEffect().value());
        graphics.setColor(1.0F, 1.0F, 1.0F, alpha);
        if (XeroDelta.MOD_ID.equals(id.getNamespace())) {
            ResourceLocation customIcon = customEffectIcon(id.getPath());
            if (customIcon != null) {
                graphics.blit(customIcon, x + offset, y + offset, size, size,
                    0, 0, 16, 16, 16, 16);
            } else {
                graphics.drawCenteredString(minecraft.font, injuryGlyph(id.getPath()),
                    x + cellSize / 2, y + Math.max(9, cellSize / 2 - 4),
                    withAlpha(0xFFF6E0DD, alpha));
            }
        } else {
            graphics.blit(x + offset, y + offset, 0, size, size,
                minecraft.getMobEffectTextures().get(instance.getEffect()));
        }
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private static EffectKey effectKey(MobEffectInstance instance) {
        return new EffectKey(BuiltInRegistries.MOB_EFFECT.getKey(instance.getEffect().value()),
            instance.getAmplifier());
    }

    private static int withAlpha(int color, float alpha) {
        return Math.round(Math.max(0.0F, Math.min(1.0F, alpha)) * 255.0F) << 24
            | color & 0x00FFFFFF;
    }

    private static void drawCell(GuiGraphics graphics, Font font, int x, int y, int border) {
        graphics.fill(x + 1, y + 1, x + CELL - 1, y + CELL - 1, 0xD51A2326);
        graphics.fill(x, y, x + CELL, y + 1, border);
        graphics.fill(x, y + CELL - 1, x + CELL, y + CELL, border);
        graphics.fill(x, y, x + 1, y + CELL, border);
        graphics.fill(x + CELL - 1, y, x + CELL, y + CELL, border);
    }

    private static Component effectTooltip(MobEffectInstance instance) {
        Component name = instance.getEffect().value().getDisplayName();
        String level = instance.getAmplifier() <= 0 ? ""
            : " " + (instance.getAmplifier() + 1);
        return Component.literal(name.getString() + level + "  " + formatDuration(instance.getDuration()));
    }

    public static String formatDuration(int ticks) {
        if (ticks < 0) return "∞";
        int seconds = Math.max(0, ticks / 20);
        int minutes = seconds / 60;
        return String.format(java.util.Locale.ROOT, "%d:%02d", minutes, seconds % 60);
    }

    private static String injuryGlyph(String path) {
        if (path.contains("head")) return "头";
        if (path.contains("chest")) return "胸";
        if (path.contains("arm")) return "臂";
        if (path.contains("leg")) return "腿";
        return "全";
    }

    private static ResourceLocation customEffectIcon(String path) {
        return EffectIconResources.icon(path);
    }
    private record EffectKey(ResourceLocation id, int amplifier) {
    }

    public record Bounds(int x, int y, int width, int height) {
        public static final Bounds EMPTY = new Bounds(0, 0, 0, 0);
        public boolean contains(double mouseX, double mouseY) {
            return width > 0 && height > 0 && mouseX >= x && mouseX < x + width
                && mouseY >= y && mouseY < y + height;
        }
    }
}
