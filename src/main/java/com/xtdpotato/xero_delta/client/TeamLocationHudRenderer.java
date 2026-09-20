package com.xtdpotato.xero_delta.client;

import com.xtdpotato.xero_delta.network.TeamStatusPacket;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/** Projects online FTB teammates into a compact distance/name/number marker. */
public final class TeamLocationHudRenderer {
    private TeamLocationHudRenderer() {
    }

    public static void render(GuiGraphics graphics, Minecraft minecraft) {
        if (minecraft.player == null || minecraft.level == null) return;
        if (TeamStatusClientState.INSTANCE.members().isEmpty()) return;

        Camera camera = minecraft.gameRenderer.getMainCamera();
        Vec3 cameraPos = camera.getPosition();
        Vector3f look = camera.getLookVector();
        Vector3f up = camera.getUpVector();
        Vector3f left = camera.getLeftVector();
        Vec3 forward = new Vec3(look.x, look.y, look.z);
        Vec3 right = new Vec3(-left.x, -left.y, -left.z);
        Vec3 upAxis = new Vec3(up.x, up.y, up.z);
        int width = minecraft.getWindow().getGuiScaledWidth();
        int height = minecraft.getWindow().getGuiScaledHeight();
        double fov = Math.max(30.0D, Math.min(120.0D, minecraft.options.fov().get()));
        double focal = height / (2.0D * Math.tan(Math.toRadians(fov) / 2.0D));
        String dimension = minecraft.level.dimension().location().toString();

        for (TeamStatusPacket.Entry entry : TeamStatusClientState.INSTANCE.members()) {
            if (!dimension.equals(entry.dimension())) continue;
            Vec3 position = new Vec3(entry.x(), entry.y() + 2.15D, entry.z());
            Vec3 relative = position.subtract(cameraPos);
            double depth = relative.dot(forward);
            double horizontal = relative.dot(right);
            double vertical = relative.dot(upAxis);
            double screenX = width * 0.5D + horizontal / Math.max(0.01D, depth) * focal;
            double screenY = height * 0.5D - vertical / Math.max(0.01D, depth) * focal;

            int safeLeft = 88;
            int safeRight = Math.max(safeLeft, width - 88);
            int safeTop = 18;
            int safeBottom = Math.max(safeTop, height - 48);
            boolean onScreen = depth > 0.05D && screenX >= safeLeft && screenX <= safeRight
                && screenY >= safeTop && screenY <= safeBottom;
            double directionX = horizontal;
            double directionY = -vertical;
            if (!onScreen) {
                if (depth <= 0.05D) {
                    directionX = -directionX;
                    directionY = -directionY;
                }
                if (Math.abs(directionX) + Math.abs(directionY) < 0.0001D) directionY = 1.0D;
                double halfWidth = Math.max(1.0D, (safeRight - safeLeft) * 0.5D);
                double halfHeight = Math.max(1.0D, (safeBottom - safeTop) * 0.5D);
                double scale = Math.min(halfWidth / Math.max(0.0001D, Math.abs(directionX)),
                    halfHeight / Math.max(0.0001D, Math.abs(directionY)));
                screenX = width * 0.5D + directionX * scale;
                screenY = (safeTop + safeBottom) * 0.5D + directionY * scale;
                screenX = Math.max(safeLeft, Math.min(safeRight, screenX));
                screenY = Math.max(safeTop, Math.min(safeBottom, screenY));
            }

            int x = (int) Math.round(screenX);
            int y = (int) Math.round(screenY);
            int color = entry.teamColor() == 0
                ? TeamColorPalette.colorForNumber(entry.teamNumber()) : entry.teamColor();
            int distance = (int) Math.min(99999L, Math.max(0L,
                Math.round(minecraft.player.position().distanceTo(new Vec3(entry.x(), entry.y(), entry.z())))));
            int textColor = 0xFFF2F5F4;
            graphics.pose().pushPose();
            graphics.pose().translate(0.0F, 0.0F, 440.0F);
            if (!onScreen) {
                renderOffscreen(graphics, minecraft, entry, x, y, directionX, directionY,
                    distance, color, textColor);
                graphics.pose().popPose();
                continue;
            }
            int nameWidth = minecraft.font.width(entry.name());
            int distanceWidth = minecraft.font.width(Component.translatable(
                "team_location.xero_delta.distance", distance));
            int panelWidth = Math.max(42, Math.max(nameWidth, distanceWidth) + 12);
            int panelLeft = x - panelWidth / 2;
            int panelTop = y - 2;
            graphics.fill(panelLeft, panelTop, panelLeft + panelWidth, y + 40, 0x9910161A);
            graphics.drawCenteredString(minecraft.font,
                Component.translatable("team_location.xero_delta.distance", distance),
                x, y, textColor);
            graphics.drawCenteredString(minecraft.font, Component.literal(entry.name()),
                x, y + minecraft.font.lineHeight + 1, textColor);
            int circleY = y + minecraft.font.lineHeight * 2 + 5;
            drawCircle(graphics, x, circleY, 9, color);
            Component number = Component.literal(Byte.toString(entry.teamNumber()));
            graphics.drawCenteredString(minecraft.font, number, x, circleY - minecraft.font.lineHeight / 2,
                0xFF14202A);
            graphics.pose().popPose();
        }
    }

