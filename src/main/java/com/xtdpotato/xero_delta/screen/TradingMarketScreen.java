package com.xtdpotato.xero_delta.screen;

import com.xtdpotato.xero_delta.client.ClientDataCache;
import com.xtdpotato.xero_delta.client.FtbQuestIntegration;
import com.xtdpotato.xero_delta.client.RecipeViewerIntegration;
import com.xtdpotato.xero_delta.client.ScreenTransition;
import com.xtdpotato.xero_delta.client.TradingClientState;
import com.xtdpotato.xero_delta.client.TradingHistorySelection;
import com.xtdpotato.xero_delta.client.TradingUi;
import com.xtdpotato.xero_delta.client.TradingUiPreferences;
import com.xtdpotato.xero_delta.client.TradingUiScale;
import com.xtdpotato.xero_delta.data.ModDataStorage;
import com.xtdpotato.xero_delta.network.ModNetwork;
import com.xtdpotato.xero_delta.network.TradingActionPacket;
import com.xtdpotato.xero_delta.network.TradingSyncPacket;
import com.xtdpotato.xero_delta.trading.TradingCategory;
import com.xtdpotato.xero_delta.trading.TradingCreativeCategoryGroups;
import com.xtdpotato.xero_delta.trading.TradingCategorySectionPresentation;
import com.xtdpotato.xero_delta.trading.TradingHtmlTemplate;
import com.xtdpotato.xero_delta.trading.TradingHtmlThemeParser;
import com.xtdpotato.xero_delta.trading.TradingItemCategory;
import com.xtdpotato.xero_delta.trading.TradingMenu;
import com.xtdpotato.xero_delta.trading.TradingRules;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.IntConsumer;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** Native Minecraft renderer driven by config/delta_packs/trading.html. */
public final class TradingMarketScreen extends AbstractContainerScreen<TradingMenu> {
    private static final ResourceLocation FAVORITE_SELECTED_ICON =
        icon("textures/gui/action/favorite.png");
    private static final ResourceLocation FAVORITE_UNSELECTED_ICON =
        icon("textures/gui/action/favorite_off.png");
    private static final ResourceLocation SOURCE_ICON =
        icon("textures/gui/action/source.png");
    private static final ResourceLocation INFO_ICON =
        icon("textures/gui/action/info.png");

    private enum Tab { BUY, SELL, HISTORY }
    private enum Group { ALL, VANILLA, MODDED }
    private static final int CARD_WIDTH_SHRINK = 2;
    private static final float CARD_TEXT_SCALE = 7.0F / 9.0F;
    private static final int CARD_MARQUEE_GAP = 14;
    private static final long CARD_MARQUEE_MILLIS_PER_PIXEL = 45L;
    private static final float LIST_ANIMATION_DURATION_MILLIS = 250.0F;
    private static final long CATEGORY_SCROLL_ANIMATION_NANOS = 100_000_000L;
    private static final int BUY_INFO_USE_POPUP_HEIGHT = 220;
    private static final int BUY_INFO_TASK_LIST_TOP = 56;
    private static final int BUY_INFO_TASK_LIST_BOTTOM_PADDING = 10;
    private static final int BUY_INFO_TASK_ROW_HEIGHT = 28;
    private static final int BUY_INFO_TASK_ROW_GAP = 4;
    private static final int PRICE_CHART_COLUMNS = 8;
    private static final int CREATIVE_LIST_BUTTON_WIDTH = 100;
    /** Keep the purchase-detail preview prominent at every UI scale. */
    private static final int BUY_DETAIL_ITEM_SCALE = 4;
    private static final int BUY_DETAIL_ITEM_PREFERRED_Y = 48;
    private static final ResourceLocation REFRESH_ICON =
        ResourceLocation.fromNamespaceAndPath("xero_delta", "textures/gui/refresh.png");
    private static final ResourceLocation REFRESH_ICON_HOVERED =
        ResourceLocation.fromNamespaceAndPath("xero_delta", "textures/gui/refresh_highlighted.png");
    private TradingUiScale.Viewport uiViewport = TradingUiScale.viewport(1, 1, 0);

