package com.xtdpotato.xero_delta.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/** Immediate-mode circular sectors shared by the tactical and medical wheels. */
final class RadialWheelRenderer {
    private static final int BASE = 0xE6151D20;
    private static final int DIVIDER = 0xB84A5A58;

    private RadialWheelRenderer() {
    }

    static void draw(GuiGraphics graphics, int centerX, int centerY, float innerRadius,
                     float outerRadius, int sectors, int selected, int accent, float animation) {
        List<Vertex> vertices = new ArrayList<>();
        double sectorSize = Math.PI * 2.0D / sectors;
        for (int sector = 0; sector < sectors; sector++) {
            float sectorAnimation = Math.max(0.0F, Math.min(1.0F,
                (animation * 1.35F) - sector * 0.07F));
            double center = -Math.PI / 2.0D + sector * sectorSize;
            double start = center - sectorSize / 2.0D;
            int color = scaleAlpha(sector == selected ? withAlpha(accent, 210) : BASE,
                sectorAnimation);
            int steps = Math.max(8, (int) Math.ceil(outerRadius * sectorSize / 7.0D));
            for (int step = 0; step < steps; step++) {
                double a0 = start + sectorSize * step / steps;
                double a1 = start + sectorSize * (step + 1) / steps;
                addQuad(vertices,
                    point(centerX, centerY, innerRadius, a0),
                    point(centerX, centerY, outerRadius, a0),
                    point(centerX, centerY, outerRadius, a1),
                    point(centerX, centerY, innerRadius, a1), color);
            }
            addRadialDivider(vertices, centerX, centerY, innerRadius, outerRadius, start, animation);
        }
        addRing(vertices, centerX, centerY, innerRadius, 1.1F, scaleAlpha(DIVIDER, animation));
        addRing(vertices, centerX, centerY, outerRadius, 1.1F, scaleAlpha(DIVIDER, animation));
        drawTriangles(graphics, vertices);
    }

    private static void addRadialDivider(List<Vertex> out, float cx, float cy,
                                         float inner, float outer, double angle, float animation) {
        float nx = (float) -Math.sin(angle) * 0.65F;
        float ny = (float) Math.cos(angle) * 0.65F;
        Point a = point(cx, cy, inner, angle);
        Point b = point(cx, cy, outer, angle);
        addQuad(out, new Point(a.x - nx, a.y - ny), new Point(b.x - nx, b.y - ny),
            new Point(b.x + nx, b.y + ny), new Point(a.x + nx, a.y + ny),
            scaleAlpha(DIVIDER, animation));
    }

    private static void addRing(List<Vertex> out, float cx, float cy, float radius,
                                float thickness, int color) {
        int steps = 96;
        for (int i = 0; i < steps; i++) {
            double a0 = Math.PI * 2.0D * i / steps;
            double a1 = Math.PI * 2.0D * (i + 1) / steps;
            addQuad(out, point(cx, cy, radius - thickness, a0),
                point(cx, cy, radius + thickness, a0),
                point(cx, cy, radius + thickness, a1),
                point(cx, cy, radius - thickness, a1), color);
        }
    }

    private static Point point(float cx, float cy, float radius, double angle) {
        return new Point(cx + (float) Math.cos(angle) * radius,
            cy + (float) Math.sin(angle) * radius);
    }

    private static void addQuad(List<Vertex> out, Point a, Point b, Point c, Point d, int color) {
        out.add(new Vertex(a, color));
        out.add(new Vertex(b, color));
        out.add(new Vertex(c, color));
        out.add(new Vertex(a, color));
        out.add(new Vertex(c, color));
        out.add(new Vertex(d, color));
    }

    private static void drawTriangles(GuiGraphics graphics, List<Vertex> vertices) {
        graphics.flush();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        Matrix4f matrix = graphics.pose().last().pose();
        BufferBuilder buffer = Tesselator.getInstance().begin(
            VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        for (Vertex vertex : vertices) {
            buffer.addVertex(matrix, vertex.point.x, vertex.point.y, 0.0F)
                .setColor(vertex.color);
        }
        BufferUploader.drawWithShader(buffer.buildOrThrow());
        RenderSystem.enableCull();
    }

    private static int withAlpha(int color, int alpha) {
        return alpha << 24 | color & 0x00FFFFFF;
    }

    private static int scaleAlpha(int color, float factor) {
        int alpha = Math.round(((color >>> 24) & 0xFF)
            * Math.max(0.0F, Math.min(1.0F, factor)));
        return alpha << 24 | color & 0x00FFFFFF;
    }

    private record Point(float x, float y) {
    }

    private record Vertex(Point point, int color) {
    }
}
