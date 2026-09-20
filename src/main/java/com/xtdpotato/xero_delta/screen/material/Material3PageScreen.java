package com.xtdpotato.xero_delta.screen.material;

import com.xtdpotato.xero_delta.screen.Material3CompactEditBox;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

/** Shared fixed chrome and clipped, independently scrolling Material 3 forms. */
public abstract class Material3PageScreen extends Screen {
    protected final Screen parent;
    protected int contentX;
    protected int contentWidth;
    protected int bodyTop = 36;
    protected int bodyBottom;
    private int cursor;
    private int contentHeight;
    private double scroll;
    private final List<Entry> entries = new ArrayList<>();
    private final List<Label> labels = new ArrayList<>();
    private final List<Nav> navigation = new ArrayList<>();
    private final List<ColorField> colors = new ArrayList<>();
    private int navScroll;
    private String selectedPage = "";
    private String dialogTitle;
    private Runnable dialogAction;
    private final List<AbstractWidget> dialogWidgets = new ArrayList<>();
    private AbstractWidget pointer;

    protected Material3PageScreen(Component title, Screen parent) {
        super(title);
        this.parent = parent;
    }

    protected abstract void buildPage();

    @Override
    protected void init() {
        Material3Theme.refreshFromConfig();
        entries.clear();
        labels.clear();
        navigation.clear();
        colors.clear();
        dialogWidgets.clear();
        dialogTitle = null;
        pointer = null;
        MaterialPageLayout layout = MaterialPageLayout.of(width, height, true);
        contentX = layout.contentX();
        contentWidth = layout.contentWidth();
        bodyTop = layout.bodyTop();
        bodyBottom = layout.bodyBottom();
        cursor = 0;
        buildPage();
        contentHeight = cursor;
        positionEntries();
    }

    protected void rebuildPage() {
        clearWidgets();
        init();
    }

    protected void navigate(String page, Runnable action) {
        scroll = 0;
        selectedPage = page;
        action.run();
        rebuildPage();
    }

    protected void navigation(String id, String label, Material2Icon icon, Runnable action) {
        navigation.add(new Nav(id, label, icon, action));
    }

    protected void selectedPage(String id) {
        selectedPage = id;
    }

    protected void section(String text) {
        if (cursor > 0) cursor += 10;
        labels.add(new Label(cursor, () -> text, true));
        cursor += 22;
    }

    protected void text(String value) {
        for (var line : font.split(Component.literal(value), Math.max(20, contentWidth - 8))) {
            StringBuilder plain = new StringBuilder();
            line.accept((index, style, codePoint) -> { plain.appendCodePoint(codePoint); return true; });
            labels.add(new Label(cursor, plain::toString, false));
            cursor += 13;
        }
        cursor += 4;
    }

    protected <T extends AbstractWidget> T row(T widget, int rowHeight) {
        widget.setX(contentX);
        widget.setWidth(contentWidth);
        entries.add(new Entry(widget, cursor));
        addRenderableWidget(widget);
        cursor += rowHeight;
        return widget;
    }

    protected void toggle(String label, boolean value, Consumer<Boolean> change) {
        boolean[] current = {value};
        row(new Material2ToggleRow(0, 0, contentWidth, Component.literal(label),
            () -> current[0], () -> { current[0] = !current[0]; change.accept(current[0]); },
            Material3Theme.PRIMARY, Material3Theme.SURFACE_CONTAINER_HIGH,
            Material3Theme.TEXT, Material3Theme.OUTLINE), 28);
    }

    protected void slider(String label, String suffix, double min, double max, double step,
                          double value, DoubleConsumer change) {
        double[] current = {value};
        labels.add(new Label(cursor, () -> label + "  "
            + MaterialSliderValue.format(step, current[0]) + suffix, false));
        cursor += 15;
        row(new Material2Slider(0, 0, contentWidth, min, max, step, value, next -> {
            current[0] = next;
            change.accept(next);
        }, Material3Theme.PRIMARY, Material3Theme.SURFACE_CONTAINER_HIGHEST,
            Material3Theme.PRIMARY_CONTAINER), 24);
    }

