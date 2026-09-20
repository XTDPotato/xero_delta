package com.xtdpotato.xero_delta.screen;

import com.xtdpotato.xero_delta.client.ScreenTransition;
import com.xtdpotato.xero_delta.screen.material.Material2Button;
import com.xtdpotato.xero_delta.screen.material.Material2Drawing;
import com.xtdpotato.xero_delta.screen.material.Material2Icon;
import com.xtdpotato.xero_delta.trading.TradingHtmlThemeParser;
import com.xtdpotato.xero_delta.trading.TradingHtmlTemplate;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/** Full-screen searchable entity selector with a model preview. */
final class CorpseEntityPickerScreen extends Screen {
    private final Screen parent;
    private final Consumer<String> callback;
    private final String initialId;
    private final TradingHtmlThemeParser.Theme theme;
    private final ScreenTransition transition = new ScreenTransition();
    private MaterialEditBox search;
    private EntityModelPreview preview;
    private Material2Button previewReset;
    private String selectedId;
    private String previewId;
    private double scroll;
    private double scrollTarget;
    private boolean scrollbarDragging;
    private double scrollbarGrabOffset;
    private Component bottomNotice;
    private long bottomNoticeAt;
    private List<CorpseEntityRuleRow> rows = List.of();
    /** Cached filter result; rebuilding the registry stream every frame was costly. */
    private List<String> filteredEntityIds = List.of();
    private int firstVisibleRow;
    private static final int ROW_STEP = 34;
    private static final int WHEEL_PAGE_ROWS = 7;

    CorpseEntityPickerScreen(Screen parent, String initialId, Consumer<String> callback) {
        super(Component.translatable("screen.xero_delta.corpse_rules.select_entity"));
        this.parent = parent;
        this.initialId = initialId == null ? "" : initialId;
        this.selectedId = this.initialId;
        this.previewId = this.initialId;
        this.callback = callback;
        this.theme = ConfigThemeCatalog.theme(
            com.xtdpotato.xero_delta.Config.INSTANCE.configTheme.get());
    }

    @Override
    protected void init() {
        search = addRenderableWidget(new MaterialEditBox(font, panelX() + 16,
            panelY() + 42, Math.max(100, panelWidth() - 32), 20,
            Component.translatable("screen.xero_delta.corpse_rules.search")));
        search.setMaxLength(128);
        search.setResponder(value -> {
            scroll = scrollTarget = 0.0D;
            rebuildRows();
        });
        preview = new EntityModelPreview(panelX() + panelWidth() * 43 / 100 + 14,
            panelY() + 84, panelWidth() * 54 / 100 - 28, panelHeight() - 132,
            entityType(previewId), theme.panelAlt(), theme.border(), theme.accent());
        previewReset = new Material2Button(0, 0, 28,
            Component.translatable("screen.xero_delta.corpse_rules.reset_preview"),
            Material2Button.Variant.TEXT, () -> preview.resetView()).icon(Material2Icon.RESTART_ALT);
        rebuildRows();
    }

    private void rebuildRows() {
        String query = search == null ? "" : search.getValue().trim().toLowerCase(Locale.ROOT);
        filteredEntityIds = BuiltInRegistries.ENTITY_TYPE.keySet().stream()
            .map(ResourceLocation::toString)
            .filter(id -> query.isBlank() || id.toLowerCase(Locale.ROOT).contains(query)
                || localizedEntityName(id).toLowerCase(Locale.ROOT).contains(query))
            .sorted().toList();
        double maximum = Math.max(0.0D, filteredEntityIds.size() * ROW_STEP - listHeight());
        scrollTarget = Math.max(0.0D, Math.min(maximum, scrollTarget));
        scroll = Math.max(0.0D, Math.min(maximum, scroll));
        refreshVisibleRows();
    }

