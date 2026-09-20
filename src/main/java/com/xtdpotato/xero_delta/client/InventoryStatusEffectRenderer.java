package com.xtdpotato.xero_delta.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Screen-space effect grid shared by the Delta inventory and container hosts. */
public final class InventoryStatusEffectRenderer {
    private InventoryStatusEffectRenderer() {}

    public static boolean render(Screen screen, GuiGraphics graphics, int mouseX, int mouseY,
                                 int defaultX, int bottom, float layoutScale) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return false;
        List<MobEffectInstance> effects = new ArrayList<>(minecraft.player.getActiveEffects());
        effects.removeIf(effect -> !effect.showIcon());
        effects.sort(Comparator.comparing((MobEffectInstance effect) ->
            effect.getEffect().value().isBeneficial()).reversed()
            .thenComparing(effect -> effect.getEffect().value().getDisplayName().getString()));
        if (effects.isEmpty()) return false;
        float scale = layoutScale * StatusEffectHudState.inventoryEffectScale();
        float opacity = StatusEffectHudState.inventoryEffectOpacity();
        int cell = Math.max(12, Math.round(24 * scale));
        int gap = Math.max(1, Math.round(2 * scale));
        int count = Math.min(12, effects.size());
        int rows = (count + 5) / 6;
        int[] origin = StatusEffectHudState.inventoryEffectPosition(defaultX,
            bottom - rows * (cell + gap), screen.width, screen.height,
            6 * cell + 5 * gap, rows * cell + (rows - 1) * gap);
        MobEffectInstance hovered = null;
        for (int index = 0; index < count; index++) {
            MobEffectInstance effect = effects.get(index);
            int x = origin[0] + index % 6 * (cell + gap);
            int y = origin[1] + index / 6 * (cell + gap);
            boolean over = mouseX >= x && mouseX < x + cell && mouseY >= y && mouseY < y + cell;
            int border = effect.getEffect().value().isBeneficial() ? 0xFF83E8DF : 0xFFFFA09B;
            graphics.fill(x, y, x + cell, y + cell, alpha(0xD0141D20, opacity));
            graphics.renderOutline(x, y, cell, cell, alpha(over ? 0xFFFFFFFF : border, opacity));
            StatusEffectHudRenderer.drawEffectIconScaled(graphics, minecraft, effect, x, y, cell, opacity);
            if (effect.getAmplifier() > 0) graphics.drawCenteredString(minecraft.font,
                String.valueOf(effect.getAmplifier() + 1), x + cell / 2,
                y + cell - minecraft.font.lineHeight, alpha(0xFFFFFFFF, opacity));
            if (over) hovered = effect;
        }
        if (hovered == null) return false;
        Component name = hovered.getEffect().value().getDisplayName();
        if (hovered.getAmplifier() > 0) name = name.copy().append(" ").append(
            Component.translatable("potion.potency." + hovered.getAmplifier()));
        Component duration = Component.literal(hovered.isInfiniteDuration() ? "∞"
            : StatusEffectHudRenderer.formatDuration(hovered.getDuration()));
        graphics.renderComponentTooltip(minecraft.font, List.of(name, duration), mouseX, mouseY);
        return true;
    }

    private static int alpha(int color, float opacity) {
        return (Math.round((color >>> 24) * opacity) << 24) | (color & 0xFFFFFF);
    }
}
