package com.xtdpotato.xero_delta.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

/** Screen-edge curved hit markers that remain anchored to their world direction. */
public final class DamageDirectionHudRenderer {
    private DamageDirectionHudRenderer() {
    }

    public static void render(GuiGraphics graphics, Minecraft minecraft) {
        if (minecraft.player == null || DownedClientState.INSTANCE.redDown()) return;
        var samples = DamageDirectionClientState.INSTANCE.samples();
        if (samples.isEmpty()) return;
        int width = minecraft.getWindow().getGuiScaledWidth();
        int height = minecraft.getWindow().getGuiScaledHeight();
        int centerX = width / 2;
        int centerY = height / 2;
        double baseRadius = Math.max(42.0D, Math.min(width, height) * 0.34D);
        float cameraYaw = minecraft.player.getYRot();

        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, 420.0F);
        for (DamageDirectionClientState.Sample sample : samples) {
            double bearing = Math.toDegrees(Math.atan2(
                -sample.directionX(), sample.directionZ()));
            double relative = Math.toRadians(Mth.wrapDegrees((float) (bearing - cameraYaw)) - 90.0F);
            drawArc(graphics, centerX, centerY, baseRadius, relative, sample);
        }
        graphics.pose().popPose();
    }

    private static void drawArc(GuiGraphics graphics, int centerX, int centerY,
                                double baseRadius, double direction,
                                DamageDirectionClientState.Sample sample) {
        float progress = sample.progress();
        float ease = 1.0F - (1.0F - progress) * (1.0F - progress);
        double radius = baseRadius + ease * 9.0D;
        double halfSpan = Math.toRadians(30.0D - ease * 11.0D);
        int thickness = Math.max(2, Math.round(7.0F - ease * 5.0F));
        float strength = Math.max(0.35F, Math.min(1.0F, sample.damage() / 12.0F));
        int alpha = Math.max(0, Math.min(255,
            Math.round((1.0F - progress) * (150.0F + 90.0F * strength))));
        int color = alpha << 24 | 0x00E83D20;
        int segments = 34;
        for (int segment = 0; segment <= segments; segment++) {
            double angle = direction - halfSpan
                + halfSpan * 2.0D * segment / segments;
            int x = (int) Math.round(centerX + Math.cos(angle) * radius);
            int y = (int) Math.round(centerY + Math.sin(angle) * radius);
            graphics.fill(x - thickness / 2, y - thickness / 2,
                x + (thickness + 1) / 2, y + (thickness + 1) / 2, color);
        }
    }
}