    /** Creates rows only for the viewport plus a small buffer. Entity models are expensive. */
    private void refreshVisibleRows() {
        int first = Math.max(0, (int) Math.floor(scroll / ROW_STEP) - 1);
        int last = Math.min(filteredEntityIds.size(),
            (int) Math.ceil((scroll + listHeight()) / ROW_STEP) + 1);
        firstVisibleRow = first;
        List<CorpseEntityRuleRow> result = new ArrayList<>(Math.max(0, last - first));
        int x = panelX() + 24;
        int y = panelY() + 80;
        int width = panelWidth() * 41 / 100 - 36;
        for (int index = first; index < last; index++) {
            String id = filteredEntityIds.get(index);
            EntityType<?> type = entityType(id);
            result.add(new CorpseEntityRuleRow(font, x, y + index * ROW_STEP, width, type, id,
                () -> id.equals(selectedId), () -> {
                    selectedId = id;
                    previewId = id;
                    if (preview != null) preview.setEntityType(type);
                }, () -> {
                    previewId = id;
                    if (preview != null) preview.setEntityType(type);
                }, () -> copyEntityId(id), theme.panelAlt(), Material2Drawing.alpha(theme.accent(), 46),
                theme.accent(), theme.text()));
        }
        rows = List.copyOf(result);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xC90A1013);
        transition.push(graphics);
        int x = panelX(), y = panelY(), w = panelWidth(), h = panelHeight();
        graphics.fill(x, y, x + w, y + h, theme.background());
        graphics.drawString(font, title, x + 24, y + 16, theme.text(), false);
        graphics.fill(x + 24, y + 68, x + w - 24, y + 69, theme.border());
        int listX = x + 24;
        int listW = w * 41 / 100;
        int listY = y + 76;
        int listH = listHeight();
        graphics.fill(listX, listY, listX + listW, listY + listH, theme.panel());
        int scrollbarX = listX + listW - 8;
        drawScrollbar(graphics, scrollbarX, listY, listH);
        graphics.enableScissor(listX, listY, scrollbarX, listY + listH);
        int index = firstVisibleRow;
        for (CorpseEntityRuleRow row : rows) {
            row.setY(listY + index++ * ROW_STEP - (int) Math.round(scroll));
            row.render(graphics, mouseX, mouseY, partialTick);
        }
        graphics.disableScissor();
        if (preview != null) {
            preview.setPosition(listX + listW + 24, y + 82);
            preview.setWidth(w - listW - 72);
            preview.setHeight(h - 132);
            preview.render(graphics, mouseX, mouseY, partialTick);
            int resetX = preview.getX() + preview.getWidth() - 34;
            int resetY = preview.getY() + preview.getHeight() - 26;
            previewReset.setPosition(resetX, resetY);
            previewReset.render(graphics, mouseX, mouseY, partialTick);
        }
        drawAction(graphics, x + w - 194, y + h - 34, 84,
            Component.translatable("screen.xero_delta.config.cancel"), true,
            Material2Icon.CLOSE, mouseX, mouseY);
        drawAction(graphics, x + w - 102, y + h - 34, 84,
            Component.translatable("screen.xero_delta.config.confirm"), !selectedId.isBlank(),
            Material2Icon.CHECK, mouseX, mouseY);
        search.render(graphics, mouseX, mouseY, partialTick);
        renderBottomNotice(graphics);
        transition.pop(graphics);
        transition.drawFade(graphics, width, height);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY,
                                 float partialTick) {
        // This screen draws its own crisp dimmed popup background.
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (transition.closing()) return true;
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        int x = panelX(), y = panelY(), w = panelWidth(), h = panelHeight();
        int listX = x + 24, listW = w * 41 / 100, listY = y + 76, listH = listHeight();
        if (button == 0 && inside(mouseX, mouseY, listX + listW - 12, listY, 16, listH)) {
            scrollbarDragging = true;
            scrollbarGrabOffset = mouseY - scrollbarY(listY, listH);
            setScrollFromMouse(mouseY, listY, listH, scrollbarGrabOffset);
            return true;
        }
        if (inside(mouseX, mouseY, x + w - 194, y + h - 34, 84, 24)) {
            onClose();
            return true;
        }
        if (inside(mouseX, mouseY, x + w - 102, y + h - 34, 84, 24)
            && !selectedId.isBlank()) {
            String result = selectedId;
            transition.beginClose(() -> {
                callback.accept(result);
                minecraft.setScreen(parent);
            });
            return true;
        }
        if (previewReset != null && previewReset.isMouseOver(mouseX, mouseY)) {
            preview.resetView();
            return true;
        }
        if (preview != null && preview.mouseClicked(mouseX, mouseY, button)) return true;
        listY = panelY() + 76;
        for (CorpseEntityRuleRow row : rows) {
            if (row.mouseClicked(mouseX, mouseY, button)) return true;
        }
        return inside(mouseX, mouseY, panelX() + 14, listY, panelWidth() * 41 / 100,
            listHeight());
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button,
                                double dragX, double dragY) {
        if (scrollbarDragging && button == 0) {
            setScrollFromMouse(mouseY, panelY() + 76, listHeight(), scrollbarGrabOffset);
            return true;
        }
        return preview != null && preview.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && scrollbarDragging) {
            scrollbarDragging = false;
            return true;
        }
        return preview != null && preview.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (preview != null && preview.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) return true;
        int listX = panelX() + 24;
        if (!inside(mouseX, mouseY, listX, panelY() + 76, panelWidth() * 41 / 100,
            listHeight())) return false;
        double maximum = Math.max(0.0D, filteredEntityIds.size() * ROW_STEP - listHeight());
        scrollTarget = Math.max(0.0D, Math.min(maximum,
            scrollTarget - scrollY * ROW_STEP * WHEEL_PAGE_ROWS));
        scroll += (scrollTarget - scroll) * 0.45D;
        refreshVisibleRows();
        return true;
    }

    @Override
    public void tick() {
        super.tick();
        scroll += (scrollTarget - scroll) * 0.32D;
        refreshVisibleRows();
        transition.tick(minecraft);
    }

    @Override
    public void onClose() {
        transition.beginClose(() -> minecraft.setScreen(parent));
    }

    private List<String> filteredIds() {
        return filteredEntityIds;
    }

    private static EntityType<?> entityType(String id) {
        ResourceLocation resource = ResourceLocation.tryParse(id == null ? "" : id);
        return resource == null ? null : BuiltInRegistries.ENTITY_TYPE.get(resource);
    }

    private static String localizedEntityName(String id) {
        EntityType<?> type = entityType(id);
        return type == null ? id : type.getDescription().getString();
    }

    private int listHeight() {
        return Math.max(72, panelHeight() - 132);
    }

    private int panelWidth() { return width; }
    private int panelHeight() { return height; }
    private int panelX() { return 0; }
    private int panelY() { return 0; }
    private static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private void drawAction(GuiGraphics graphics, int x, int y, int w, Component label,
                            boolean active, Material2Icon icon, int mouseX, int mouseY) {
        Material2Button button = new Material2Button(x, y, w, label,
            Material2Button.Variant.TONAL, () -> { }).icon(icon);
        button.active = active;
        button.render(graphics, mouseX, mouseY, 0.0F);
    }

    private void copyEntityId(String id) {
        minecraft.keyboardHandler.setClipboard(id);
        bottomNotice = Component.translatable("screen.xero_delta.corpse_rules.copied_entity_id", id);
        bottomNoticeAt = System.currentTimeMillis();
    }

    private void renderBottomNotice(GuiGraphics graphics) {
        if (bottomNotice == null || System.currentTimeMillis() - bottomNoticeAt > 1800L) return;
        int w = Math.min(width - 24, font.width(bottomNotice) + 20);
        int x = (width - w) / 2;
        int y = height - 62;
        Material2Drawing.roundedRect(graphics, x, y, w, 22, 6, theme.panelAlt());
        graphics.drawCenteredString(font, bottomNotice, width / 2,
            y + (22 - font.lineHeight) / 2, theme.accent());
    }

    private void drawScrollbar(GuiGraphics graphics, int x, int y, int height) {
        int total = Math.max(1, filteredEntityIds.size() * ROW_STEP);
        int thumbHeight = Math.min(height, Math.max(24,
            (int) Math.round(height * (height / (double) total))));
        int thumbY = scrollbarY(y, height);
        graphics.fill(x, y, x + 4, y + height, Material2Drawing.alpha(theme.border(), 80));
        graphics.fill(x, thumbY, x + 4, thumbY + thumbHeight,
            scrollbarDragging ? theme.accent() : Material2Drawing.alpha(theme.accent(), 190));
    }

    private int scrollbarY(int y, int height) {
        int total = Math.max(1, filteredEntityIds.size() * ROW_STEP);
        int thumbHeight = Math.min(height, Math.max(24,
            (int) Math.round(height * (height / (double) total))));
        double max = Math.max(0.0D, total - height);
        double track = Math.max(0.0D, height - thumbHeight);
        return y + (int) Math.round(track * (max <= 0.0D ? 0.0D : scroll / max));
    }

    private void setScrollFromMouse(double mouseY, int y, int height, double grabOffset) {
        int total = Math.max(1, filteredEntityIds.size() * ROW_STEP);
        int thumbHeight = Math.min(height, Math.max(24,
            (int) Math.round(height * (height / (double) total))));
        double track = Math.max(1.0D, height - thumbHeight);
        double position = Math.max(0.0D, Math.min(track, mouseY - y - grabOffset));
        double max = Math.max(0.0D, total - height);
        scrollTarget = max <= 0.0D ? 0.0D : position / track * max;
        scroll = scrollTarget;
        refreshVisibleRows();
    }
}

