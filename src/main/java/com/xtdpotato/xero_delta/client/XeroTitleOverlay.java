package com.xtdpotato.xero_delta.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.xtdpotato.xero_delta.XeroDelta;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reusable multi-anchor notice overlay with text, localization, item and texture segments. */
public final class XeroTitleOverlay {
    private static final Pattern TOKEN = Pattern.compile("\\[(translate|item|icon):([^]\\r\\n]+)]");
    private static final long TRANSITION_MS = 100L;
    private static final Map<String, Notice> ACTIVE = new LinkedHashMap<>();
    private static final ResourceLocation COIN = ResourceLocation.fromNamespaceAndPath(
        XeroDelta.MOD_ID, "textures/quality/coin.png");

    private XeroTitleOverlay() {
    }

    public static void show(String position, String content, int durationTicks) {
        String anchor = normalizePosition(position);
        ACTIVE.put(anchor, new Notice(anchor, parse(content), System.currentTimeMillis(),
            Math.max(10, Math.min(1_200, durationTicks)) * 50L));
    }

    public static void showTranslated(String position, String translationKey) {
        show(position, "[translate:" + translationKey + "]", 60);
    }

    public static void render(GuiGraphics graphics, Minecraft minecraft) {
        if (ACTIVE.isEmpty() || minecraft.options.hideGui) return;
        long now = System.currentTimeMillis();
        Iterator<Notice> iterator = ACTIVE.values().iterator();
        while (iterator.hasNext()) {
            Notice notice = iterator.next();
            long age = now - notice.startedAtMs;
            if (age >= notice.durationMs) {
                iterator.remove();
                continue;
            }
            renderNotice(graphics, minecraft, notice, age);
        }
    }