    private final TradingClientState state = TradingClientState.INSTANCE;
    private TradingHtmlThemeParser.Theme theme;
    private TradingHtmlThemeParser.Document document;
    private EditBox searchBox;
    private EditBox priceBox;
    private EditBox amountBox;
    private List<String> recentSearches = List.of();
    private boolean searchHistoryOpen;
    private final Set<String> expandedCategorySections = new LinkedHashSet<>();
    private String selectedCreativeSection = "";
    private double categoryScrollPixels;
    private double categoryScrollStart;
    private double categoryScrollTarget;
    private long categoryScrollAnimationNanos;
    private boolean draggingCategoryScrollbar;
    private double categoryScrollbarGrabOffset;
    private boolean categoryTouchPressed;
    private boolean categoryTouchDragging;
    private boolean categoryTouchReleaseClick;
    private double categoryTouchStartX;
    private double categoryTouchStartY;
    private double categoryTouchStartScroll;
    private double categoryTouchLastY;
    private long categoryTouchLastNanos;
    private double categoryTouchVelocity;
    private boolean categorySectionsInitialized;
    private Tab tab = Tab.BUY;
    private Group group = Group.ALL;
    private TradingCategory category = TradingCategory.ALL;
    private UUID selectedListing;
    private String selectedSource = "";
    private int scroll;
    private long seenRevision = -1;
    private String filteredCacheKey = "";
    private List<TradingSyncPacket.ListingView> cachedListings = List.of();
    private List<TradingSyncPacket.SourceView> cachedSources = List.of();
    private List<TradingSyncPacket.RecordView> cachedHistory = List.of();
    private ItemStack hoveredStack = ItemStack.EMPTY;
    private String toast = "";
    private boolean toastSuccess = true;
    private long toastUntil;
    private long toastValue;
    private boolean draggingScrollbar;
    private boolean draggingBuyAmountSlider;
    private int catalogueColumns = 3;
    private boolean buyDetailMode;
    private int buyAmount = 1;
    private MarketValueSlider buyAmountSlider;
    private ItemStack buyDetailStack = ItemStack.EMPTY;
    private final ScreenTransition transition = new ScreenTransition(false);
    private boolean syncingBuyAmount;
    private boolean buyRequiresRefresh;
    private long buyRefreshRevision = -1L;
    private long buyRefreshCooldownUntil;
    private BuyInfoPopup buyInfoPopup = BuyInfoPopup.NONE;
    private int buyInfoScroll;
    private List<FtbQuestIntegration.Match> buyInfoQuestMatches = List.of();
    private boolean draggingBuyInfoScrollbar;
    private boolean touchPressed;
    private boolean touchDragging;
    private double touchLastY;
    private double touchStartX;
    private double touchStartY;
    private boolean touchReleaseClick;
    private long listAnimationStartedAt = -1L;
    private UUID pinnedOwnListing;
    private final LinkedHashSet<HistoryRecordKey> selectedHistoryRecords = new LinkedHashSet<>();
    private HistoryRecordKey historySelectionAnchor;
    private boolean historyDetailMode;
    private HistoryDeleteConfirm historyDeleteConfirm = HistoryDeleteConfirm.NONE;
    private static final DateTimeFormatter RECORD_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter RECORD_DATE = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    public TradingMarketScreen(TradingMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected void init() {
        String retainedSearch = searchBox == null ? "" : searchBox.getValue();
        uiViewport = TradingUiScale.viewport(width, height, TradingUiPreferences.tradingUiScaleLevel());
        width = uiViewport.logicalWidth();
        height = uiViewport.logicalHeight();
        imageWidth = width;
        imageHeight = height;
        super.init();
        leftPos = 0;
        topPos = 0;
        document = TradingHtmlTemplate.loadDocument();
        theme = document.theme();
        TradingCreativeCategoryGroups.refresh(minecraft);
        initializeCategorySections();
        catalogueColumns = TradingUiPreferences.marketColumns();
        recentSearches = TradingUiPreferences.marketSearchHistory();
        searchHistoryOpen = false;
        int sidebar = sidebarWidth();
        searchBox = addRenderableWidget(new Material3CompactEditBox(font, 18, 52, sidebar - 36, 20,
            Component.translatable("market.xero_delta.search")));
        searchBox.setHint(Component.translatable("market.xero_delta.search_hint"));
        searchBox.setMaxLength(128);
        searchBox.setValue(retainedSearch);
        searchBox.setResponder(ignored -> {
            resetCatalogueSelection();
            if (searchBox.isFocused()) searchHistoryOpen = true;
        });
        priceBox = addRenderableWidget(new Material3CompactEditBox(font, 0, 0, 126, 20,
            Component.translatable("market.xero_delta.price")));
        priceBox.setFilter(text -> text.isEmpty() || text.matches("\\d{0,9}"));
        priceBox.setHint(Component.translatable("market.xero_delta.price"));
        amountBox = addRenderableWidget(new Material3CompactEditBox(font, 0, 0, 52, 20,
            Component.translatable("market.xero_delta.amount")));
        amountBox.setFilter(text -> text.isEmpty() || text.matches("\\d{0,3}"));
        amountBox.setValue(String.valueOf(Math.max(1, buyAmount)));
        amountBox.setResponder(this::onBuyAmountText);
        buyAmountSlider = addRenderableWidget(new MarketValueSlider(0, 0, 160, 18, 1, 1, 1,
            this::setBuyAmountFromSlider));
        buyAmountSlider.visible = false;
        positionBottomFields();
        if (buyDetailMode) {
            refreshBuyDetailSelection();
            positionBuyDetailSlider();
        } else {
            updateFieldVisibility();
        }
        ModNetwork.sendToServer(TradingActionPacket.refresh());
        state.restoreCursor();
    }

    private void updateFieldVisibility() {
        if (priceBox == null || amountBox == null) return;
        priceBox.visible = false;
        if (searchBox != null) {
            searchBox.visible = !buyDetailMode && !historyDetailMode;
            if (!searchBox.visible) {
                searchHistoryOpen = false;
                searchBox.setFocused(false);
            }
        }
        TradingSyncPacket.ListingView listing = selectedListing();
        boolean showBuyControls = buyDetailMode && listing != null && !isOwnListing(listing);
        amountBox.visible = showBuyControls;
        amountBox.active = showBuyControls && !buyRequiresRefresh;
        if (buyAmountSlider != null) {
            buyAmountSlider.visible = showBuyControls;
            buyAmountSlider.active = showBuyControls && !buyRequiresRefresh;
            if (!buyAmountSlider.visible || !buyAmountSlider.active) draggingBuyAmountSlider = false;
        }
    }

    private void setBuyAmountFromSlider(int value) {
        buyAmount = value;
        if (amountBox != null && amountBox.visible && !amountBox.isFocused()
            && !String.valueOf(value).equals(amountBox.getValue())) {
            syncingBuyAmount = true;
            amountBox.setValue(String.valueOf(value));
            syncingBuyAmount = false;
        }
    }

    private void onBuyAmountText(String value) {
        if (syncingBuyAmount || !buyDetailMode || value.isBlank()) return;
        try {
            int maximum = purchaseAmountLimit(buyDetailStack);
            buyAmount = Math.max(1, Math.min(maximum, Integer.parseInt(value)));
            buyAmountSlider.setIntValue(buyAmount);
        } catch (NumberFormatException ignored) {
        }
    }

    private int availableBuyAmount(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 0;
        TradingSyncPacket.ListingView lowest = preferredListing(stack);
        if (lowest == null) return 0;
        long lowestPrice = unitPrice(lowest);
        int total = 0;
        UUID ownId = minecraft != null && minecraft.player != null ? minecraft.player.getUUID() : null;
        for (TradingSyncPacket.ListingView listing : state.listings()) {
            if (listing.expired() || ownId != null && listing.sellerId().equals(ownId)
                || !ItemStack.isSameItemSameComponents(stack, listing.stack())
                || unitPrice(listing) != lowestPrice) continue;
            total = Math.min(640, total + listing.stack().getCount());
        }
        return total;
    }

    private int purchaseAmountLimit(ItemStack stack) {
        return TradingCategory.classify(itemId(stack)) == TradingCategory.AMMO ? 200 : 10;
    }

    private void drawTopToast(GuiGraphics graphics) {
        if (System.currentTimeMillis() >= toastUntil || toast.isBlank()) return;
        int boxWidth = Math.min(width - 80, font.width(toast) + 30);
        int x = (width - boxWidth) / 2;
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 600);
        graphics.fill(x, 42, x + boxWidth, 68, toastSuccess ? 0xF02B4942 : 0xF05A2928);
        graphics.drawCenteredString(font, toast, width / 2, 51, theme.text());
        graphics.pose().popPose();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (state.revision() != seenRevision) {
            if (buyRefreshRevision >= 0L && state.revision() > buyRefreshRevision) {
                buyRefreshRevision = -1L;
                buyRequiresRefresh = false;
            }
            seenRevision = state.revision();
            String context = state.consumePendingSourceId();
            if (context.equals("tab:history")) tab = Tab.HISTORY;
            else if (context.equals("tab:buy")) tab = Tab.BUY;
            else if (context.equals("tab:sell")) tab = Tab.SELL;
            if (context.startsWith("detail-listing:")) {
                try {
                    TradingSyncPacket.ListingView listing = listing(UUID.fromString(context.substring(15)));
                    if (listing != null) openBuyDetail(listing);
                } catch (IllegalArgumentException ignored) {
                }
            }
            if (!state.message().isBlank()) {
                toast = Component.translatable(state.message()).getString();
                toastValue = state.messageValue();
                if (state.success() && toastValue > 0L
                    && "market.xero_delta.success.bought".equals(state.message())) {
                    toast = Component.translatable("market.xero_delta.purchase_toast",
                        TradingUi.format(toastValue)).getString();
                }
                if (buyDetailMode && ("market.xero_delta.error.stale".equals(state.message())
                    || "market.xero_delta.error.amount".equals(state.message()))) {
                    buyRequiresRefresh = true;
                }
                toastSuccess = state.success();
                toastUntil = System.currentTimeMillis() + 3000L;
            }
            pruneHistorySelection();
            if (buyDetailMode) refreshBuyDetailSelection();
        }
        scroll = Mth.clamp(scroll, 0, maxScroll());
        hoveredStack = ItemStack.EMPTY;
        mouseX = uiViewport.mouseX(mouseX);
        mouseY = uiViewport.mouseY(mouseY);
        graphics.pose().pushPose();
        uiViewport.apply(graphics);
        transition.push(graphics);
        try {
            super.render(graphics, mouseX, mouseY, partialTick);
            if (historyDeleteConfirm != HistoryDeleteConfirm.NONE) {
                drawHistoryDeleteConfirm(graphics, mouseX, mouseY);
            }
            int scaleLevel = TradingUiPreferences.tradingUiScaleLevel();
            TradingUiScale.drawControls(graphics, font, width, mouseX, mouseY,
                scaleLevel > TradingUiScale.MIN_LEVEL, scaleLevel < TradingUiScale.MAX_LEVEL);
            TradingUi.drawBackButton(graphics, font, width, mouseX, mouseY);
            drawSearchHistory(graphics, mouseX, mouseY);
            drawTopToast(graphics);
            if (!hoveredStack.isEmpty()) graphics.renderTooltip(font, hoveredStack, mouseX, mouseY);
            if (!historyDetailMode && historyDeleteConfirm == HistoryDeleteConfirm.NONE) {
                TradingUi.renderBalanceTooltipIfHovered(graphics, font, state.balance(),
                    width, 16, mouseX, mouseY);
            }
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

    private void drawSearchHistory(GuiGraphics graphics, int mouseX, int mouseY) {
        if (!searchHistoryOpen || searchBox == null || !searchBox.visible || !searchBox.isFocused()
            || recentSearches.isEmpty()) return;
        List<String> suggestions = visibleSearchHistory();
        int x = searchBox.getX();
        int y = searchBox.getY() + searchBox.getHeight() + 2;
        int rowHeight = 18;
        int width = searchBox.getWidth();
        int height = (suggestions.size() + 1) * rowHeight;
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 500);
        graphics.fill(x, y, x + width, y + height, 0xF21B2529);
        graphics.renderOutline(x, y, width, height, theme.border());
        for (int index = 0; index < suggestions.size(); index++) {
            int rowY = y + index * rowHeight;
            if (inside(mouseX, mouseY, x, rowY, width, rowHeight)) {
                graphics.fill(x + 1, rowY + 1, x + width - 1, rowY + rowHeight, theme.panelAlt());
            }
            String value = font.plainSubstrByWidth(suggestions.get(index), Math.max(8, width - 12));
            graphics.drawString(font, value, x + 6, rowY + 5, theme.text(), false);
        }
        int clearY = y + suggestions.size() * rowHeight;
        if (inside(mouseX, mouseY, x, clearY, width, rowHeight)) {
            graphics.fill(x + 1, clearY, x + width - 1, clearY + rowHeight - 1, theme.panelAlt());
        }
        graphics.drawCenteredString(font, Component.translatable("market.xero_delta.clear_search_history"),
            x + width / 2, clearY + 5, 0xFFE5A6A6);
        graphics.pose().popPose();
    }

    private List<String> visibleSearchHistory() {
        String query = searchBox == null ? "" : searchBox.getValue().trim().toLowerCase(Locale.ROOT);
        if (query.isBlank()) return recentSearches;
        return recentSearches.stream()
            .filter(value -> value.toLowerCase(Locale.ROOT).contains(query))
            .limit(5)
            .toList();
    }

    private boolean handleSearchHistoryClick(double mouseX, double mouseY, int button) {
        if (button != 0 || !searchHistoryOpen || searchBox == null || recentSearches.isEmpty()) return false;
        List<String> suggestions = visibleSearchHistory();
        int x = searchBox.getX();
        int y = searchBox.getY() + searchBox.getHeight() + 2;
        int rowHeight = 18;
        int width = searchBox.getWidth();
        int height = (suggestions.size() + 1) * rowHeight;
        if (!inside(mouseX, mouseY, x, y, width, height)) return false;
        int row = ((int) mouseY - y) / rowHeight;
        if (row < suggestions.size()) {
            searchBox.setValue(suggestions.get(row));
            rememberCurrentSearch();
        } else {
            TradingUiPreferences.clearMarketSearchHistory();
            recentSearches = List.of();
        }
        searchHistoryOpen = false;
        searchBox.setFocused(false);
        return true;
    }

    private void rememberCurrentSearch() {
        if (searchBox == null) return;
        TradingUiPreferences.rememberMarketSearch(searchBox.getValue());
        recentSearches = TradingUiPreferences.marketSearchHistory();
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        if (buyDetailMode) {
            drawBuyDetail(g, mouseX, mouseY);
            return;
        }
        if (historyDetailMode) {
            drawHistoryDetail(g, mouseX, mouseY);
            return;
        }
        int sidebar = sidebarWidth();
        g.fill(0, 0, width, height, theme.background());
        g.fill(0, 0, width, 43, 0xEE081014);
        g.fill(0, 42, width, 43, theme.accent());
        g.fill(0, 43, sidebar, height, theme.panel());
        drawTopBar(g, sidebar, mouseX, mouseY);
        drawSidebar(g, sidebar, mouseX, mouseY);
        drawCatalogue(g, sidebar, mouseX, mouseY);
        drawBottomBar(g, sidebar, mouseX, mouseY);
    }

    private void drawTopBar(GuiGraphics g, int sidebar, int mouseX, int mouseY) {
        int tabX = tabStartX(sidebar);
        int tabW = tabWidth(sidebar);
        int tabIndex = 0;
        for (Tab value : configuredTabs()) {
            int x = tabX + tabIndex++ * (tabW + 4);
            boolean selected = tab == value;
            boolean hovered = inside(mouseX, mouseY, x, 8, tabW, 28);
            g.fill(x, 8, x + tabW, 36, selected ? theme.panelAlt() : hovered ? theme.panel() : 0x55000000);
            if (selected) g.fill(x, 34, x + tabW, 36, theme.accent());
            g.drawCenteredString(font, Component.translatable("market.xero_delta.tab."
                + value.name().toLowerCase(Locale.ROOT)), x + tabW / 2, 18,
                selected ? theme.text() : theme.muted());
        }
        int amountWidth = TradingUi.balanceWidth(font, state.balance());
        TradingUi.drawBalance(g, font, state.balance(), width - 88 - amountWidth, 16, 0xFFFFD36A);
    }

    private void drawSidebar(GuiGraphics g, int sidebar, int mouseX, int mouseY) {
        int y = 84;
        g.drawString(font, Component.translatable("market.xero_delta.categories"), 18, y,
            theme.muted(), false);
        y = categoryTop();
        int rowHeight = categoryRowHeight();
        int rowGap = categoryRowGap();
        int rowStride = rowHeight + rowGap;
        List<CategoryMenuRow> rows = categoryMenuRows();
        updateCategoryScroll();
        int firstIndex = Math.max(0, (int) Math.floor(categoryScrollPixels / rowStride));
        double rowOffset = categoryScrollPixels - firstIndex * rowStride;
        int visibleRows = Math.max(1, (categoryAreaBottom() - y) / rowStride + 2);
        uiViewport.enableScissor(g, 8, y, sidebar - 8, categoryAreaBottom());
        for (int visibleIndex = 0; visibleIndex < visibleRows; visibleIndex++) {
            int index = firstIndex + visibleIndex;
            if (index >= rows.size()) break;
            CategoryMenuRow row = rows.get(index);
            int rowY = y + (int) Math.round(visibleIndex * rowStride - rowOffset);
            int h = rowHeight;
            boolean sectionHeader = row.sectionHeader();
            boolean expanded = sectionHeader && expandedCategorySections.contains(row.sectionId());
            boolean selected = sectionHeader
                ? row.sectionId().equals(selectedCreativeSection)
                : category == row.category()
                    && (row.sectionId().isBlank()
                        ? selectedCreativeSection.isBlank()
                        : row.sectionId().equals(selectedCreativeSection));
            boolean hovered = !categoryTouchDragging && !draggingCategoryScrollbar
                && rowY + h > y && rowY < categoryAreaBottom()
                && inside(mouseX, mouseY, 12, rowY, sidebar - 24, h);
            int rowX = !sectionHeader && !row.sectionId().isBlank() ? 20 : 12;
            g.fill(rowX, rowY, sidebar - 12, rowY + h,
                selected || expanded ? theme.panelAlt() : hovered ? 0x66405258 : 0x22000000);
            if (sectionHeader) g.renderOutline(12, rowY, sidebar - 24, h,
                selected ? theme.accent() : expanded ? theme.border() : 0x88405258);
            else if (selected) g.renderOutline(rowX, rowY, sidebar - 12 - rowX, h, theme.accent());
            Component label = sectionHeader
                ? TradingCategorySectionPresentation.label(row.sectionId(), categorySections())
                : Component.translatable("market.xero_delta.category."
                    + row.category().name().toLowerCase(Locale.ROOT));
            ItemStack icon = sectionHeader
                ? TradingCategorySectionPresentation.icon(row.sectionId(), categorySections())
                : TradingCreativeCategoryGroups.categoryIcon(row.sectionId(), row.category());
            if (!icon.isEmpty()) renderNeutralItem(g, icon, rowX + 4, rowY + Math.max(1, (h - 16) / 2));
            int labelX = rowX + 26;
            g.drawString(font, label, labelX, rowY + Math.max(3, (h - 8) / 2),
                selected || expanded ? theme.text() : theme.muted(), false);
            if (sectionHeader && sectionCategories(row.sectionId()).size() > 1) {
                g.drawCenteredString(font, expanded ? "\u25B2" : "\u25BC",
                    sidebar - 25, rowY + Math.max(3, (h - 8) / 2), theme.muted());
            }
        }
        g.disableScissor();
        drawCategoryScrollbar(g, sidebar, mouseX, mouseY);
        y = categoryGroupLabelY();
        g.drawString(font, Component.translatable("market.xero_delta.group"), 18, y + 5,
            theme.muted(), false);
        y += 20;
        int groupW = Math.max(38, (sidebar - 32) / 3);
        for (Group value : configuredGroups()) {
            int x = 12 + value.ordinal() * groupW;
            boolean selected = group == value;
            g.fill(x, y, x + groupW - 2, y + 22, selected ? theme.accent() : theme.panelAlt());
            g.drawCenteredString(font, Component.translatable("market.xero_delta.group."
                + value.name().toLowerCase(Locale.ROOT)), x + (groupW - 2) / 2, y + 7,
                selected ? 0xFF0A1517 : theme.text());
        }
    }

    private void drawCatalogue(GuiGraphics g, int sidebar, int mouseX, int mouseY) {
        int contentX = sidebar + 16;
        int contentY = 52;
        int contentRight = width - 14;
        int contentBottom = height - 42;
        drawColumnControl(g, contentX, contentY, contentRight, mouseX, mouseY);
        contentY += 30;
        uiViewport.enableScissor(g, contentX, contentY, contentRight, contentBottom);
        if (tab == Tab.BUY) drawListingCards(g, filteredListings(), contentX, contentY,
            contentRight, contentBottom, mouseX, mouseY);
        else if (tab == Tab.SELL) drawSourceCards(g, filteredSources(), contentX, contentY,
            contentRight, contentBottom, mouseX, mouseY);
        else drawHistoryCards(g, filteredHistory(), contentX, contentY,
            contentRight, contentBottom, mouseX, mouseY);
        g.disableScissor();
    }

    private void drawColumnControl(GuiGraphics graphics, int left, int top, int right,
                                   int mouseX, int mouseY) {
        if (isCreativePlayer() && tab == Tab.BUY) {
            boolean hovered = inside(mouseX, mouseY, left, top, CREATIVE_LIST_BUTTON_WIDTH, 22);
            graphics.fill(left, top, left + CREATIVE_LIST_BUTTON_WIDTH, top + 22,
                hovered ? 0xFF5B7B62 : 0xFF405A48);
            graphics.renderOutline(left, top, CREATIVE_LIST_BUTTON_WIDTH, 22,
                hovered ? 0xFFFFFFFF : 0xFF6FA17D);
            graphics.drawCenteredString(font,
                Component.translatable("trading_op.xero_delta.creative_list"),
                left + CREATIVE_LIST_BUTTON_WIDTH / 2, top + 7, 0xFFFFFFFF);
        }
        Component label = Component.translatable("market.xero_delta.columns");
        int controlWidth = 82;
        int x = right - controlWidth;
        int labelX = x - font.width(label) - 8;
        int refreshX = Math.max(left + (isCreativePlayer() && tab == Tab.BUY
            ? CREATIVE_LIST_BUTTON_WIDTH + 8 : 0), labelX - 32);
        drawRefreshIconButton(graphics, refreshX, top, mouseX, mouseY);
        graphics.drawString(font, label, labelX, top + 7, theme.muted(), false);
        drawCompactControl(graphics, x, top, 24, "-", catalogueColumns > 1, mouseX, mouseY);
        graphics.fill(x + 26, top, x + 56, top + 22, theme.panel());
        graphics.drawCenteredString(font, String.valueOf(catalogueColumns), x + 41, top + 7, theme.text());
        drawCompactControl(graphics, x + 58, top, 24, "+", catalogueColumns < 10, mouseX, mouseY);
    }

    private void drawCompactControl(GuiGraphics graphics, int x, int y, int width, String text,
                                    boolean enabled, int mouseX, int mouseY) {
        int color = !enabled ? 0xFF263338
            : inside(mouseX, mouseY, x, y, width, 22) ? theme.panelAlt() : theme.panel();
        graphics.fill(x, y, x + width, y + 22, color);
        graphics.drawCenteredString(font, text, x + width / 2, y + 7, enabled ? theme.text() : theme.muted());
    }

    private void drawRefreshIconButton(GuiGraphics graphics, int x, int y, int mouseX, int mouseY) {
        boolean hovered = inside(mouseX, mouseY, x, y, 24, 22);
        graphics.fill(x, y, x + 24, y + 22, hovered ? theme.panelAlt() : theme.panel());
        graphics.blit(hovered ? REFRESH_ICON_HOVERED : REFRESH_ICON,
            x + 4, y + 3, 0, 0, 16, 16, 16, 16);
    }

    private static void drawRefreshIcon(GuiGraphics graphics, int centerX, int centerY, int color) {
        graphics.blit(REFRESH_ICON, centerX - 8, centerY - 8,
            0, 0, 16, 16, 16, 16);
    }

    private void drawListingCards(GuiGraphics g, List<TradingSyncPacket.ListingView> values,
                                  int x, int y, int right, int bottom, int mouseX, int mouseY) {
        if (values.isEmpty()) {
            drawEmptyState(g, "market.xero_delta.empty.listings", x, y, right, bottom);
            return;
        }
        int cardW = cardWidth(x, right);
        int columns = cardColumns(x, right);
        int stride = marketCardHeight() + theme.cardGap();
        int first = Math.max(0, scroll / stride - 1) * columns;
        int visibleRows = Math.max(1, (bottom - y) / stride + 3);
        int last = Math.min(values.size(), first + visibleRows * columns);
        for (int i = first; i < last; i++) {
            TradingSyncPacket.ListingView value = values.get(i);
            int[] pos = cardPosition(i, x, y, cardW);
            if (pos[1] + marketCardHeight() < y || pos[1] > bottom) continue;
            int drawY = pos[1] + listAnimationOffset(i);
            boolean selected = value.id().equals(selectedListing);
            String referenceValue = Component.translatable("market.xero_delta.reference_value",
                TradingUi.format(Math.max(1L, value.itemValue() / Math.max(1, value.stack().getCount())))).getString();
            drawItemCard(g, value.stack(), value.stack().getHoverName().getString(),
                value.sellerName(),
                unitPrice(value), referenceValue, pos[0], drawY, cardW, selected, false, mouseX, mouseY);
            boolean favorite = state.favorites().contains(favoriteKey(value.stack()));
            drawActionIcon(g, favorite ? FAVORITE_SELECTED_ICON : FAVORITE_UNSELECTED_ICON,
                pos[0] + cardW - 13, drawY + 5, 11,
                favorite ? 0xFFFFD36A : theme.muted());
            drawListFadeOverlay(g, pos[0], drawY, cardW, i);
        }
        drawScrollbar(g, values.size(), x, y, right, bottom);
    }

    private void drawSourceCards(GuiGraphics g, List<TradingSyncPacket.SourceView> values,
                                 int x, int y, int right, int bottom, int mouseX, int mouseY) {
        if (values.isEmpty()) {
            drawEmptyState(g, "market.xero_delta.empty.sources", x, y, right, bottom);
            return;
        }
        int cardW = cardWidth(x, right);
        int columns = cardColumns(x, right);
        int stride = marketCardHeight() + theme.cardGap();
        int first = Math.max(0, scroll / stride - 1) * columns;
        int visibleRows = Math.max(1, (bottom - y) / stride + 3);
        int last = Math.min(values.size(), first + visibleRows * columns);
        for (int i = first; i < last; i++) {
            TradingSyncPacket.SourceView value = values.get(i);
            int[] pos = cardPosition(i, x, y, cardW);
            if (pos[1] + marketCardHeight() < y || pos[1] > bottom) continue;
            int drawY = pos[1] + listAnimationOffset(i);
            boolean selected = value.id().equals(selectedSource);
            long unitValue = TradingRules.normalizeItemValue(ClientDataCache.INSTANCE.getPrice(value.stack()));
            long price = Math.min(TradingRules.MAX_CURRENCY, unitValue * value.availableCount());
            String sourceLabel = Component.translatable(value.label()).getString();
            drawItemCard(g, value.stack(), value.stack().getHoverName().getString(), sourceLabel, price,
                "", pos[0], drawY, cardW, selected, true, mouseX, mouseY);
            drawListFadeOverlay(g, pos[0], drawY, cardW, i);
        }
        drawScrollbar(g, values.size(), x, y, right, bottom);
    }

    private void drawHistoryCards(GuiGraphics g, List<TradingSyncPacket.RecordView> values,
                                  int x, int y, int right, int bottom, int mouseX, int mouseY) {
        if (values.isEmpty()) {
            drawEmptyState(g, "market.xero_delta.empty.history", x, y, right, bottom);
            return;
        }
        List<TradingSyncPacket.RecordView> purchases = historyPurchases(values);
        List<TradingSyncPacket.RecordView> sales = historySales(values);
        int gap = 12;
        int columnWidth = Math.max(80, (right - x - gap - 4) / 2);
        int rightX = x + columnWidth + gap;
        int rowsTop = y + 20;
        g.drawString(font, Component.translatable("market.xero_delta.history.purchase_records"),
            x + 4, y + 6, theme.text(), false);
        g.drawString(font, Component.translatable("market.xero_delta.history.sale_records"),
            rightX + 4, y + 6, theme.text(), false);
        if (purchases.isEmpty()) drawHistoryColumnEmpty(g, x, rowsTop, columnWidth,
            "market.xero_delta.history.empty_purchases");
        if (sales.isEmpty()) drawHistoryColumnEmpty(g, rightX, rowsTop, columnWidth,
            "market.xero_delta.history.empty_sales");
        int maximumRows = Math.max(purchases.size(), sales.size());
        int stride = marketCardHeight() + theme.cardGap();
        int firstRow = Math.max(0, scroll / stride - 1);
        int lastRow = Math.min(maximumRows, firstRow + Math.max(1, (bottom - rowsTop) / stride + 3));
        for (int row = firstRow; row < lastRow; row++) {
            int rowY = rowsTop + row * (marketCardHeight() + theme.cardGap()) - scroll;
            if (rowY + marketCardHeight() < rowsTop || rowY > bottom) continue;
            if (row < purchases.size()) {
                drawHistoryRow(g, purchases.get(row), x,
                    rowY + listAnimationOffset(row * 2), columnWidth, true, mouseX, mouseY);
            }
            if (row < sales.size()) {
                drawHistoryRow(g, sales.get(row), rightX,
                    rowY + listAnimationOffset(row * 2 + 1), columnWidth, false, mouseX, mouseY);
            }
        }
        drawScrollbar(g, maximumRows, x, y, right, bottom);
    }

    private void drawHistoryColumnEmpty(GuiGraphics graphics, int x, int y, int width, String key) {
        Component text = Component.translatable(key);
        graphics.drawCenteredString(font, text, x + width / 2, y + 18, theme.muted());
    }

    private void drawHistoryRow(GuiGraphics graphics, TradingSyncPacket.RecordView record,
                                int x, int y, int width, boolean purchase,
                                int mouseX, int mouseY) {
        int height = marketCardHeight();
        boolean selected = isSelectedHistoryRecord(record);
        boolean hovered = inside(mouseX, mouseY, x, y, width, height);
        int quality = qualityColor(ClientDataCache.INSTANCE.getQuality(record.stack()));
        graphics.fill(x, y, x + width, y + height,
            selected ? 0xEE304248 : hovered ? theme.panelAlt() : theme.panel());
        graphics.fill(x, y, x + width, y + 2, quality);
        graphics.fill(x, y + height - 1, x + width, y + height, theme.border());
        int itemAreaWidth = Math.max(58, Math.min(width - 56, width * 3 / 5));
        int detailX = x + itemAreaWidth;
        graphics.fill(detailX, y + 2, detailX + 1, y + height - 1, theme.border());
        drawQualityName(graphics, record.stack().getHoverName().getString(), x + 6, y + 6,
            Math.max(20, itemAreaWidth - 12), quality);
        drawLargeItem(graphics, record.stack(), x + itemAreaWidth / 2 - 16,
            y + Math.max(24, height / 2 - 10), false);
        Component amount = Component.translatable("market.xero_delta.history_amount",
            record.stack().getCount());
        graphics.drawString(font, amount, x + 6, y + height - 12, theme.muted(), false);
        drawScaledCardAmount(graphics, record.price(), x, y, itemAreaWidth, height);
        String date = historyDate(record);
        Component action = Component.translatable(purchase
            ? "market.xero_delta.history.buy_action" : "market.xero_delta.history.sell_action");
        int detailWidth = Math.max(1, width - itemAreaWidth);
        graphics.drawCenteredString(font, font.plainSubstrByWidth(date, Math.max(1, detailWidth - 8)),
            detailX + detailWidth / 2, y + Math.max(8, height / 2 - 9), theme.text());
        graphics.drawCenteredString(font, action, detailX + detailWidth / 2,
            y + Math.max(19, height / 2 + 3), theme.muted());
        if (selected) graphics.renderOutline(x, y, width, height, 0xFFFFFFFF);
        if (hovered) hoveredStack = record.stack();
    }

    private static String historyDate(TradingSyncPacket.RecordView record) {
        return record.completedEpochMillis() <= 0L ? "-"
            : RECORD_DATE.format(Instant.ofEpochMilli(record.completedEpochMillis())
                .atZone(ZoneId.systemDefault()));
    }

    private void drawItemCard(GuiGraphics g, ItemStack stack, String title, String subtitle, long price,
                              String detail, int x, int y, int width, boolean selected,
                              boolean showStackCount, int mouseX, int mouseY) {
        int height = marketCardHeight();
        boolean hovered = inside(mouseX, mouseY, x, y, width, height);
        int quality = qualityColor(ClientDataCache.INSTANCE.getQuality(stack));
        g.fill(x, y, x + width, y + height, selected ? 0xEE304248 : hovered ? theme.panelAlt() : theme.panel());
        g.fill(x, y, x + width, y + 2, quality);
        g.fill(x, y + height - 1, x + width, y + height, theme.border());
        boolean compact = width < 90;
        int titleX = x + (compact ? 4 : 7);
        int titleWidth = Math.max(18, width - (compact ? 10 : 28));
        drawQualityName(g, title, titleX, y + 6, titleWidth, quality);
        if (!compact) {
            drawCardMarquee(g, subtitle, x + 7, y + 19,
                Math.max(18, width - 14), theme.muted(), x * 31L + y);
        }
        boolean showDetail = !compact && !detail.isBlank() && height >= 88;
        if (showDetail) {
            drawCardMarquee(g, detail, x + 7, y + 31,
                Math.max(18, width - 14), theme.muted(), x * 17L + y * 3L);
        }
        drawLargeItem(g, stack, x + width / 2 - 16,
            y + (showDetail ? 43 : compact ? 25 : Math.max(30, height / 2 - 10)), showStackCount);
        drawScaledCardAmount(g, price, x, y, width, height);
        if (selected) g.renderOutline(x, y, width, height, 0xFFFFFFFF);
        if (hovered) hoveredStack = stack;
    }

    private void startListAnimation() {
        listAnimationStartedAt = System.currentTimeMillis();
    }

    private float listAnimationProgress(int index) {
        if (listAnimationStartedAt < 0L) return 1.0F;
        long elapsed = System.currentTimeMillis() - listAnimationStartedAt - Math.min(8, index) * 15L;
        return Mth.clamp(elapsed / LIST_ANIMATION_DURATION_MILLIS, 0.0F, 1.0F);
    }

    private int listAnimationOffset(int index) {
        return Math.round(10.0F * (1.0F - listAnimationProgress(index)));
    }

    private void drawListFadeOverlay(GuiGraphics graphics, int x, int y, int width, int index) {
        float progress = listAnimationProgress(index);
        if (progress >= 1.0F) return;
        int alpha = Math.round(190.0F * (1.0F - progress));
        graphics.fill(x, y, x + width, y + marketCardHeight(),
            alpha << 24 | theme.background() & 0x00FFFFFF);
    }

    private static String compactAmount(long value) {
        return TradingUi.formatMarket(value);
    }

    private void drawQualityName(GuiGraphics graphics, String text, int x, int y,
                                 int availableWidth, int qualityColor) {
        int textWidth = Math.min(availableWidth, scaledCardTextWidth(text));
        graphics.fill(x - 2, y - 2, x + textWidth + 3, y + 8,
            0xB0000000 | qualityColor & 0x00FFFFFF);
        drawCardMarquee(graphics, text, x, y, availableWidth, theme.text(), x * 19L + y);
    }

    private void drawScaledCardAmount(GuiGraphics graphics, long price,
                                      int cardX, int cardY, int cardWidth, int cardHeight) {
        int amountWidth = (int) Math.ceil(TradingUi.amountWidth(font, price) * CARD_TEXT_SCALE);
        int drawY = cardY + cardHeight - 11;
        if (amountWidth <= cardWidth - 8) {
            int drawX = cardX + cardWidth - amountWidth - 4;
            graphics.pose().pushPose();
            graphics.pose().translate(drawX, drawY, 220);
            graphics.pose().scale(CARD_TEXT_SCALE, CARD_TEXT_SCALE, 1.0F);
            TradingUi.drawAmount(graphics, font, price, 0, 0, 0xFFE4E8E6);
            graphics.pose().popPose();
            return;
        }
        String compact = compactAmount(price);
        int compactWidth = scaledCardTextWidth(compact);
        drawScaledCardText(graphics, compact,
            cardX + Math.max(2, (cardWidth - compactWidth) / 2), drawY, 0xFFE4E8E6);
    }

    private void drawCardMarquee(GuiGraphics graphics, String text, int x, int y,
                                 int availableWidth, int color, long phase) {
        if (text == null || text.isBlank() || availableWidth <= 0) return;
        int textWidth = scaledCardTextWidth(text);
        uiViewport.enableScissor(graphics, x, y - 1, x + availableWidth, y + 8);
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 220);
        graphics.pose().scale(CARD_TEXT_SCALE, CARD_TEXT_SCALE, 1.0F);
        if (textWidth <= availableWidth) {
            graphics.drawString(font, text, 0, 0, color, false);
        } else {
            int period = textWidth + CARD_MARQUEE_GAP;
            long pixels = System.currentTimeMillis() / CARD_MARQUEE_MILLIS_PER_PIXEL + phase;
            int offset = (int) Math.floorMod(pixels, period);
            float unscaledOffset = offset / CARD_TEXT_SCALE;
            float unscaledPeriod = period / CARD_TEXT_SCALE;
            graphics.drawString(font, text, -unscaledOffset, 0, color, false);
            graphics.drawString(font, text, -unscaledOffset + unscaledPeriod, 0, color, false);
        }
        graphics.pose().popPose();
        graphics.disableScissor();
    }

