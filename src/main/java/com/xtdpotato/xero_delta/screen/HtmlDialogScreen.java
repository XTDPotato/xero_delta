package com.xtdpotato.xero_delta.screen;

import com.xtdpotato.xero_delta.screen.material.Material2Drawing;
import com.xtdpotato.xero_delta.screen.material.Material3Theme;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

import javax.swing.text.MutableAttributeSet;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.parser.ParserDelegator;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Server-driven, stackable modal dialog with a safe HTML presentation subset. */
public final class HtmlDialogScreen extends Screen {
    public static final int MAX_OPTIONS_LENGTH = 2_048;
    public static final int MAX_DATA_LENGTH = 30_000;

    private static final ResourceLocation CLOSE_SPRITE =
        ResourceLocation.withDefaultNamespace("widget/cross_button");
    private static final ResourceLocation CLOSE_HOVERED_SPRITE =
        ResourceLocation.withDefaultNamespace("widget/cross_button_highlighted");

    private final Screen parent;
    private final DialogOptions options;
    private final List<HtmlBlock> blocks;
    private double scroll;
    private double targetScroll;
    private boolean draggingScrollbar;
    private double dragStartY;
    private double dragStartScroll;

    private HtmlDialogScreen(Screen parent, String rawOptions, String html) {
        super(Component.translatable("dialog.xero_delta.title"));
        this.parent = parent;
        this.options = DialogOptions.parse(rawOptions);
        this.blocks = HtmlDocument.parse(html);
    }