    private static void renderNotice(GuiGraphics graphics, Minecraft minecraft, Notice notice, long age) {
        Font font = minecraft.font;
        int contentWidth = 0;
        for (Segment segment : notice.segments) contentWidth += segment.width(font);
        int screenWidth = minecraft.getWindow().getGuiScaledWidth();
        int screenHeight = minecraft.getWindow().getGuiScaledHeight();
        int panelWidth = Math.min(Math.max(72, contentWidth + 16), Math.max(72, screenWidth - 24));
        int panelHeight = 24;
        Anchor anchor = Anchor.parse(notice.position);
        int x = switch (anchor.horizontal) {
            case LEFT -> 12;
            case RIGHT -> screenWidth - panelWidth - 12;
            case CENTER -> (screenWidth - panelWidth) / 2;
        };
        int baseY = switch (anchor.vertical) {
            case TOP -> 16;
            case BOTTOM -> screenHeight - panelHeight - 44;
            case CENTER -> (screenHeight - panelHeight) / 2;
        };
        float fadeIn = Math.min(1.0F, age / (float) TRANSITION_MS);
        float fadeOut = Math.min(1.0F, (notice.durationMs - age) / (float) TRANSITION_MS);
        float alpha = smooth(Math.min(fadeIn, fadeOut));
        boolean leaving = notice.durationMs - age < TRANSITION_MS;
        int slide = Math.round((1.0F - alpha) * 6.0F) * (leaving ? 1 : -1);
        int y = baseY + slide;
        int a = Math.max(0, Math.min(255, Math.round(alpha * 255.0F)));

        graphics.fill(x, y, x + panelWidth, y + panelHeight, a << 24 | 0x111820);
        graphics.fill(x, y, x + panelWidth, y + 1, a << 24 | 0xD6B75A);
        graphics.fill(x, y + panelHeight - 1, x + panelWidth, y + panelHeight,
            Math.round(a * 0.65F) << 24 | 0xD6B75A);
        graphics.enableScissor(x + 5, y + 2, x + panelWidth - 5, y + panelHeight - 2);
        int drawX = x + Math.max(8, (panelWidth - contentWidth) / 2);
        int textY = y + (panelHeight - font.lineHeight) / 2;
        RenderSystem.enableBlend();
        for (Segment segment : notice.segments) {
            drawX += segment.render(graphics, font, drawX, textY, a);
        }
        graphics.disableScissor();
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private static List<Segment> parse(String input) {
        String text = input == null ? "" : input;
        List<Segment> segments = new ArrayList<>();
        Matcher matcher = TOKEN.matcher(text);
        int cursor = 0;
        while (matcher.find()) {
            addText(segments, text.substring(cursor, matcher.start()));
            String type = matcher.group(1).toLowerCase(Locale.ROOT);
            String value = matcher.group(2).trim();
            switch (type) {
                case "translate" -> segments.add(new TextSegment(Component.translatable(value)));
                case "item" -> {
                    ResourceLocation id = ResourceLocation.tryParse(value);
                    if (id != null && BuiltInRegistries.ITEM.containsKey(id)) {
                        segments.add(new ItemSegment(BuiltInRegistries.ITEM.get(id).getDefaultInstance()));
                    } else addText(segments, value);
                }
                case "icon" -> {
                    ResourceLocation texture = resolveIcon(value);
                    if (texture != null) segments.add(new IconSegment(texture));
                    else addText(segments, value);
                }
                default -> addText(segments, matcher.group());
            }
            cursor = matcher.end();
        }
        addText(segments, text.substring(cursor));
        if (segments.isEmpty()) segments.add(new TextSegment(Component.empty()));
        return List.copyOf(segments);
    }

    private static void addText(List<Segment> target, String value) {
        if (value == null || value.isEmpty()) return;
        target.add(new TextSegment(MailTextFormatter.parse(value)));
    }

    private static ResourceLocation resolveIcon(String value) {
        if ("coin".equalsIgnoreCase(value)) return COIN;
        ResourceLocation id = ResourceLocation.tryParse(value);
        if (id == null) return null;
        String path = id.getPath();
        if (!path.startsWith("textures/")) path = "textures/" + path;
        if (!path.endsWith(".png")) path += ".png";
        return ResourceLocation.fromNamespaceAndPath(id.getNamespace(), path);
    }

    public static String normalizePosition(String raw) {
        String value = raw == null ? "top|center" : raw.toLowerCase(Locale.ROOT);
        boolean left = containsToken(value, "left");
        boolean right = containsToken(value, "right");
        boolean top = containsToken(value, "top");
        boolean bottom = containsToken(value, "bottom");
        String horizontal = left && !right ? "left" : right && !left ? "right" : "center";
        String vertical = top && !bottom ? "top" : bottom && !top ? "bottom" : "center";
        return vertical + "|" + horizontal;
    }

    private static boolean containsToken(String value, String token) {
        for (String part : value.split("\\|")) if (token.equals(part.trim())) return true;
        return false;
    }

    private static float smooth(float value) {
        float clamped = Math.max(0.0F, Math.min(1.0F, value));
        return clamped * clamped * (3.0F - 2.0F * clamped);
    }

    private interface Segment {
        int width(Font font);
        int render(GuiGraphics graphics, Font font, int x, int y, int alpha);
    }

    private record TextSegment(Component component) implements Segment {
        @Override public int width(Font font) { return font.width(component); }
        @Override public int render(GuiGraphics graphics, Font font, int x, int y, int alpha) {
            graphics.drawString(font, component, x, y, alpha << 24 | 0xFFFFFF, false);
            return width(font);
        }
    }

    private record ItemSegment(ItemStack stack) implements Segment {
        @Override public int width(Font font) { return 18; }
        @Override public int render(GuiGraphics graphics, Font font, int x, int y, int alpha) {
            graphics.renderItem(stack, x, y - 4);
            return 18;
        }
    }

    private record IconSegment(ResourceLocation texture) implements Segment {
        @Override public int width(Font font) { return 14; }
        @Override public int render(GuiGraphics graphics, Font font, int x, int y, int alpha) {
            graphics.setColor(1.0F, 1.0F, 1.0F, alpha / 255.0F);
            graphics.blit(texture, x, y - 1, 0, 0, 12, 12, 12, 12);
            graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
            return 14;
        }
    }

    private enum Horizontal { LEFT, CENTER, RIGHT }
    private enum Vertical { TOP, CENTER, BOTTOM }
    private record Anchor(Horizontal horizontal, Vertical vertical) {
        static Anchor parse(String position) {
            String normalized = normalizePosition(position);
            Horizontal horizontal = normalized.endsWith("|left") ? Horizontal.LEFT
                : normalized.endsWith("|right") ? Horizontal.RIGHT : Horizontal.CENTER;
            Vertical vertical = normalized.startsWith("top|") ? Vertical.TOP
                : normalized.startsWith("bottom|") ? Vertical.BOTTOM : Vertical.CENTER;
            return new Anchor(horizontal, vertical);
        }
    }

    private record Notice(String position, List<Segment> segments, long startedAtMs, long durationMs) {
    }
}