    private static void renderOffscreen(GuiGraphics graphics, Minecraft minecraft,
                                        TeamStatusPacket.Entry entry, int x, int y,
                                        double directionX, double directionY, int distance,
                                        int color, int textColor) {
        double length = Math.sqrt(directionX * directionX + directionY * directionY);
        if (length < 0.0001D) {
            directionX = 0.0D;
            directionY = 1.0D;
        } else {
            directionX /= length;
            directionY /= length;
        }
        int arrowX = x - 30;
        int circleX = x - 13;
        drawDirectionArrow(graphics, arrowX, y, directionX, directionY, textColor);
        drawCircle(graphics, circleX, y, 8, color);
        graphics.drawCenteredString(minecraft.font,
            Component.literal(Byte.toString(entry.teamNumber())), circleX,
            y - minecraft.font.lineHeight / 2, 0xFF14202A);
        graphics.drawString(minecraft.font,
            Component.translatable("team_location.xero_delta.distance", distance),
            x + 1, y - minecraft.font.lineHeight / 2, textColor, true);
    }

    private static void drawDirectionArrow(GuiGraphics graphics, int centerX, int centerY,
                                           double directionX, double directionY, int color) {
        double perpendicularX = -directionY;
        double perpendicularY = directionX;
        int tipX = centerX + (int) Math.round(directionX * 6.0D);
        int tipY = centerY + (int) Math.round(directionY * 6.0D);
        int backX = centerX - (int) Math.round(directionX * 4.0D);
        int backY = centerY - (int) Math.round(directionY * 4.0D);
        int firstX = backX + (int) Math.round(perpendicularX * 4.5D);
        int firstY = backY + (int) Math.round(perpendicularY * 4.5D);
        int secondX = backX - (int) Math.round(perpendicularX * 4.5D);
        int secondY = backY - (int) Math.round(perpendicularY * 4.5D);
        drawLine(graphics, tipX, tipY, firstX, firstY, color);
        drawLine(graphics, tipX, tipY, secondX, secondY, color);
    }

    private static void drawLine(GuiGraphics graphics, int x0, int y0,
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

    private static void drawCircle(GuiGraphics graphics, int centerX, int centerY,
                                   int radius, int color) {
        for (int y = -radius; y <= radius; y++) {
            int half = (int) Math.floor(Math.sqrt(Math.max(0.0D, radius * radius - y * y)));
            graphics.fill(centerX - half, centerY + y, centerX + half + 1, centerY + y + 1, color);
        }
    }
}