    private void drawScaledCardText(GuiGraphics graphics, String text, int x, int y, int color) {
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 220);
        graphics.pose().scale(CARD_TEXT_SCALE, CARD_TEXT_SCALE, 1.0F);
        graphics.drawString(font, text, 0, 0, color, false);
        graphics.pose().popPose();
    }

    private int scaledCardTextWidth(String text) {
        return (int) Math.ceil(font.width(text == null ? "" : text) * CARD_TEXT_SCALE);
    }

    private void drawEmptyState(GuiGraphics graphics, String key, int left, int top, int right, int bottom) {
        Component message = Component.translatable(key);
        int maxWidth = Math.max(80, right - left - 36);
        List<net.minecraft.util.FormattedCharSequence> lines = font.split(message, maxWidth);
        int y = (top + bottom - lines.size() * 11) / 2;
        for (var line : lines) {
            graphics.drawCenteredString(font, line, (left + right) / 2, y, theme.muted());
            y += 11;
        }
    }

    private void drawLargeItem(GuiGraphics g, ItemStack stack, int x, int y, boolean showStackCount) {
        g.pose().pushPose();
        g.pose().translate(x, y, 80);
        g.pose().scale(2.0F, 2.0F, 1.0F);
        renderNeutralItem(g, stack, 0, 0);
        if (showStackCount) g.renderItemDecorations(font, stack, 0, 0);
        g.pose().popPose();
    }

    /**
     * Item rendering shares GuiGraphics' tint state with textured buttons.
     * Establish white before and after every market item so a previously drawn
     * icon cannot make the next item appear dark or black.
     */
    private static void renderNeutralItem(GuiGraphics graphics, ItemStack stack, int x, int y) {
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        try {
            graphics.renderItem(stack, x, y);
        } finally {
            graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }

    private void drawBottomBar(GuiGraphics g, int sidebar, int mouseX, int mouseY) {
        int y = height - 44;
        g.fill(sidebar, y, width, height, 0xF20C171B);
        if (System.currentTimeMillis() < toastUntil && !toast.isBlank()) {
            g.drawString(font, toast, sidebar + 16, y + 15,
                toastSuccess ? theme.accent() : theme.danger(), false);
        }
        if (tab == Tab.SELL) {
            drawSellActions(g, mouseX, mouseY);
            return;
        }
        if (tab == Tab.HISTORY) {
            HistoryActionLayout layout = historyActionLayout(sidebar);
            boolean detailEnabled = selectedHistoryRecords.size() == 1;
            boolean deleteEnabled = !selectedHistoryRecords.isEmpty();
            boolean hasHistory = !state.history().isEmpty();
            drawHistoryActionButton(g, layout.detailX(), layout.buttonWidth(), mouseX, mouseY,
                detailEnabled, theme.panelAlt(), 0xFF40545B, theme.text(),
                Component.translatable("market.xero_delta.history_details"));
            drawHistoryActionButton(g, layout.refreshX(), layout.buttonWidth(), mouseX, mouseY,
                true, theme.accent(), 0xFF75E2C0, 0xFF071214,
                Component.translatable("market.xero_delta.refresh"));
            drawHistoryActionButton(g, layout.deleteX(), layout.buttonWidth(), mouseX, mouseY,
                deleteEnabled, 0xFF8B3D3B, 0xFFA94B47, 0xFFFFE7E5,
                Component.translatable("market.xero_delta.delete_history"));
            drawHistoryActionButton(g, layout.deleteAllX(), layout.buttonWidth(), mouseX, mouseY,
                hasHistory, 0xFFB2423F, 0xFFD0524E, 0xFFFFE7E5,
                Component.translatable("market.xero_delta.delete_all_history"));
            return;
        }
        int actionX = actionX();
        int actionY = actionY();
        int actionW = actionWidth();
        boolean enabled = primaryActionEnabled();
        int color = enabled ? theme.accent() : 0xFF39474B;
        g.fill(actionX, actionY, actionX + actionW, actionY + 20, color);
        Component label = tab == Tab.BUY ? buyActionLabel() : tab == Tab.SELL
            ? Component.translatable("market.xero_delta.list")
            : Component.translatable("market.xero_delta.refresh");
        g.drawCenteredString(font, label, actionX + actionW / 2, actionY + 7,
            enabled ? 0xFF071214 : theme.muted());
    }

    private void drawHistoryActionButton(GuiGraphics graphics, int x, int buttonWidth,
                                         int mouseX, int mouseY, boolean enabled,
                                         int normalColor, int hoverColor, int textColor,
                                         Component label) {
        int y = actionY();
        boolean hovered = enabled && inside(mouseX, mouseY, x, y, buttonWidth, 20);
        graphics.fill(x, y, x + buttonWidth, y + 20,
            enabled ? (hovered ? hoverColor : normalColor) : 0xFF493536);
        if (hovered) graphics.renderOutline(x, y, buttonWidth, 20, theme.text());
        graphics.drawCenteredString(font, label, x + buttonWidth / 2, y + 7,
            enabled ? textColor : theme.muted());
    }

    private HistoryActionLayout historyActionLayout(int sidebar) {
        int gap = 6;
        int available = Math.max(4, width - sidebar - 30);
        int buttonWidth = Math.max(1, Math.min(96, (available - gap * 3) / 4));
        int totalWidth = buttonWidth * 4 + gap * 3;
        int detailX = Math.max(sidebar + 16, width - 14 - totalWidth);
        return new HistoryActionLayout(detailX, detailX + buttonWidth + gap,
            detailX + (buttonWidth + gap) * 2, detailX + (buttonWidth + gap) * 3, buttonWidth);
    }

    private void drawHistoryDeleteConfirm(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 550);
        graphics.fill(0, 0, width, height, 0x99000000);
        int panelWidth = Math.min(340, width - 40);
        int panelHeight = 126;
        int x = (width - panelWidth) / 2;
        int y = (height - panelHeight) / 2;
        graphics.fill(x, y, x + panelWidth, y + panelHeight, 0xF21A272D);
        graphics.renderOutline(x, y, panelWidth, panelHeight, theme.danger());
        String key = historyDeleteConfirm == HistoryDeleteConfirm.ALL
            ? "market.xero_delta.confirm_delete_all_history"
            : selectedHistoryRecords.size() > 1
            ? "market.xero_delta.confirm_delete_selected_history"
            : "market.xero_delta.confirm_delete_history";
        Component prompt = historyDeleteConfirm == HistoryDeleteConfirm.SELECTED
            && selectedHistoryRecords.size() > 1
            ? Component.translatable(key, selectedHistoryRecords.size())
            : Component.translatable(key);
        graphics.drawCenteredString(font, prompt, width / 2, y + 28, theme.text());
        int buttonWidth = 108;
        int buttonY = y + 78;
        int cancelX = width / 2 - buttonWidth - 8;
        int confirmX = width / 2 + 8;
        graphics.fill(cancelX, buttonY, cancelX + buttonWidth, buttonY + 24,
            inside(mouseX, mouseY, cancelX, buttonY, buttonWidth, 24) ? theme.panelAlt() : 0xFF40545B);
        graphics.drawCenteredString(font, Component.translatable("gui.cancel"),
            cancelX + buttonWidth / 2, buttonY + 8, theme.text());
        graphics.fill(confirmX, buttonY, confirmX + buttonWidth, buttonY + 24,
            inside(mouseX, mouseY, confirmX, buttonY, buttonWidth, 24) ? 0xFFD0524E : theme.danger());
        graphics.drawCenteredString(font, Component.translatable("market.xero_delta.confirm_delete"),
            confirmX + buttonWidth / 2, buttonY + 8, 0xFFFFF4F3);
        graphics.pose().popPose();
    }

    private void drawSellActions(GuiGraphics graphics, int mouseX, int mouseY) {
        int gap = 8;
        int buttonWidth = sellActionButtonWidth();
        int total = buttonWidth * 2 + gap;
        int x = width - total - 14;
        int y = actionY();
        boolean enabled = !selectedSource.isBlank();
        int listColor = enabled && action("open-listing-screen") ? theme.accent() : 0xFF39474B;
        int recycleColor = enabled && action("open-recycling") ? 0xFF4A5C62 : 0xFF39474B;
        if (enabled && inside(mouseX, mouseY, x, y, buttonWidth, 20)) listColor = 0xFF75E2C0;
        if (enabled && inside(mouseX, mouseY, x + buttonWidth + gap, y, buttonWidth, 20)) recycleColor = 0xFF60757C;
        graphics.fill(x, y, x + buttonWidth, y + 20, listColor);
        graphics.drawCenteredString(font, Component.translatable("market.xero_delta.open_listing"),
            x + buttonWidth / 2, y + 7, enabled ? 0xFF071214 : theme.muted());
        int recycleX = x + buttonWidth + gap;
        graphics.fill(recycleX, y, recycleX + buttonWidth, y + 20, recycleColor);
        graphics.drawCenteredString(font, Component.translatable("market.xero_delta.open_recycling"),
            recycleX + buttonWidth / 2, y + 7, enabled ? theme.text() : theme.muted());
    }

    private Component buyActionLabel() {
        return Component.translatable("market.xero_delta.view_details");
    }

    private void openBuyDetail(TradingSyncPacket.ListingView listing) {
        buyDetailStack = listing.stack().copyWithCount(1);
        boolean ownListing = isOwnListing(listing);
        pinnedOwnListing = ownListing ? listing.id() : null;
        TradingSyncPacket.ListingView preferred = ownListing ? listing : preferredListing(listing.stack());
        selectedListing = preferred == null ? null : preferred.id();
        buyDetailMode = true;
        buyRequiresRefresh = false;
        buyRefreshRevision = -1L;
        buyAmount = 1;
        updateFieldVisibility();
        buyAmountSlider.active = !ownListing;
        int available = availableBuyAmount(buyDetailStack);
        buyAmountSlider.setRange(1, purchaseAmountLimit(buyDetailStack));
        buyAmountSlider.setIntValue(1);
        amountBox.setValue("1");
        positionBuyDetailSlider();
    }

    private void leaveBuyDetail() {
        buyDetailMode = false;
        selectedListing = null;
        buyDetailStack = ItemStack.EMPTY;
        buyAmount = 1;
        buyRequiresRefresh = false;
        buyRefreshRevision = -1L;
        pinnedOwnListing = null;
        updateFieldVisibility();
    }

    private void selectBuyOffer(TradingSyncPacket.ListingView listing) {
        selectedListing = listing.id();
        buyDetailStack = listing.stack().copyWithCount(1);
        int available = availableBuyAmount(listing.stack());
        int maximum = purchaseAmountLimit(listing.stack());
        buyAmount = Math.min(Math.max(1, buyAmount), maximum);
        buyAmountSlider.active = !buyRequiresRefresh;
        buyAmountSlider.setRange(1, maximum);
        buyAmountSlider.setIntValue(buyAmount);
        amountBox.setValue(String.valueOf(buyAmount));
        updateFieldVisibility();
    }

    private void refreshBuyDetailSelection() {
        if (pinnedOwnListing != null) {
            TradingSyncPacket.ListingView pinned = listing(pinnedOwnListing);
            if (pinned != null) {
                selectedListing = pinned.id();
                buyDetailStack = pinned.stack().copyWithCount(1);
                buyAmount = 1;
                buyAmountSlider.setRange(1, purchaseAmountLimit(buyDetailStack));
                buyAmountSlider.setIntValue(1);
                buyAmountSlider.active = false;
                amountBox.setValue("1");
                updateFieldVisibility();
                return;
            }
            pinnedOwnListing = null;
        }
        TradingSyncPacket.ListingView lowest = preferredListing(buyDetailStack);
        if (lowest != null) {
            selectBuyOffer(lowest);
            return;
        }
        selectedListing = null;
        buyAmount = 1;
        buyAmountSlider.setRange(1, purchaseAmountLimit(buyDetailStack));
        buyAmountSlider.setIntValue(1);
        buyAmountSlider.active = false;
        updateFieldVisibility();
    }

    private int buyPanelWidth() {
        return Math.min(920, Math.max(240, width - 72));
    }

    private int buyPanelHeight() {
        return Math.min(460, Math.max(240, height - 92));
    }

    private static int buyPanelRightWidth(int panelWidth) {
        return Math.max(210, panelWidth * 30 / 100);
    }

    private void positionBuyDetailSlider() {
        int panelWidth = buyPanelWidth();
        int panelHeight = buyPanelHeight();
        int panelX = (width - panelWidth) / 2;
        int panelY = (height - panelHeight) / 2 + 12;
        int rightWidth = buyPanelRightWidth(panelWidth);
        int x = panelX + panelWidth - rightWidth + 18;
        int y = panelY + 18;
        int areaWidth = rightWidth - 36;
        int areaHeight = panelHeight - 36;
        BuyPanelLayout layout = buyPanelLayout(areaHeight);
        buyAmountSlider.setX(x + 26);
        buyAmountSlider.setY(y + layout.sliderY());
        buyAmountSlider.setWidth(Math.max(40, areaWidth - 52));
        int editorWidth = Math.max(52, Math.min(88, areaWidth - 48));
        amountBox.setX(x + (areaWidth - editorWidth) / 2);
        amountBox.setY(y + layout.editorY());
        amountBox.setWidth(editorWidth);
    }

    private void drawBuyDetail(GuiGraphics graphics, int mouseX, int mouseY) {
        TradingSyncPacket.ListingView listing = selectedListing();
        ItemStack stack = listing == null ? buyDetailStack : listing.stack();
        if (stack.isEmpty()) return;
        int panelWidth = buyPanelWidth();
        int panelHeight = buyPanelHeight();
        int panelX = (width - panelWidth) / 2;
        int panelY = (height - panelHeight) / 2 + 12;
        int rightWidth = buyPanelRightWidth(panelWidth);
        int divider = panelX + panelWidth - rightWidth;
        graphics.fill(0, 0, width, height, theme.background());
        graphics.fill(0, 0, width, 42, 0xF2081014);
        graphics.fill(0, 40, width, 42, theme.accent());
        graphics.drawString(font, Component.translatable("market.xero_delta.purchase_details"),
            18, 16, theme.text(), false);
        int balanceWidth = TradingUi.balanceWidth(font, state.balance());
        TradingUi.drawBalance(graphics, font, state.balance(), width - balanceWidth - 88, 16, 0xFFFFD36A);
        graphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, theme.panel());
        graphics.fill(divider, panelY, divider + 1, panelY + panelHeight, theme.border());
        drawBuyPriceChart(graphics, stack, listing, panelX + 18, panelY + 18,
            divider - panelX - 36, panelHeight - 36);
        drawBuyItemPanel(graphics, stack, listing, divider + 18, panelY + 18,
            rightWidth - 36, panelHeight - 36, mouseX, mouseY);
        drawBuyInfoPopup(graphics, stack, listing, panelX, panelY, divider, mouseX, mouseY);
    }

    private void drawBuyPriceChart(GuiGraphics graphics, ItemStack stack,
                                   TradingSyncPacket.ListingView listing,
                                   int x, int y, int areaWidth, int areaHeight) {
        List<PricePoint> points = visiblePricePoints(pricePoints(stack));
        Component lowestLabel = Component.translatable("market.xero_delta.lowest_listing");
        graphics.drawString(font, lowestLabel, x, y, theme.text(), false);
        drawBuyChartLegend(graphics, x, y, areaWidth);
        if (points.isEmpty()) {
            graphics.drawCenteredString(font,
                Component.translatable("market.xero_delta.no_item_listings"),
                x + areaWidth / 2, y + areaHeight / 2, theme.muted());
            return;
        }
        TradingUi.drawDetailedAmount(graphics, font, points.getFirst().unitPrice(), x + font.width(lowestLabel) + 8,
            y - 1, theme.text());
        graphics.fill(x, y + 20, x + areaWidth, y + 21, theme.border());
        int chartTop = y + 52;
        int chartBottom = y + areaHeight - 38;
        int gap = Math.max(4, areaWidth / 64);
        int barWidth = Math.max(12,
            (areaWidth - gap * (PRICE_CHART_COLUMNS + 1)) / PRICE_CHART_COLUMNS);
        int maxAmount = points.stream().mapToInt(PricePoint::amount).max().orElse(1);
        long selectedUnitPrice = listing == null ? -1L : unitPrice(listing);
        for (int i = 0; i < PRICE_CHART_COLUMNS; i++) {
            int barX = x + gap + i * (barWidth + gap);
            graphics.fill(barX, chartTop, barX + barWidth, chartBottom, 0x161B282D);
            if (i >= points.size()) continue;
            PricePoint point = points.get(i);
            int chartHeight = Math.max(1, chartBottom - chartTop);
            int listingHeight = Math.max(12,
                chartHeight * point.amount() / Math.max(1, maxAmount));
            boolean selected = point.unitPrice() == selectedUnitPrice;
            int purchaseAmount = selected ? Math.min(buyAmount, point.amount()) : 0;
            int amountHeight = purchaseAmount <= 0 ? 0
                : Math.max(1, chartHeight * purchaseAmount / Math.max(1, point.amount()));
            int listingY = chartBottom - listingHeight;
            int amountY = chartBottom - amountHeight;
            graphics.fill(barX, listingY, barX + barWidth, chartBottom, 0x553C4B51);
            int listingLabelY = Math.max(chartTop + 2, listingY - 11);
            graphics.drawCenteredString(font, TradingUi.format(point.amount()),
                barX + barWidth / 2, listingLabelY, theme.muted());
            int amountInset = Math.max(2, barWidth / 6);
            if (purchaseAmount > 0) {
                graphics.fill(barX + amountInset, amountY, barX + barWidth - amountInset, chartBottom,
                    theme.accent());
                graphics.drawCenteredString(font, TradingUi.format(purchaseAmount), barX + barWidth / 2,
                    Math.max(chartTop, amountY - 11), theme.accent());
            }
            graphics.drawCenteredString(font,
                font.plainSubstrByWidth(TradingUi.formatDetailed(point.unitPrice()), barWidth + gap),
                barX + barWidth / 2, chartBottom + 8, selected ? 0xFFFFD36A : theme.muted());
            if (selected) graphics.fill(barX - 1, chartBottom + 21, barX + barWidth + 1,
                chartBottom + 24, 0xFFFFD36A);
        }
    }

    private void drawBuyChartLegend(GuiGraphics graphics, int x, int y, int areaWidth) {
        Component amountLabel = Component.translatable("market.xero_delta.chart_purchase_amount");
        Component listingLabel = Component.translatable("market.xero_delta.chart_listing_count");
        int gap = 10;
        int amountWidth = 7 + font.width(amountLabel);
        int listingWidth = 7 + font.width(listingLabel);
        int legendX = x + areaWidth - amountWidth - listingWidth - gap;
        graphics.fill(legendX, y + 2, legendX + 5, y + 7, theme.accent());
        graphics.drawString(font, amountLabel, legendX + 8, y, theme.accent(), false);
        int listingX = legendX + amountWidth + gap;
        graphics.fill(listingX, y + 2, listingX + 5, y + 7, 0xFF526269);
        graphics.drawString(font, listingLabel, listingX + 8, y, theme.muted(), false);
    }

    private void drawBuyItemPanel(GuiGraphics graphics, ItemStack stack,
                                  TradingSyncPacket.ListingView listing,
                                  int x, int y, int areaWidth, int areaHeight,
                                  int mouseX, int mouseY) {
        String visibleName = font.plainSubstrByWidth(stack.getHoverName().getString(), areaWidth - 56);
        String id = itemId(stack);
        String visibleId = font.plainSubstrByWidth(id, areaWidth);
        BuyDetailCopyTarget copyTarget = buyDetailCopyTarget(stack, x, y, areaWidth, mouseX, mouseY);
        if (copyTarget != null) {
            graphics.fill(copyTarget.x(), copyTarget.y(),
                copyTarget.x() + copyTarget.width(), copyTarget.y() + copyTarget.height(), 0x2235D6B0);
            graphics.renderOutline(copyTarget.x(), copyTarget.y(),
                copyTarget.width(), copyTarget.height(), theme.accent());
        }
        graphics.drawString(font, visibleName, x, y, theme.text(), false);
        graphics.drawString(font, visibleId, x, y + 15, theme.muted(), false);
        boolean favorite = state.favorites().contains(favoriteKey(stack));
        int refreshX = x + areaWidth - 48;
        graphics.fill(refreshX, y - 2, refreshX + 22, y + 20,
            inside(mouseX, mouseY, refreshX, y - 2, 22, 22) ? theme.panelAlt() : 0x44000000);
        drawRefreshIcon(graphics, refreshX + 11, y + 9,
            buyRequiresRefresh ? theme.accent() : theme.muted());
        long cooldown = buyRefreshCooldownUntil - System.currentTimeMillis();
        if (cooldown > 0L) {
            int seconds = Math.max(1, (int) Math.ceil(cooldown / 1000.0D));
            graphics.drawString(font, Component.translatable("market.xero_delta.refresh_countdown", seconds),
                Math.max(x, refreshX - 64), y + 23, 0xFFFF5B58, false);
        }
        graphics.fill(x + areaWidth - 22, y - 2, x + areaWidth, y + 20,
            inside(mouseX, mouseY, x + areaWidth - 22, y - 2, 22, 22) ? theme.panelAlt() : 0x44000000);
        drawActionIcon(graphics,
            favorite ? FAVORITE_SELECTED_ICON : FAVORITE_UNSELECTED_ICON,
            x + areaWidth - 17, y + 2, 12,
            favorite ? 0xFFFFD36A : theme.muted());
        BuyPanelLayout layout = buyPanelLayout(areaHeight);
        graphics.pose().pushPose();
        graphics.pose().translate(x + areaWidth / 2 - layout.itemScale() * 8,
            y + layout.itemY(), 80);
        graphics.pose().scale(layout.itemScale(), layout.itemScale(), 1.0F);
        renderNeutralItem(graphics, stack, 0, 0);
        graphics.pose().popPose();
        int infoY = y + layout.sellerY();
        int infoGap = 6;
        int infoWidth = (areaWidth - infoGap) / 2;
        drawBuyInfoButton(graphics, x, infoY, infoWidth,
            Component.translatable("market.xero_delta.source"), BuyInfoIcon.SEARCH,
            buyInfoPopup == BuyInfoPopup.SOURCE, mouseX, mouseY);
        drawBuyInfoButton(graphics, x + infoWidth + infoGap, infoY, areaWidth - infoWidth - infoGap,
            Component.translatable("market.xero_delta.uses"), BuyInfoIcon.HELP,
            buyInfoPopup == BuyInfoPopup.USES, mouseX, mouseY);
        int dividerY = layout.sellerY() + 24;
        graphics.fill(x, y + dividerY, x + areaWidth, y + dividerY + 1, theme.border());
        int sliderY = y + layout.sliderY();
        boolean ownListing = isOwnListing(listing);
        if (listing != null && !ownListing) {
            graphics.drawCenteredString(font,
                Component.translatable("market.xero_delta.purchase_amount", buyAmount,
                    purchaseAmountLimit(stack)),
                x + areaWidth / 2, y + layout.amountLabelY(), theme.muted());
            drawAmountStepButton(graphics, x, sliderY - 1, "-", !buyRequiresRefresh && buyAmount > 1, mouseX, mouseY);
            drawAmountStepButton(graphics, x + areaWidth - 20, sliderY - 1, "+",
                !buyRequiresRefresh && buyAmount < purchaseAmountLimit(stack), mouseX, mouseY);
            long total = lowestPurchasePrice(stack, buyAmount);
            Component unit = Component.translatable("market.xero_delta.unit_price",
                TradingUi.formatDetailed(listing == null ? 0L : unitPrice(listing)));
            graphics.drawCenteredString(font, unit, x + areaWidth / 2,
                y + layout.unitPriceY(), theme.muted());
            TradingUi.drawDetailedAmount(graphics, font, total,
                x + (areaWidth - TradingUi.detailedAmountWidth(font, total)) / 2,
                y + layout.totalPriceY(), 0xFF55D6B0);
        }
        int buttonY = y + layout.buttonY();
        long purchasePrice = lowestPurchasePrice(stack, buyAmount);
        boolean enabled = listing != null && !ownListing && !buyRequiresRefresh && !listing.expired()
            && buyAmount > 0 && purchasePrice > 0L && state.balance() >= purchasePrice;
        int buttonColor = enabled ? theme.accent() : 0xFF39474B;
        graphics.fill(x, buttonY, x + areaWidth, buttonY + 24,
            enabled && inside(mouseX, mouseY, x, buttonY, areaWidth, 24) ? 0xFF75E2C0 : buttonColor);
        Component actionLabel = ownListing
            ? Component.translatable("market.xero_delta.error.own_listing")
            : buyRequiresRefresh
            ? Component.translatable("market.xero_delta.refresh_lowest_price")
            : listing == null
            ? Component.translatable("market.xero_delta.no_item_listings")
            : Component.translatable("market.xero_delta.buy");
        int textColor = enabled ? 0xFF071214 : theme.muted();
        if (listing != null && !ownListing) {
            long buttonPrice = purchasePrice;
            int combinedWidth = font.width(actionLabel) + 6 + TradingUi.detailedAmountWidth(font, buttonPrice);
            int startX = x + (areaWidth - combinedWidth) / 2;
            graphics.drawString(font, actionLabel, startX, buttonY + 8, textColor, false);
            TradingUi.drawDetailedAmount(graphics, font, buttonPrice, startX + font.width(actionLabel) + 6,
                buttonY + 7, textColor);
        } else {
            graphics.drawCenteredString(font, actionLabel, x + areaWidth / 2, buttonY + 8, textColor);
        }
        int previewHeight = layout.itemScale() * 16 + 12;
        hoveredStack = inside(mouseX, mouseY, x, y + layout.itemY() - 6,
            areaWidth, previewHeight) ? stack : hoveredStack;
    }

    private void drawBuyInfoButton(GuiGraphics graphics, int x, int y, int width, Component label,
                                   BuyInfoIcon icon, boolean selected, int mouseX, int mouseY) {
        boolean hovered = inside(mouseX, mouseY, x, y, width, 20);
        graphics.fill(x, y, x + width, y + 20,
            selected ? theme.panelAlt() : hovered ? 0xFF40545B : 0x55000000);
        graphics.renderOutline(x, y, width, 20, selected ? theme.accent() : theme.border());
        int color = selected || hovered ? theme.text() : theme.muted();
        drawBuyInfoIconLabel(graphics, x, y, width, 20, label, icon, color);
    }

    private void drawBuyInfoPopup(GuiGraphics graphics, ItemStack stack,
                                  TradingSyncPacket.ListingView listing,
                                  int panelX, int panelY, int divider,
                                  int mouseX, int mouseY) {
        if (buyInfoPopup == BuyInfoPopup.NONE) return;
        BuyInfoPopupLayout popup = buyInfoPopupLayout(panelX, panelY, divider);
        int popupWidth = popup.width();
        int popupHeight = popup.height();
        int x = popup.x();
        int y = popup.y();
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 450);
        graphics.fill(x, y, x + popupWidth, y + popupHeight, 0xFA101A1F);
        graphics.renderOutline(x, y, popupWidth, popupHeight, theme.border());
        Component title = Component.translatable(buyInfoPopup == BuyInfoPopup.SOURCE
            ? "market.xero_delta.source" : "market.xero_delta.uses");
        graphics.drawString(font, title, x + 10, y + 9, theme.text(), false);
        drawCloseIconButton(graphics, x + popupWidth - 26, y + 4, mouseX, mouseY);
        graphics.fill(x + 8, y + 27, x + popupWidth - 8, y + 28, theme.border());
        String viewer = recipeViewerName();
        String openViewerKey = buyInfoPopup == BuyInfoPopup.SOURCE
            ? "market.xero_delta.open_recipe_viewer"
            : "market.xero_delta.open_usage_viewer";
        drawBuyInfoActionButton(graphics, x + 8, y + 30, popupWidth - 16, 22,
            Component.translatable(openViewerKey, viewer),
            buyInfoPopup == BuyInfoPopup.SOURCE ? BuyInfoIcon.SEARCH : BuyInfoIcon.HELP,
            !viewer.isBlank(), mouseX, mouseY);
        if (buyInfoPopup == BuyInfoPopup.SOURCE) {
            boolean marketAvailable = preferredListing(stack) != null;
            drawBuyInfoActionButton(graphics, x + 8, y + 56, popupWidth - 16, 22,
                Component.translatable("market.xero_delta.source_market_purchase"),
                BuyInfoIcon.SEARCH, marketAvailable, mouseX, mouseY);
            String namespace = BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace();
            graphics.drawString(font, Component.translatable("market.xero_delta.source_namespace", namespace),
                x + 10, y + 85, theme.muted(), false);
            long recommended = recommendedPriceToday(stack);
            graphics.drawString(font, Component.translatable("market.xero_delta.recommended_price",
                TradingUi.formatDetailed(recommended)), x + 10, y + 104, theme.text(), false);
        } else {
            drawFtbQuestUsageList(graphics, stack, popup, mouseX, mouseY);
        }
        graphics.pose().popPose();
    }

    private void drawBuyInfoActionButton(GuiGraphics graphics, int x, int y, int width, int height,
                                         Component label, BuyInfoIcon icon, boolean enabled,
                                         int mouseX, int mouseY) {
        boolean hovered = enabled && inside(mouseX, mouseY, x, y, width, height);
        graphics.fill(x, y, x + width, y + height,
            hovered ? 0xFF40545B : 0x55000000);
        graphics.renderOutline(x, y, width, height,
            hovered ? theme.accent() : theme.border());
        int color = enabled ? hovered ? theme.text() : theme.accent() : theme.muted();
        drawBuyInfoIconLabel(graphics, x, y, width, height, label, icon, color);
    }

    private void drawCloseIconButton(GuiGraphics graphics, int x, int y, int mouseX, int mouseY) {
        boolean hovered = inside(mouseX, mouseY, x, y, 20, 20);
        graphics.fill(x, y, x + 20, y + 20, hovered ? theme.panelAlt() : 0xFA101A1F);
        int color = hovered ? theme.text() : theme.muted();
        for (int offset = 0; offset < 2; offset++) {
            for (int index = 0; index < 8; index++) {
                graphics.fill(x + 6 + index, y + 6 + index + offset,
                    x + 7 + index, y + 7 + index + offset, color);
                graphics.fill(x + 13 - index, y + 6 + index + offset,
                    x + 14 - index, y + 7 + index + offset, color);
            }
        }
    }

    private void drawBuyInfoIconLabel(GuiGraphics graphics, int x, int y, int width, int height,
                                      Component label, BuyInfoIcon icon, int color) {
        String text = font.plainSubstrByWidth(label.getString(), Math.max(0, width - 24));
        int iconWidth = 9;
        int gap = 4;
        int totalWidth = iconWidth + gap + font.width(text);
        int startX = x + Math.max(5, (width - totalWidth) / 2);
        int iconY = y + (height - 9) / 2;
        drawBuyInfoIcon(graphics, startX, iconY, icon, color);
        graphics.drawString(font, text, startX + iconWidth + gap,
            y + (height - 8) / 2, color, false);
    }

    private void drawBuyInfoIcon(GuiGraphics graphics, int x, int y,
                                 BuyInfoIcon icon, int color) {
        drawActionIcon(graphics,
            icon == BuyInfoIcon.HELP ? INFO_ICON : SOURCE_ICON,
            x, y, 9, color);
    }

    private static void drawActionIcon(GuiGraphics graphics, ResourceLocation texture,
                                       int x, int y, int size, int color) {
        graphics.setColor(((color >> 16) & 0xFF) / 255.0F,
            ((color >> 8) & 0xFF) / 255.0F, (color & 0xFF) / 255.0F,
            ((color >>> 24) & 0xFF) / 255.0F);
        graphics.blit(texture, x, y, size, size,
            0, 0, 16, 16, 16, 16);
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private void drawFtbQuestUsageList(GuiGraphics graphics, ItemStack stack,
                                       BuyInfoPopupLayout popup, int mouseX, int mouseY) {
        List<FtbQuestIntegration.Match> matches = buyInfoQuestMatches;
        int maxScroll = buyInfoMaxScroll(matches.size(), popup.height());
        buyInfoScroll = Mth.clamp(buyInfoScroll, 0, maxScroll);
        int listX = popup.x() + 8;
        int listY = popup.y() + BUY_INFO_TASK_LIST_TOP;
        int listRight = popup.x() + popup.width() - 16;
        int listBottom = popup.y() + popup.height() - BUY_INFO_TASK_LIST_BOTTOM_PADDING;
        if (matches.isEmpty()) {
            graphics.drawString(font,
                Component.translatable("market.xero_delta.quest_usage_unavailable"),
                listX + 2, listY + 5, theme.muted(), false);
            drawBuyInfoScrollbar(graphics, popup, 0);
            return;
        }

        uiViewport.enableScissor(graphics, listX, listY, listRight, listBottom);
        int stride = BUY_INFO_TASK_ROW_HEIGHT + BUY_INFO_TASK_ROW_GAP;
        for (int index = 0; index < matches.size(); index++) {
            int rowY = listY + index * stride - buyInfoScroll;
            if (rowY + BUY_INFO_TASK_ROW_HEIGHT <= listY || rowY >= listBottom) continue;
            boolean hovered = inside(mouseX, mouseY, listX, rowY,
                listRight - listX, BUY_INFO_TASK_ROW_HEIGHT)
                && inside(mouseX, mouseY, listX, listY, listRight - listX, listBottom - listY);
            graphics.fill(listX, rowY, listRight, rowY + BUY_INFO_TASK_ROW_HEIGHT,
                hovered ? 0xFF40545B : 0x55000000);
            graphics.renderOutline(listX, rowY, listRight - listX, BUY_INFO_TASK_ROW_HEIGHT,
                hovered ? theme.accent() : theme.border());
            FtbQuestIntegration.Match match = matches.get(index);
            String label = Component.translatable("market.xero_delta.quest_usage_available",
                match.questTitle(), match.taskTitle()).getString();
            graphics.drawString(font, font.plainSubstrByWidth(label, listRight - listX - 12),
                listX + 6, rowY + 10, hovered ? theme.text() : theme.accent(), false);
        }
        graphics.disableScissor();
        drawBuyInfoScrollbar(graphics, popup, maxScroll);
    }

    private void drawBuyInfoScrollbar(GuiGraphics graphics, BuyInfoPopupLayout popup, int maxScroll) {
        int trackX = popup.x() + popup.width() - 12;
        int trackY = popup.y() + BUY_INFO_TASK_LIST_TOP;
        int trackHeight = popup.height() - BUY_INFO_TASK_LIST_TOP - BUY_INFO_TASK_LIST_BOTTOM_PADDING;
        int thumbHeight = buyInfoScrollbarThumbHeight(trackHeight, maxScroll);
        int travel = trackHeight - thumbHeight;
        int thumbY = trackY + (maxScroll <= 0 ? 0
            : Math.round(buyInfoScroll * travel / (float) maxScroll));
        graphics.fill(trackX, trackY, trackX + 5, trackY + trackHeight, 0x77000000);
        graphics.fill(trackX, thumbY, trackX + 5, thumbY + thumbHeight,
            maxScroll > 0 ? theme.accent() : theme.border());
    }

    private int buyInfoMaxScroll(int taskCount, int popupHeight) {
        int visibleHeight = popupHeight - BUY_INFO_TASK_LIST_TOP - BUY_INFO_TASK_LIST_BOTTOM_PADDING;
        int contentHeight = taskCount <= 0 ? 0
            : taskCount * (BUY_INFO_TASK_ROW_HEIGHT + BUY_INFO_TASK_ROW_GAP) - BUY_INFO_TASK_ROW_GAP;
        return Math.max(0, contentHeight - visibleHeight);
    }

    private static int buyInfoScrollbarThumbHeight(int trackHeight, int maxScroll) {
        if (maxScroll <= 0) return trackHeight;
        return Math.max(18, Math.round(trackHeight * trackHeight / (float) (trackHeight + maxScroll)));
    }

    private BuyInfoPopupLayout buyInfoPopupLayout(int panelX, int panelY, int divider) {
        int popupWidth = Math.min(300, Math.max(220, divider - panelX - 36));
        int x = Math.max(panelX + 18, divider - popupWidth - 8);
        int y = panelY + 18;
        int popupHeight = buyInfoPopup == BuyInfoPopup.SOURCE
            ? 116 : Mth.clamp(height - y - 18, 116, BUY_INFO_USE_POPUP_HEIGHT);
        return new BuyInfoPopupLayout(x, y, popupWidth, popupHeight);
    }

    private BuyInfoPopupLayout buyInfoPopupLayout() {
        int panelWidth = buyPanelWidth();
        int panelHeight = buyPanelHeight();
        int panelX = (width - panelWidth) / 2;
        int panelY = (height - panelHeight) / 2 + 12;
        int divider = panelX + panelWidth - buyPanelRightWidth(panelWidth);
        return buyInfoPopupLayout(panelX, panelY, divider);
    }

    private void updateBuyInfoScrollFromMouse(double mouseY) {
        if (buyInfoPopup != BuyInfoPopup.USES) return;
        BuyInfoPopupLayout popup = buyInfoPopupLayout();
        int maxScroll = buyInfoMaxScroll(
            buyInfoQuestMatches.size(), popup.height());
        if (maxScroll <= 0) {
            buyInfoScroll = 0;
            return;
        }
        int trackY = popup.y() + BUY_INFO_TASK_LIST_TOP;
        int trackHeight = popup.height() - BUY_INFO_TASK_LIST_TOP - BUY_INFO_TASK_LIST_BOTTOM_PADDING;
        int thumbHeight = buyInfoScrollbarThumbHeight(trackHeight, maxScroll);
        int travel = trackHeight - thumbHeight;
        float position = (float) ((mouseY - trackY - thumbHeight / 2.0D) / Math.max(1, travel));
        buyInfoScroll = Math.round(Mth.clamp(position, 0.0F, 1.0F) * maxScroll);
    }

    private static String recipeViewerName() {
        if (ModList.get().isLoaded("roughlyenoughitems")) return "REI";
        if (ModList.get().isLoaded("jei")) return "JEI";
        if (ModList.get().isLoaded("emi")) return "EMI";
        return "";
    }

    private void drawAmountStepButton(GuiGraphics graphics, int x, int y, String label, boolean enabled,
                                      int mouseX, int mouseY) {
        int color = enabled ? (inside(mouseX, mouseY, x, y, 20, 20) ? theme.panelAlt() : 0xFF40545B)
            : 0xFF263338;
        graphics.fill(x, y, x + 20, y + 20, color);
        graphics.drawCenteredString(font, label, x + 10, y + 7, enabled ? theme.text() : theme.muted());
    }

    private static BuyPanelLayout buyPanelLayout(int areaHeight) {
        int buttonY = Math.max(24, areaHeight - 27);
        int totalPriceY = buttonY - 26;
        int unitPriceY = totalPriceY - 16;
        int sliderY = unitPriceY - 26;
        int editorY = sliderY - 25;
        int amountLabelY = editorY - 14;
        int itemScale = BUY_DETAIL_ITEM_SCALE;
        int itemY = Math.min(BUY_DETAIL_ITEM_PREFERRED_Y,
            Math.max(8, amountLabelY - itemScale * 16 - 26));
        int sellerY = itemY + itemScale * 16 + 6;
        return new BuyPanelLayout(itemScale, itemY, sellerY, sellerY + 13,
            amountLabelY, editorY, sliderY, unitPriceY, totalPriceY, buttonY);
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
        if (historyDeleteConfirm != HistoryDeleteConfirm.NONE) {
            if (TradingUi.backButtonClicked(mouseX, mouseY, width)) {
                historyDeleteConfirm = HistoryDeleteConfirm.NONE;
                return true;
            }
            return handleHistoryDeleteConfirmClick(mouseX, mouseY);
        }
        if (TradingUi.backButtonClicked(mouseX, mouseY, width)) {
            if (buyDetailMode) leaveBuyDetail();
            else if (historyDetailMode) closeHistoryDetail();
            else navigateBack();
            return true;
        }
        if (buyDetailMode) return buyDetailMouseClicked(mouseX, mouseY, button);
        if (historyDetailMode) return handleHistoryDetailClick(mouseX, mouseY, button);
        if (searchBox != null && searchBox.visible) {
            if (handleSearchHistoryClick(mouseX, mouseY, button)) return true;
            if (searchBox.isMouseOver(mouseX, mouseY)) {
                searchHistoryOpen = true;
                return super.mouseClicked(mouseX, mouseY, button);
            }
            if (searchHistoryOpen) {
                searchHistoryOpen = false;
                searchBox.setFocused(false);
            }
        }
        int sidebar = sidebarWidth();
        int tabX = tabStartX(sidebar);
        int tabW = tabWidth(sidebar);
        int tabIndex = 0;
        for (Tab value : configuredTabs()) {
            int x = tabX + tabIndex++ * (tabW + 4);
            if (inside(mouseX, mouseY, x, 8, tabW, 28)) {
                if (value == Tab.SELL) {
                    state.rememberCursor();
                    ModNetwork.sendToServer(TradingActionPacket.openOperator());
                    return true;
                }
                if (tab != value) startListAnimation();
                tab = value; scroll = 0; selectedListing = null; selectedSource = "";
                updateFieldVisibility();
                return true;
            }
        }
        int categoryY = categoryTop();
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
            double contentY = mouseY - categoryY + categoryScrollPixels;
            int index = (int) Math.floor(contentY / rowStride);
            double within = contentY - index * (double) rowStride;
            List<CategoryMenuRow> rows = categoryMenuRows();
            if (within < rowHeight && index >= 0 && index < rows.size()) {
                CategoryMenuRow row = rows.get(index);
                if (row.sectionHeader()) {
                    List<TradingCategory> children = sectionCategories(row.sectionId());
                    if (children.size() == 1) {
                        if (!row.sectionId().equals(selectedCreativeSection)
                            || category != children.getFirst()) startListAnimation();
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
                    if (category != row.category()
                        || !row.sectionId().equals(selectedCreativeSection)) startListAnimation();
                    selectedCreativeSection = row.sectionId();
                    category = row.category();
                    resetCatalogueSelection();
                }
                return true;
            }
        }
        int sidebarGroupY = categoryGroupY();
        int groupW = Math.max(38, (sidebar - 32) / 3);
        for (Group value : configuredGroups()) {
            if (inside(mouseX, mouseY, 12 + value.ordinal() * groupW, sidebarGroupY, groupW - 2, 22)) {
                if (group != value) startListAnimation();
                group = value;
                resetCatalogueSelection();
                return true;
            }
        }
        int contentX = sidebar + 16;
        if (isCreativePlayer() && tab == Tab.BUY
            && inside(mouseX, mouseY, contentX, 52, CREATIVE_LIST_BUTTON_WIDTH, 22)) {
            openCreativeListingPicker();
            return true;
        }
        int columnControlX = width - 14 - 82;
        Component columnLabel = Component.translatable("market.xero_delta.columns");
        int refreshX = Math.max(contentX + (isCreativePlayer() && tab == Tab.BUY
                ? CREATIVE_LIST_BUTTON_WIDTH + 8 : 0),
            columnControlX - font.width(columnLabel) - 8 - 32);
        if (action("refresh-market")
            && inside(mouseX, mouseY, refreshX, 52, 24, 22)) {
            ModNetwork.sendToServer(TradingActionPacket.refresh());
            return true;
        }
        if (action("decrease-columns") && inside(mouseX, mouseY, columnControlX, 52, 24, 22)
            && catalogueColumns > 1) {
            catalogueColumns--;
            TradingUiPreferences.setMarketColumns(catalogueColumns);
            scroll = 0;
            return true;
        }
        if (action("increase-columns") && inside(mouseX, mouseY, columnControlX + 58, 52, 24, 22)
            && catalogueColumns < 10) {
            catalogueColumns++;
            TradingUiPreferences.setMarketColumns(catalogueColumns);
            scroll = 0;
            return true;
        }
        if (tab == Tab.SELL && handleSellActionClick(mouseX, mouseY)) return true;
        if (tab == Tab.HISTORY && handleHistoryActionClick(mouseX, mouseY)) return true;
        if (tab != Tab.HISTORY && primaryActionDeclared()
            && inside(mouseX, mouseY, actionX(), actionY(), actionWidth(), 20)) {
            performAction();
            return true;
        }
        if (button == 0 && !touchReleaseClick && insideCatalogue(mouseX, mouseY)) {
            touchPressed = true;
            touchDragging = false;
            touchStartX = mouseX;
            touchStartY = mouseY;
            touchLastY = mouseY;
            return true;
        }
        int contentY = 82;
        int contentRight = width - 14;
        if (tab == Tab.HISTORY) {
            List<TradingSyncPacket.RecordView> values = filteredHistory();
            HistoryRecordHit hit = historyRecordAt(mouseX, mouseY, values,
                contentX, contentY, contentRight);
            if (hit != null) {
                handleHistoryRecordClick(historyDisplayOrder(values), hit.orderedIndex(), button);
                return true;
            }
            if (beginScrollbarDrag(mouseX, mouseY)) return true;
            return super.mouseClicked(mouseX, mouseY, button);
        }
        int cardW = cardWidth(contentX, contentRight);
        int index = cardIndexAt(mouseX, mouseY, contentX, contentY, cardW);
        if (index >= 0) {
            if (tab == Tab.BUY) {
                List<TradingSyncPacket.ListingView> values = filteredListings();
                if (index < values.size()) {
                    TradingSyncPacket.ListingView value = values.get(index);
                    if (button == 1 && isCreativePlayer()) {
                        openCreativeListingEditor(value);
                        return true;
                    }
                    int[] pos = cardPosition(index, contentX, contentY, cardW);
                    if (action("toggle-favorite")
                        && inside(mouseX, mouseY, pos[0] + cardW - 22, pos[1], 22, 24)) {
                        ModNetwork.sendToServer(TradingActionPacket.favorite(favoriteKey(value.stack())));
                    } else if (action("select-listing")) {
                        selectedListing = value.id();
                        rememberCurrentSearch();
                        openBuyDetail(value);
                    }
                    return true;
                }
            } else if (tab == Tab.SELL) {
                List<TradingSyncPacket.SourceView> values = filteredSources();
                if (index < values.size()) {
                    TradingSyncPacket.SourceView value = values.get(index);
                    if (!action("select-source")) return false;
                    selectedSource = value.id();
                    return true;
                }
            }
        }
        if (beginScrollbarDrag(mouseX, mouseY)) return true;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean insideCatalogue(double mouseX, double mouseY) {
        int left = sidebarWidth() + 16;
        int right = width - 22;
        return mouseX >= left && mouseX < right && mouseY >= 82 && mouseY < height - 46;
    }

    private boolean handleHistoryActionClick(double mouseX, double mouseY) {
        HistoryActionLayout layout = historyActionLayout(sidebarWidth());
        if (inside(mouseX, mouseY, layout.detailX(), actionY(), layout.buttonWidth(), 20)
            && selectedHistoryRecords.size() == 1) {
            openHistoryDetail();
            return true;
        }
        if (inside(mouseX, mouseY, layout.refreshX(), actionY(), layout.buttonWidth(), 20)) {
            ModNetwork.sendToServer(TradingActionPacket.refresh());
            return true;
        }
        if (inside(mouseX, mouseY, layout.deleteX(), actionY(), layout.buttonWidth(), 20)
            && !selectedHistoryRecords.isEmpty()) {
            historyDeleteConfirm = HistoryDeleteConfirm.SELECTED;
            return true;
        }
        if (inside(mouseX, mouseY, layout.deleteAllX(), actionY(), layout.buttonWidth(), 20)
            && !state.history().isEmpty()) {
            historyDeleteConfirm = HistoryDeleteConfirm.ALL;
            return true;
        }
        return false;
    }

    private boolean handleHistoryDeleteConfirmClick(double mouseX, double mouseY) {
        int panelWidth = Math.min(340, width - 40);
        int panelHeight = 126;
        int y = (height - panelHeight) / 2;
        int buttonWidth = 108;
        int buttonY = y + 78;
        int cancelX = width / 2 - buttonWidth - 8;
        int confirmX = width / 2 + 8;
        if (inside(mouseX, mouseY, cancelX, buttonY, buttonWidth, 24)) {
            historyDeleteConfirm = HistoryDeleteConfirm.NONE;
            return true;
        }
        if (!inside(mouseX, mouseY, confirmX, buttonY, buttonWidth, 24)) return true;
        if (historyDeleteConfirm == HistoryDeleteConfirm.ALL) {
            ModNetwork.sendToServer(TradingActionPacket.deleteAllHistory());
            clearHistorySelection();
        } else {
            for (TradingSyncPacket.RecordView record : selectedHistoryRecords()) {
                ModNetwork.sendToServer(TradingActionPacket.deleteHistory(
                    record.listingId(), record.completedEpochMillis()));
            }
            clearHistorySelection();
        }
        historyDeleteConfirm = HistoryDeleteConfirm.NONE;
        return true;
    }

    private boolean buyDetailMouseClicked(double mouseX, double mouseY, int button) {
        int panelWidth = buyPanelWidth();
        int panelHeight = buyPanelHeight();
        int panelX = (width - panelWidth) / 2;
        int panelY = (height - panelHeight) / 2 + 12;
        int rightWidth = buyPanelRightWidth(panelWidth);
        int x = panelX + panelWidth - rightWidth + 18;
        int y = panelY + 18;
        int areaWidth = rightWidth - 36;
        int areaHeight = panelHeight - 36;
        TradingSyncPacket.ListingView listing = selectedListing();
        ItemStack stack = listing == null ? buyDetailStack : listing.stack();
        BuyDetailCopyTarget copyTarget = buyDetailCopyTarget(stack, x, y, areaWidth, mouseX, mouseY);
        if (button == 0 && copyTarget != null && minecraft != null) {
            minecraft.keyboardHandler.setClipboard(copyTarget.value());
            toast = Component.translatable(copyTarget.itemId()
                ? "market.xero_delta.copied_item_id"
                : "market.xero_delta.copied_item_name").getString();
            toastSuccess = true;
            toastValue = 0L;
            toastUntil = System.currentTimeMillis() + 2_200L;
            return true;
        }
        if (inside(mouseX, mouseY, x + areaWidth - 48, y - 2, 22, 22)) {
            if (System.currentTimeMillis() < buyRefreshCooldownUntil) return true;
            buyRefreshCooldownUntil = System.currentTimeMillis() + 4_000L;
            buyRequiresRefresh = true;
            buyRefreshRevision = state.revision();
            updateFieldVisibility();
            ModNetwork.sendToServer(TradingActionPacket.refresh());
            return true;
        }
        if (inside(mouseX, mouseY, x + areaWidth - 22, y - 2, 22, 22)) {
            ModNetwork.sendToServer(TradingActionPacket.favorite(favoriteKey(buyDetailStack)));
            return true;
        }
        BuyPanelLayout layout = buyPanelLayout(areaHeight);
        int infoY = y + layout.sellerY();
        int infoGap = 6;
        int infoWidth = (areaWidth - infoGap) / 2;
        if (inside(mouseX, mouseY, x, infoY, infoWidth, 20)) {
            buyInfoPopup = buyInfoPopup == BuyInfoPopup.SOURCE ? BuyInfoPopup.NONE : BuyInfoPopup.SOURCE;
            buyInfoQuestMatches = List.of();
            buyInfoScroll = 0;
            draggingBuyInfoScrollbar = false;
            return true;
        }
        if (inside(mouseX, mouseY, x + infoWidth + infoGap, infoY,
            areaWidth - infoWidth - infoGap, 20)) {
            if (buyInfoPopup == BuyInfoPopup.USES) {
                buyInfoPopup = BuyInfoPopup.NONE;
                buyInfoQuestMatches = List.of();
            } else {
                buyInfoQuestMatches = FtbQuestIntegration.refreshMatches(buyDetailStack);
                buyInfoPopup = BuyInfoPopup.USES;
            }
            buyInfoScroll = 0;
            draggingBuyInfoScrollbar = false;
            return true;
        }
        if (buyInfoPopup != BuyInfoPopup.NONE) {
            BuyInfoPopupLayout popup = buyInfoPopupLayout();
            int popupWidth = popup.width();
            int popupX = popup.x();
            int popupY = popup.y();
            if (inside(mouseX, mouseY, popupX + popupWidth - 26, popupY, 26, 28)) {
                buyInfoPopup = BuyInfoPopup.NONE;
                draggingBuyInfoScrollbar = false;
                return true;
            }
            if (inside(mouseX, mouseY, popupX + 8, popupY + 30, popupWidth - 16, 22)) {
                String viewer = recipeViewerName();
                boolean opened = buyInfoPopup == BuyInfoPopup.SOURCE
                    ? RecipeViewerIntegration.openRecipes(buyDetailStack)
                    : RecipeViewerIntegration.openUses(buyDetailStack);
                if (opened) {
                    buyInfoPopup = BuyInfoPopup.NONE;
                    return true;
                }
                toast = Component.translatable(viewer.isBlank()
                    ? "market.xero_delta.recipe_viewer_missing"
                    : "market.xero_delta.recipe_viewer_open_failed", viewer).getString();
                toastSuccess = false;
                toastUntil = System.currentTimeMillis() + 3_000L;
                return true;
            }
            if (buyInfoPopup == BuyInfoPopup.SOURCE
                && inside(mouseX, mouseY, popupX + 8, popupY + 56, popupWidth - 16, 22)) {
                TradingSyncPacket.ListingView lowest = preferredListing(buyDetailStack);
                if (lowest != null) {
                    selectBuyOffer(lowest);
                    buyInfoPopup = BuyInfoPopup.NONE;
                }
                return true;
            }
            if (buyInfoPopup == BuyInfoPopup.USES) {
                int listY = popupY + BUY_INFO_TASK_LIST_TOP;
                int listBottom = popupY + popup.height() - BUY_INFO_TASK_LIST_BOTTOM_PADDING;
                if (inside(mouseX, mouseY, popupX + popupWidth - 16, listY, 16,
                    listBottom - listY)) {
                    draggingBuyInfoScrollbar = true;
                    updateBuyInfoScrollFromMouse(mouseY);
                    return true;
                }
                int listX = popupX + 8;
                int listRight = popupX + popupWidth - 16;
                if (inside(mouseX, mouseY, listX, listY, listRight - listX, listBottom - listY)) {
                    List<FtbQuestIntegration.Match> matches = buyInfoQuestMatches;
                    int relativeY = (int) mouseY - listY + buyInfoScroll;
                    int stride = BUY_INFO_TASK_ROW_HEIGHT + BUY_INFO_TASK_ROW_GAP;
                    int index = relativeY / stride;
                    if (index >= 0 && index < matches.size()
                        && relativeY % stride < BUY_INFO_TASK_ROW_HEIGHT) {
                        if (FtbQuestIntegration.open(matches.get(index))) {
                            buyInfoPopup = BuyInfoPopup.NONE;
                            return true;
                        }
                        toast = Component.translatable("market.xero_delta.quest_open_failed").getString();
                        toastSuccess = false;
                        toastUntil = System.currentTimeMillis() + 3_000L;
                    }
                    return true;
                }
            }
        }
        int sliderY = y + layout.sliderY();
        int maximum = purchaseAmountLimit(buyDetailStack);
        if (!buyRequiresRefresh && inside(mouseX, mouseY, x, sliderY - 1, 20, 20) && buyAmount > 1) {
            buyAmount--;
            amountBox.setValue(String.valueOf(buyAmount));
            buyAmountSlider.setIntValue(buyAmount);
            return true;
        }
        if (!buyRequiresRefresh && inside(mouseX, mouseY, x + areaWidth - 20, sliderY - 1, 20, 20)
            && buyAmount < maximum) {
            buyAmount++;
            amountBox.setValue(String.valueOf(buyAmount));
            buyAmountSlider.setIntValue(buyAmount);
            return true;
        }
        if (button == 0 && buyAmountSlider.visible && buyAmountSlider.active
            && buyAmountSlider.isMouseOver(mouseX, mouseY)) {
            draggingBuyAmountSlider = buyAmountSlider.mouseClicked(mouseX, mouseY, button);
            return draggingBuyAmountSlider;
        }
        int buttonY = y + layout.buttonY();
        if (listing != null && !isOwnListing(listing) && !buyRequiresRefresh && action("confirm-buy")
            && inside(mouseX, mouseY, x, buttonY, areaWidth, 24)) {
            if (!listing.expired()) {
                buyRequiresRefresh = true;
                buyRefreshRevision = state.revision();
                updateFieldVisibility();
                ModNetwork.sendToServer(TradingActionPacket.buy(listing.id(), buyAmount));
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) {
            if (historyDeleteConfirm != HistoryDeleteConfirm.NONE) {
                historyDeleteConfirm = HistoryDeleteConfirm.NONE;
                return true;
            }
            if (buyDetailMode) {
                leaveBuyDetail();
                return true;
            }
            if (historyDetailMode) {
                closeHistoryDetail();
                return true;
            }
            navigateBack();
            return true;
        }
        if ((keyCode == 257 || keyCode == 335) && searchBox != null && searchBox.visible
            && searchBox.isFocused()) {
            rememberCurrentSearch();
            searchHistoryOpen = false;
            searchBox.setFocused(false);
            return true;
        }
        if (minecraft != null && minecraft.options.keyInventory.matches(keyCode, scanCode)) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void performAction() {
        if (tab == Tab.BUY) {
            TradingSyncPacket.ListingView listing = selectedListing();
            if (listing == null || minecraft == null || minecraft.player == null) return;
            openBuyDetail(listing);
        } else if (tab == Tab.HISTORY && action("refresh-market")) {
            ModNetwork.sendToServer(TradingActionPacket.refresh());
        }
    }

    private boolean isCreativePlayer() {
        return minecraft != null && minecraft.player != null && minecraft.player.isCreative();
    }

    private void openCreativeListingPicker() {
        if (!isCreativePlayer()) return;
        minecraft.setScreen(MailItemPickerScreen.creativeListing(this,
            state.sources().stream().map(source -> source.stack().copy()).toList(), stacks -> {
                if (stacks == null || stacks.isEmpty()) return;
                state.queueCreativeDrafts(stacks);
                state.rememberTransition("market:buy");
                ModNetwork.sendToServer(TradingActionPacket.openOperator("creative:new"));
            }));
    }

    private void openCreativeListingEditor(TradingSyncPacket.ListingView listing) {
        if (!isCreativePlayer()) return;
        state.rememberTransition("market:buy");
        if (listing.virtualSupply()) {
            ModNetwork.sendToServer(TradingActionPacket.openOperator("creative:edit:" + listing.id()));
            return;
        }
        state.queueCreativeDrafts(List.of(listing.stack().copyWithCount(1)));
        ModNetwork.sendToServer(TradingActionPacket.openOperator("creative:new"));
    }

    private TradingSyncPacket.RecordView selectedHistoryRecord() {
        for (TradingSyncPacket.RecordView record : state.history()) {
            if (isSelectedHistoryRecord(record)) return record;
        }
        return null;
    }

    private void selectHistoryRecord(TradingSyncPacket.RecordView record) {
        selectedHistoryRecords.clear();
        HistoryRecordKey key = historyRecordKey(record);
        selectedHistoryRecords.add(key);
        historySelectionAnchor = key;
    }

    private boolean isSelectedHistoryRecord(TradingSyncPacket.RecordView record) {
        return selectedHistoryRecords.contains(historyRecordKey(record));
    }

    private void handleHistoryRecordClick(List<TradingSyncPacket.RecordView> values,
                                          int index, int button) {
        TradingSyncPacket.RecordView record = values.get(index);
        if (button == 1) {
            selectHistoryRecord(record);
            openHistoryDetail();
            return;
        }
        if (button != 0) return;
        HistoryRecordKey clicked = historyRecordKey(record);
        List<HistoryRecordKey> ordered = values.stream()
            .map(TradingMarketScreen::historyRecordKey).toList();
        historySelectionAnchor = TradingHistorySelection.select(selectedHistoryRecords,
            ordered, historySelectionAnchor, clicked, hasControlDown(), hasShiftDown());
    }

    private List<TradingSyncPacket.RecordView> selectedHistoryRecords() {
        List<TradingSyncPacket.RecordView> result = new ArrayList<>();
        for (TradingSyncPacket.RecordView record : state.history()) {
            if (selectedHistoryRecords.contains(historyRecordKey(record))) result.add(record);
        }
        return result;
    }

    private void pruneHistorySelection() {
        selectedHistoryRecords.removeIf(key -> state.history().stream()
            .noneMatch(record -> key.equals(historyRecordKey(record))));
        if (historySelectionAnchor != null && !selectedHistoryRecords.contains(historySelectionAnchor)) {
            historySelectionAnchor = selectedHistoryRecords.stream().findFirst().orElse(null);
        }
    }

    private void clearHistorySelection() {
        selectedHistoryRecords.clear();
        historySelectionAnchor = null;
    }

    private static HistoryRecordKey historyRecordKey(TradingSyncPacket.RecordView record) {
        return new HistoryRecordKey(record.listingId(), record.completedEpochMillis());
    }

    private void openHistoryDetail() {
        if (selectedHistoryRecord() == null) return;
        historyDetailMode = true;
        updateFieldVisibility();
    }

    private void closeHistoryDetail() {
        historyDetailMode = false;
        updateFieldVisibility();
    }

    private void drawHistoryDetail(GuiGraphics graphics, int mouseX, int mouseY) {
        TradingSyncPacket.RecordView record = selectedHistoryRecord();
        if (record == null) {
            closeHistoryDetail();
            return;
        }
        graphics.fill(0, 0, width, height, theme.background());
        HistoryDetailLayout layout = historyDetailLayout();
        int panelWidth = layout.panelWidth();
        int panelHeight = layout.panelHeight();
        int x = layout.panelX();
        int y = layout.panelY();
        graphics.fill(x, y, x + panelWidth, y + panelHeight, theme.panel());
        graphics.renderOutline(x, y, panelWidth, panelHeight, theme.accent());
        HistoryCopyTarget hoveredCopy = historyCopyTarget(record, mouseX, mouseY);
        if (hoveredCopy != null) {
            graphics.fill(hoveredCopy.x(), hoveredCopy.y(),
                hoveredCopy.x() + hoveredCopy.width(), hoveredCopy.y() + hoveredCopy.height(), 0x2235D6B0);
            graphics.renderOutline(hoveredCopy.x(), hoveredCopy.y(),
                hoveredCopy.width(), hoveredCopy.height(), theme.accent());
        }
        graphics.drawString(font, Component.translatable("market.xero_delta.history_detail_title"),
            x + 20, y + 18, theme.text(), false);
        graphics.pose().pushPose();
        graphics.pose().translate(x + 28, y + 64, 80);
        graphics.pose().scale(3.0F, 3.0F, 1.0F);
        renderNeutralItem(graphics, record.stack(), 0, 0);
        graphics.pose().popPose();
        int textX = layout.textX();
        int textY = layout.textY();
        Component[] lines = historyDetailLines(record);
        for (Component line : lines) {
            graphics.drawString(font, line, textX, textY, theme.muted(), false);
            textY += 20;
        }
        TradingUi.drawDetailedAmount(graphics, font, record.price(), textX, textY + 4, 0xFFFFD36A);
        hoveredStack = inside(mouseX, mouseY, x + 20, y + 50, 80, 80) ? record.stack() : hoveredStack;
    }

    private boolean handleHistoryDetailClick(double mouseX, double mouseY, int button) {
        if (button != 0) return true;
        TradingSyncPacket.RecordView record = selectedHistoryRecord();
        if (record == null || minecraft == null) return true;
        HistoryCopyTarget target = historyCopyTarget(record, mouseX, mouseY);
        if (target == null) return true;
        minecraft.keyboardHandler.setClipboard(target.value());
        toast = Component.translatable(target.orderId()
            ? "market.xero_delta.copied_id"
            : "market.xero_delta.copied_value").getString();
        toastSuccess = true;
        toastValue = 0L;
        toastUntil = System.currentTimeMillis() + 2200L;
        return true;
    }

    private BuyDetailCopyTarget buyDetailCopyTarget(ItemStack stack, int x, int y, int areaWidth,
                                                     double mouseX, double mouseY) {
        if (stack == null || stack.isEmpty()) return null;
        String name = stack.getHoverName().getString();
        String visibleName = font.plainSubstrByWidth(name, Math.max(1, areaWidth - 56));
        int nameWidth = Math.max(1, font.width(visibleName));
        if (inside(mouseX, mouseY, x - 2, y - 2, nameWidth + 4, 12)) {
            return new BuyDetailCopyTarget(x - 2, y - 2, nameWidth + 4, 12, name, false);
        }
        String id = itemId(stack);
        String visibleId = font.plainSubstrByWidth(id, Math.max(1, areaWidth));
        int idWidth = Math.max(1, font.width(visibleId));
        if (inside(mouseX, mouseY, x - 2, y + 13, idWidth + 4, 12)) {
            return new BuyDetailCopyTarget(x - 2, y + 13, idWidth + 4, 12, id, true);
        }
        return null;
    }

    private HistoryCopyTarget historyCopyTarget(TradingSyncPacket.RecordView record,
                                                double mouseX, double mouseY) {
        HistoryDetailLayout layout = historyDetailLayout();
        int iconX = layout.panelX() + 20;
        int iconY = layout.panelY() + 50;
        if (inside(mouseX, mouseY, iconX, iconY, 80, 80)) {
            return new HistoryCopyTarget(iconX, iconY, 80, 80,
                BuiltInRegistries.ITEM.getKey(record.stack().getItem()).toString(), false);
        }
        Component[] lines = historyDetailLines(record);
        String completed = historyCompletedText(record);
        String[] values = new String[]{
            record.stack().getHoverName().getString(),
            record.publicId().isBlank() ? "-" : record.publicId(),
            record.sellerName(),
            record.buyerName(),
            completed,
            String.valueOf(record.stack().getCount())
        };
        for (int index = 0; index < lines.length; index++) {
            int lineY = layout.textY() + index * 20;
            int fieldWidth = Math.max(16, font.width(lines[index])) + 8;
            int fieldX = layout.textX() - 4;
            int fieldY = lineY - 3;
            if (inside(mouseX, mouseY, fieldX, fieldY, fieldWidth, 17)) {
                return new HistoryCopyTarget(fieldX, fieldY, fieldWidth, 17,
                    values[index], index == 1);
            }
        }
        int priceY = layout.textY() + lines.length * 20 + 4;
        int priceWidth = TradingUi.detailedAmountWidth(font, record.price()) + 8;
        int priceX = layout.textX() - 4;
        if (inside(mouseX, mouseY, priceX, priceY - 3, priceWidth, 18)) {
            return new HistoryCopyTarget(priceX, priceY - 3, priceWidth, 18,
                TradingUi.formatDetailed(record.price()), false);
        }
        return null;
    }

    private Component[] historyDetailLines(TradingSyncPacket.RecordView record) {
        UUID playerId = minecraft != null && minecraft.player != null
            ? minecraft.player.getUUID() : new UUID(0L, 0L);
        Component completedTime = Component.translatable(
            record.buyerId().equals(playerId)
                ? "market.xero_delta.history_bought_time"
                : "market.xero_delta.history_sold_time",
            historyCompletedText(record));
        return new Component[]{
            record.stack().getHoverName(),
            Component.translatable("market.xero_delta.history_id",
                record.publicId().isBlank() ? "-" : record.publicId()),
            Component.translatable("market.xero_delta.history_seller", record.sellerName()),
            Component.translatable("market.xero_delta.history_buyer", record.buyerName()),
            completedTime,
            Component.translatable("market.xero_delta.history_amount", record.stack().getCount())
        };
    }

    private static String historyCompletedText(TradingSyncPacket.RecordView record) {
        return record.completedEpochMillis() <= 0L ? "-"
            : RECORD_TIME.format(Instant.ofEpochMilli(record.completedEpochMillis())
                .atZone(ZoneId.systemDefault()));
    }

    private HistoryDetailLayout historyDetailLayout() {
        int panelWidth = Math.min(620, width - 60);
        int panelHeight = Math.min(360, height - 70);
        int panelX = (width - panelWidth) / 2;
        int panelY = (height - panelHeight) / 2;
        return new HistoryDetailLayout(panelX, panelY, panelWidth, panelHeight,
            panelX + 110, panelY + 62);
    }

    private boolean handleSellActionClick(double mouseX, double mouseY) {
        if (selectedSource.isBlank()) return false;
        int gap = 8;
        int buttonWidth = sellActionButtonWidth();
        int x = width - buttonWidth * 2 - gap - 14;
        int y = actionY();
        if (action("open-listing-screen") && inside(mouseX, mouseY, x, y, buttonWidth, 20)) {
            state.rememberTransition("market:sell");
            ModNetwork.sendToServer(TradingActionPacket.openOperator(selectedSource));
            return true;
        }
        if (action("open-recycling")
            && inside(mouseX, mouseY, x + buttonWidth + gap, y, buttonWidth, 20)) {
            state.rememberTransition("market:sell");
            ModNetwork.sendToServer(TradingActionPacket.openRecycling(selectedSource));
            return true;
        }
        return false;
    }

    private int sellActionButtonWidth() {
        int available = Math.max(2, width - sidebarWidth() - 36 - 8);
        return Math.max(1, Math.min(124, available / 2));
    }

    private void navigateBack() {
        transition.beginClose(this::navigateBackNow);
    }

    private void navigateBackNow() {
        String previous = state.popPreviousScreen();
        state.rememberCursor();
        if (previous.startsWith("operator")) {
            ModNetwork.sendToServer(TradingActionPacket.openOperator());
        } else if (previous.startsWith("recycling")) {
            String source = previous.contains(":") ? previous.substring(previous.indexOf(':') + 1) : "";
            ModNetwork.sendToServer(TradingActionPacket.openRecycling(source));
        } else if (previous.startsWith("market:")) {
            ModNetwork.sendToServer(TradingActionPacket.openMarket(previous.substring(7)));
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
        if (!buyDetailMode && inside(mouseX, mouseY, 8, categoryTop(),
            sidebarWidth() - 16, categoryAreaBottom() - categoryTop())) {
            beginCategoryScrollAnimation(clamp(categoryScrollTarget
                - Math.signum(scrollY) * (categoryRowHeight() + categoryRowGap()) * 2.0D,
                0.0D, maxCategoryScroll()));
            return true;
        }
        if (buyDetailMode) {
            if (buyInfoPopup != BuyInfoPopup.NONE) {
                BuyInfoPopupLayout popup = buyInfoPopupLayout();
                if (inside(mouseX, mouseY, popup.x(), popup.y(), popup.width(), popup.height())) {
                    if (buyInfoPopup == BuyInfoPopup.USES) {
                        int maxScroll = buyInfoMaxScroll(
                            FtbQuestIntegration.findMatches(buyDetailStack).size(), popup.height());
                        buyInfoScroll = Mth.clamp(buyInfoScroll
                            - (int) Math.signum(scrollY) * (BUY_INFO_TASK_ROW_HEIGHT + BUY_INFO_TASK_ROW_GAP),
                            0, maxScroll);
                    }
                    return true;
                }
            }
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        int max = maxScroll();
        scroll = Math.max(0, Math.min(max, scroll - (int) Math.signum(scrollY) * 28));
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
        if (draggingBuyInfoScrollbar) {
            updateBuyInfoScrollFromMouse(mouseY);
            return true;
        }
        if (draggingBuyAmountSlider) {
            buyAmountSlider.mouseDragged(mouseX, mouseY, button, dragX, dragY);
            return true;
        }
        if (draggingScrollbar) {
            updateScrollFromMouse(mouseY);
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
        if (draggingBuyInfoScrollbar) {
            draggingBuyInfoScrollbar = false;
            return true;
        }
        if (draggingBuyAmountSlider) {
            draggingBuyAmountSlider = false;
            buyAmountSlider.mouseReleased(mouseX, mouseY, button);
            return true;
        }
        if (draggingScrollbar) {
            draggingScrollbar = false;
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

    private void ensureFilteredCache() {
        String query = searchBox == null ? "" : searchBox.getValue().trim().toLowerCase(Locale.ROOT);
        String key = state.revision() + "|" + tab + "|" + group + "|" + category + "|"
            + selectedCreativeSection + "|" + query;
        if (key.equals(filteredCacheKey)) return;
        filteredCacheKey = key;
        cachedListings = buildFilteredListings();
        cachedSources = buildFilteredSources();
        cachedHistory = buildFilteredHistory();
    }

    private List<TradingSyncPacket.ListingView> filteredListings() {
        ensureFilteredCache();
        return cachedListings;
    }

    private List<TradingSyncPacket.ListingView> buildFilteredListings() {
        List<TradingSyncPacket.ListingView> result = new ArrayList<>();
        Map<net.minecraft.world.item.Item, List<Integer>> itemBuckets = new LinkedHashMap<>();
        for (TradingSyncPacket.ListingView value : state.listings()) {
            if (!value.expired() && !isOwnListing(value)
                && matches(value.stack(), state.favorites().contains(favoriteKey(value.stack())))) {
                List<Integer> bucket = itemBuckets.computeIfAbsent(value.stack().getItem(), ignored -> new ArrayList<>());
                int matchingIndex = -1;
                for (int index : bucket) {
                    if (ItemStack.isSameItemSameComponents(result.get(index).stack(), value.stack())) {
                        matchingIndex = index;
                        break;
                    }
                }
                if (matchingIndex < 0) {
                    bucket.add(result.size());
                    result.add(value);
                } else if (shouldReplaceCatalogueListing(result.get(matchingIndex), value)) {
                    result.set(matchingIndex, value);
                }
            }
        }
        result.sort(Comparator.comparingLong(TradingMarketScreen::unitPrice));
        return List.copyOf(result);
    }

    private List<TradingSyncPacket.SourceView> filteredSources() {
        ensureFilteredCache();
        return cachedSources;
    }

    private List<TradingSyncPacket.SourceView> buildFilteredSources() {
        List<TradingSyncPacket.SourceView> result = new ArrayList<>();
        for (TradingSyncPacket.SourceView value : state.sources()) if (matches(value.stack(), false)) result.add(value);
        return List.copyOf(result);
    }

    private List<TradingSyncPacket.RecordView> filteredHistory() {
        ensureFilteredCache();
        return cachedHistory;
    }

    private List<TradingSyncPacket.RecordView> buildFilteredHistory() {
        List<TradingSyncPacket.RecordView> result = new ArrayList<>();
        String query = searchBox == null ? "" : searchBox.getValue().trim().toLowerCase(Locale.ROOT);
        for (TradingSyncPacket.RecordView value : state.history()) {
            boolean favorite = state.favorites().contains(favoriteKey(value.stack()));
            if (!matchesItemFilters(value.stack(), favorite)) continue;
            boolean purchase = isHistoryPurchase(value);
            String action = Component.translatable(purchase
                ? "market.xero_delta.history.buy_action" : "market.xero_delta.history.sell_action")
                .getString().toLowerCase(Locale.ROOT);
            if (query.isBlank() || matchesStackQuery(value.stack(), query)
                || historyDate(value).toLowerCase(Locale.ROOT).contains(query)
                || action.contains(query)
                || value.sellerName().toLowerCase(Locale.ROOT).contains(query)
                || value.buyerName().toLowerCase(Locale.ROOT).contains(query)
                || value.publicId().toLowerCase(Locale.ROOT).contains(query)) {
                result.add(value);
            }
        }
        return List.copyOf(result);
    }

    private List<TradingSyncPacket.RecordView> historyPurchases(
        List<TradingSyncPacket.RecordView> values) {
        return values.stream().filter(this::isHistoryPurchase).toList();
    }

    private List<TradingSyncPacket.RecordView> historySales(
        List<TradingSyncPacket.RecordView> values) {
        UUID playerId = minecraft != null && minecraft.player != null
            ? minecraft.player.getUUID() : new UUID(0L, 0L);
        return values.stream().filter(value -> value.sellerId().equals(playerId)
            && !value.buyerId().equals(playerId)).toList();
    }

    private boolean isHistoryPurchase(TradingSyncPacket.RecordView value) {
        UUID playerId = minecraft != null && minecraft.player != null
            ? minecraft.player.getUUID() : new UUID(0L, 0L);
        return value.buyerId().equals(playerId);
    }

    private List<TradingSyncPacket.RecordView> historyDisplayOrder(
        List<TradingSyncPacket.RecordView> values) {
        List<TradingSyncPacket.RecordView> purchases = historyPurchases(values);
        List<TradingSyncPacket.RecordView> sales = historySales(values);
        List<TradingSyncPacket.RecordView> result = new ArrayList<>(values.size());
        for (int row = 0; row < Math.max(purchases.size(), sales.size()); row++) {
            if (row < purchases.size()) result.add(purchases.get(row));
            if (row < sales.size()) result.add(sales.get(row));
        }
        return List.copyOf(result);
    }

    private HistoryRecordHit historyRecordAt(double mouseX, double mouseY,
                                             List<TradingSyncPacket.RecordView> values,
                                             int left, int top, int right) {
        int gap = 12;
        int columnWidth = Math.max(80, (right - left - gap - 4) / 2);
        int rightX = left + columnWidth + gap;
        int rowsTop = top + 20;
        if (mouseY < rowsTop || mouseY >= height - 42) return null;
        boolean purchaseColumn = mouseX >= left && mouseX < left + columnWidth;
        boolean saleColumn = mouseX >= rightX && mouseX < rightX + columnWidth;
        if (!purchaseColumn && !saleColumn) return null;
        int stride = marketCardHeight() + theme.cardGap();
        int localY = (int) mouseY - rowsTop + scroll;
        int row = localY / stride;
        if (row < 0 || localY % stride >= marketCardHeight()) return null;
        List<TradingSyncPacket.RecordView> column = purchaseColumn
            ? historyPurchases(values) : historySales(values);
        if (row >= column.size()) return null;
        TradingSyncPacket.RecordView record = column.get(row);
        List<TradingSyncPacket.RecordView> ordered = historyDisplayOrder(values);
        HistoryRecordKey key = historyRecordKey(record);
        for (int index = 0; index < ordered.size(); index++) {
            if (historyRecordKey(ordered.get(index)).equals(key)) return new HistoryRecordHit(index);
        }
        return null;
    }

    private void resetCatalogueSelection() {
        scroll = 0;
        if (tab == Tab.BUY) selectedListing = null;
        else if (tab == Tab.SELL) selectedSource = "";
        else {
            clearHistorySelection();
        }
    }

    private boolean matches(ItemStack stack, boolean favorite) {
        if (!matchesItemFilters(stack, favorite)) return false;
        String query = searchBox == null ? "" : searchBox.getValue().trim().toLowerCase(Locale.ROOT);
        return query.isBlank() || matchesStackQuery(stack, query);
    }

    private boolean matchesItemFilters(ItemStack stack, boolean favorite) {
        String id = itemId(stack);
        if (!TradingCreativeCategoryGroups.matches(selectedCreativeSection, stack)) return false;
        boolean categoryMatches = TradingItemCategory.matches(category, stack, favorite);
        if (!categoryMatches) return false;
        if (group == Group.VANILLA && !id.startsWith("minecraft:")) return false;
        if (group == Group.MODDED && id.startsWith("minecraft:")) return false;
        return true;
    }

    private static boolean matchesStackQuery(ItemStack stack, String query) {
        return itemId(stack).contains(query)
            || stack.getHoverName().getString().toLowerCase(Locale.ROOT).contains(query);
    }

    private int cardWidth(int left, int right) {
        int columns = cardColumns(left, right);
        int calculated = (right - left - theme.cardGap() * (columns - 1) - 8) / columns;
        return Math.max(48, calculated - CARD_WIDTH_SHRINK);
    }

    private int cardColumns(int left, int right) {
        int minimumCardWidth = tab == Tab.BUY ? 78 : 64;
        int availableColumns = Math.max(1,
            (right - left + theme.cardGap()) / (minimumCardWidth + theme.cardGap()));
        return Math.max(1, Math.min(catalogueColumns, availableColumns));
    }

    private int marketCardHeight() {
        return Math.max(1, theme.cardHeight());
    }

    private int[] cardPosition(int index, int left, int top, int cardWidth) {
        int columns = cardColumns(left, width - 14);
        int x = left + (index % columns) * (cardWidth + theme.cardGap());
        int y = top + (index / columns) * (marketCardHeight() + theme.cardGap()) - scroll;
        return new int[]{x, y};
    }

    private int cardIndexAt(double mouseX, double mouseY, int left, int top, int cardWidth) {
        if (mouseX < left || mouseY < top || mouseY >= height - 42) return -1;
        int columns = cardColumns(left, width - 14);
        int column = (int) (mouseX - left) / (cardWidth + theme.cardGap());
        int row = ((int) mouseY - top + scroll) / (marketCardHeight() + theme.cardGap());
        int localX = (int) (mouseX - left) % (cardWidth + theme.cardGap());
        int localY = ((int) mouseY - top + scroll) % (marketCardHeight() + theme.cardGap());
        if (column < 0 || column >= columns || localX >= cardWidth || localY >= marketCardHeight()) return -1;
        return row * columns + column;
    }

    private int maxScroll() {
        if (tab == Tab.HISTORY) {
            List<TradingSyncPacket.RecordView> values = filteredHistory();
            int rows = Math.max(historyPurchases(values).size(), historySales(values).size());
            int contentHeight = rows * (marketCardHeight() + theme.cardGap());
            return Math.max(0, contentHeight - (height - 144));
        }
        int sidebar = sidebarWidth();
        int left = sidebar + 16;
        int columns = cardColumns(left, width - 14);
        int count = tab == Tab.BUY ? filteredListings().size()
            : tab == Tab.SELL ? filteredSources().size() : filteredHistory().size();
        int rows = (count + columns - 1) / columns;
        int contentHeight = rows * (marketCardHeight() + theme.cardGap());
        return Math.max(0, contentHeight - (height - 124));
    }

    private void drawScrollbar(GuiGraphics g, int count, int left, int top, int right, int bottom) {
        int max = maxScroll();
        if (max <= 0 || count == 0) return;
        int trackX = right - 3;
        int trackH = bottom - top;
        int thumbH = Math.max(24, trackH * trackH / (trackH + max));
        int thumbY = top + (trackH - thumbH) * scroll / max;
        g.fill(trackX, top, trackX + 2, bottom, 0x55405258);
        g.fill(trackX, thumbY, trackX + 2, thumbY + thumbH, theme.accent());
    }

    private boolean beginScrollbarDrag(double mouseX, double mouseY) {
        int left = sidebarWidth() + 16;
        int top = 82;
        int right = width - 14;
        int bottom = height - 46;
        if (maxScroll() <= 0 || !inside(mouseX, mouseY, right - 7, top, 10, bottom - top)) return false;
        draggingScrollbar = true;
        updateScrollFromMouse(mouseY);
        return true;
    }

    private void updateScrollFromMouse(double mouseY) {
        int top = 82;
        int bottom = height - 46;
        int max = maxScroll();
        if (max <= 0) {
            scroll = 0;
            return;
        }
        int trackHeight = Math.max(1, bottom - top);
        scroll = Math.max(0, Math.min(max, (int) Math.round((mouseY - top) * max / trackHeight)));
    }

    private int sidebarWidth() {
        return Math.min(theme.sidebarWidth(), Math.max(132, Math.min(220, width / 3)));
    }

    private int tabStartX(int sidebar) {
        int tabCount = Math.max(1, configuredTabs().size());
        return Math.max(0, (width - tabCount * tabWidth(sidebar) - (tabCount - 1) * 4) / 2);
    }

    private int tabWidth(int sidebar) {
        return Math.max(54, Math.min(96, (width - 180) / 3));
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

    private int categoryVisibleRows() {
        return Math.max(1, (categoryAreaBottom() - categoryTop())
            / (categoryRowHeight() + categoryRowGap()));
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

    private int categoryGroupLabelY() {
        return height - 58;
    }

    private int categoryGroupY() {
        return height - 38;
    }

    private int actionWidth() {
        return Math.max(90, Math.min(140, width / 5));
    }

    private int actionX() {
        return width - actionWidth() - 14;
    }

    private int actionY() {
        return height - 30;
    }

    private void positionBottomFields() {
        if (priceBox == null || amountBox == null) return;
        int available = Math.max(130, actionX() - sidebarWidth() - 26);
        int amountWidth = Math.max(38, Math.min(52, available / 4));
        int priceWidth = Math.max(72, Math.min(126, available - amountWidth - 8));
        amountBox.setX(actionX() - priceWidth - amountWidth - 16);
        amountBox.setY(actionY());
        amountBox.setWidth(amountWidth);
        priceBox.setX(actionX() - priceWidth - 8);
        priceBox.setY(actionY());
        priceBox.setWidth(priceWidth);
    }

    private boolean primaryActionDeclared() {
        if (tab == Tab.SELL) return action("open-listing-screen") || action("open-recycling");
        if (tab == Tab.HISTORY) return action("refresh-market");
        return action("buy-listing");
    }

    private boolean primaryActionEnabled() {
        if (!primaryActionDeclared()) return false;
        if (tab == Tab.HISTORY) return true;
        if (tab == Tab.SELL) return !selectedSource.isBlank();
        return selectedListing() != null;
    }

    private boolean action(String id) {
        return document != null && document.actions().contains(id);
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
            return configured.stream()
                .map(value -> new CategoryMenuRow("", value, false))
                .toList();
        }
        List<CategoryMenuRow> rows = new ArrayList<>();
        for (TradingCategory value : configured) {
            if (value == TradingCategory.ALL || value == TradingCategory.FAVORITES) {
                rows.add(new CategoryMenuRow("", value, false));
            }
        }
        for (TradingHtmlThemeParser.CategorySection section : sections) {
            List<TradingCategory> children = sectionCategories(section).stream()
                .filter(configured::contains)
                .toList();
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

    private List<Tab> configuredTabs() {
        return configuredValues(document == null ? List.of() : document.tabs(), Tab.class, List.of(Tab.values()));
    }

    private List<TradingCategory> configuredCategories() {
        return configuredValues(document == null ? List.of() : document.categories(), TradingCategory.class,
            List.of(TradingCategory.values()));
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

    private TradingSyncPacket.ListingView selectedListing() {
        return selectedListing == null ? null : listing(selectedListing);
    }

    private TradingSyncPacket.ListingView listing(UUID id) {
        for (TradingSyncPacket.ListingView value : state.listings()) if (value.id().equals(id)) return value;
        return null;
    }

    private TradingSyncPacket.ListingView preferredListing(ItemStack stack) {
        TradingSyncPacket.ListingView lowestOther = null;
        for (TradingSyncPacket.ListingView value : state.listings()) {
            if (value.expired() || isOwnListing(value) || !sameMarketItem(stack, value.stack())) continue;
            if (isCheaperListing(value, lowestOther)) {
                lowestOther = value;
            }
        }
        return lowestOther;
    }

    private long lowestPurchasePrice(ItemStack stack, int amount) {
        if (stack == null || stack.isEmpty() || amount <= 0) return 0L;
        TradingSyncPacket.ListingView lowest = preferredListing(stack);
        if (lowest == null) return 0L;
        long lowestUnitPrice = unitPrice(lowest);
        List<TradingSyncPacket.ListingView> candidates = new ArrayList<>();
        for (TradingSyncPacket.ListingView listing : state.listings()) {
            if (!listing.expired() && !isOwnListing(listing) && sameMarketItem(stack, listing.stack())
                && unitPrice(listing) == lowestUnitPrice) candidates.add(listing);
        }
        candidates.sort(Comparator.comparingLong(TradingSyncPacket.ListingView::createdAt));
        int remaining = amount;
        long total = 0L;
        for (TradingSyncPacket.ListingView candidate : candidates) {
            if (remaining <= 0) break;
            int take = Math.min(remaining, candidate.stack().getCount());
            long part = proportionalPrice(candidate.price(), take, candidate.stack().getCount());
            total = Math.min(TradingRules.MAX_CURRENCY, total + part);
            remaining -= take;
        }
        return remaining == 0 ? total : 0L;
    }

    private boolean shouldReplaceCatalogueListing(TradingSyncPacket.ListingView current,
                                                   TradingSyncPacket.ListingView candidate) {
        return isCheaperListing(candidate, current);
    }

    private static boolean isCheaperListing(TradingSyncPacket.ListingView candidate,
                                            TradingSyncPacket.ListingView current) {
        return current == null || unitPrice(candidate) < unitPrice(current)
            || unitPrice(candidate) == unitPrice(current) && candidate.createdAt() < current.createdAt();
    }

    private boolean isOwnListing(TradingSyncPacket.ListingView listing) {
        return listing != null && minecraft != null && minecraft.player != null
            && listing.sellerId().equals(minecraft.player.getUUID());
    }

    private List<PricePoint> pricePoints(ItemStack stack) {
        List<TradingSyncPacket.ListingView> matches = new ArrayList<>();
        for (TradingSyncPacket.ListingView listing : state.listings()) {
            if (!listing.expired() && sameMarketItem(stack, listing.stack())
                && !isOwnListing(listing)) matches.add(listing);
        }
        matches.sort(Comparator.comparingLong(TradingMarketScreen::unitPrice));
        List<PricePoint> result = new ArrayList<>();
        for (TradingSyncPacket.ListingView listing : matches) {
            long price = unitPrice(listing);
            if (!result.isEmpty() && result.getLast().unitPrice() == price) {
                PricePoint previous = result.removeLast();
                result.add(new PricePoint(price,
                    Math.min(Integer.MAX_VALUE, previous.amount() + listing.stack().getCount()),
                    previous.listingCount() + 1,
                    previous.representative()));
            } else {
                result.add(new PricePoint(price, listing.stack().getCount(), 1, listing));
            }
        }
        return result;
    }

    private static List<PricePoint> visiblePricePoints(List<PricePoint> points) {
        if (points.size() <= 8) return points;
        List<PricePoint> result = new ArrayList<>(8);
        for (int i = 0; i < 8; i++) {
            int index = (int) Math.round(i * (points.size() - 1) / 7.0D);
            PricePoint point = points.get(index);
            if (result.isEmpty() || result.getLast().unitPrice() != point.unitPrice()) result.add(point);
        }
        return result;
    }

    private static boolean sameMarketItem(ItemStack first, ItemStack second) {
        return !first.isEmpty() && !second.isEmpty() && ItemStack.isSameItemSameComponents(first, second);
    }

    private long recommendedPriceToday(ItemStack stack) {
        long fallback = Math.max(1L, ClientDataCache.INSTANCE.getPrice(stack));
        if (minecraft == null || minecraft.level == null) return fallback;
        long gameTime = minecraft.level.getGameTime();
        long dayStart = gameTime - Math.floorMod(gameTime, 24_000L);
        long lowest = Long.MAX_VALUE;
        for (TradingSyncPacket.ListingView listing : state.listings()) {
            if (listing.expired() || listing.createdAt() < dayStart
                || !sameMarketItem(stack, listing.stack())) continue;
            lowest = Math.min(lowest, unitPrice(listing));
        }
        return lowest == Long.MAX_VALUE ? fallback : lowest;
    }

    private static long unitPrice(TradingSyncPacket.ListingView listing) {
        int count = Math.max(1, listing.stack().getCount());
        return Math.max(1L, (listing.price() + count - 1L) / count);
    }

    private static long proportionalPrice(long total, int amount, int totalAmount) {
        if (amount <= 0 || totalAmount <= 0 || total <= 0) return 0L;
        if (amount >= totalAmount) return total;
        long quotient = total / totalAmount;
        long remainder = total % totalAmount;
        return Math.min(total, quotient * amount + (remainder * amount + totalAmount - 1L) / totalAmount);
    }

    private static String itemId(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().toLowerCase(Locale.ROOT);
    }

    private static String favoriteKey(ItemStack stack) {
        return ModDataStorage.getKey(stack);
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

    private static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private static int parseInt(String value, int fallback) {
        try { return Math.max(1, Math.min(64, Integer.parseInt(value))); }
        catch (Exception ignored) { return fallback; }
    }

    private static long parseLong(String value, long fallback) {
        try { return Long.parseLong(value); }
        catch (Exception ignored) { return fallback; }
    }

    private record PricePoint(long unitPrice, int amount, int listingCount,
                              TradingSyncPacket.ListingView representative) {
    }

    private record BuyPanelLayout(int itemScale, int itemY, int sellerY, int publicIdY,
                                  int amountLabelY, int editorY, int sliderY, int unitPriceY,
                                  int totalPriceY, int buttonY) {
    }

    private record BuyInfoPopupLayout(int x, int y, int width, int height) {
    }

    private record BuyDetailCopyTarget(int x, int y, int width, int height,
                                       String value, boolean itemId) {
    }

    private record HistoryActionLayout(int detailX, int refreshX, int deleteX, int deleteAllX,
                                       int buttonWidth) {
    }

    private record HistoryDetailLayout(int panelX, int panelY, int panelWidth, int panelHeight,
                                       int textX, int textY) {
    }

    private record HistoryCopyTarget(int x, int y, int width, int height,
                                     String value, boolean orderId) {
    }

    private record HistoryRecordKey(UUID listingId, long completedEpochMillis) {
    }

    private record HistoryRecordHit(int orderedIndex) {
    }

    private record CategoryMenuRow(String sectionId, TradingCategory category, boolean sectionHeader) {
    }

    private enum BuyInfoPopup {
        NONE,
        SOURCE,
        USES
    }

    private static ResourceLocation icon(String path) {
        return ResourceLocation.fromNamespaceAndPath(
            com.xtdpotato.xero_delta.XeroDelta.MOD_ID, path);
    }

    private enum BuyInfoIcon {
        SEARCH,
        HELP
    }

    private enum HistoryDeleteConfirm {
        NONE,
        SELECTED,
        ALL
    }

    private final class MarketValueSlider extends AbstractSliderButton {
        private int minimum;
        private int maximum;
        private final IntConsumer consumer;

        private MarketValueSlider(int x, int y, int width, int height, int minimum, int maximum,
                                  int initial, IntConsumer consumer) {
            super(x, y, width, height, Component.empty(), fraction(initial, minimum, maximum));
            this.minimum = minimum;
            this.maximum = maximum;
            this.consumer = consumer;
            updateMessage();
        }

        private void setRange(int minimum, int maximum) {
            int current = intValue();
            this.minimum = minimum;
            this.maximum = Math.max(minimum, maximum);
            setIntValue(current);
        }

        private void setIntValue(int value) {
            this.value = fraction(value, minimum, maximum);
            updateMessage();
        }

        private int intValue() {
            return minimum + (int) Math.round(value * (maximum - minimum));
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.literal(String.valueOf(intValue())));
        }

        @Override
        protected void applyValue() {
            consumer.accept(intValue());
        }

        @Override
        public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            int centerY = getY() + getHeight() / 2;
            graphics.fill(getX(), centerY - 1, getX() + getWidth(), centerY + 2, 0xFF394A50);
            int divisions = Math.min(12, Math.max(0, maximum - minimum));
            for (int index = 0; index <= divisions && divisions > 0; index++) {
                int tickX = getX() + 4 + index * Math.max(1, getWidth() - 8) / divisions;
                graphics.fill(tickX, centerY - 3, tickX + 1, centerY + 4, 0xAA91A2A5);
            }
            int knobX = getX() + (int) Math.round(value * (getWidth() - 8));
            graphics.fill(getX(), centerY - 1, knobX + 4, centerY + 2,
                active ? theme.accent() : theme.muted());
            graphics.fill(knobX, centerY - 5, knobX + 8, centerY + 6,
                active ? (isHoveredOrFocused() ? 0xFF75E2C0 : theme.accent()) : 0xFF526168);
        }

        private static double fraction(int value, int minimum, int maximum) {
            if (maximum <= minimum) return 0.0D;
            return Mth.clamp((double) (value - minimum) / (double) (maximum - minimum), 0.0D, 1.0D);
        }
    }
}
