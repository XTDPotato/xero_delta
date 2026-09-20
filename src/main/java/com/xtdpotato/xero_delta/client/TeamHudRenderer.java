package com.xtdpotato.xero_delta.client;

import com.xtdpotato.xero_delta.network.TeamStatusPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffects;

import java.util.ArrayList;
import java.util.List;

/** Compact FTB team rows positioned immediately above the local-player health panel. */
public final class TeamHudRenderer {
    private static final int WIDTH = 206;
    private static final int ROW_HEIGHT = 38;
    private static final int FACE = 24;
    private static final int BAR_X = 36;
    private static final int BAR_WIDTH = 162;

    private TeamHudRenderer() {
    }

    public static void renderAbove(GuiGraphics graphics, Minecraft minecraft,
                                   int baseX, int baseY, float scale) {
        List<TeamStatusPacket.Entry> all = TeamStatusClientState.INSTANCE.members();
        if (all.isEmpty() || baseY <= 4) return;
        int scaledRow = Math.max(1, Math.round(ROW_HEIGHT * scale));
        int maxRows = Math.max(0, (baseY - 4) / scaledRow);
        int count = Math.min(all.size(), maxRows);
        if (count <= 0) return;
        List<TeamStatusPacket.Entry> visible = all.subList(0, count);
        int originY = baseY - Math.round((ROW_HEIGHT * count + 4) * scale);

        graphics.pose().pushPose();
        graphics.pose().translate(baseX, originY, 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        for (int index = 0; index < visible.size(); index++) {
            drawRow(graphics, minecraft, visible.get(index), index * ROW_HEIGHT);
        }
        graphics.pose().popPose();
    }

    private static void drawRow(GuiGraphics graphics, Minecraft minecraft,
                                TeamStatusPacket.Entry entry, int y) {

        int faceX = 5;
        int faceY = y + 6;
        graphics.fill(faceX - 1, faceY - 1, faceX + FACE + 1, faceY + FACE + 1,
            0xCC12191C);
        if (entry.stage() == 1) {
            PlayerHudRenderer.drawEcgIcon(graphics, faceX - 3, faceY - 3, 0xFFFF342E);
        } else if (entry.stage() == 2) {
            PlayerHudRenderer.drawRescueIcon(graphics, faceX - 3, faceY - 3, 0xFFFFC83D);
        } else {
            var levelPlayer = minecraft.level == null ? null
                : minecraft.level.getPlayerByUUID(entry.playerId());
            var skin = levelPlayer instanceof AbstractClientPlayer clientPlayer
                ? clientPlayer.getSkin() : DefaultPlayerSkin.get(entry.playerId());
            PlayerFaceRenderer.draw(graphics, skin, faceX, faceY, FACE);
        }

        PlayerHudRenderer.drawRescueRequestPulse(graphics, entry.playerId(),
            faceX, faceY, FACE);

        int healthPercent = Math.round(entry.healthFraction() * 100.0F);
        boolean critical = entry.healthFraction() < 0.20F;
        int weakIconX = WIDTH - 14;
        int textRight = entry.weakness() ? weakIconX - 3 : WIDTH - 5;
        if (entry.stage() != 0) {
            String name = trim(minecraft, entry.name(), BAR_WIDTH);
            graphics.drawString(minecraft.font, name, BAR_X, y + 16,
                0xFFD8DEDC, false);
            int downedColor = entry.stage() == 1 ? 0xFFFF342E : 0xFFFFC83D;
            int downedFill = Math.round(FACE * entry.downedProgress());
            graphics.fill(faceX, y + ROW_HEIGHT - 4, faceX + FACE,
                y + ROW_HEIGHT - 2, 0xFF221B1C);
            if (downedFill > 0) {
                graphics.fill(faceX, y + ROW_HEIGHT - 4, faceX + downedFill,
                    y + ROW_HEIGHT - 2, downedColor);
            }
            return;
        }

        String health = healthPercent + "/100";
        int healthWidth = minecraft.font.width(health);
        String name = trim(minecraft, entry.name(), Math.max(16,
            textRight - BAR_X - healthWidth - 6));
        graphics.drawString(minecraft.font, name, BAR_X, y + 4, 0xFFD8DEDC, false);
        graphics.drawString(minecraft.font, health, textRight - healthWidth, y + 4,
            critical ? 0xFFFF4038 : 0xFFF0F3F2, false);
        if (entry.weakness()) {
            graphics.blit(weakIconX, y + 2, 0, 10, 10,
                minecraft.getMobEffectTextures().get(MobEffects.WEAKNESS));
        }

        int barY = y + 16;
        graphics.fill(BAR_X, barY, BAR_X + BAR_WIDTH, barY + 5, 0xFF1A2225);
        int color = critical ? 0xFFFF4038 : 0xFFE5EAE8;
        int fill = Math.round(BAR_WIDTH * entry.healthFraction());
        if (fill > 0) graphics.fill(BAR_X, barY, BAR_X + fill, barY + 5, color);

        Component status = status(entry);
        if (status != null) {
            int statusColor = entry.weakness() ? 0xFFFF7777 : 0xFFC7D1CF;
            graphics.drawString(minecraft.font,
                trim(minecraft, status.getString(), BAR_WIDTH), BAR_X, y + 26,
                statusColor, false);
        }
    }

    private static Component status(TeamStatusPacket.Entry entry) {
        List<Component> values = new ArrayList<>();
        if (entry.stage() == 1) values.add(Component.translatable("hud.xero_delta.team.red_down"));
        if (entry.stage() == 2) values.add(Component.translatable("hud.xero_delta.team.yellow_down"));
        if (entry.weakness()) values.add(Component.translatable("hud.xero_delta.team.weakness"));
        if (entry.carried()) values.add(Component.translatable("hud.xero_delta.team.carried"));
        if (entry.carrying()) values.add(Component.translatable("hud.xero_delta.team.carrying"));
        if (entry.rescuing()) values.add(Component.translatable("hud.xero_delta.team.rescuing"));
        if (entry.beingRescued()) values.add(Component.translatable("hud.xero_delta.team.being_rescued"));
        if (values.isEmpty()) return null;
        Component result = Component.empty();
        for (int index = 0; index < values.size(); index++) {
            if (index > 0) result = result.copy().append(" · ");
            result = result.copy().append(values.get(index));
        }
        return result;
    }

    private static String trim(Minecraft minecraft, String value, int width) {
        if (minecraft.font.width(value) <= width) return value;
        int dots = minecraft.font.width("...");
        return width <= dots ? "" : minecraft.font.plainSubstrByWidth(value, width - dots) + "...";
    }
}