    protected Material3CompactEditBox field(String label, String value, int maxLength,
                                            Predicate<String> valid, Consumer<String> change) {
        labels.add(new Label(cursor, () -> label, false));
        cursor += 14;
        Material3CompactEditBox field = new Material3CompactEditBox(font, 0, 0,
            contentWidth, 22, Component.literal(label));
        field.setMaxLength(maxLength);
        field.setValue(value);
        field.setTextColor(valid.test(value) ? Material3Theme.TEXT : Material3Theme.ERROR);
        field.setResponder(next -> {
            field.setTextColor(valid.test(next) ? Material3Theme.TEXT : Material3Theme.ERROR);
            change.accept(next);
        });
        return row(field, 30);
    }

    protected Material3CompactEditBox color(String label, String value, Consumer<String> change) {
        Material3CompactEditBox field = field(label, value, 10,
            text -> text.matches("(?i)(?:0x|#)?(?:[0-9a-f]{6}|[0-9a-f]{8})"), change);
        field.setTooltip(Tooltip.create(Component.literal("#RRGGBB / 0xAARRGGBB")));
        field.setWidth(contentWidth - 30);
        colors.add(new ColorField(field));
        return field;
    }

    protected <T> void choice(String label, List<T> values, T value,
                               java.util.function.Function<T, String> names, Consumer<T> change) {
        action(label + ": " + names.apply(value), () -> {
            Screen owner = this;
            minecraft.setScreen(new Material3PageScreen(Component.literal(label), owner) {
                @Override protected void buildPage() {
                    contentX = 16;
                    contentWidth = width - 32;
                    for (T option : values) action(names.apply(option), () -> {
                        change.accept(option);
                        minecraft.setScreen(owner);
                    });
                    footer(null, null);
                }
            });
        });
    }

    protected Material3Button action(String label, Runnable action) {
        return row(Material3Button.builder(Component.literal(label), button -> action.run())
            .size(contentWidth, 24).variant(Material2Button.Variant.TONAL).build(), 30);
    }

    protected void footer(String primary, Runnable save) {
        int y = height - 28;
        addRenderableWidget(Material3Button.builder(Component.translatable("gui.back"),
            button -> onClose()).bounds(12, y, 70, 22)
            .variant(Material2Button.Variant.TEXT).build());
        if (primary != null) addRenderableWidget(Material3Button.builder(Component.literal(primary),
            button -> save.run()).bounds(Math.max(86, width - 92), y, 80, 22)
            .variant(Material2Button.Variant.FILLED).build());
    }

    protected void confirm(String title, Runnable action) {
        dialogTitle = title;
        dialogAction = action;
        setFocused(null);
        pointer = null;
        dialogWidgets.clear();
        int x = Math.max(8, (width - 248) / 2);
        int w = Math.min(248, width - 16);
        int y = height / 2 + 10;
        dialogWidgets.add(Material3Button.builder(Component.translatable("gui.cancel"),
            button -> dismissDialog()).bounds(x + 10, y, (w - 30) / 2, 22)
            .variant(Material2Button.Variant.TEXT).build());
        dialogWidgets.add(Material3Button.builder(Component.translatable("gui.ok"), button -> {
            Runnable pending = dialogAction;
            dismissDialog();
            pending.run();
        }).bounds(x + 20 + (w - 30) / 2, y, (w - 30) / 2, 22)
            .variant(Material2Button.Variant.FILLED).build());
    }

    protected void error(String message) {
        confirm(message, () -> {});
    }

    private void dismissDialog() {
        dialogTitle = null;
        dialogAction = null;
        dialogWidgets.clear();
    }