    public static void open(String rawOptions, String html) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.setScreen(new HtmlDialogScreen(minecraft.screen, rawOptions, html));
    }

    public static void closeTop() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof HtmlDialogScreen dialog) dialog.onClose();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (parent != null) parent.render(graphics, mouseX, mouseY, partialTick);
        else renderTransparentBackground(graphics);

        tickSmoothScroll();
        Bounds panel = panelBounds();
        graphics.fill(0, 0, width, height, options.scrimColor);
        Material2Drawing.roundedRect(graphics, panel.x, panel.y, panel.width, panel.height,
            Material3Theme.RADIUS_MEDIUM, options.backgroundColor);
        Material2Drawing.outlineRoundedRect(graphics, panel.x, panel.y, panel.width, panel.height,
            Material3Theme.RADIUS_MEDIUM, 1.0F, options.borderColor);

        Bounds content = contentBounds(panel);
        int contentHeight = contentHeight(content.width);
        int maxScroll = Math.max(0, contentHeight - content.height);
        scroll = clamp(scroll, 0.0D, maxScroll);
        targetScroll = clamp(targetScroll, 0.0D, maxScroll);

        graphics.enableScissor(content.x, content.y, content.right(), content.bottom());
        renderDocument(graphics, content, (int) Math.round(scroll));
        graphics.disableScissor();
        renderScrollbar(graphics, content, contentHeight);
        renderCloseButton(graphics, panel, mouseX, mouseY);
    }

    private void renderDocument(GuiGraphics graphics, Bounds content, int scrollOffset) {
        int y = content.y - scrollOffset;
        for (HtmlBlock block : blocks) {
            y += block.spacingBefore;
            if (block.rule) {
                if (y >= content.y - 2 && y <= content.bottom() + 2) {
                    graphics.fill(content.x, y, content.right() - 6, y + 1,
                        options.borderColor);
                }
                y += 5 + block.spacingAfter;
                continue;
            }
            float scale = block.scale;
            int wrapWidth = Math.max(1, (int) ((content.width - 8) / scale));
            List<FormattedCharSequence> lines = font.split(block.text, wrapWidth);
            int lineHeight = Math.max(1, Math.round(10.0F * scale));
            for (FormattedCharSequence line : lines) {
                int lineWidth = Math.round(font.width(line) * scale);
                int x = switch (block.alignment) {
                    case CENTER -> content.x + Math.max(0, (content.width - 8 - lineWidth) / 2);
                    case RIGHT -> content.right() - 8 - lineWidth;
                    default -> content.x;
                };
                if (y + lineHeight >= content.y && y <= content.bottom()) {
                    graphics.pose().pushPose();
                    graphics.pose().translate(x, y, 0.0F);
                    graphics.pose().scale(scale, scale, 1.0F);
                    graphics.drawString(font, line, 0, 0, options.textColor, false);
                    graphics.pose().popPose();
                }
                y += lineHeight;
            }
            y += block.spacingAfter;
        }
    }

    private int contentHeight(int width) {
        int height = 0;
        for (HtmlBlock block : blocks) {
            height += block.spacingBefore;
            if (block.rule) {
                height += 5 + block.spacingAfter;
                continue;
            }
            int wrapWidth = Math.max(1, (int) ((width - 8) / block.scale));
            int lines = Math.max(1, font.split(block.text, wrapWidth).size());
            height += lines * Math.max(1, Math.round(10.0F * block.scale));
            height += block.spacingAfter;
        }
        return Math.max(1, height);
    }

    private void renderScrollbar(GuiGraphics graphics, Bounds content, int contentHeight) {
        if (contentHeight <= content.height) return;
        int trackX = content.right() - 3;
        int thumbHeight = Math.max(18, content.height * content.height / contentHeight);
        int travel = Math.max(1, content.height - thumbHeight);
        double maximum = Math.max(1.0D, contentHeight - content.height);
        int thumbY = content.y + (int) Math.round(travel * scroll / maximum);
        graphics.fill(trackX, content.y, trackX + 2, content.bottom(), 0x553F4E52);
        graphics.fill(trackX - 1, thumbY, trackX + 3, thumbY + thumbHeight,
            options.accentColor);
    }

    private void renderCloseButton(GuiGraphics graphics, Bounds panel,
                                   int mouseX, int mouseY) {
        Bounds close = closeBounds(panel);
        if (close == null) return;
        boolean hovered = close.contains(mouseX, mouseY);
        graphics.fill(close.x, close.y, close.right(), close.bottom(),
            hovered ? 0xFF33464A : 0x991A2528);
        graphics.blitSprite(hovered ? CLOSE_HOVERED_SPRITE : CLOSE_SPRITE,
            close.x + 3, close.y + 3, close.width - 6, close.height - 6);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return true;
        Bounds panel = panelBounds();
        Bounds close = closeBounds(panel);
        if (close != null && close.contains(mouseX, mouseY)) {
            onClose();
            return true;
        }
        if (!panel.contains(mouseX, mouseY)) {
            if (options.canceledOnTouchOutside) onClose();
            return true;
        }
        Bounds content = contentBounds(panel);
        if (scrollbarContains(content, mouseX, mouseY)) {
            draggingScrollbar = true;
            dragStartY = mouseY;
            dragStartScroll = targetScroll;
        }
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button,
                                double dragX, double dragY) {
        if (!draggingScrollbar || button != 0) return true;
        Bounds content = contentBounds(panelBounds());
        int documentHeight = contentHeight(content.width);
        int maximum = Math.max(0, documentHeight - content.height);
        int thumbHeight = documentHeight <= content.height ? content.height
            : Math.max(18, content.height * content.height / documentHeight);
        int travel = Math.max(1, content.height - thumbHeight);
        targetScroll = clamp(dragStartScroll
            + (mouseY - dragStartY) * maximum / travel, 0.0D, maximum);
        scroll = targetScroll;
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        draggingScrollbar = false;
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY,
                                 double scrollX, double scrollY) {
        Bounds panel = panelBounds();
        Bounds content = contentBounds(panel);
        int maximum = Math.max(0, contentHeight(content.width) - content.height);
        if (panel.contains(mouseX, mouseY) && maximum > 0) {
            targetScroll = clamp(targetScroll - scrollY * 38.0D, 0.0D, maximum);
        }
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) return true;
        if (keyCode == GLFW.GLFW_KEY_UP) targetScroll -= 20.0D;
        else if (keyCode == GLFW.GLFW_KEY_DOWN) targetScroll += 20.0D;
        else if (keyCode == GLFW.GLFW_KEY_PAGE_UP) targetScroll -= contentBounds(panelBounds()).height;
        else if (keyCode == GLFW.GLFW_KEY_PAGE_DOWN) targetScroll += contentBounds(panelBounds()).height;
        else if (keyCode == GLFW.GLFW_KEY_HOME) targetScroll = 0.0D;
        else if (keyCode == GLFW.GLFW_KEY_END) {
            Bounds content = contentBounds(panelBounds());
            targetScroll = Math.max(0, contentHeight(content.width) - content.height);
        }
        return true;
    }

    private void tickSmoothScroll() {
        scroll += (targetScroll - scroll) * 0.28D;
        if (Math.abs(targetScroll - scroll) < 0.05D) scroll = targetScroll;
    }

    private Bounds panelBounds() {
        if (options.fullscreen) return new Bounds(0, 0, width, height);
        int panelWidth = options.width.resolve(width, 0.70D);
        int panelHeight = options.height.resolve(height, 0.68D);
        panelWidth = Math.max(1, Math.min(width, panelWidth));
        panelHeight = Math.max(1, Math.min(height, panelHeight));
        int x = switch (options.gravity.horizontal) {
            case START -> 0;
            case END -> width - panelWidth;
            default -> (width - panelWidth) / 2;
        };
        int y = switch (options.gravity.vertical) {
            case START -> 0;
            case END -> height - panelHeight;
            default -> (height - panelHeight) / 2;
        };
        return new Bounds(x, y, panelWidth, panelHeight);
    }

    private Bounds contentBounds(Bounds panel) {
        int padding = Math.max(2, Math.min(options.padding,
            Math.max(2, Math.min(panel.width, panel.height) / 3)));
        int left = panel.x + padding;
        int top = panel.y + padding;
        int right = panel.right() - padding;
        int bottom = panel.bottom() - padding;
        if (options.closeButton.isTop()) top += 22;
        if (options.closeButton.isBottom()) bottom -= 22;
        return new Bounds(left, top, Math.max(1, right - left), Math.max(1, bottom - top));
    }

    private Bounds closeBounds(Bounds panel) {
        if (options.closeButton == Corner.NONE) return null;
        int size = Math.max(16, Math.min(22, Math.min(panel.width, panel.height) / 5));
        int inset = Math.max(3, Math.min(8, options.padding / 2));
        int x = options.closeButton.isRight()
            ? panel.right() - size - inset : panel.x + inset;
        int y = options.closeButton.isBottom()
            ? panel.bottom() - size - inset : panel.y + inset;
        return new Bounds(x, y, size, size);
    }

    private boolean scrollbarContains(Bounds content, double mouseX, double mouseY) {
        return contentHeight(content.width) > content.height
            && mouseX >= content.right() - 7 && mouseX < content.right() + 2
            && mouseY >= content.y && mouseY < content.bottom();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private enum Axis { START, CENTER, END }

    private record Gravity(Axis horizontal, Axis vertical) {
        private static Gravity parse(String value) {
            String normalized = normalize(value);
            Axis horizontal = normalized.contains("left") ? Axis.START
                : normalized.contains("right") ? Axis.END : Axis.CENTER;
            Axis vertical = normalized.contains("top") ? Axis.START
                : normalized.contains("bottom") ? Axis.END : Axis.CENTER;
            return new Gravity(horizontal, vertical);
        }
    }

    private enum Corner {
        NONE, LEFT_TOP, RIGHT_TOP, LEFT_BOTTOM, RIGHT_BOTTOM;

        private static Corner parse(String value) {
            return switch (normalize(value)) {
                case "none", "false", "off" -> NONE;
                case "lefttop", "topleft" -> LEFT_TOP;
                case "leftbottom", "bottomleft" -> LEFT_BOTTOM;
                case "rightbottom", "bottomright" -> RIGHT_BOTTOM;
                default -> RIGHT_TOP;
            };
        }

        private boolean isRight() { return this == RIGHT_TOP || this == RIGHT_BOTTOM; }
        private boolean isTop() { return this == LEFT_TOP || this == RIGHT_TOP; }
        private boolean isBottom() { return this == LEFT_BOTTOM || this == RIGHT_BOTTOM; }
    }

    private record DimensionValue(double value, boolean percent, boolean set) {
        private static DimensionValue unset() { return new DimensionValue(0.0D, true, false); }

        private static DimensionValue parse(String raw) {
            if (raw == null || raw.isBlank()) return unset();
            String value = raw.trim().toLowerCase(Locale.ROOT);
            try {
                if (value.endsWith("%")) {
                    return new DimensionValue(Double.parseDouble(
                        value.substring(0, value.length() - 1)) / 100.0D, true, true);
                }
                if (value.endsWith("px")) value = value.substring(0, value.length() - 2);
                return new DimensionValue(Double.parseDouble(value), false, true);
            } catch (NumberFormatException ignored) {
                return unset();
            }
        }

        private int resolve(int available, double fallbackFraction) {
            if (!set) return Math.max(1, (int) Math.round(available * fallbackFraction));
            return percent ? Math.max(1, (int) Math.round(available * value))
                : Math.max(1, (int) Math.round(value));
        }
    }

    private record DialogOptions(boolean fullscreen, boolean canceledOnTouchOutside,
                                 Corner closeButton, DimensionValue width,
                                 DimensionValue height, Gravity gravity, int padding,
                                 int backgroundColor, int borderColor, int textColor,
                                 int accentColor, int scrimColor) {
        private static DialogOptions parse(String raw) {
            boolean fullscreen = false;
            boolean outside = false;
            Corner close = Corner.NONE;
            DimensionValue width = DimensionValue.unset();
            DimensionValue height = DimensionValue.unset();
            Gravity gravity = new Gravity(Axis.CENTER, Axis.CENTER);
            int padding = 14;
            int background = 0xF20D1518;
            int border = 0xFF52666B;
            int text = 0xFFF1F4F3;
            int accent = 0xFF67D8B1;
            int scrim = 0x99060A0C;

            if (raw != null) for (String part : raw.split("\\|")) {
                String token = part.trim();
                if (token.isEmpty() || token.equalsIgnoreCase("default")) continue;
                int equals = token.indexOf('=');
                String key = normalize(equals < 0 ? token : token.substring(0, equals));
                String value = equals < 0 ? "true" : token.substring(equals + 1).trim();
                switch (key) {
                    case "fullscreen" -> fullscreen = parseBoolean(value, true);
                    case "canceledontouchoutside", "cancelledontouchoutside" ->
                        outside = parseBoolean(value, true);
                    case "closebutton" -> close = Corner.parse(value);
                    case "width" -> width = DimensionValue.parse(value);
                    case "height" -> height = DimensionValue.parse(value);
                    case "gravity", "position" -> gravity = Gravity.parse(value);
                    case "padding" -> padding = parseInt(value, padding, 0, 128);
                    case "background", "backgroundcolor" -> background = parseColor(value, background);
                    case "border", "bordercolor" -> border = parseColor(value, border);
                    case "text", "textcolor" -> text = parseColor(value, text);
                    case "accent", "accentcolor" -> accent = parseColor(value, accent);
                    case "scrim", "scrimcolor" -> scrim = parseColor(value, scrim);
                    default -> { }
                }
            }
            return new DialogOptions(fullscreen, outside, close, width, height,
                gravity, padding, background, border, text, accent, scrim);
        }
    }

    private enum Alignment { LEFT, CENTER, RIGHT }

    private record HtmlBlock(Component text, float scale, Alignment alignment,
                             int spacingBefore, int spacingAfter, boolean rule) { }

    private static final class HtmlDocument extends HTMLEditorKit.ParserCallback {
        private final List<HtmlBlock> blocks = new ArrayList<>();
        private final Deque<TagState> states = new ArrayDeque<>();
        private MutableComponent current = Component.empty();
        private Style style = Style.EMPTY;
        private float scale = 1.0F;
        private Alignment alignment = Alignment.LEFT;
        private int spacingBefore;
        private int spacingAfter = 4;

        private static List<HtmlBlock> parse(String html) {
            HtmlDocument document = new HtmlDocument();
            try {
                new ParserDelegator().parse(new StringReader(
                    html == null ? "" : html), document, true);
            } catch (IOException | RuntimeException ignored) {
                document.current.append(Component.literal(html == null ? "" : html));
            }
            document.flushDocument();
            if (document.blocks.isEmpty()) {
                document.blocks.add(new HtmlBlock(Component.empty(), 1.0F,
                    Alignment.LEFT, 0, 0, false));
            }
            return List.copyOf(document.blocks);
        }

        @Override
        public void handleStartTag(HTML.Tag tag, MutableAttributeSet attributes, int position) {
            boolean block = isBlock(tag);
            if (block) flushDocument();
            states.push(new TagState(tag, style, scale, alignment,
                spacingBefore, spacingAfter));
            applyTag(tag, attributes);
            if (tag == HTML.Tag.LI) current.append(Component.literal("- ").withStyle(style));
        }

        @Override
        public void handleEndTag(HTML.Tag tag, int position) {
            if (isBlock(tag)) flushDocument();
            while (!states.isEmpty()) {
                TagState previous = states.pop();
                style = previous.style;
                scale = previous.scale;
                alignment = previous.alignment;
                spacingBefore = previous.spacingBefore;
                spacingAfter = previous.spacingAfter;
                if (previous.tag == tag) break;
            }
        }

        @Override
        public void handleSimpleTag(HTML.Tag tag, MutableAttributeSet attributes, int position) {
            if (tag == HTML.Tag.BR) {
                flushDocument();
            } else if (tag == HTML.Tag.HR) {
                flushDocument();
                blocks.add(new HtmlBlock(Component.empty(), 1.0F,
                    Alignment.LEFT, 5, 5, true));
            } else if (tag == HTML.Tag.IMG) {
                Object alt = attributes.getAttribute(HTML.Attribute.ALT);
                if (alt != null) current.append(Component.literal(alt.toString()).withStyle(style));
            }
        }

        @Override
        public void handleText(char[] data, int position) {
            String value = new String(data).replaceAll("\\s+", " ");
            if (!value.isEmpty()) current.append(Component.literal(value).withStyle(style));
        }

        private void applyTag(HTML.Tag tag, MutableAttributeSet attributes) {
            String name = tag.toString().toLowerCase(Locale.ROOT);
            if (tag == HTML.Tag.B || tag == HTML.Tag.STRONG || name.equals("b")) {
                style = style.withBold(true);
            }
            if (tag == HTML.Tag.I || tag == HTML.Tag.EM || name.equals("i")) {
                style = style.withItalic(true);
            }
            if (tag == HTML.Tag.U) style = style.withUnderlined(true);
            if (name.equals("s") || name.equals("strike") || name.equals("del")) {
                style = style.withStrikethrough(true);
            }
            if (tag == HTML.Tag.CODE || name.equals("code")) {
                style = style.withColor(TextColor.fromRgb(0x8FE2C0));
            }
            if (tag == HTML.Tag.H1) {
                scale = 1.60F; style = style.withBold(true); spacingBefore = 4; spacingAfter = 8;
            } else if (tag == HTML.Tag.H2) {
                scale = 1.35F; style = style.withBold(true); spacingBefore = 4; spacingAfter = 6;
            } else if (tag == HTML.Tag.H3) {
                scale = 1.18F; style = style.withBold(true); spacingBefore = 3; spacingAfter = 5;
            } else if (tag == HTML.Tag.P || tag == HTML.Tag.DIV || tag == HTML.Tag.LI) {
                spacingBefore = tag == HTML.Tag.LI ? 1 : 2;
                spacingAfter = tag == HTML.Tag.LI ? 2 : 5;
            }

            Object align = attributes.getAttribute(HTML.Attribute.ALIGN);
            if (align != null) alignment = parseAlignment(align.toString(), alignment);
            Object color = attributes.getAttribute(HTML.Attribute.COLOR);
            if (color != null) style = style.withColor(TextColor.fromRgb(
                parseColor(color.toString(), 0xFFFFFFFF) & 0x00FFFFFF));
            Object css = attributes.getAttribute(HTML.Attribute.STYLE);
            if (css != null) applyCss(css.toString());
        }

        private void applyCss(String css) {
            for (String declaration : css.split(";")) {
                int colon = declaration.indexOf(':');
                if (colon < 0) continue;
                String key = normalize(declaration.substring(0, colon));
                String value = declaration.substring(colon + 1).trim();
                switch (key) {
                    case "color" -> style = style.withColor(TextColor.fromRgb(
                        parseColor(value, 0xFFFFFFFF) & 0x00FFFFFF));
                    case "textalign" -> alignment = parseAlignment(value, alignment);
                    case "fontweight" -> {
                        if (value.equalsIgnoreCase("bold") || parseInt(value, 0, 0, 1000) >= 600) {
                            style = style.withBold(true);
                        }
                    }
                    case "fontstyle" -> {
                        if (value.equalsIgnoreCase("italic")) style = style.withItalic(true);
                    }
                    case "textdecoration" -> {
                        if (value.toLowerCase(Locale.ROOT).contains("underline")) {
                            style = style.withUnderlined(true);
                        }
                        if (value.toLowerCase(Locale.ROOT).contains("line-through")) {
                            style = style.withStrikethrough(true);
                        }
                    }
                    default -> { }
                }
            }
        }

        private void flushDocument() {
            if (current.getString().isBlank()) {
                current = Component.empty();
                return;
            }
            blocks.add(new HtmlBlock(current, scale, alignment,
                spacingBefore, spacingAfter, false));
            current = Component.empty();
            spacingBefore = 0;
            spacingAfter = 4;
        }

        private static boolean isBlock(HTML.Tag tag) {
            return tag == HTML.Tag.P || tag == HTML.Tag.DIV || tag == HTML.Tag.LI
                || tag == HTML.Tag.H1 || tag == HTML.Tag.H2 || tag == HTML.Tag.H3
                || tag == HTML.Tag.BLOCKQUOTE || tag == HTML.Tag.PRE
                || tag == HTML.Tag.CENTER;
        }

        private record TagState(HTML.Tag tag, Style style, float scale,
                                Alignment alignment, int spacingBefore,
                                int spacingAfter) { }
    }

    private static Alignment parseAlignment(String value, Alignment fallback) {
        return switch (normalize(value)) {
            case "center", "middle" -> Alignment.CENTER;
            case "right", "end" -> Alignment.RIGHT;
            case "left", "start" -> Alignment.LEFT;
            default -> fallback;
        };
    }

    private static boolean parseBoolean(String value, boolean fallback) {
        if (value == null || value.isBlank()) return fallback;
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "true", "1", "yes", "on" -> true;
            case "false", "0", "no", "off" -> false;
            default -> fallback;
        };
    }

    private static int parseInt(String value, int fallback, int minimum, int maximum) {
        try {
            return Math.max(minimum, Math.min(maximum, Integer.parseInt(value.trim())));
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static int parseColor(String value, int fallback) {
        if (value == null) return fallback;
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        Map<String, Integer> named = NAMED_COLORS;
        if (named.containsKey(normalized)) return named.get(normalized);
        try {
            if (normalized.startsWith("#")) normalized = normalized.substring(1);
            else if (normalized.startsWith("0x")) normalized = normalized.substring(2);
            long parsed = Long.parseLong(normalized, 16);
            if (normalized.length() <= 6) parsed |= 0xFF000000L;
            return (int) parsed;
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static final Map<String, Integer> NAMED_COLORS = namedColors();

    private static Map<String, Integer> namedColors() {
        Map<String, Integer> colors = new HashMap<>();
        colors.put("black", 0xFF000000);
        colors.put("white", 0xFFFFFFFF);
        colors.put("gray", 0xFF808080);
        colors.put("grey", 0xFF808080);
        colors.put("red", 0xFFFF5555);
        colors.put("green", 0xFF55FF55);
        colors.put("blue", 0xFF5599FF);
        colors.put("yellow", 0xFFFFFF55);
        colors.put("aqua", 0xFF55FFFF);
        colors.put("cyan", 0xFF55FFFF);
        colors.put("purple", 0xFFAA55FF);
        colors.put("magenta", 0xFFFF55FF);
        colors.put("orange", 0xFFFFAA33);
        return Map.copyOf(colors);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT)
            .replace("_", "").replace("-", "").replace(" ", "");
    }

    private record Bounds(int x, int y, int width, int height) {
        private int right() { return x + width; }
        private int bottom() { return y + height; }
        private boolean contains(double px, double py) {
            return px >= x && px < right() && py >= y && py < bottom();
        }
    }
}

