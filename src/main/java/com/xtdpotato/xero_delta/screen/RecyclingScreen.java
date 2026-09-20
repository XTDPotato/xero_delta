package com.xtdpotato.xero_delta.screen;

import com.xtdpotato.xero_delta.client.ClientDataCache;
import com.xtdpotato.xero_delta.client.TradingClientState;
import com.xtdpotato.xero_delta.client.ScreenTransition;
import com.xtdpotato.xero_delta.client.TradingHistorySelection;
import com.xtdpotato.xero_delta.client.TradingUi;
import com.xtdpotato.xero_delta.client.TradingUiPreferences;
import com.xtdpotato.xero_delta.client.TradingUiScale;
import com.xtdpotato.xero_delta.data.ModDataStorage;
import com.xtdpotato.xero_delta.network.ModNetwork;
import com.xtdpotato.xero_delta.network.TradingActionPacket;
import com.xtdpotato.xero_delta.network.TradingSyncPacket;
import com.xtdpotato.xero_delta.trading.RecyclingHtmlTemplate;
import com.xtdpotato.xero_delta.trading.RecyclingMenu;
import com.xtdpotato.xero_delta.trading.TradingCategory;
import com.xtdpotato.xero_delta.trading.TradingCreativeCategoryGroups;
import com.xtdpotato.xero_delta.trading.TradingCategorySectionPresentation;
import com.xtdpotato.xero_delta.trading.TradingHtmlThemeParser;
import com.xtdpotato.xero_delta.trading.TradingItemCategory;
import com.xtdpotato.xero_delta.trading.TradingRules;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class RecyclingScreen extends AbstractContainerScreen<RecyclingMenu> {
    private static final int HEADER_HEIGHT = 43;
    private static final int CATALOGUE_TOP = 82;
    private static final int FOOTER_HEIGHT = 44;
    private static final long CATEGORY_SCROLL_ANIMATION_NANOS = 100_000_000L;

    private enum Group { ALL, VANILLA, MODDED }

    private final TradingClientState state = TradingClientState.INSTANCE;
    private TradingHtmlThemeParser.Theme theme;
    private TradingHtmlThemeParser.Document document;
    private EditBox searchBox;
    private TradingCategory category = TradingCategory.ALL;
    private final Set<String> expandedCategorySections = new LinkedHashSet<>();
    private String selectedCreativeSection = "";
    private double categoryScrollPixels;
    private double categoryScrollStart;
    private double categoryScrollTarget;
    private long categoryScrollAnimationNanos;
    private boolean categorySectionsInitialized;
    private Group group = Group.ALL;
    private String selectedSource = "";
    private final Set<String> selectedSources = new LinkedHashSet<>();
    private final Set<String> expandedSourceGroups = new LinkedHashSet<>();
    private String selectionAnchor;
    private int catalogueColumns = 3;
    private int scroll;
    private long seenRevision = -1;
    private boolean confirming;
    private long confirmUntil;
    private String toast = "";
    private long toastValue;
    private boolean toastSuccess = true;
    private long toastUntil;
    private ItemStack hoveredStack = ItemStack.EMPTY;
    private boolean draggingScrollbar;
    private boolean draggingCategoryScrollbar;
    private double categoryScrollbarGrabOffset;
    private final ScreenTransition transition = new ScreenTransition(false);
    private boolean categoryTouchPressed;
    private boolean categoryTouchDragging;
    private boolean categoryTouchReleaseClick;
    private double categoryTouchStartX;
    private double categoryTouchStartY;
    private double categoryTouchStartScroll;
    private double categoryTouchLastY;
    private long categoryTouchLastNanos;
    private double categoryTouchVelocity;
    private boolean touchPressed;
    private boolean touchDragging;
    private boolean touchReleaseClick;
    private double touchStartX;
    private double touchStartY;
    private double touchLastY;
    private TradingUiScale.Viewport uiViewport = TradingUiScale.viewport(1, 1, 0);

    public RecyclingScreen(RecyclingMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected void init() {
        uiViewport = TradingUiScale.viewport(width, height, TradingUiPreferences.tradingUiScaleLevel());
        width = uiViewport.logicalWidth();
        height = uiViewport.logicalHeight();
        imageWidth = width;
        imageHeight = height;
        super.init();
        leftPos = 0;
        topPos = 0;
        document = RecyclingHtmlTemplate.loadDocument();
        theme = document.theme();
        TradingCreativeCategoryGroups.refresh(minecraft);
        initializeCategorySections();
        catalogueColumns = TradingUiPreferences.recyclingColumns();
        int sidebar = sidebarWidth();
        searchBox = addRenderableWidget(new Material3CompactEditBox(font, 18, 52, sidebar - 36, 20,
            Component.translatable("recycle.xero_delta.search")));
        searchBox.setHint(Component.translatable("recycle.xero_delta.search_hint"));
        searchBox.setMaxLength(128);
        searchBox.setResponder(ignored -> resetCatalogueSelection());
        ModNetwork.sendToServer(TradingActionPacket.refresh());
        state.restoreCursor();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (state.revision() != seenRevision) {
            seenRevision = state.revision();
            if (expandedSourceGroups.isEmpty()) {
                state.sourceGroups().stream().map(TradingSyncPacket.SourceGroupView::id)
                    .forEach(expandedSourceGroups::add);
            }
            String pendingSource = state.consumePendingSourceId();
            if (!pendingSource.isBlank()) {
                selectedSources.clear();
                selectedSources.add(pendingSource);
                selectedSource = pendingSource;
                selectionAnchor = pendingSource;
            }
            if (!state.message().isBlank()) {
                toastValue = Math.max(0L, state.messageValue());
                toast = toastValue > 0
                    ? Component.translatable("recycle.xero_delta.success_label").getString()
                    : Component.translatable(state.message()).getString();
                toastSuccess = state.success();
                toastUntil = System.currentTimeMillis() + 3500L;
                if (state.success() && state.messageValue() > 0) {
                    selectedSource = "";
                    selectedSources.clear();
                    selectionAnchor = null;
                    confirming = false;
                }
            }
        }
        if (confirming && System.currentTimeMillis() > confirmUntil) confirming = false;
        pruneSourceSelection();
        if (selectedSources.isEmpty()) {
            selectedSource = "";
            selectionAnchor = null;
            confirming = false;
        }
        scroll = Math.min(scroll, maxScroll());
        hoveredStack = ItemStack.EMPTY;
        mouseX = uiViewport.mouseX(mouseX);
        mouseY = uiViewport.mouseY(mouseY);
        graphics.pose().pushPose();
        uiViewport.apply(graphics);
        transition.push(graphics);
        try {
            super.render(graphics, mouseX, mouseY, partialTick);
            int scaleLevel = TradingUiPreferences.tradingUiScaleLevel();
            TradingUiScale.drawControls(graphics, font, width, mouseX, mouseY,
                scaleLevel > TradingUiScale.MIN_LEVEL, scaleLevel < TradingUiScale.MAX_LEVEL);
            TradingUi.drawBackButton(graphics, font, width, mouseX, mouseY);
            if (!hoveredStack.isEmpty()) graphics.renderTooltip(font, hoveredStack, mouseX, mouseY);
            TradingUi.renderBalanceTooltipIfHovered(graphics, font, state.balance(),
                width, 17, mouseX, mouseY);
        } finally {
            transition.pop(graphics);
            transition.drawFade(graphics, width, height);
            graphics.pose().popPose();
        }
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        transition.tick(minecraft);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int sidebar = sidebarWidth();
        graphics.fill(0, 0, width, height, theme.background());
        graphics.fill(0, 0, width, HEADER_HEIGHT, 0xF2081014);
        graphics.fill(0, HEADER_HEIGHT - 1, width, HEADER_HEIGHT, theme.accent());
        graphics.fill(0, HEADER_HEIGHT, sidebar, height, theme.panel());
        drawTopBar(graphics);
        drawSidebar(graphics, sidebar, mouseX, mouseY);
        drawCatalogue(graphics, sidebar, mouseX, mouseY);
        drawBottomBar(graphics, sidebar, mouseX, mouseY);
        drawToast(graphics);
    }

    private void drawTopBar(GuiGraphics graphics) {
        graphics.drawString(font, Component.translatable("screen.xero_delta.recycling_station"),
            18, 18, theme.text(), false);
        int balanceWidth = TradingUi.balanceWidth(font, state.balance());
        TradingUi.drawBalance(graphics, font, state.balance(), width - balanceWidth - 88, 17, 0xFFFFD36A);
    }

    private void drawSidebar(GuiGraphics graphics, int sidebar, int mouseX, int mouseY) {
        int y = 84;
        graphics.drawString(font, Component.translatable("market.xero_delta.categories"),
            18, y, theme.muted(), false);
        y = categoryTop();
        int rowHeight = categoryRowHeight();
        int rowGap = categoryRowGap();
        int rowStride = rowHeight + rowGap;
        List<CategoryMenuRow> rows = categoryMenuRows();
        updateCategoryScroll();
        int firstIndex = Math.max(0, (int) Math.floor(categoryScrollPixels / rowStride));
        double rowOffset = categoryScrollPixels - firstIndex * rowStride;
        int visibleRows = Math.max(1, (categoryAreaBottom() - y) / rowStride + 2);
        uiViewport.enableScissor(graphics, 8, y, sidebar - 8, categoryAreaBottom());
        for (int visibleIndex = 0; visibleIndex < visibleRows; visibleIndex++) {
            int index = firstIndex + visibleIndex;
            if (index >= rows.size()) break;
            CategoryMenuRow row = rows.get(index);
            int rowY = y + (int) Math.round(visibleIndex * rowStride - rowOffset);
            boolean sectionHeader = row.sectionHeader();
            boolean expanded = sectionHeader && expandedCategorySections.contains(row.sectionId());
            boolean selected = sectionHeader
                ? row.sectionId().equals(selectedCreativeSection)
                : category == row.category()
                    && (row.sectionId().isBlank()
                        ? selectedCreativeSection.isBlank()
                        : row.sectionId().equals(selectedCreativeSection));
            boolean hovered = !categoryTouchDragging && !draggingCategoryScrollbar
                && rowY + rowHeight > y && rowY < categoryAreaBottom()
                && inside(mouseX, mouseY, 12, rowY, sidebar - 24, rowHeight);
            int rowX = !sectionHeader && !row.sectionId().isBlank() ? 20 : 12;
            graphics.fill(rowX, rowY, sidebar - 12, rowY + rowHeight,
                selected || expanded ? theme.panelAlt() : hovered ? 0x66405258 : 0x22000000);
            if (sectionHeader) graphics.renderOutline(12, rowY, sidebar - 24, rowHeight,
                selected ? theme.accent() : expanded ? theme.border() : 0x88405258);
            else if (selected) graphics.renderOutline(rowX, rowY, sidebar - 12 - rowX,
                rowHeight, theme.accent());
            Component label = sectionHeader
                ? TradingCategorySectionPresentation.label(row.sectionId(), categorySections())
                : Component.translatable("market.xero_delta.category."
                    + row.category().name().toLowerCase(Locale.ROOT));
            ItemStack icon = sectionHeader
                ? TradingCategorySectionPresentation.icon(row.sectionId(), categorySections())
                : TradingCreativeCategoryGroups.categoryIcon(row.sectionId(), row.category());
            if (!icon.isEmpty()) graphics.renderItem(icon, rowX + 4,
                rowY + Math.max(1, (rowHeight - 16) / 2));
            int labelX = rowX + 26;
            graphics.drawString(font, label, labelX, rowY + Math.max(5, (rowHeight - 8) / 2),
                selected || expanded ? theme.text() : theme.muted(), false);
            if (sectionHeader && sectionCategories(row.sectionId()).size() > 1) {
                graphics.drawCenteredString(font, expanded ? "▲" : "▼",
                    sidebar - 25, rowY + Math.max(5, (rowHeight - 8) / 2), theme.muted());
            }
        }
        graphics.disableScissor();
        drawCategoryScrollbar(graphics, sidebar, mouseX, mouseY);
        y = categoryGroupLabelY();
        graphics.drawString(font, Component.translatable("market.xero_delta.group"),
            18, y + 5, theme.muted(), false);
        y += 20;
        int groupWidth = Math.max(38, (sidebar - 32) / 3);
        for (Group value : configuredGroups()) {
            int x = 12 + value.ordinal() * groupWidth;
            boolean selected = group == value;
            boolean hovered = inside(mouseX, mouseY, x, y, groupWidth - 2, 22);
            graphics.fill(x, y, x + groupWidth - 2, y + 22,
                selected ? theme.accent() : hovered ? 0xFF40545B : theme.panelAlt());
            graphics.drawCenteredString(font, Component.translatable("market.xero_delta.group."
                    + value.name().toLowerCase(Locale.ROOT)),
                x + (groupWidth - 2) / 2, y + 7, selected ? 0xFF0A1517 : theme.text());
        }
    }

    private void drawCatalogue(GuiGraphics graphics, int sidebar, int mouseX, int mouseY) {
        int left = sidebar + 16;
        int right = width - 14;
        drawColumnControl(graphics, left, 52, right, mouseX, mouseY);
        int bottom = height - FOOTER_HEIGHT - 2;
        SourceLayout layout = sourceLayout(left, right);
        uiViewport.enableScissor(graphics, left, CATALOGUE_TOP, right, bottom);
        if (layout.headers().isEmpty()) {
            graphics.drawCenteredString(font, Component.translatable("recycle.xero_delta.empty_catalogue"),
                (left + right) / 2, (CATALOGUE_TOP + bottom) / 2, theme.muted());
        } else {
            for (SourceHeaderPlacement header : layout.headers()) {
                int headerY = CATALOGUE_TOP + header.y() - scroll;
                if (headerY + 24 < CATALOGUE_TOP || headerY >= bottom) continue;
                boolean expanded = expandedSourceGroups.contains(header.groupId());
                boolean hover = inside(mouseX, mouseY, left, headerY, right - left - 8, 24);
                graphics.fill(left, headerY, right - 8, headerY + 24,
                    hover ? theme.panelAlt() : theme.panel());
                graphics.renderOutline(left, headerY, right - left - 8, 24, theme.border());
                ItemStack icon = header.group() == null ? ItemStack.EMPTY : header.group().icon();
                if (!icon.isEmpty()) graphics.renderItem(icon, left + 4, headerY + 4);
                graphics.drawString(font, sourceGroupLabel(header.group(), header.groupId()),
                    left + 25, headerY + 8, theme.text(), false);
                graphics.drawCenteredString(font, expanded ? "▼" : "▲",
                    right - 20, headerY + 8, theme.muted());
            }
            for (SourceCardPlacement card : layout.cards()) {
                int cardY = CATALOGUE_TOP + card.y() - scroll;
                if (cardY + theme.cardHeight() < CATALOGUE_TOP || cardY > bottom) continue;
                drawSourceCard(graphics, card.source(), card.x(), cardY,
                    card.width(), mouseX, mouseY);
            }
        }
        graphics.disableScissor();
        drawScrollbar(graphics, layout.cards().size(), CATALOGUE_TOP, bottom, right);
    }

    private void drawColumnControl(GuiGraphics graphics, int left, int top, int right,
                                   int mouseX, int mouseY) {
        Component catalogue = Component.translatable("recycle.xero_delta.catalogue");
        graphics.drawString(font, catalogue, left, top + 7, theme.muted(), false);
        int hintX = left + font.width(catalogue) + 12;
        drawInfoDiamond(graphics, hintX + 5, top + 11);
        graphics.drawString(font, Component.translatable("recycle.xero_delta.multi_select_hint"),
            hintX + 14, top + 7, theme.muted(), false);
        int x = right - 82;
        drawCompactControl(graphics, x, top, 24, "-", catalogueColumns > 1, mouseX, mouseY);
        graphics.fill(x + 26, top, x + 56, top + 22, theme.panel());
        graphics.drawCenteredString(font, String.valueOf(catalogueColumns), x + 41, top + 7, theme.text());
        drawCompactControl(graphics, x + 58, top, 24, "+", catalogueColumns < 10, mouseX, mouseY);
    }

    private void drawInfoDiamond(GuiGraphics graphics, int centerX, int centerY) {
        for (int offsetY = -5; offsetY <= 5; offsetY++) {
            int halfWidth = 5 - Math.abs(offsetY);
            graphics.fill(centerX - halfWidth, centerY + offsetY,
                centerX + halfWidth + 1, centerY + offsetY + 1, theme.border());
        }
        graphics.drawCenteredString(font, "i", centerX, centerY - 4, theme.text());
    }

    private void drawCompactControl(GuiGraphics graphics, int x, int y, int buttonWidth, String text,
                                    boolean enabled, int mouseX, int mouseY) {
        int color = !enabled ? 0xFF263338
            : inside(mouseX, mouseY, x, y, buttonWidth, 22) ? theme.panelAlt() : theme.panel();
        graphics.fill(x, y, x + buttonWidth, y + 22, color);
        graphics.drawCenteredString(font, text, x + buttonWidth / 2, y + 7,
            enabled ? theme.text() : theme.muted());
    }

    private void drawSourceCard(GuiGraphics graphics, TradingSyncPacket.SourceView value,
                                int x, int y, int cardWidth, int mouseX, int mouseY) {
        boolean selected = selectedSources.contains(value.id());
        boolean hovered = inside(mouseX, mouseY, x, y, cardWidth, theme.cardHeight());
        graphics.fill(x, y, x + cardWidth, y + theme.cardHeight(),
            selected ? 0xEE30464A : hovered ? theme.panelAlt() : theme.panel());
        graphics.fill(x, y, x + cardWidth, y + 2,
            qualityColor(ClientDataCache.INSTANCE.getQuality(value.stack())));
        if (selected) graphics.renderOutline(x, y, cardWidth, theme.cardHeight(), theme.accent());
        String title = value.stack().getHoverName().getString();
        drawQualityName(graphics, title, x + 7, y + 7, Math.max(20, cardWidth - 14),
            qualityColor(ClientDataCache.INSTANCE.getQuality(value.stack())), x * 31L + y);
        if (cardWidth >= 82) {
            String source = font.plainSubstrByWidth(Component.translatable(value.label()).getString(), cardWidth - 14);
            graphics.drawString(font, source, x + 7, y + 21, theme.muted(), false);
        }
        graphics.pose().pushPose();
        graphics.pose().translate(x + cardWidth / 2 - 16,
            y + Math.max(29, theme.cardHeight() / 2 - 9), 80);
        graphics.pose().scale(2.0F, 2.0F, 1.0F);
        graphics.renderItem(value.stack(), 0, 0);
        graphics.renderItemDecorations(font, value.stack(), 0, 0);
        graphics.pose().popPose();
        long valueAmount = sourceValue(value);
        int amountWidth = TradingUi.amountWidth(font, valueAmount);
        if (amountWidth <= cardWidth - 8) {
            TradingUi.drawAmount(graphics, font, valueAmount,
                x + cardWidth - amountWidth - 4, y + theme.cardHeight() - 14, 0xFFE4E8E6);
        }
        if (hovered) hoveredStack = value.stack();
    }

    private void drawBottomBar(GuiGraphics graphics, int sidebar, int mouseX, int mouseY) {
        int barY = height - FOOTER_HEIGHT;
        graphics.fill(sidebar, barY, width, height, 0xF20C171B);
        List<TradingSyncPacket.SourceView> selected = selectedViews();
        if (!selected.isEmpty()) {
            TradingSyncPacket.SourceView first = selected.getFirst();
            String summary = selected.size() > 1
                ? Component.translatable("recycle.xero_delta.selected_count", selected.size()).getString()
                : first.stack().getHoverName().getString() + "  x" + first.stack().getCount();
            int actionLeft = actionButtonsX();
            graphics.drawString(font, font.plainSubstrByWidth(summary,
                    Math.max(40, actionLeft - sidebar - 30)),
                sidebar + 16, barY + 16, theme.text(), false);
        }
        int buttonWidth = actionButtonWidth();
        int x = actionButtonsX();
        int y = actionY();
        boolean enabled = !selected.isEmpty();
        Component recycleLabel = confirming
            ? Component.translatable("recycle.xero_delta.confirm_sell")
            : Component.translatable("recycle.xero_delta.sell_vendor");
        drawActionButton(graphics, x, y, buttonWidth, recycleLabel, enabled,
            confirming ? theme.danger() : theme.accent(), confirming ? 0xFFF07A70 : 0xFF75E2C0,
            confirming ? 0xFFFFF4F3 : 0xFF071214, mouseX, mouseY);
        drawActionButton(graphics, x + buttonWidth + 8, y, buttonWidth,
            Component.translatable("recycle.xero_delta.list_market"), enabled,
            theme.accent(), 0xFF75E2C0, 0xFF071214, mouseX, mouseY);
    }

    private void drawActionButton(GuiGraphics graphics, int x, int y, int buttonWidth,
                                  Component label, boolean enabled, int normalColor,
                                  int hoverColor, int textColor, int mouseX, int mouseY) {
        boolean hovered = enabled && inside(mouseX, mouseY, x, y, buttonWidth, 22);
        graphics.fill(x, y, x + buttonWidth, y + 22,
            enabled ? (hovered ? hoverColor : normalColor) : 0xFF39474B);
        if (hovered) graphics.renderOutline(x, y, buttonWidth, 22, theme.text());
        graphics.drawCenteredString(font, label, x + buttonWidth / 2, y + 8,
            enabled ? textColor : theme.muted());
    }

    private void drawToast(GuiGraphics graphics) {
        if (System.currentTimeMillis() >= toastUntil || toast.isBlank()) return;
        int amountWidth = toastValue > 0 ? TradingUi.amountWidth(font, toastValue) + 6 : 0;
        int toastWidth = Math.min(width - 40, font.width(toast) + amountWidth + 34);
        int x = (width - toastWidth) / 2;
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 500);
        graphics.fill(x, 7, x + toastWidth, 35, toastSuccess ? 0xF02B4942 : 0xF05A2928);
        graphics.fill(x, 34, x + toastWidth, 35, toastSuccess ? theme.accent() : theme.danger());
        int contentWidth = font.width(toast) + amountWidth;
        int contentX = (width - contentWidth) / 2;
        graphics.drawString(font, toast, contentX, 17, theme.text(), false);
        if (toastValue > 0) {
            TradingUi.drawAmount(graphics, font, toastValue,
                contentX + font.width(toast) + 6, 16, 0xFFFFD36A);
        }
        graphics.pose().popPose();
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        mouseX = uiViewport.mouseXDouble(mouseX);
        mouseY = uiViewport.mouseYDouble(mouseY);
        if (TradingUiScale.zoomOutClicked(mouseX, mouseY, width)) {
            adjustPageZoom(-1);
            return true;
        }
        if (TradingUiScale.zoomInClicked(mouseX, mouseY, width)) {
            adjustPageZoom(1);
            return true;
        }
        if (TradingUi.backButtonClicked(mouseX, mouseY, width)) {
            navigateBack();
            return true;
        }
        int sidebar = sidebarWidth();
        int y = categoryTop();
        int rowHeight = categoryRowHeight();
        int rowGap = categoryRowGap();
        int rowStride = rowHeight + rowGap;
        if (button == 0 && beginCategoryScrollbarDrag(mouseX, mouseY)) return true;
        if (button == 0 && insideCategoryArea(mouseX, mouseY)) {
            if (!categoryTouchReleaseClick) {
                categoryTouchPressed = true;
                categoryTouchDragging = false;
                categoryTouchStartX = mouseX;
                categoryTouchStartY = mouseY;
                categoryTouchStartScroll = categoryScrollPixels;
                categoryTouchLastY = mouseY;
                categoryTouchLastNanos = System.nanoTime();
                categoryTouchVelocity = 0.0D;
                return true;
            }
            double contentY = mouseY - y + categoryScrollPixels;
            int index = (int) Math.floor(contentY / rowStride);
            double within = contentY - index * (double) rowStride;
            List<CategoryMenuRow> rows = categoryMenuRows();
            if (within < rowHeight && index >= 0 && index < rows.size()) {
                CategoryMenuRow row = rows.get(index);
                if (row.sectionHeader()) {
                    List<TradingCategory> children = sectionCategories(row.sectionId());
                    if (children.size() == 1) {
                        selectedCreativeSection = row.sectionId();
                        category = children.getFirst();
                        resetCatalogueSelection();
                    } else if (children.size() > 1) {
                        if (!expandedCategorySections.remove(row.sectionId())) {
                            expandedCategorySections.add(row.sectionId());
                        }
                        clampCategoryScroll();
                    }
                } else {
                    selectedCreativeSection = row.sectionId();
                    category = row.category();
                    resetCatalogueSelection();
                }
                return true;
            }
        }
        int groupY = categoryGroupY();
        int groupWidth = Math.max(38, (sidebar - 32) / 3);
        for (Group value : configuredGroups()) {
            if (inside(mouseX, mouseY, 12 + value.ordinal() * groupWidth, groupY, groupWidth - 2, 22)) {
                group = value;
                resetCatalogueSelection();
                return true;
            }
        }
        int controlX = width - 14 - 82;
        if (inside(mouseX, mouseY, controlX, 52, 24, 22) && catalogueColumns > 1) {
            catalogueColumns--;
            TradingUiPreferences.setRecyclingColumns(catalogueColumns);
            scroll = 0;
            return true;
        }
        if (inside(mouseX, mouseY, controlX + 58, 52, 24, 22) && catalogueColumns < 10) {
            catalogueColumns++;
            TradingUiPreferences.setRecyclingColumns(catalogueColumns);
            scroll = 0;
            return true;
        }
        List<TradingSyncPacket.SourceView> selected = selectedViews();
        if (!selected.isEmpty() && handleActionClick(mouseX, mouseY, selected)) return true;
        int left = sidebar + 16;
        int right = width - 14;
        if (button == 0 && !touchReleaseClick && insideCatalogue(mouseX, mouseY)) {
            touchPressed = true;
            touchDragging = false;
            touchStartX = mouseX;
            touchStartY = mouseY;
            touchLastY = mouseY;
            return true;
        }
        SourceLayout layout = sourceLayout(left, right);
        for (SourceHeaderPlacement header : layout.headers()) {
            int headerY = CATALOGUE_TOP + header.y() - scroll;
            if (!inside(mouseX, mouseY, left, headerY, right - left - 8, 24)) continue;
            if (!expandedSourceGroups.remove(header.groupId())) {
                expandedSourceGroups.add(header.groupId());
            }
            scroll = Math.min(scroll, maxScroll());
            return true;
        }
        for (SourceCardPlacement card : layout.cards()) {
            int cardY = CATALOGUE_TOP + card.y() - scroll;
            if (!inside(mouseX, mouseY, card.x(), cardY, card.width(), theme.cardHeight())) continue;
            String clicked = card.source().id();
            List<String> ordered = layout.cards().stream()
                .map(value -> value.source().id()).toList();
            selectionAnchor = TradingHistorySelection.select(selectedSources, ordered,
                selectionAnchor, clicked, hasControlDown(), hasShiftDown());
            selectedSource = selectedSources.contains(clicked)
                ? clicked : selectedSources.stream().findFirst().orElse("");
            confirming = false;
            return true;
        }
        if (beginScrollbarDrag(mouseX, mouseY)) return true;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean insideCatalogue(double mouseX, double mouseY) {
        int left = sidebarWidth() + 16;
        int right = width - 22;
        int bottom = height - FOOTER_HEIGHT - 2;
        return mouseX >= left && mouseX < right && mouseY >= CATALOGUE_TOP && mouseY < bottom;
    }

    private boolean handleActionClick(double mouseX, double mouseY,
                                      List<TradingSyncPacket.SourceView> selected) {
        int buttonWidth = actionButtonWidth();
        int x = actionButtonsX();
        int y = actionY();
        if ((document.actions().contains("recycle") || document.actions().contains("confirm-recycle"))
            && inside(mouseX, mouseY, x, y, buttonWidth, 22)) {
            if (confirming && System.currentTimeMillis() <= confirmUntil) {
                for (TradingSyncPacket.SourceView source : selected) {
                    ModNetwork.sendToServer(TradingActionPacket.recycle(source.id(), source.stack().getCount()));
                }
                confirming = false;
            } else {
                confirming = true;
                confirmUntil = System.currentTimeMillis() + 5000L;
            }
            return true;
        }
        if (document.actions().contains("open-operator")
            && inside(mouseX, mouseY, x + buttonWidth + 8, y, buttonWidth, 22)) {
            String sourceIds = String.join(TradingActionPacket.SOURCE_LIST_SEPARATOR,
                selected.stream().map(TradingSyncPacket.SourceView::id).toList());
            state.rememberTransition("recycling:" + selected.getFirst().id());
            ModNetwork.sendToServer(TradingActionPacket.openOperator(sourceIds));
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) {
            navigateBack();
            return true;
        }
        if (minecraft != null && minecraft.options.keyInventory.matches(keyCode, scanCode)) return true;
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void navigateBack() {
        transition.beginClose(this::navigateBackNow);
    }

    private void navigateBackNow() {
        String previous = state.popPreviousScreen();
        state.rememberCursor();
        if (previous.startsWith("market:")) {
            ModNetwork.sendToServer(TradingActionPacket.openMarket(previous.substring(7)));
        } else if (previous.startsWith("operator")) {
            ModNetwork.sendToServer(TradingActionPacket.openOperator());
        } else if (previous.startsWith("recycling")) {
            String source = previous.contains(":") ? previous.substring(previous.indexOf(':') + 1) : "";
            ModNetwork.sendToServer(TradingActionPacket.openRecycling(source));
        } else {
            onClose();
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        mouseX = uiViewport.mouseXDouble(mouseX);
        mouseY = uiViewport.mouseYDouble(mouseY);
        if (hasControlDown() && scrollY != 0.0D) {
            adjustPageZoom(scrollY > 0.0D ? 1 : -1);
            return true;
        }
        if (inside(mouseX, mouseY, 8, categoryTop(),
            sidebarWidth() - 16, categoryAreaBottom() - categoryTop())) {
            beginCategoryScrollAnimation(clamp(categoryScrollTarget
                - Math.signum(scrollY) * (categoryRowHeight() + categoryRowGap()) * 2.0D,
                0.0D, maxCategoryScroll()));
            return true;
        }
        if (!inside(mouseX, mouseY, sidebarWidth() + 16, CATALOGUE_TOP,
            width - sidebarWidth() - 30, height - CATALOGUE_TOP - FOOTER_HEIGHT)) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        scroll = Math.max(0, Math.min(maxScroll(), scroll - (int) Math.signum(scrollY) * 28));
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        mouseX = uiViewport.mouseXDouble(mouseX);
        mouseY = uiViewport.mouseYDouble(mouseY);
        dragX = uiViewport.deltaX(dragX);
        dragY = uiViewport.deltaY(dragY);
        if (draggingCategoryScrollbar) {
            updateCategoryScrollbar(mouseY);
            return true;
        }
        if (draggingScrollbar) {
            updateScroll(mouseY);
            return true;
        }
        if (categoryTouchPressed && button == 0) {
            double movedX = mouseX - categoryTouchStartX;
            double movedY = mouseY - categoryTouchStartY;
            if (!categoryTouchDragging && movedX * movedX + movedY * movedY >= 25.0D) {
                categoryTouchDragging = true;
            }
            long now = System.nanoTime();
            double elapsed = Math.max(1.0D, (now - categoryTouchLastNanos) / 1_000_000.0D);
            if (categoryTouchDragging) {
                double next = clamp(categoryTouchStartScroll - movedY, 0.0D, maxCategoryScroll());
                categoryScrollPixels = categoryScrollTarget = next;
                categoryScrollAnimationNanos = now;
                categoryTouchVelocity = -(mouseY - categoryTouchLastY) / elapsed * 16.0D;
            }
            categoryTouchLastY = mouseY;
            categoryTouchLastNanos = now;
            return true;
        }
        if (touchPressed && button == 0) {
            double movedX = mouseX - touchStartX;
            double movedY = mouseY - touchStartY;
            if (!touchDragging && movedX * movedX + movedY * movedY >= 25.0D) touchDragging = true;
            if (touchDragging) {
                scroll = Mth.clamp(scroll - (int) Math.round(mouseY - touchLastY), 0, maxScroll());
                touchLastY = mouseY;
            }
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        mouseX = uiViewport.mouseXDouble(mouseX);
        mouseY = uiViewport.mouseYDouble(mouseY);
        if (draggingCategoryScrollbar) {
            draggingCategoryScrollbar = false;
            return true;
        }
        if (draggingScrollbar) {
            draggingScrollbar = false;
            return true;
        }
        if (categoryTouchPressed && button == 0) {
            boolean click = !categoryTouchDragging;
            categoryTouchPressed = false;
            categoryTouchDragging = false;
            if (click) {
                categoryTouchReleaseClick = true;
                mouseClicked(mouseX * uiViewport.scale() + uiViewport.offsetX(),
                    mouseY * uiViewport.scale() + uiViewport.offsetY(), button);
                categoryTouchReleaseClick = false;
            } else {
                beginCategoryScrollAnimation(clamp(
                    categoryScrollPixels + categoryTouchVelocity * 5.0D,
                    0.0D, maxCategoryScroll()));
            }
            categoryTouchVelocity = 0.0D;
            return true;
        }
        if (touchPressed && button == 0) {
            boolean click = !touchDragging;
            touchPressed = false;
            touchDragging = false;
            if (click) {
                touchReleaseClick = true;
                mouseClicked(mouseX * uiViewport.scale() + uiViewport.offsetX(),
                    mouseY * uiViewport.scale() + uiViewport.offsetY(), button);
                touchReleaseClick = false;
            }
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void adjustPageZoom(int direction) {
        int current = TradingUiPreferences.tradingUiScaleLevel();
        int next = TradingUiScale.adjustLevel(current, direction);
        if (next == current) return;
        TradingUiPreferences.setTradingUiScaleLevel(next);
        if (minecraft != null) {
            resize(minecraft, uiViewport.physicalWidth(), uiViewport.physicalHeight());
        }
    }

    private List<TradingSyncPacket.SourceView> filteredSources() {
        List<TradingSyncPacket.SourceView> values = new ArrayList<>();
        for (TradingSyncPacket.SourceView value : state.sources()) {
            if (matches(value)) values.add(value);
        }
        return values;
    }

    private boolean matches(TradingSyncPacket.SourceView value) {
        ItemStack stack = value.stack();
        String id = itemId(stack);
        boolean favorite = state.favorites().contains(ModDataStorage.getKey(stack));
        if (!TradingCreativeCategoryGroups.matches(selectedCreativeSection, stack)) return false;
        boolean categoryMatches = TradingItemCategory.matches(category, stack, favorite);
        if (!categoryMatches) return false;
        if (group == Group.VANILLA && !id.startsWith("minecraft:")) return false;
        if (group == Group.MODDED && id.startsWith("minecraft:")) return false;
        String query = searchBox == null ? "" : searchBox.getValue().trim().toLowerCase(Locale.ROOT);
        if (query.isBlank()) return true;
        return id.contains(query)
            || stack.getHoverName().getString().toLowerCase(Locale.ROOT).contains(query)
            || Component.translatable(value.label()).getString().toLowerCase(Locale.ROOT).contains(query);
    }

    private TradingSyncPacket.SourceView selectedView() {
        if (selectedSources.isEmpty()) return null;
        for (TradingSyncPacket.SourceView value : state.sources()) {
            if (selectedSources.contains(value.id())) return value;
        }
        return null;
    }

    private List<TradingSyncPacket.SourceView> selectedViews() {
        if (selectedSources.isEmpty()) return List.of();
        List<TradingSyncPacket.SourceView> result = new ArrayList<>();
        for (TradingSyncPacket.SourceView value : state.sources()) {
            if (selectedSources.contains(value.id())) result.add(value);
        }
        return result;
    }

    private void pruneSourceSelection() {
        selectedSources.removeIf(id -> state.sources().stream().noneMatch(value -> id.equals(value.id())));
        if (!selectedSources.contains(selectedSource)) {
            selectedSource = selectedSources.stream().findFirst().orElse("");
        }
        if (selectionAnchor != null && !selectedSources.contains(selectionAnchor)) {
            selectionAnchor = selectedSource.isBlank() ? null : selectedSource;
        }
    }

    private long sourceValue(TradingSyncPacket.SourceView source) {
        long unit = TradingRules.normalizeItemValue(ClientDataCache.INSTANCE.getPrice(source.stack()));
        if (unit > TradingRules.MAX_CURRENCY / Math.max(1, source.stack().getCount())) {
            return TradingRules.MAX_CURRENCY;
        }
        return unit * source.stack().getCount();
    }

    private void resetCatalogueSelection() {
        scroll = 0;
        selectedSource = "";
        selectedSources.clear();
        selectionAnchor = null;
        confirming = false;
    }

    private int sidebarWidth() {
        return Math.min(theme.sidebarWidth(), Math.max(132, Math.min(220, width / 3)));
    }

    private int categoryTop() {
        return 101;
    }

    private int categoryRowGap() {
        return height < 420 ? 1 : 2;
    }

    private int categoryRowHeight() {
        return height < 420 ? 16 : 20;
    }

    private int categoryAreaBottom() {
        return Math.max(categoryTop() + categoryRowHeight(), height - 62);
    }

    private int categoryGroupLabelY() {
        return height - 58;
    }

    private int categoryGroupY() {
        return height - 38;
    }

    private int cardWidth(int left, int right) {
        int availableColumns = Math.max(1,
            (right - left + theme.cardGap()) / (64 + theme.cardGap()));
        int columns = Math.max(1, Math.min(catalogueColumns, availableColumns));
        return Math.max(52, (right - left - theme.cardGap() * (columns - 1) - 8) / columns - 2);
    }

    private int[] cardPosition(int index, int left, int top, int right, int cardWidth) {
        int columns = columnsFor(left, right, cardWidth);
        int x = left + (index % columns) * (cardWidth + theme.cardGap());
        int y = top + (index / columns) * (theme.cardHeight() + theme.cardGap()) - scroll;
        return new int[]{x, y};
    }

    private int cardIndexAt(double mouseX, double mouseY, int left, int top, int right, int cardWidth) {
        if (mouseX < left || mouseX >= right || mouseY < top || mouseY >= height - FOOTER_HEIGHT - 2) return -1;
        int columns = columnsFor(left, right, cardWidth);
        int column = (int) (mouseX - left) / (cardWidth + theme.cardGap());
        int row = ((int) mouseY - top + scroll) / (theme.cardHeight() + theme.cardGap());
        int localX = (int) (mouseX - left) % (cardWidth + theme.cardGap());
        int localY = ((int) mouseY - top + scroll) % (theme.cardHeight() + theme.cardGap());
        if (column < 0 || column >= columns || localX >= cardWidth || localY >= theme.cardHeight()) return -1;
        return row * columns + column;
    }

    private int columnsFor(int left, int right, int cardWidth) {
        return Math.max(1, (right - left + theme.cardGap()) / (cardWidth + theme.cardGap()));
    }

    private int maxScroll() {
        int left = sidebarWidth() + 16;
        int right = width - 14;
        int contentHeight = sourceLayout(left, right).totalHeight();
        int visibleHeight = height - FOOTER_HEIGHT - 2 - CATALOGUE_TOP;
        return Math.max(0, contentHeight - visibleHeight);
    }

    private SourceLayout sourceLayout(int left, int right) {
        int cardWidth = cardWidth(left, right);
        int columns = columnsFor(left, right, cardWidth);
        Map<String, List<TradingSyncPacket.SourceView>> grouped = new LinkedHashMap<>();
        for (TradingSyncPacket.SourceGroupView sourceGroup : state.sourceGroups()) {
            grouped.put(sourceGroup.id(), new ArrayList<>());
        }
        for (TradingSyncPacket.SourceView source : filteredSources()) {
            grouped.computeIfAbsent(source.groupId(), ignored -> new ArrayList<>()).add(source);
        }
        List<SourceHeaderPlacement> headers = new ArrayList<>();
        List<SourceCardPlacement> cards = new ArrayList<>();
        int y = 0;
        for (var entry : grouped.entrySet()) {
            if (entry.getValue().isEmpty()) continue;
            TradingSyncPacket.SourceGroupView sourceGroup = sourceGroup(entry.getKey());
            headers.add(new SourceHeaderPlacement(entry.getKey(), sourceGroup, y));
            y += 24 + theme.cardGap();
            if (expandedSourceGroups.contains(entry.getKey())) {
                for (int index = 0; index < entry.getValue().size(); index++) {
                    int cardX = left + (index % columns) * (cardWidth + theme.cardGap());
                    int cardY = y + (index / columns) * (theme.cardHeight() + theme.cardGap());
                    cards.add(new SourceCardPlacement(entry.getValue().get(index), cardX, cardY, cardWidth));
                }
                int rows = (entry.getValue().size() + columns - 1) / columns;
                y += rows * (theme.cardHeight() + theme.cardGap());
            }
            y += theme.cardGap() + 4;
        }
        return new SourceLayout(List.copyOf(headers), List.copyOf(cards), Math.max(0, y - 4));
    }

    private TradingSyncPacket.SourceGroupView sourceGroup(String groupId) {
        for (TradingSyncPacket.SourceGroupView sourceGroup : state.sourceGroups()) {
            if (sourceGroup.id().equals(groupId)) return sourceGroup;
        }
        return null;
    }

    private Component sourceGroupLabel(TradingSyncPacket.SourceGroupView sourceGroup, String groupId) {
        if (sourceGroup == null || "inventory".equals(groupId)) {
            return Component.translatable("trading_op.xero_delta.source_group.inventory");
        }
        return Component.translatable("trading_op.xero_delta.source_group.backpack",
            sourceGroup.ordinal(), sourceGroup.icon().getHoverName());
    }

    private void drawScrollbar(GuiGraphics graphics, int count, int top, int bottom, int right) {
        int max = maxScroll();
        if (max <= 0 || count == 0) return;
        int trackHeight = bottom - top;
        int thumbHeight = Math.max(22, trackHeight * trackHeight / (trackHeight + max));
        int thumbY = top + (trackHeight - thumbHeight) * scroll / max;
        graphics.fill(right - 3, top, right - 1, bottom, 0x55405258);
        graphics.fill(right - 3, thumbY, right - 1, thumbY + thumbHeight, theme.accent());
    }

    private boolean beginScrollbarDrag(double mouseX, double mouseY) {
        int right = width - 14;
        int bottom = height - FOOTER_HEIGHT - 2;
        if (maxScroll() <= 0 || !inside(mouseX, mouseY, right - 8, CATALOGUE_TOP, 10,
            bottom - CATALOGUE_TOP)) return false;
        draggingScrollbar = true;
        updateScroll(mouseY);
        return true;
    }

    private void updateScroll(double mouseY) {
        int max = maxScroll();
        int bottom = height - FOOTER_HEIGHT - 2;
        int track = Math.max(1, bottom - CATALOGUE_TOP);
        scroll = Math.max(0, Math.min(max,
            (int) Math.round((mouseY - CATALOGUE_TOP) * max / track)));
    }

    private boolean insideCategoryArea(double mouseX, double mouseY) {
        return inside(mouseX, mouseY, 8, categoryTop(), sidebarWidth() - 16,
            categoryAreaBottom() - categoryTop());
    }

    private double maxCategoryScroll() {
        int stride = categoryRowHeight() + categoryRowGap();
        int contentHeight = Math.max(0, categoryMenuRows().size() * stride - categoryRowGap());
        return Math.max(0.0D, contentHeight - (categoryAreaBottom() - categoryTop()));
    }

    private void updateCategoryScroll() {
        double maximum = maxCategoryScroll();
        categoryScrollTarget = clamp(categoryScrollTarget, 0.0D, maximum);
        categoryScrollStart = clamp(categoryScrollStart, 0.0D, maximum);
        if (Math.abs(categoryScrollTarget - categoryScrollPixels) < 0.1D) {
            categoryScrollPixels = categoryScrollTarget;
        } else {
            double progress = clamp((System.nanoTime() - categoryScrollAnimationNanos)
                / (double) CATEGORY_SCROLL_ANIMATION_NANOS, 0.0D, 1.0D);
            double eased = 1.0D - Math.pow(1.0D - progress, 3.0D);
            categoryScrollPixels = categoryScrollStart
                + (categoryScrollTarget - categoryScrollStart) * eased;
        }
        categoryScrollPixels = clamp(categoryScrollPixels, 0.0D, maximum);
    }

    private void beginCategoryScrollAnimation(double target) {
        categoryScrollStart = categoryScrollPixels;
        categoryScrollTarget = target;
        categoryScrollAnimationNanos = System.nanoTime();
    }

    private void clampCategoryScroll() {
        double maximum = maxCategoryScroll();
        categoryScrollPixels = clamp(categoryScrollPixels, 0.0D, maximum);
        categoryScrollStart = clamp(categoryScrollStart, 0.0D, maximum);
        categoryScrollTarget = clamp(categoryScrollTarget, 0.0D, maximum);
    }

    private void drawCategoryScrollbar(GuiGraphics graphics, int sidebar, int mouseX, int mouseY) {
        double maximum = maxCategoryScroll();
        if (maximum <= 0.0D) return;
        int top = categoryTop();
        int viewport = categoryAreaBottom() - top;
        int thumbHeight = categoryScrollbarThumb(viewport, maximum);
        int thumbY = top + (int) Math.round((viewport - thumbHeight)
            * clamp(categoryScrollPixels, 0.0D, maximum) / maximum);
        int trackX = sidebar - 9;
        boolean hovered = inside(mouseX, mouseY, trackX - 3, top, 8, viewport);
        graphics.fill(trackX, top, trackX + 3, top + viewport, 0x55405258);
        graphics.fill(trackX, thumbY, trackX + 3, thumbY + thumbHeight,
            draggingCategoryScrollbar || hovered ? theme.text() : theme.accent());
    }

    private boolean beginCategoryScrollbarDrag(double mouseX, double mouseY) {
        double maximum = maxCategoryScroll();
        if (maximum <= 0.0D) return false;
        int top = categoryTop();
        int viewport = categoryAreaBottom() - top;
        int trackX = sidebarWidth() - 9;
        if (!inside(mouseX, mouseY, trackX - 3, top, 8, viewport)) return false;
        int thumbHeight = categoryScrollbarThumb(viewport, maximum);
        double thumbY = top + (viewport - thumbHeight)
            * clamp(categoryScrollPixels, 0.0D, maximum) / maximum;
        draggingCategoryScrollbar = true;
        categoryScrollbarGrabOffset = mouseY >= thumbY && mouseY <= thumbY + thumbHeight
            ? mouseY - thumbY : thumbHeight / 2.0D;
        updateCategoryScrollbar(mouseY);
        return true;
    }

    private void updateCategoryScrollbar(double mouseY) {
        double maximum = maxCategoryScroll();
        int top = categoryTop();
        int viewport = categoryAreaBottom() - top;
        int thumbHeight = categoryScrollbarThumb(viewport, maximum);
        double track = Math.max(1.0D, viewport - thumbHeight);
        double value = clamp((mouseY - top - categoryScrollbarGrabOffset) / track,
            0.0D, 1.0D) * maximum;
        categoryScrollPixels = categoryScrollTarget = value;
        categoryScrollStart = value;
        categoryScrollAnimationNanos = System.nanoTime();
    }

    private static int categoryScrollbarThumb(int viewport, double maximum) {
        return Math.max(20, (int) Math.round(viewport * viewport / (viewport + maximum)));
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private int actionButtonWidth() {
        int available = Math.max(2, width - sidebarWidth() - 36 - 8);
        return Math.max(1, Math.min(142, available / 2));
    }

    private int actionButtonsX() {
        return width - actionButtonWidth() * 2 - 8 - 14;
    }

    private int actionY() {
        return height - 31;
    }

    private List<TradingCategory> configuredCategories() {
        return configuredValues(document == null ? List.of() : document.categories(), TradingCategory.class,
            List.of(TradingCategory.values()));
    }

    private void initializeCategorySections() {
        List<TradingHtmlThemeParser.CategorySection> sections = categorySections();
        if (!categorySectionsInitialized) {
            for (TradingHtmlThemeParser.CategorySection section : sections) {
                if (section.expanded() && sectionCategories(section).size() > 1) {
                    expandedCategorySections.add(section.id());
                }
            }
            if (expandedCategorySections.isEmpty()) {
                for (TradingHtmlThemeParser.CategorySection section : sections) {
                    if (sectionCategories(section).size() > 1) {
                        expandedCategorySections.add(section.id());
                        break;
                    }
                }
            }
            categorySectionsInitialized = true;
        }
        expandedCategorySections.removeIf(id -> sections.stream().noneMatch(section -> section.id().equals(id)));
        if (!selectedCreativeSection.isBlank()
            && sections.stream().noneMatch(section -> section.id().equals(selectedCreativeSection))) {
            selectedCreativeSection = "";
            category = TradingCategory.ALL;
        }
    }

    private List<CategoryMenuRow> categoryMenuRows() {
        List<TradingCategory> configured = configuredCategories();
        List<TradingHtmlThemeParser.CategorySection> sections = categorySections();
        if (sections.isEmpty()) {
            return configured.stream().map(value -> new CategoryMenuRow("", value, false)).toList();
        }
        List<CategoryMenuRow> rows = new ArrayList<>();
        for (TradingCategory value : configured) {
            if (value == TradingCategory.ALL || value == TradingCategory.FAVORITES) {
                rows.add(new CategoryMenuRow("", value, false));
            }
        }
        for (TradingHtmlThemeParser.CategorySection section : sections) {
            List<TradingCategory> children = sectionCategories(section).stream()
                .filter(configured::contains).toList();
            if (children.isEmpty()) continue;
            rows.add(new CategoryMenuRow(section.id(), null, true));
            if (children.size() > 1 && expandedCategorySections.contains(section.id())) {
                for (TradingCategory child : children) {
                    rows.add(new CategoryMenuRow(section.id(), child, false));
                }
            }
        }
        return List.copyOf(rows);
    }

    private List<TradingCategory> sectionCategories(TradingHtmlThemeParser.CategorySection section) {
        return configuredValues(section.categories(), TradingCategory.class, List.of());
    }

    private List<TradingCategory> sectionCategories(String sectionId) {
        for (TradingHtmlThemeParser.CategorySection section : categorySections()) {
            if (section.id().equals(sectionId)) return sectionCategories(section);
        }
        return List.of();
    }

    private boolean sectionContainsCategory(String sectionId, TradingCategory value) {
        for (TradingHtmlThemeParser.CategorySection section : categorySections()) {
            if (section.id().equals(sectionId) && sectionCategories(section).contains(value)) return true;
        }
        return false;
    }

    private List<TradingHtmlThemeParser.CategorySection> categorySections() {
        List<TradingHtmlThemeParser.CategorySection> configured = document == null
            ? List.of() : document.categorySections();
        return configured.isEmpty() || !document.customCategorySections()
            ? TradingCreativeCategoryGroups.sections(configuredCategories()) : configured;
    }

    private List<Group> configuredGroups() {
        return configuredValues(document == null ? List.of() : document.groups(), Group.class,
            List.of(Group.values()));
    }

    private static <E extends Enum<E>> List<E> configuredValues(List<String> ids, Class<E> type,
                                                                 List<E> fallback) {
        List<E> values = new ArrayList<>();
        for (String id : ids) {
            try {
                E value = Enum.valueOf(type, id.toUpperCase(Locale.ROOT));
                if (!values.contains(value)) values.add(value);
            } catch (IllegalArgumentException ignored) {
            }
        }
        return values.isEmpty() ? fallback : List.copyOf(values);
    }

    private static String itemId(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().toLowerCase(Locale.ROOT);
    }

    private static int qualityColor(String quality) {
        return switch (quality == null ? "gray" : quality) {
            case "red" -> 0xFFE85B5B;
            case "gold" -> 0xFFFFC857;
            case "purple" -> 0xFFB57AE8;
            case "blue" -> 0xFF5DADE8;
            case "green" -> 0xFF58C98D;
            default -> 0xFF89969A;
        };
    }

    private void drawQualityName(GuiGraphics graphics, String text, int x, int y,
                                 int availableWidth, int qualityColor, long phase) {
        int textWidth = Math.min(availableWidth, font.width(text));
        graphics.fill(x - 2, y - 2, x + textWidth + 3, y + 10,
            0xB0000000 | qualityColor & 0x00FFFFFF);
        TradingUi.drawMarquee(graphics, font, text, x, y, availableWidth,
            theme.text(), phase, uiViewport);
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private record CategoryMenuRow(String sectionId, TradingCategory category, boolean sectionHeader) {
    }

    private record SourceLayout(List<SourceHeaderPlacement> headers,
                                List<SourceCardPlacement> cards, int totalHeight) {
    }

    private record SourceHeaderPlacement(String groupId,
                                         TradingSyncPacket.SourceGroupView group, int y) {
    }

    private record SourceCardPlacement(TradingSyncPacket.SourceView source,
                                       int x, int y, int width) {
    }
}