    private void positionEntries() {
        scroll = new MaterialPageLayout(contentX, contentWidth, bodyTop, bodyBottom)
            .clampScroll(scroll, contentHeight);
        for (Entry entry : entries) {
            entry.widget.setY(bodyTop + entry.y - (int) scroll);
            entry.widget.visible = entry.widget.getY() < bodyBottom
                && entry.widget.getY() + entry.widget.getHeight() > bodyTop;
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, Material3Theme.BACKGROUND);
        graphics.fill(0, 0, width, 28, Material3Theme.SURFACE);
        graphics.drawString(font, font.plainSubstrByWidth(title.getString(), width - 24),
            12, 10, Material3Theme.TEXT, false);
        graphics.fill(0, height - 34, width, height, Material3Theme.SURFACE);
        if (!navigation.isEmpty()) {
            graphics.fill(0, 28, contentX - 10, height - 34, Material3Theme.SURFACE_CONTAINER_LOW);
            int visible = Math.max(1, (height - 68) / 28);
            navScroll = Math.max(0, Math.min(navScroll, navigation.size() - visible));
            for (int index = navScroll; index < Math.min(navigation.size(), navScroll + visible); index++) {
                Nav nav = navigation.get(index);
                int y = 36 + (index - navScroll) * 28;
                boolean chosen = nav.id.equals(selectedPage);
                if (chosen || mouseX < contentX - 10 && mouseY >= y && mouseY < y + 24)
                    Material2Drawing.roundedRect(graphics, 6, y, contentX - 22, 24, 8,
                        chosen ? Material3Theme.SECONDARY_CONTAINER : Material3Theme.SURFACE_CONTAINER_HIGH);
                nav.icon.render(graphics, 18, y + 12, chosen ? Material3Theme.PRIMARY : Material3Theme.TEXT_MUTED);
                graphics.drawString(font, font.plainSubstrByWidth(nav.label, contentX - 48),
                    30, y + 8, Material3Theme.TEXT, false);
            }
        }
        graphics.enableScissor(contentX, bodyTop, width - 8, bodyBottom);
        for (Label label : labels) {
            int y = bodyTop + label.y - (int) scroll;
            if (y + 12 < bodyTop || y >= bodyBottom) continue;
            if (label.heading) graphics.fill(contentX, y + 17, width - 16, y + 18, Material3Theme.OUTLINE_VARIANT);
            graphics.drawString(font, font.plainSubstrByWidth(label.text.get(), contentWidth),
                contentX, y, label.heading ? Material3Theme.PRIMARY : Material3Theme.TEXT_MUTED, false);
        }
        for (Entry entry : entries) if (entry.widget.visible)
            entry.widget.render(graphics, dialogTitle == null ? mouseX : -1,
                mouseY >= bodyTop && mouseY < bodyBottom && dialogTitle == null ? mouseY : -1, partialTick);
        for (ColorField color : colors) if (color.field.visible) {
            int fill = Material3Theme.ERROR;
            String raw = color.field.getValue().replaceFirst("(?i)^(0x|#)", "");
            if (raw.matches("(?i)([0-9a-f]{6}|[0-9a-f]{8})"))
                fill = 0xFF000000 | (int) Long.parseLong(raw, 16);
            Material2Drawing.roundedRect(graphics, contentX + contentWidth - 22,
                color.field.getY() + 2, 18, 18, 4, fill);
        }
        graphics.disableScissor();
        for (var child : children()) if (child instanceof AbstractWidget widget
            && entries.stream().noneMatch(entry -> entry.widget == widget)) {
            widget.render(graphics, dialogTitle == null ? mouseX : -1, mouseY, partialTick);
        }
        if (contentHeight > bodyBottom - bodyTop) {
            int track = bodyBottom - bodyTop;
            int thumb = Math.max(12, track * track / contentHeight);
            int top = bodyTop + (int) (scroll * (track - thumb) / (contentHeight - track));
            Material2Drawing.roundedRect(graphics, width - 6, top, 2, thumb, 1, Material3Theme.PRIMARY);
        }
        if (dialogTitle != null) {
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, 1000);
            graphics.fill(0, 0, width, height, Material3Theme.SCRIM);
            int w = Math.min(248, width - 16), x = (width - w) / 2;
            var lines = font.split(Component.literal(dialogTitle), w - 24);
            int h = Math.min(height - 16, 58 + lines.size() * 12);
            Material2Drawing.roundedRect(graphics, x, height / 2 + 42 - h, w, h, 8,
                Material3Theme.SURFACE_CONTAINER_HIGH);
            int y = height / 2 - 4 - lines.size() * 12;
            for (var line : lines) { graphics.drawString(font, line, x + 12, y, Material3Theme.TEXT, false); y += 12; }
            for (AbstractWidget widget : dialogWidgets) widget.render(graphics, mouseX, mouseY, partialTick);
            graphics.pose().popPose();
        }
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        if (dialogTitle != null) {
            for (AbstractWidget widget : List.copyOf(dialogWidgets)) if (widget.mouseClicked(x, y, button)) return true;
            return true;
        }
        if (button == 0 && !navigation.isEmpty() && x < contentX - 10 && y >= 36 && y < height - 34) {
            int index = (int) (y - 36) / 28 + navScroll;
            if (index < navigation.size()) navigation.get(index).action.run();
            return true;
        }
        for (var child : List.copyOf(children())) if (child instanceof AbstractWidget widget) {
            boolean body = entries.stream().anyMatch(entry -> entry.widget == widget);
            if (body && (y < bodyTop || y >= bodyBottom)) continue;
            if (widget.mouseClicked(x, y, button)) {
                setFocused(widget);
                pointer = widget;
                return true;
            }
        }
        setFocused(null);
        return false;
    }

    @Override public boolean mouseReleased(double x, double y, int button) {
        if (dialogTitle != null) {
            for (AbstractWidget widget : List.copyOf(dialogWidgets)) widget.mouseReleased(x, y, button);
            return true;
        }
        AbstractWidget pressed = pointer;
        pointer = null;
        return pressed != null && pressed.mouseReleased(x, y, button);
    }

    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        return dialogTitle != null || pointer != null && pointer.mouseDragged(x, y, button, dx, dy);
    }

    @Override public boolean mouseScrolled(double x, double y, double dx, double dy) {
        if (dialogTitle != null) return true;
        if (!navigation.isEmpty() && x < contentX - 10) {
            navScroll -= (int) Math.signum(dy);
            return true;
        }
        if (y >= bodyTop && y < bodyBottom) {
            scroll -= dy * 24;
            positionEntries();
            return true;
        }
        return false;
    }

    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (dialogTitle != null) {
            if (key == 256) dismissDialog();
            else if (key == 257 || key == 335) {
                Runnable action = dialogAction;
                dismissDialog();
                action.run();
            }
            return true;
        }
        boolean handled = super.keyPressed(key, scan, modifiers);
        if (key == 258 && getFocused() instanceof AbstractWidget widget) {
            entries.stream().filter(entry -> entry.widget == widget).findFirst().ifPresent(entry -> {
                if (entry.y < scroll) scroll = entry.y;
                else if (entry.y + widget.getHeight() > scroll + bodyBottom - bodyTop)
                    scroll = entry.y + widget.getHeight() - (bodyBottom - bodyTop);
                positionEntries();
            });
        }
        return handled;
    }

    @Override public boolean charTyped(char character, int modifiers) {
        return dialogTitle != null || super.charTyped(character, modifiers);
    }

    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
    private record Entry(AbstractWidget widget, int y) {}
    private record Label(int y, Supplier<String> text, boolean heading) {}
    private record Nav(String id, String label, Material2Icon icon, Runnable action) {}
    private record ColorField(Material3CompactEditBox field) {}
}
