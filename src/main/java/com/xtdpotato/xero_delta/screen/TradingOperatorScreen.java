package com.xtdpotato.xero_delta.screen;

import com.xtdpotato.xero_delta.client.ClientDataCache;
import com.xtdpotato.xero_delta.client.CalendarDateSelection;
import com.xtdpotato.xero_delta.client.ChineseLunarCalendar;
import com.xtdpotato.xero_delta.client.FtbQuestIntegration;
import com.xtdpotato.xero_delta.client.ItemDetailOverlay;
import com.xtdpotato.xero_delta.client.TradingClientState;
import com.xtdpotato.xero_delta.client.ScreenTransition;
import com.xtdpotato.xero_delta.client.TradingUi;
import com.xtdpotato.xero_delta.client.TradingUiPreferences;
import com.xtdpotato.xero_delta.client.TradingUiScale;
import com.xtdpotato.xero_delta.data.ModDataStorage;
import com.xtdpotato.xero_delta.data.BoundItemPolicy;
import com.xtdpotato.xero_delta.network.ModNetwork;
import com.xtdpotato.xero_delta.network.CreativeListingPacket;
import com.xtdpotato.xero_delta.network.TradingActionPacket;
import com.xtdpotato.xero_delta.network.TradingSyncPacket;
import com.xtdpotato.xero_delta.trading.TradingCategory;
import com.xtdpotato.xero_delta.trading.TradingCreativeCategoryGroups;
import com.xtdpotato.xero_delta.trading.TradingHtmlThemeParser;
import com.xtdpotato.xero_delta.trading.TradingItemCategory;
import com.xtdpotato.xero_delta.trading.TradingMarketData;
import com.xtdpotato.xero_delta.trading.TradingOperatorHtmlTemplate;
import com.xtdpotato.xero_delta.trading.TradingOperatorMenu;
import com.xtdpotato.xero_delta.trading.TradingRules;
import com.xtdpotato.xero_delta.trading.TradingItemEligibility;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
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
import java.util.TreeMap;
import java.util.UUID;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.function.LongConsumer;

public final class TradingOperatorScreen extends AbstractContainerScreen<TradingOperatorMenu> {
    private static final int HEADER_HEIGHT = 44;
    private static final int ROW_HEIGHT = 42;
    private static final int ROW_GAP = 4;
    private static final int WORKSPACE_TOP = 108;
    private static final int OWNED_ROW_HEIGHT = 90;
    private static final int OWNED_ROW_GAP = 5;
    private static final int SOURCE_CELL_HEIGHT = 56;
    private static final int SOURCE_CELL_GAP = 4;
    private static final int SOURCE_GROUP_HEADER_HEIGHT = 24;
    private static final int SOURCE_GROUP_GAP = 8;
    private static final int DETAIL_CONTENT_HEIGHT = 282;
    private static final long MAX_PRICE = 99_999_999L;
    private static final int MAX_AMOUNT = 99_999;
    private static final int MAX_CREATIVE_AMOUNT = 4_096;
    private static final int CREATIVE_BATCH_MAX_COLUMNS = 5;
    private static final int CREATIVE_BATCH_CARD_HEIGHT = 86;
    private static final int CREATIVE_BATCH_CARD_GAP = 4;
    private static final int BATCH_EDITOR_CONTENT_HEIGHT = 246;

    private final TradingClientState state = TradingClientState.INSTANCE;
    private final List<Draft> drafts = new ArrayList<>();
    private TradingHtmlThemeParser.Theme theme;
    private TradingHtmlThemeParser.Document document;
    private EditBox sourceSearch;
    private EditBox draftSearch;
    private EditBox priceBox;
    private EditBox creativeBatchSearch;
    private ValueSlider amountSlider;
    private ValueSlider durationSlider;
    private long nextDraftId = 1L;
    private long selectedDraftId = -1L;
    private int sourceScroll;
    private int draftScroll;
    private long seenRevision = -1;
    private String toast = "";
    private boolean toastSuccess = true;
    private long toastUntil;
    private boolean syncingFields;
    private ItemStack hoveredStack = ItemStack.EMPTY;
    private boolean detailMode;
    private int draggingScrollbar;
    private boolean draggingAmountSlider;
    private boolean draggingDurationSlider;
    private double sourceScrollTarget;
    private double draftScrollTarget;
    private int detailScroll;
    private double detailScrollTarget;
    private int sourceColumns;
    private int ownedColumns;
    private boolean datePickerOpen;
    private final Set<LocalDate> dateFilters = new LinkedHashSet<>();
    private final Set<LocalDate> calendarSelectedDates = new LinkedHashSet<>();
    private YearMonth calendarMonth = YearMonth.now();
    private LocalDate calendarSelectionAnchor;
    private EditBox calendarYearBox;
    private EditBox calendarMonthBox;
    private CalendarEditTarget calendarEditTarget = CalendarEditTarget.NONE;
    private long calendarListingRevision = -1L;
    private Map<LocalDate, List<ItemStack>> calendarListingItems = Map.of();
    private long datePopupOpenedAt;
    private final ScreenTransition transition = new ScreenTransition(false);
    private boolean ownedMultiSelect;
    private final Set<UUID> selectedOwnedListings = new LinkedHashSet<>();
    private boolean batchRelistMode;
    private boolean creativeBatchMode;
    private boolean sourceCategoryOpen;
    private String expandedSourceCategorySection = "";
    private String selectedSourceCreativeSection = "";
    private TradingCategory sourceCategory = TradingCategory.ALL;
    private int sourceCategoryScroll;
    private boolean listingUnlockDialogOpen;
    private List<FtbQuestIntegration.UnlockMatch> listingUnlockMatches = List.of();
    private final Set<Long> selectedBatchDraftIds = new LinkedHashSet<>();
    private int batchRelistScroll;
    private double batchRelistScrollTarget;
    private int batchEditorScroll;
    private double batchEditorScrollTarget;
    private int creativeBatchColumns = 3;
    private long creativeBatchSelectionAnchor = -1L;
    private TradingUiScale.Viewport uiViewport = TradingUiScale.viewport(1, 1, 0);
    private boolean touchPressed;
    private boolean touchDragging;
    private boolean touchReleaseClick;
    private int touchList;
    private double touchStartX;
    private double touchStartY;
    private double touchLastY;
    private static final DateTimeFormatter DATE_FILTER_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter LISTED_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public TradingOperatorScreen(TradingOperatorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected void init() {
        String retainedSourceSearch = sourceSearch == null ? "" : sourceSearch.getValue();
        String retainedDraftSearch = draftSearch == null ? "" : draftSearch.getValue();
        uiViewport = TradingUiScale.viewport(width, height, TradingUiPreferences.tradingUiScaleLevel());
        width = uiViewport.logicalWidth();
        height = uiViewport.logicalHeight();
        imageWidth = width;
        imageHeight = height;
        super.init();
        leftPos = 0;
        topPos = 0;
        document = TradingOperatorHtmlTemplate.loadDocument();
        theme = document.theme();
        TradingCreativeCategoryGroups.refresh(minecraft);
        initializeSourceCategories();
        sourceColumns = TradingUiPreferences.operatorSourceColumns();
        ownedColumns = TradingUiPreferences.operatorOwnedColumns();
        int split = splitX();
        sourceSearch = addRenderableWidget(new Material3CompactEditBox(font, split + 14, 52, width - split - 130, 20,
            Component.translatable("trading_op.xero_delta.source_search")));
        sourceSearch.setHint(Component.translatable("trading_op.xero_delta.source_search"));
        sourceSearch.setMaxLength(128);
        sourceSearch.setValue(retainedSourceSearch);
        sourceSearch.setResponder(ignored -> {
            sourceScroll = 0;
            sourceScrollTarget = 0.0D;
        });
        draftSearch = addRenderableWidget(new Material3CompactEditBox(font, 14, 52, Math.max(80, split - 160), 20,
            Component.translatable("trading_op.xero_delta.owned_search")));
        draftSearch.setHint(Component.translatable("trading_op.xero_delta.owned_search"));
        draftSearch.setMaxLength(128);
        draftSearch.setValue(retainedDraftSearch);
        draftSearch.setResponder(ignored -> {
            draftScroll = 0;
            draftScrollTarget = 0.0D;
            selectedOwnedListings.clear();
        });

        priceBox = addRenderableWidget(new Material3CompactEditBox(font, 0, 0, 100, 20,
            Component.translatable("trading_op.xero_delta.price")));
        priceBox.setFilter(value -> value.isEmpty()
            || (creativeBatchMode ? value.matches("[+-]?\\d{0,10}") : value.matches("\\d{0,10}")));
        priceBox.setResponder(this::onPriceText);
        creativeBatchSearch = addRenderableWidget(new Material3CompactEditBox(font, 0, 0, 100, 20,
            Component.translatable("trading_op.xero_delta.creative_batch_search")));
        creativeBatchSearch.setHint(Component.translatable(
            "trading_op.xero_delta.creative_batch_search_hint"));
        creativeBatchSearch.setMaxLength(128);
        creativeBatchSearch.setResponder(ignored -> {
            batchRelistScroll = 0;
            batchRelistScrollTarget = 0.0D;
        });
        creativeBatchSearch.visible = false;
        amountSlider = addRenderableWidget(new ValueSlider(0, 0, 100, 20,
            1L, 1L, 1L, this::onAmountSlider));
        durationSlider = addRenderableWidget(new ValueSlider(0, 0, 100, 20,
            1L, TradingRules.DEFAULT_LISTING_DAYS, TradingRules.DEFAULT_LISTING_DAYS,
            this::onDurationSlider));
        CalendarHeaderLayout calendarHeader = calendarHeaderLayout(calendarLayout());
        calendarYearBox = addRenderableWidget(new Material3CompactEditBox(font, calendarHeader.yearX(), calendarHeader.y(),
            calendarHeader.yearWidth(), 20,
            Component.translatable("trading_op.xero_delta.calendar_year_input")));
        calendarYearBox.setFilter(value -> value.isEmpty() || value.matches("\\d{0,4}"));
        calendarYearBox.setMaxLength(4);
        calendarMonthBox = addRenderableWidget(new Material3CompactEditBox(font, calendarHeader.monthX(), calendarHeader.y(),
            calendarHeader.monthWidth(), 20,
            Component.translatable("trading_op.xero_delta.calendar_month_input")));
        calendarMonthBox.setFilter(value -> value.isEmpty() || value.matches("\\d{0,2}"));
        calendarMonthBox.setMaxLength(2);
        restoreCalendarEditorAfterInit();
        setEditorVisible(false);
        if (detailMode && selectedDraft() != null) {
            sourceSearch.visible = false;
            draftSearch.visible = false;
            setEditorVisible(true);
        }
        if (datePickerOpen) {
            sourceSearch.visible = false;
            draftSearch.visible = false;
        }
        ModNetwork.sendToServer(TradingActionPacket.refresh());
        state.restoreCursor();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (state.revision() != seenRevision) {
            seenRevision = state.revision();
            String pendingSource = state.consumePendingSourceId();
            if ("creative:new".equals(pendingSource)) {
                openCreativeDrafts(state.consumeCreativeDrafts());
            } else if (pendingSource.startsWith("creative:edit:")) {
                try {
                    UUID listingId = UUID.fromString(pendingSource.substring("creative:edit:".length()));
                    state.listings().stream().filter(listing -> listing.id().equals(listingId))
                        .findFirst().ifPresent(this::openCreativeEditDraft);
                } catch (IllegalArgumentException ignored) {
                    showLocalToast("market.xero_delta.error.stale", false);
                }
            } else if (!pendingSource.isBlank()) {
                Set<String> pendingSources = Set.of(
                    pendingSource.split(TradingActionPacket.SOURCE_LIST_SEPARATOR, -1));
                for (TradingSyncPacket.SourceView source : state.sources()) {
                    if (pendingSources.contains(source.id())) {
                        addDraft(source);
                    }
                }
            }
            if (!state.message().isBlank()) {
                toast = Component.translatable(state.message()).getString();
                toastSuccess = state.success();
                toastUntil = System.currentTimeMillis() + 3000L;
            }
            selectedOwnedListings.removeIf(id -> state.listings().stream()
                .noneMatch(listing -> listing.id().equals(id)));
        }
        updateSmoothScroll();
        hoveredStack = ItemStack.EMPTY;
        mouseX = uiViewport.mouseX(mouseX);
        mouseY = uiViewport.mouseY(mouseY);
        graphics.pose().pushPose();
        uiViewport.apply(graphics);
        transition.push(graphics);
        try {
            super.render(graphics, mouseX, mouseY, partialTick);
            if (!datePickerOpen) {
                int scaleLevel = TradingUiPreferences.tradingUiScaleLevel();
                TradingUiScale.drawControls(graphics, font, width, mouseX, mouseY,
                    scaleLevel > TradingUiScale.MIN_LEVEL, scaleLevel < TradingUiScale.MAX_LEVEL);
                TradingUi.drawBackButton(graphics, font, width, mouseX, mouseY);
            }
            if (detailMode) drawIncomeTooltipIfHovered(graphics, mouseX, mouseY);
            if (!hoveredStack.isEmpty() && !ItemDetailOverlay.isOpen(this)) {
                graphics.renderTooltip(font, hoveredStack, mouseX, mouseY);
            }
            ItemDetailOverlay.render(this, graphics, mouseX, mouseY);
            if (!datePickerOpen && !detailMode) {
                TradingUi.renderBalanceTooltipIfHovered(graphics, font, state.balance(),
                    width, 17, mouseX, mouseY);
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

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        if (datePickerOpen) {
            graphics.fill(0, 0, width, height, theme.background());
            hoveredStack = ItemStack.EMPTY;
            drawDatePicker(graphics, mouseX, mouseY);
            drawToast(graphics);
            return;
        }
        if (detailMode && selectedDraft() != null) {
            if (batchRelistMode) drawBatchRelistPage(graphics, mouseX, mouseY);
            else drawDetailPage(graphics, mouseX, mouseY);
            drawToast(graphics);
            return;
        }
        int split = splitX();
        graphics.fill(0, 0, width, height, theme.background());
        graphics.fill(0, 0, width, HEADER_HEIGHT, 0xF2081014);
        graphics.fill(0, HEADER_HEIGHT - 2, width, HEADER_HEIGHT, theme.accent());
        graphics.fill(0, HEADER_HEIGHT, split, height, theme.panel());
        graphics.fill(split, HEADER_HEIGHT, split + 1, height, theme.border());
        drawWorkspaceTopBar(graphics, mouseX, mouseY);
        int multiSelectX = multiSelectX();
        Component ownedTitle = Component.translatable("trading_op.xero_delta.owned_title",
            ownedListingCount(), state.playerListingSlots());
        int unlockButtonX = Math.min(multiSelectX - 24, 18 + font.width(ownedTitle));
        int titleWidth = Math.max(0, unlockButtonX - 18);
        if (titleWidth > 0) {
            graphics.drawString(font, font.plainSubstrByWidth(ownedTitle.getString(), titleWidth),
                14, 86, theme.text(), false);
        }
        drawPlusIconButton(graphics, unlockButtonX, 80, mouseX, mouseY);
        drawButton(graphics, multiSelectX, 80, 48, 20,
            Component.translatable("trading_op.xero_delta.multi_select"),
            ownedMultiSelect ? theme.accent() : 0xFF40545B, mouseX, mouseY);
        int ownedControlX = split - 104;
        drawStepButton(graphics, ownedControlX, 80, "-", ownedColumns > 1, mouseX, mouseY);
        graphics.drawCenteredString(font, String.valueOf(ownedColumns), ownedControlX + 35, 87, theme.text());
        drawStepButton(graphics, ownedControlX + 50, 80, "+", ownedColumns < 3, mouseX, mouseY);
        drawButton(graphics, split - 160, 80, 48, 20,
            Component.translatable("trading_op.xero_delta.date"), 0xFF40545B, mouseX, mouseY);
        drawSourceList(graphics, mouseX, mouseY);
        drawDraftList(graphics, mouseX, mouseY);
        drawSourceCategoryControl(graphics, mouseX, mouseY);
        drawOwnedMultiSelectBar(graphics, mouseX, mouseY);
        if (listingUnlockDialogOpen) drawListingUnlockDialog(graphics, mouseX, mouseY);
        drawToast(graphics);
    }

    private void drawPlusIconButton(GuiGraphics graphics, int x, int y, int mouseX, int mouseY) {
        boolean hovered = inside(mouseX, mouseY, x, y, 20, 20);
        graphics.fill(x, y, x + 20, y + 20, hovered ? theme.panelAlt() : 0xFF40545B);
        int color = hovered ? theme.text() : theme.accent();
        graphics.fill(x + 5, y + 9, x + 15, y + 11, color);
        graphics.fill(x + 9, y + 5, x + 11, y + 15, color);
        graphics.renderOutline(x, y, 20, 20, hovered ? theme.text() : theme.border());
    }

    private void drawListingUnlockDialog(GuiGraphics graphics, int mouseX, int mouseY) {
        int visibleTasks = Math.min(3, listingUnlockMatches.size());
        int panelWidth = Math.min(390, width - 32);
        int panelHeight = 154 + visibleTasks * 27;
        int x = (width - panelWidth) / 2;
        int y = (height - panelHeight) / 2;
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 450);
        graphics.fill(0, 0, width, height, 0xAA000000);
        graphics.fill(x, y, x + panelWidth, y + panelHeight, 0xFA101A1F);
        graphics.renderOutline(x, y, panelWidth, panelHeight, theme.accent());
        graphics.drawString(font, Component.translatable("trading_op.xero_delta.unlock_title"),
            x + 12, y + 12, theme.text(), false);
        drawDialogCloseIcon(graphics, x + panelWidth - 28, y + 6, mouseX, mouseY);
        graphics.drawString(font, Component.translatable("trading_op.xero_delta.unlock_status",
            state.playerListingSlots(), state.maxPlayerListingSlots()), x + 12, y + 35, theme.text(), false);
        Component command = Component.translatable("trading_op.xero_delta.unlock_command",
            "/xero market slots unlock <player>");
        graphics.drawString(font, font.plainSubstrByWidth(command.getString(), panelWidth - 24),
            x + 12, y + 52, theme.muted(), false);
        graphics.drawString(font, Component.translatable("trading_op.xero_delta.unlock_ftb"),
            x + 12, y + 72, theme.muted(), false);
        int rowY = y + 87;
        if (listingUnlockMatches.isEmpty()) {
            graphics.drawString(font, Component.translatable(
                "trading_op.xero_delta.unlock_ftb_none"), x + 16, rowY + 7, theme.muted(), false);
        } else {
            for (int index = 0; index < visibleTasks; index++) {
                FtbQuestIntegration.UnlockMatch match = listingUnlockMatches.get(index);
                boolean hovered = inside(mouseX, mouseY, x + 12, rowY, panelWidth - 24, 22);
                graphics.fill(x + 12, rowY, x + panelWidth - 12, rowY + 22,
                    hovered ? 0xFF40545B : 0x55000000);
                graphics.renderOutline(x + 12, rowY, panelWidth - 24, 22,
                    hovered ? theme.accent() : theme.border());
                Component label = Component.translatable("trading_op.xero_delta.unlock_ftb_task",
                    match.chapterTitle(), match.questTitle());
                graphics.drawString(font, font.plainSubstrByWidth(label.getString(), panelWidth - 38),
                    x + 19, rowY + 7, hovered ? theme.text() : theme.accent(), false);
                rowY += 27;
            }
        }
        int buttonY = y + panelHeight - 34;
        boolean canUnlock = state.playerListingSlots() < state.maxPlayerListingSlots();
        int buttonColor = canUnlock ? theme.accent() : 0xFF39474B;
        drawButton(graphics, x + 12, buttonY, panelWidth - 24, 24,
            Component.translatable(canUnlock
                ? "trading_op.xero_delta.unlock_levels"
                : "trading_op.xero_delta.unlock_max", state.listingSlotLevelCost()),
            buttonColor, canUnlock ? mouseX : Integer.MIN_VALUE, canUnlock ? mouseY : Integer.MIN_VALUE);
        graphics.pose().popPose();
    }

    private void drawDialogCloseIcon(GuiGraphics graphics, int x, int y, int mouseX, int mouseY) {
        boolean hovered = inside(mouseX, mouseY, x, y, 20, 20);
        graphics.fill(x, y, x + 20, y + 20, hovered ? theme.panelAlt() : 0xFA101A1F);
        int color = hovered ? theme.text() : theme.muted();
        for (int index = 0; index < 8; index++) {
            graphics.fill(x + 6 + index, y + 6 + index, x + 8 + index, y + 8 + index, color);
            graphics.fill(x + 12 - index, y + 6 + index, x + 14 - index, y + 8 + index, color);
        }
    }

    private void drawDatePicker(GuiGraphics graphics, int mouseX, int mouseY) {
        float progress = Math.min(1.0F, (System.currentTimeMillis() - datePopupOpenedAt) / 160.0F);
        int alpha = (int) (0x88 * progress);
        graphics.fill(0, 0, width, height, alpha << 24);
        CalendarLayout layout = calendarLayout();
        int x = layout.panelX();
        int y = layout.panelY();
        graphics.fill(x, y, x + layout.panelWidth(), y + layout.panelHeight(), 0xF21A272D);
        graphics.renderOutline(x, y, layout.panelWidth(), layout.panelHeight(), theme.accent());
        graphics.drawCenteredString(font, Component.translatable("trading_op.xero_delta.choose_date"),
            width / 2, y + 10, theme.text());
        if (calendarSelectedDates.size() > 1) {
            graphics.drawString(font, Component.translatable(
                "trading_op.xero_delta.selected_days", calendarSelectedDates.size()),
                x + 12, y + 10, 0xFFFFD36A, false);
        }
        drawButton(graphics, x + layout.panelWidth() - 30, y + 7, 20, 18,
            Component.literal("脳"), 0xFF40545B, mouseX, mouseY);
        drawButton(graphics, x + 14, y + 25, 24, 20,
            Component.literal("<"), 0xFF40545B, mouseX, mouseY);
        drawButton(graphics, x + layout.panelWidth() - 38, y + 25, 24, 20,
            Component.literal(">"), 0xFF40545B, mouseX, mouseY);
        drawCalendarHeader(graphics, layout, mouseX, mouseY);
        drawCalendarWeekdays(graphics, layout);
        drawCalendarDays(graphics, layout, mouseX, mouseY);
        int gap = 10;
        int buttonWidth = (layout.panelWidth() - 32 - gap * 2) / 3;
        drawButton(graphics, x + 16, layout.footerY(), buttonWidth, 24,
            Component.translatable("trading_op.xero_delta.today"), 0xFF40545B, mouseX, mouseY);
        drawButton(graphics, x + 16 + buttonWidth + gap, layout.footerY(), buttonWidth, 24,
            Component.translatable("trading_op.xero_delta.clear_date"), 0xFF40545B, mouseX, mouseY);
        drawButton(graphics, x + 16 + (buttonWidth + gap) * 2, layout.footerY(), buttonWidth, 24,
            Component.translatable("trading_op.xero_delta.confirm_date"), theme.accent(), mouseX, mouseY);
    }

    private void drawCalendarHeader(GuiGraphics graphics, CalendarLayout layout, int mouseX, int mouseY) {
        CalendarHeaderLayout header = calendarHeaderLayout(layout);
        positionCalendarEditors(header);
        Component year = Component.translatable("trading_op.xero_delta.calendar_year",
            calendarMonth.getYear());
        Component month = Component.translatable("trading_op.xero_delta.calendar_month_value",
            calendarMonth.getMonthValue());
        if (calendarEditTarget != CalendarEditTarget.YEAR) {
            drawCalendarHeaderValue(graphics, year, header.yearX(), header.y(), header.yearWidth(),
                mouseX, mouseY);
        }
        graphics.drawCenteredString(font,
            Component.translatable("trading_op.xero_delta.calendar_separator"),
            header.separatorX(), header.y() + 6, theme.muted());
        if (calendarEditTarget != CalendarEditTarget.MONTH) {
            drawCalendarHeaderValue(graphics, month, header.monthX(), header.y(), header.monthWidth(),
                mouseX, mouseY);
        }
    }

    private void drawCalendarHeaderValue(GuiGraphics graphics, Component value, int x, int y, int width,
                                         int mouseX, int mouseY) {
        boolean hovered = inside(mouseX, mouseY, x, y, width, 20);
        graphics.fill(x, y, x + width, y + 20, hovered ? 0xCC40545B : 0x66172327);
        graphics.renderOutline(x, y, width, 20, hovered ? theme.accent() : theme.border());
        graphics.drawCenteredString(font, value, x + width / 2, y + 6, theme.text());
    }

    private void drawCalendarWeekdays(GuiGraphics graphics, CalendarLayout layout) {
        String[] keys = {
            "mon", "tue", "wed", "thu", "fri", "sat", "sun"
        };
        for (int column = 0; column < 7; column++) {
            graphics.drawCenteredString(font,
                Component.translatable("trading_op.xero_delta.week." + keys[column]),
                layout.gridX() + column * layout.cellWidth() + layout.cellWidth() / 2,
                layout.gridY() - 12, column >= 5 ? 0xFFFFB7A8 : theme.muted());
        }
    }

    private void drawCalendarDays(GuiGraphics graphics, CalendarLayout layout,
                                  int mouseX, int mouseY) {
        LocalDate today = LocalDate.now();
        LocalDate start = calendarStartDate();
        Map<LocalDate, List<ItemStack>> itemsByDate = ownedListingItemsByDate();
        for (int index = 0; index < 42; index++) {
            LocalDate date = start.plusDays(index);
            int column = index % 7;
            int row = index / 7;
            int cellX = layout.gridX() + column * layout.cellWidth();
            int cellY = layout.gridY() + row * layout.cellHeight();
            int cellWidth = layout.cellWidth() - 1;
            int cellHeight = layout.cellHeight() - 1;
            boolean currentMonth = YearMonth.from(date).equals(calendarMonth);
            boolean selected = calendarSelectedDates.contains(date);
            boolean hovered = inside(mouseX, mouseY, cellX, cellY, cellWidth, cellHeight);
            List<ItemStack> items = itemsByDate.getOrDefault(date, List.of());
            boolean hasListings = !items.isEmpty();
            int color = currentMonth ? 0xCC1B292E : 0x88131E22;
            if (hasListings) color = currentMonth ? 0xDD29483F : 0xAA203A34;
            if (selected) color = 0xEE315B50;
            else if (hovered) color = 0xDD40545B;
            graphics.fill(cellX, cellY, cellX + cellWidth, cellY + cellHeight, color);
            graphics.renderOutline(cellX, cellY, cellWidth, cellHeight,
                selected ? 0xFFFFD36A : hasListings ? 0xFF55D6B0 : theme.border());
            if (date.equals(today)) {
                graphics.fill(cellX + 2, cellY + cellHeight - 2,
                    cellX + cellWidth - 2, cellY + cellHeight, 0xFFFFD36A);
            }
            if (hasListings && cellHeight >= 34) {
                int carousel = (int) Math.floorMod(
                    System.currentTimeMillis() / 1200L + date.toEpochDay(), (long) items.size());
                ItemStack preview = items.get(carousel);
                int itemX = cellX + (cellWidth - 16) / 2;
                int itemY = cellY + 3;
                graphics.renderItem(preview, itemX, itemY);
                if (hovered && inside(mouseX, mouseY, itemX - 2, itemY - 2, 20, 20)) {
                    hoveredStack = preview;
                }
            }
            int dayY = cellY + Math.max(3, cellHeight - 20);
            int dayColor = currentMonth ? theme.text() : 0xFF66777B;
            graphics.drawCenteredString(font, String.valueOf(date.getDayOfMonth()),
                cellX + cellWidth / 2, dayY, dayColor);
            ChineseLunarCalendar.LunarDate lunarDate = ChineseLunarCalendar.supports(date)
                ? ChineseLunarCalendar.from(date) : null;
            String lunar = lunarDate == null ? "" : lunarDate.displayLabel();
            if (!lunar.isBlank() && cellHeight >= 24) {
                boolean solarTerm = !lunarDate.solarTerm().isBlank();
                graphics.drawCenteredString(font,
                    font.plainSubstrByWidth(lunar, Math.max(12, cellWidth - 4)),
                    cellX + cellWidth / 2, dayY + 9,
                    solarTerm ? 0xFFFFD36A : currentMonth ? theme.muted() : 0xFF56666A);
            }
        }
    }

    private CalendarLayout calendarLayout() {
        int panelWidth = Math.min(560, Math.max(350, width - 48));
        int panelHeight = Math.min(420, Math.max(300, height - 46));
        int panelX = (width - panelWidth) / 2;
        int panelY = (height - panelHeight) / 2;
        int gridWidth = panelWidth - 32;
        int cellWidth = Math.max(1, gridWidth / 7);
        int gridX = panelX + (panelWidth - cellWidth * 7) / 2;
        int gridY = panelY + 70;
        int footerHeight = 42;
        int availableGridHeight = Math.max(120,
            panelHeight - (gridY - panelY) - footerHeight - 8);
        int cellHeight = Math.max(20, availableGridHeight / 6);
        int footerY = Math.min(panelY + panelHeight - 32, gridY + cellHeight * 6 + 8);
        return new CalendarLayout(panelX, panelY, panelWidth, panelHeight,
            gridX, gridY, cellWidth, cellHeight, footerY);
    }

    private CalendarHeaderLayout calendarHeaderLayout(CalendarLayout layout) {
        Component year = Component.translatable("trading_op.xero_delta.calendar_year",
            calendarMonth.getYear());
        Component month = Component.translatable("trading_op.xero_delta.calendar_month_value",
            calendarMonth.getMonthValue());
        Component separator = Component.translatable("trading_op.xero_delta.calendar_separator");
        int yearWidth = Math.max(48, font.width(year) + 10);
        int monthWidth = Math.max(36, font.width(month) + 10);
        int separatorWidth = Math.max(10, font.width(separator) + 4);
        int totalWidth = yearWidth + separatorWidth + monthWidth;
        int yearX = layout.panelX() + (layout.panelWidth() - totalWidth) / 2;
        int monthX = yearX + yearWidth + separatorWidth;
        return new CalendarHeaderLayout(yearX, monthX, yearX + yearWidth + separatorWidth / 2,
            layout.panelY() + 25, yearWidth, monthWidth);
    }

    private void positionCalendarEditors(CalendarHeaderLayout header) {
        if (calendarYearBox == null || calendarMonthBox == null) return;
        calendarYearBox.setX(header.yearX());
        calendarYearBox.setY(header.y());
        calendarYearBox.setWidth(header.yearWidth());
        calendarMonthBox.setX(header.monthX());
        calendarMonthBox.setY(header.y());
        calendarMonthBox.setWidth(header.monthWidth());
    }

    private void restoreCalendarEditorAfterInit() {
        positionCalendarEditors(calendarHeaderLayout(calendarLayout()));
        boolean yearVisible = datePickerOpen && calendarEditTarget == CalendarEditTarget.YEAR;
        boolean monthVisible = datePickerOpen && calendarEditTarget == CalendarEditTarget.MONTH;
        calendarYearBox.visible = yearVisible;
        calendarMonthBox.visible = monthVisible;
        if (yearVisible) {
            calendarYearBox.setValue(String.valueOf(calendarMonth.getYear()));
            calendarYearBox.setFocused(true);
            setFocused(calendarYearBox);
        } else if (monthVisible) {
            calendarMonthBox.setValue(String.valueOf(calendarMonth.getMonthValue()));
            calendarMonthBox.setFocused(true);
            setFocused(calendarMonthBox);
        }
    }

    private LocalDate calendarStartDate() {
        LocalDate first = calendarMonth.atDay(1);
        return first.minusDays(first.getDayOfWeek().getValue() - 1L);
    }

    private Map<LocalDate, List<ItemStack>> ownedListingItemsByDate() {
        if (calendarListingRevision == state.revision()) return calendarListingItems;
        Map<LocalDate, List<ItemStack>> result = new LinkedHashMap<>();
        if (minecraft == null || minecraft.player == null) {
            calendarListingRevision = state.revision();
            calendarListingItems = result;
            return result;
        }
        UUID playerId = minecraft.player.getUUID();
        for (TradingSyncPacket.ListingView listing : state.listings()) {
            if (!listing.sellerId().equals(playerId)) continue;
            LocalDate date = listingDate(listing.publicId());
            if (date == null) continue;
            List<ItemStack> items = result.computeIfAbsent(date, ignored -> new ArrayList<>());
            boolean duplicate = false;
            for (ItemStack existing : items) {
                if (ItemStack.isSameItemSameComponents(existing, listing.stack())) {
                    duplicate = true;
                    break;
                }
            }
            if (!duplicate) items.add(listing.stack().copyWithCount(1));
        }
        calendarListingRevision = state.revision();
        calendarListingItems = result;
        return calendarListingItems;
    }

    private static LocalDate listingDate(String publicId) {
        if (publicId == null || publicId.length() < 8) return null;
        try {
            return LocalDate.parse(publicId.substring(0, 8), DATE_FILTER_FORMAT);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private void drawWorkspaceTopBar(GuiGraphics graphics, int mouseX, int mouseY) {
        int tabWidth = Math.max(54, Math.min(82, width / 8));
        int startX = (width - tabWidth * 3 - 8) / 2;
        String[] tabs = {"buy", "sell", "history"};
        for (int i = 0; i < tabs.length; i++) {
            int x = startX + i * (tabWidth + 4);
            boolean selected = i == 1;
            boolean hovered = inside(mouseX, mouseY, x, 8, tabWidth, 28);
            graphics.fill(x, 8, x + tabWidth, 36,
                selected ? theme.panelAlt() : hovered ? theme.panel() : 0x44000000);
            if (selected) graphics.fill(x, 34, x + tabWidth, 36, theme.accent());
            graphics.drawCenteredString(font, Component.translatable("market.xero_delta.tab." + tabs[i]),
                x + tabWidth / 2, 18, selected ? theme.text() : theme.muted());
        }
        int balanceWidth = TradingUi.balanceWidth(font, state.balance());
        TradingUi.drawBalance(graphics, font, state.balance(), width - balanceWidth - 88, 17, 0xFFFFD36A);
        int controlX = width - 104;
        drawStepButton(graphics, controlX, 52, "-", sourceColumns > 1, mouseX, mouseY);
        graphics.drawCenteredString(font, String.valueOf(sourceColumns), controlX + 35, 59, theme.text());
        drawStepButton(graphics, controlX + 50, 52, "+", sourceColumns < 10, mouseX, mouseY);
    }

    private void drawSourceList(GuiGraphics graphics, int mouseX, int mouseY) {
        int left = splitX() + 12;
        int right = width - 12;
        int top = WORKSPACE_TOP;
        int bottom = height - 12;
        SourceLayout layout = sourceLayout(left, right);
        uiViewport.enableScissor(graphics, left, top, right, bottom);
        for (SourceHeaderPlacement header : layout.headers()) {
            int y = top + header.y() - sourceScroll;
            if (y + SOURCE_GROUP_HEADER_HEIGHT < top || y > bottom) continue;
            boolean hovered = inside(mouseX, mouseY, left, y, right - left, SOURCE_GROUP_HEADER_HEIGHT);
            graphics.fill(left, y, right, y + SOURCE_GROUP_HEADER_HEIGHT,
                hovered ? theme.panelAlt() : 0xCC223137);
            graphics.renderOutline(left, y, right - left, SOURCE_GROUP_HEADER_HEIGHT, theme.border());
            int labelX = left + 7;
            if (header.group() != null && !header.group().icon().isEmpty()) {
                graphics.renderItem(header.group().icon(), left + 4, y + 4);
                labelX = left + 24;
                if (hovered) hoveredStack = header.group().icon();
            }
            graphics.drawString(font, sourceGroupLabel(header.group(), header.groupId()),
                labelX, y + 8, theme.text(), false);
        }
        for (SourceCardPlacement card : layout.cards()) {
            int x = card.x();
            int y = top + card.y() - sourceScroll;
            int cellWidth = card.width();
            if (y + SOURCE_CELL_HEIGHT < top || y > bottom) continue;
            TradingSyncPacket.SourceView value = card.source();
            boolean hovered = inside(mouseX, mouseY, x, y, cellWidth, SOURCE_CELL_HEIGHT);
            graphics.fill(x, y, x + cellWidth, y + SOURCE_CELL_HEIGHT,
                !value.sellable() ? 0xCC252A2C : hovered ? theme.panelAlt() : 0xAA172327);
            graphics.fill(x, y, x + cellWidth, y + 2,
                value.sellable()
                    ? qualityColor(ClientDataCache.INSTANCE.getQuality(value.stack()))
                    : theme.danger());
            String sourceName = value.stack().getHoverName().getString();
            drawQualityName(graphics, sourceName, x + 4, y + 5,
                Math.max(18, cellWidth - 8),
                qualityColor(ClientDataCache.INSTANCE.getQuality(value.stack())), x * 31L + y);
            graphics.renderItem(value.stack(), x + (cellWidth - 16) / 2, y + 22);
            if (value.sellable()) {
                String count = "x" + value.availableCount();
                graphics.drawString(font, count, x + cellWidth - font.width(count) - 4,
                    y + SOURCE_CELL_HEIGHT - 11, theme.text(), false);
            } else {
                Component blocked = Component.translatable("trading_op.xero_delta.source_blocked");
                graphics.drawString(font, font.plainSubstrByWidth(blocked.getString(), cellWidth - 8),
                    x + 4, y + SOURCE_CELL_HEIGHT - 11, theme.danger(), false);
                graphics.renderOutline(x, y, cellWidth, SOURCE_CELL_HEIGHT, theme.danger());
            }
            if (hovered) hoveredStack = value.stack();
        }
        graphics.disableScissor();
        drawScrollbar(graphics, sourceMaxScroll(), sourceScroll, top, bottom, right - 2);
    }

    private void drawSourceCategoryControl(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = sourceCategoryControlX();
        int y = 80;
        int width = sourceCategoryControlWidth();
        boolean hovered = inside(mouseX, mouseY, x, y, width, 20);
        graphics.fill(x, y, x + width, y + 20,
            sourceCategoryOpen ? theme.panelAlt() : hovered ? 0xFF40545B : theme.panel());
        graphics.renderOutline(x, y, width, 20,
            sourceCategoryOpen ? theme.accent() : theme.border());
        ItemStack icon = selectedSourceCategoryIcon();
        int labelX = x + 7;
        if (!icon.isEmpty()) {
            graphics.renderItem(icon, x + 3, y + 2);
            labelX = x + 23;
        }
        Component label = selectedSourceCategoryLabel();
        graphics.drawString(font, font.plainSubstrByWidth(label.getString(),
            Math.max(18, width - (labelX - x) - 18)), labelX, y + 6, theme.text(), false);
        graphics.drawCenteredString(font, sourceCategoryOpen ? "v" : ">",
            x + width - 10, y + 6, theme.muted());
        if (!sourceCategoryOpen) return;

        List<SourceCategoryRow> rows = sourceCategoryRows();
        int popupY = y + 24;
        int rowHeight = 24;
        int visibleRows = Math.max(1, Math.min(12, (height - popupY - 18) / rowHeight));
        sourceCategoryScroll = Math.max(0,
            Math.min(sourceCategoryScroll, Math.max(0, rows.size() - visibleRows)));
        int popupHeight = visibleRows * rowHeight + 6;
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 650);
        graphics.fill(x, popupY, x + width, popupY + popupHeight, 0xFA172126);
        graphics.renderOutline(x, popupY, width, popupHeight, theme.accent());
        uiViewport.enableScissor(graphics, x + 1, popupY + 2, x + width - 1, popupY + popupHeight - 2);
        for (int visible = 0; visible < visibleRows; visible++) {
            int index = sourceCategoryScroll + visible;
            if (index >= rows.size()) break;
            SourceCategoryRow row = rows.get(index);
            int rowY = popupY + 3 + visible * rowHeight;
            boolean header = row.sectionHeader();
            boolean selected = header
                ? row.sectionId().equals(selectedSourceCreativeSection)
                : sourceCategory == row.category()
                    && (row.sectionId().isBlank()
                        ? selectedSourceCreativeSection.isBlank()
                        : row.sectionId().equals(selectedSourceCreativeSection));
            boolean rowHovered = inside(mouseX, mouseY, x + 2, rowY, width - 4, rowHeight - 1);
            int rowX = !header && !row.sectionId().isBlank() ? x + 10 : x + 2;
            graphics.fill(rowX, rowY, x + width - 2, rowY + rowHeight - 1,
                selected ? 0xFF4D693D : rowHovered ? 0xFF34464D : 0x00172126);
            ItemStack rowIcon = header
                ? TradingCreativeCategoryGroups.icon(row.sectionId())
                : TradingCreativeCategoryGroups.categoryIcon(row.sectionId(), row.category());
            if (!rowIcon.isEmpty()) graphics.renderItem(rowIcon, rowX + 3, rowY + 4);
            Component rowLabel = header
                ? TradingCreativeCategoryGroups.label(row.sectionId())
                : Component.translatable("market.xero_delta.category."
                    + row.category().name().toLowerCase(Locale.ROOT));
            graphics.drawString(font, font.plainSubstrByWidth(rowLabel.getString(), width - 48),
                rowX + 23, rowY + 8, selected ? theme.text() : theme.muted(), false);
            if (header) graphics.drawCenteredString(font,
                row.sectionId().equals(expandedSourceCategorySection) ? "v" : ">",
                x + width - 12, rowY + 8, theme.muted());
        }
        graphics.disableScissor();
        graphics.pose().popPose();
    }

    private void drawDraftList(GuiGraphics graphics, int mouseX, int mouseY) {
        List<TradingSyncPacket.ListingView> owned = filteredOwnedListings();
        int left = 12;
        int right = splitX() - 10;
        int top = WORKSPACE_TOP;
        int bottom = ownedListBottom();
        int columns = ownedGridColumns(left, right);
        int cardWidth = Math.max(100, (right - left - OWNED_ROW_GAP * (columns - 1) - 5) / columns);
        uiViewport.enableScissor(graphics, left, top, right, bottom);
        for (int i = 0; i < owned.size(); i++) {
            int cardX = left + (i % columns) * (cardWidth + OWNED_ROW_GAP);
            int y = top + (i / columns) * (OWNED_ROW_HEIGHT + OWNED_ROW_GAP) - draftScroll;
            if (y + OWNED_ROW_HEIGHT < top || y > bottom) continue;
            TradingSyncPacket.ListingView listing = owned.get(i);
            boolean expired = isExpired(listing);
            boolean hovered = inside(mouseX, mouseY, cardX, y, cardWidth, OWNED_ROW_HEIGHT);
            boolean selected = selectedOwnedListings.contains(listing.id());
            graphics.fill(cardX, y, cardX + cardWidth, y + OWNED_ROW_HEIGHT,
                selected ? 0xEE30464A : hovered ? theme.panelAlt() : expired ? 0xCC35282A : theme.panel());
            graphics.pose().pushPose();
            graphics.pose().translate(cardX + 10, y + 22, 60);
            graphics.pose().scale(2.0F, 2.0F, 1.0F);
            graphics.renderItem(listing.stack(), 0, 0);
            graphics.pose().popPose();
            String listingName = listing.stack().getHoverName().getString();
            drawQualityName(graphics, listingName, cardX + 50, y + 7,
                Math.max(30, cardWidth - 112),
                qualityColor(ClientDataCache.INSTANCE.getQuality(listing.stack())),
                cardX * 19L + y);
            graphics.drawString(font, "x" + listing.stack().getCount(), cardX + 50, y + 23,
                theme.muted(), false);
            Component status = Component.translatable(expired
                ? "trading_op.xero_delta.expired" : "trading_op.xero_delta.active");
            graphics.drawString(font, status, cardX + 50, y + 36,
                expired ? theme.danger() : theme.muted(), false);
            String listedAt = listedTime(listing);
            int listedX = cardX + 50 + font.width(status) + 7;
            int listedWidth = Math.max(0, cardX + cardWidth - 62 - listedX);
            if (listedWidth > 0) {
                graphics.drawString(font, font.plainSubstrByWidth(listedAt, listedWidth),
                    listedX, y + 36, theme.muted(), false);
            }
            if (!expired) {
                graphics.drawString(font, font.plainSubstrByWidth(remainingTime(listing).getString(),
                    Math.max(30, cardWidth - 112)), cardX + 50, y + 49,
                    theme.muted(), false);
            }
            long listedCount = Math.max(1L, listing.stack().getCount());
            long unitPrice = (listing.price() + listedCount - 1L) / listedCount;
            Component unitLabel = Component.translatable("trading_op.xero_delta.unit_price_label");
            Component totalLabel = Component.translatable("trading_op.xero_delta.total_price_label");
            int unitWidth = font.width(unitLabel) + 4 + TradingUi.amountWidth(font, unitPrice);
            int totalWidth = font.width(totalLabel) + 4 + TradingUi.amountWidth(font, listing.price());
            int availablePriceWidth = Math.max(40, cardWidth - 58);
            if (unitWidth + totalWidth + 8 <= availablePriceWidth) {
                graphics.drawString(font, unitLabel, cardX + 50, y + 64, theme.muted(), false);
                TradingUi.drawAmount(graphics, font, unitPrice,
                    cardX + 50 + font.width(unitLabel) + 4, y + 63, 0xFFFFD36A);
                int totalX = cardX + cardWidth - totalWidth - 6;
                graphics.drawString(font, totalLabel, totalX, y + 64, theme.muted(), false);
                TradingUi.drawAmount(graphics, font, listing.price(),
                    totalX + font.width(totalLabel) + 4, y + 63, 0xFFFFD36A);
            } else {
                graphics.drawString(font, unitLabel, cardX + 50, y + 60, theme.muted(), false);
                TradingUi.drawAmount(graphics, font, unitPrice,
                    cardX + 50 + font.width(unitLabel) + 4, y + 59, 0xFFFFD36A);
                graphics.drawString(font, totalLabel, cardX + 50, y + 75, theme.muted(), false);
                TradingUi.drawAmount(graphics, font, listing.price(),
                    cardX + 50 + font.width(totalLabel) + 4, y + 74, 0xFFFFD36A);
            }
            drawOwnedButtons(graphics, listing, cardX + cardWidth - 4, y + 7, mouseX, mouseY);
            if (selected) graphics.renderOutline(cardX, y, cardWidth, OWNED_ROW_HEIGHT, 0xFFFFFFFF);
            if (hovered) hoveredStack = listing.stack();
        }
        graphics.disableScissor();
        drawScrollbar(graphics, draftMaxScroll(), draftScroll, top, bottom, right - 2);
    }

    private void drawOwnedMultiSelectBar(GuiGraphics graphics, int mouseX, int mouseY) {
        if (!ownedMultiSelect) return;
        int split = splitX();
        int y = height - 38;
        graphics.fill(0, y - 4, split, height, 0xF20C171B);
        graphics.drawString(font, Component.translatable("trading_op.xero_delta.selected_count",
            selectedOwnedListings.size()), 14, y + 7, theme.text(), false);
        int buttonWidth = Math.max(48, Math.min(76, (split - 112) / 3));
        int cancelX = split - 12 - buttonWidth;
        int relistX = cancelX - 6 - buttonWidth;
        int selectAllX = relistX - 6 - buttonWidth;
        boolean allSelected = allFilteredOwnedSelected();
        boolean canRelist = selectedOwnedViews().stream().anyMatch(this::isExpired);
        boolean canCancel = !selectedOwnedListings.isEmpty();
        drawOwnedBatchButton(graphics, selectAllX, y, buttonWidth,
            Component.translatable(allSelected
                ? "trading_op.xero_delta.deselect_all"
                : "trading_op.xero_delta.select_all"), !filteredOwnedListings().isEmpty(),
            0xFF40545B, mouseX, mouseY);
        drawOwnedBatchButton(graphics, relistX, y, buttonWidth,
            Component.translatable("trading_op.xero_delta.batch_relist"), canRelist,
            theme.accent(), mouseX, mouseY);
        drawOwnedBatchButton(graphics, cancelX, y, buttonWidth,
            Component.translatable("trading_op.xero_delta.batch_cancel"), canCancel,
            0xFF8B3D3B, mouseX, mouseY);
    }

    private void drawOwnedBatchButton(GuiGraphics graphics, int x, int y, int buttonWidth,
                                      Component label, boolean enabled, int color,
                                      int mouseX, int mouseY) {
        boolean hovered = enabled && inside(mouseX, mouseY, x, y, buttonWidth, 22);
        graphics.fill(x, y, x + buttonWidth, y + 22,
            enabled ? (hovered ? brighten(color) : color) : 0xFF39474B);
        graphics.drawCenteredString(font, label, x + buttonWidth / 2, y + 8,
            enabled ? (color == theme.accent() ? 0xFF071214 : theme.text()) : theme.muted());
    }

    private void drawOwnedButtons(GuiGraphics graphics, TradingSyncPacket.ListingView listing,
                                  int right, int y, int mouseX, int mouseY) {
        List<OwnedAction> actions = ownedActions(listing);
        int buttonWidth = 54;
        int buttonHeight = 18;
        int gap = 3;
        int x = right - buttonWidth;
        for (OwnedAction action : actions) {
            int color = action == OwnedAction.CANCEL ? 0xFF63403F
                : action == OwnedAction.RELIST ? theme.accent() : 0xFF40545B;
            graphics.fill(x, y, x + buttonWidth, y + buttonHeight,
                inside(mouseX, mouseY, x, y, buttonWidth, buttonHeight) ? brighten(color) : color);
            graphics.drawCenteredString(font, Component.translatable(action.translationKey),
                x + buttonWidth / 2, y + 6,
                action == OwnedAction.RELIST ? 0xFF071214 : theme.text());
            y += buttonHeight + gap;
        }
    }

    private void drawDetailPage(GuiGraphics graphics, int mouseX, int mouseY) {
        Draft draft = selectedDraft();
        if (draft == null) return;
        graphics.fill(0, 0, width, height, 0xFF0B1217);
        DetailLayout layout = detailLayout();
        int panelWidth = layout.panelWidth();
        int panelHeight = layout.panelHeight();
        int panelX = layout.panelX();
        int panelY = layout.panelY();
        int divider = layout.divider();
        graphics.drawString(font, Component.translatable("trading_op.xero_delta.listing_title"),
            panelX, panelY - 22, theme.text(), false);
        graphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0xF2172228);
        graphics.fill(panelX, panelY, panelX + panelWidth, panelY + 2, theme.border());
        graphics.fill(divider, panelY + 14, divider + 1, panelY + panelHeight - 14, theme.border());
        Component slots = Component.translatable("trading_op.xero_delta.slots", 1);
        int slotWidth = font.width(slots) + 30;
        graphics.fill((width - slotWidth) / 2, panelY - 56, (width + slotWidth) / 2, panelY - 32, 0xCC0B1115);
        graphics.drawCenteredString(font, slots, width / 2, panelY - 48, theme.muted());
        drawPriceDistribution(graphics, draft, panelX + 18, panelY + 18, divider - panelX - 36,
            panelHeight - 36);
        uiViewport.enableScissor(graphics, divider + 2, panelY + 2,
            panelX + panelWidth - 2, panelY + panelHeight - 2);
        drawListingControls(graphics, draft, layout.contentX(), layout.contentY() - detailScroll,
            layout.areaWidth(), layout.areaHeight(), mouseX, mouseY);
        graphics.disableScissor();
        int detailItemX = layout.contentX() + layout.areaWidth() / 2 - 16;
        int detailItemY = layout.contentY() - detailScroll + 20;
        if (inside(mouseX, mouseY, detailItemX, detailItemY, 32, 32)
            && inside(mouseX, mouseY, divider + 2, panelY + 2,
                panelX + panelWidth - divider - 4, panelHeight - 4)) {
            hoveredStack = draft.stack;
        }
        drawScrollbar(graphics, detailMaxScroll(), detailScroll,
            panelY + 8, panelY + panelHeight - 8, panelX + panelWidth - 5);
    }

    private void drawBatchRelistPage(GuiGraphics graphics, int mouseX, int mouseY) {
        Draft selected = selectedDraft();
        if (selected == null) return;
        graphics.fill(0, 0, width, height, 0xFF0B1217);
        DetailLayout layout = detailLayout();
        int panelX = layout.panelX();
        int panelY = layout.panelY();
        int panelWidth = layout.panelWidth();
        int panelHeight = layout.panelHeight();
        int divider = layout.divider();
        graphics.drawString(font, Component.translatable(creativeBatchMode
                ? "trading_op.xero_delta.creative_batch_title"
                : "trading_op.xero_delta.batch_relist_title"),
            panelX, panelY - 22, theme.text(), false);
        graphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0xF2172228);
        graphics.fill(panelX, panelY, panelX + panelWidth, panelY + 2, theme.border());
        graphics.fill(divider, panelY + 14, divider + 1, panelY + panelHeight - 14, theme.border());
        int listX = panelX + 14;
        int listY = panelY + 16;
        int listWidth = divider - panelX - 28;
        int listBottom = panelY + panelHeight - 16;
        if (creativeBatchMode) {
            drawCreativeBatchGrid(graphics, listX, listY, listWidth, listBottom, mouseX, mouseY);
        } else {
            uiViewport.enableScissor(graphics, listX, listY, listX + listWidth, listBottom);
            for (int index = 0; index < drafts.size(); index++) {
                Draft draft = drafts.get(index);
                int rowY = listY + index * 76 - batchRelistScroll;
                if (rowY + 70 < listY || rowY >= listBottom) continue;
                boolean active = draft.id == selectedDraftId;
                boolean checked = selectedBatchDraftIds.contains(draft.id);
                boolean hovered = inside(mouseX, mouseY, listX, rowY, listWidth, 70);
                graphics.fill(listX, rowY, listX + listWidth, rowY + 70,
                    active ? 0xEE304248 : hovered ? theme.panelAlt() : theme.panel());
                if (active) graphics.renderOutline(listX, rowY, listWidth, 70, 0xFFFFFFFF);
                graphics.renderOutline(listX + 10, rowY + 27, 14, 14, theme.border());
                if (checked) graphics.fill(listX + 13, rowY + 30, listX + 21, rowY + 38, theme.accent());
                graphics.renderItem(draft.stack, listX + 34, rowY + 25);
                TradingUi.drawMarquee(graphics, font, draft.stack.getHoverName().getString(),
                    listX + 58, rowY + 12, Math.max(40, listWidth - 150),
                    theme.text(), index * 23L, uiViewport);
                graphics.drawString(font, Component.translatable("trading_op.xero_delta.batch_price",
                    TradingUi.formatDetailed(draft.price)), listX + 58, rowY + 48, 0xFFFFD36A, false);
                if (hovered) hoveredStack = draft.stack;
            }
            graphics.disableScissor();
            drawScrollbar(graphics, batchRelistMaxScroll(), batchRelistScroll,
                listY, listBottom, divider - 5);
        }
        drawBatchRelistEditor(graphics, selected, divider + 18, panelY + 18,
            panelX + panelWidth - divider - 36, panelHeight - 36, mouseX, mouseY);
    }

    private void drawBatchRelistEditor(GuiGraphics graphics, Draft draft, int x, int y,
                                       int areaWidth, int areaHeight, int mouseX, int mouseY) {
        int buttonY = y + areaHeight - 30;
        int viewportBottom = buttonY - 6;
        int contentY = y - batchEditorScroll;
        uiViewport.enableScissor(graphics, x, y, x + areaWidth, viewportBottom);
        boolean allSelected = !drafts.isEmpty()
            && drafts.stream().allMatch(value -> selectedBatchDraftIds.contains(value.id));
        boolean selectHovered = inside(mouseX, mouseY, x, contentY, areaWidth, 22);
        graphics.fill(x, contentY, x + areaWidth, contentY + 22,
            selectHovered ? 0xFF4B6067 : theme.panelAlt());
        graphics.drawCenteredString(font, Component.translatable(allSelected
                ? "trading_op.xero_delta.deselect_all"
                : "trading_op.xero_delta.select_all"),
            x + areaWidth / 2, contentY + 7, theme.text());
        graphics.drawString(font, Component.translatable("trading_op.xero_delta.creative_batch_selected",
            selectedBatchDraftIds.size(), drafts.size()), x, contentY + 30, theme.muted(), false);
        TradingUi.drawMarquee(graphics, font, draft.stack.getHoverName().getString(),
            x, contentY + 48, areaWidth, theme.text(), draft.id, uiViewport);
        graphics.renderItem(draft.stack, x + areaWidth / 2 - 8, contentY + 64);
        int itemX = x + areaWidth / 2 - 8;
        int itemY = contentY + 64;
        if (inside(mouseX, mouseY, itemX, itemY, 16, 16)
            && inside(mouseX, mouseY, x, y, areaWidth, viewportBottom - y)) {
            hoveredStack = draft.stack;
        }
        graphics.fill(x, contentY + 104, x + areaWidth, contentY + 105, theme.border());
        if (creativeBatchMode) {
            graphics.drawString(font, Component.translatable("trading_op.xero_delta.creative_batch_amount",
                draft.amount, draft.maximumAmount), x, contentY + 116, theme.muted(), false);
            drawStepButton(graphics, x, contentY + 130, "-", draft.amount > 1, mouseX, mouseY);
            drawStepButton(graphics, x + areaWidth - 20, contentY + 130, "+",
                draft.amount < draft.maximumAmount, mouseX, mouseY);
            graphics.drawString(font, Component.translatable("trading_op.xero_delta.reference_price",
                TradingUi.formatDetailed(draft.basePrice)), x, contentY + 158, theme.muted(), false);
            graphics.drawString(font, Component.translatable("trading_op.xero_delta.creative_batch_offset"),
                x, contentY + 174, theme.muted(), false);
        } else {
            graphics.drawString(font, Component.translatable("trading_op.xero_delta.amount_range",
                draft.amount, draft.maximumAmount), x, contentY + 116, theme.muted(), false);
            graphics.drawString(font, Component.translatable("trading_op.xero_delta.price"),
                x, contentY + 174, theme.muted(), false);
        }
        drawStepButton(graphics, x, contentY + 188, "-",
            creativeBatchMode || draft.price > draft.minimumPrice, mouseX, mouseY);
        drawStepButton(graphics, x + areaWidth - 20, contentY + 188, "+",
            creativeBatchMode || draft.price < draft.maximumPrice, mouseX, mouseY);
        graphics.drawString(font, Component.translatable("trading_op.xero_delta.creative_batch_unit_price",
            TradingUi.formatDetailed(draft.price)), x, contentY + 216, 0xFFFFD36A, false);
        long income = TradingRules.sellerProceeds(totalListingPrice(draft));
        graphics.drawString(font, Component.translatable("trading_op.xero_delta.expected_income",
            TradingUi.formatDetailed(income)), x, contentY + 232, 0xFFFFD36A, false);
        graphics.disableScissor();
        drawScrollbar(graphics, batchEditorMaxScroll(), batchEditorScroll,
            y, viewportBottom, x + areaWidth - 3);
        boolean enabled = !selectedBatchDraftIds.isEmpty();
        graphics.fill(x, buttonY, x + areaWidth, buttonY + 24,
            enabled && inside(mouseX, mouseY, x, buttonY, areaWidth, 24) ? 0xFF75E2C0
                : enabled ? theme.accent() : 0xFF39474B);
        graphics.drawCenteredString(font, Component.translatable(creativeBatchMode
                ? "trading_op.xero_delta.creative_batch_submit"
                : "trading_op.xero_delta.batch_submit"),
            x + areaWidth / 2, buttonY + 8, enabled ? 0xFF071214 : theme.muted());
    }

    private void drawCreativeBatchGrid(GuiGraphics graphics, int x, int y, int areaWidth,
                                       int bottom, int mouseX, int mouseY) {
        int controlsWidth = 74;
        creativeBatchSearch.setX(x);
        creativeBatchSearch.setY(y);
        creativeBatchSearch.setWidth(Math.max(54, areaWidth - controlsWidth - 6));
        creativeBatchSearch.visible = true;
        int controlsX = x + areaWidth - controlsWidth;
        drawStepButton(graphics, controlsX, y, "-", creativeBatchColumns > 1, mouseX, mouseY);
        graphics.fill(controlsX + 22, y, controlsX + 50, y + 20, theme.panelAlt());
        graphics.drawCenteredString(font, String.valueOf(creativeBatchColumns),
            controlsX + 36, y + 7, theme.text());
        drawStepButton(graphics, controlsX + 54, y, "+",
            creativeBatchColumns < CREATIVE_BATCH_MAX_COLUMNS, mouseX, mouseY);
        int gridTop = y + 28;
        List<Draft> values = filteredCreativeBatchDrafts();
        int columns = creativeBatchGridColumns(areaWidth);
        int cardWidth = Math.max(42, (areaWidth - CREATIVE_BATCH_CARD_GAP * (columns - 1)) / columns);
        uiViewport.enableScissor(graphics, x, gridTop, x + areaWidth, bottom);
        for (int index = 0; index < values.size(); index++) {
            Draft draft = values.get(index);
            int cardX = x + index % columns * (cardWidth + CREATIVE_BATCH_CARD_GAP);
            int cardY = gridTop + index / columns
                * (CREATIVE_BATCH_CARD_HEIGHT + CREATIVE_BATCH_CARD_GAP) - batchRelistScroll;
            if (cardY + CREATIVE_BATCH_CARD_HEIGHT < gridTop || cardY >= bottom) continue;
            boolean active = draft.id == selectedDraftId;
            boolean checked = selectedBatchDraftIds.contains(draft.id);
            boolean hovered = !touchDragging && inside(mouseX, mouseY, cardX, cardY,
                cardWidth, CREATIVE_BATCH_CARD_HEIGHT);
            graphics.fill(cardX, cardY, cardX + cardWidth, cardY + CREATIVE_BATCH_CARD_HEIGHT,
                active ? 0xEE304248 : hovered ? theme.panelAlt() : theme.panel());
            graphics.fill(cardX, cardY, cardX + cardWidth, cardY + 2,
                qualityColor(ClientDataCache.INSTANCE.getQuality(draft.stack)));
            if (checked) graphics.renderOutline(cardX, cardY, cardWidth,
                CREATIVE_BATCH_CARD_HEIGHT, 0xFFFFFFFF);
            TradingUi.drawMarquee(graphics, font, draft.stack.getHoverName().getString(),
                cardX + 4, cardY + 6, Math.max(20, cardWidth - 8), theme.text(),
                draft.id, uiViewport);
            graphics.renderItem(draft.stack, cardX + cardWidth / 2 - 8, cardY + 28);
            TradingUi.drawDetailedAmount(graphics, font, draft.price,
                cardX + 4, cardY + 64, 0xFFFFD36A);
            String amount = "x" + draft.amount;
            graphics.drawString(font, amount, cardX + cardWidth - font.width(amount) - 4,
                cardY + 66, theme.muted(), false);
            if (hovered) hoveredStack = draft.stack;
        }
        graphics.disableScissor();
        drawScrollbar(graphics, batchRelistMaxScroll(), batchRelistScroll,
            gridTop, bottom, x + areaWidth - 3);
    }

    private void drawPriceDistribution(GuiGraphics graphics, Draft draft, int x, int y, int areaWidth, int areaHeight) {
        List<PriceBar> prices = visiblePriceBars(priceBarsWithDraft(draft));
        Component lowestLabel = Component.translatable("trading_op.xero_delta.lowest");
        graphics.drawString(font, lowestLabel, x, y, theme.muted(), false);
        if (prices.isEmpty()) {
            graphics.drawCenteredString(font,
                Component.translatable("trading_op.xero_delta.no_listings"),
                x + areaWidth / 2, y + areaHeight / 2, theme.muted());
            return;
        }
        TradingUi.drawDetailedAmount(graphics, font, prices.getFirst().price(),
            x + font.width(lowestLabel) + 6, y - 1, theme.text());
        graphics.fill(x, y + 18, x + areaWidth, y + 19, theme.border());
        int chartTop = y + 48;
        int chartBottom = y + areaHeight - 26;
        int chartHeight = Math.max(70, chartBottom - chartTop);
        int gap = Math.max(5, areaWidth / Math.max(20, prices.size() * 5));
        int barWidth = Math.max(14, (areaWidth - gap * (prices.size() + 1)) / prices.size());
        int maxCount = prices.stream().mapToInt(PriceBar::amount).max().orElse(1);
        long selectedPrice = nearestPrice(prices, draft.price);
        for (int i = 0; i < prices.size(); i++) {
            PriceBar price = prices.get(i);
            int barX = x + gap + i * (barWidth + gap);
            int height = Math.max(8, chartHeight * Math.max(1, price.amount()) / maxCount);
            int barY = chartBottom - height;
            boolean selected = price.price() == selectedPrice;
            graphics.fill(barX, barY, barX + barWidth, chartBottom,
                selected ? 0x884FD9B0 : 0x553C4B51);
            graphics.drawCenteredString(font, String.valueOf(price.amount()), barX + barWidth / 2,
                barY - 12, theme.muted());
            String priceText = TradingUi.formatDetailed(price.price());
            graphics.drawCenteredString(font, font.plainSubstrByWidth(priceText, barWidth + gap),
                barX + barWidth / 2, chartBottom + 8, selected ? 0xFFFFD36A : theme.muted());
            if (selected) graphics.fill(barX - 1, chartBottom + 21, barX + barWidth + 1,
                chartBottom + 24, 0xFFFFD36A);
        }
    }

    private void drawListingControls(GuiGraphics graphics, Draft draft, int x, int y, int areaWidth,
                                     int areaHeight, int mouseX, int mouseY) {
        drawQualityName(graphics, draft.stack.getHoverName().getString(), x, y,
            areaWidth, qualityColor(ClientDataCache.INSTANCE.getQuality(draft.stack)), draft.id);
        graphics.pose().pushPose();
        graphics.pose().translate(x + areaWidth / 2 - 16, y + 20, 80);
        graphics.pose().scale(2.0F, 2.0F, 1.0F);
        graphics.renderItem(draft.stack, 0, 0);
        graphics.renderItemDecorations(font, draft.stack, 0, 0);
        graphics.pose().popPose();
        graphics.fill(x, y + 60, x + areaWidth, y + 61, theme.border());
        graphics.drawString(font, Component.translatable("trading_op.xero_delta.amount_range",
                draft.amount, draft.maximumAmount),
            x, y + 72, theme.muted(), false);
        drawStepButton(graphics, x, y + 88, "-", draft.amount > 1, mouseX, mouseY);
        drawStepButton(graphics, x + areaWidth - 20, y + 88, "+",
            draft.amount < draft.maximumAmount, mouseX, mouseY);
        graphics.drawString(font, Component.translatable("trading_op.xero_delta.price"),
            x, y + 116, theme.muted(), false);
        drawStepButton(graphics, x, y + 132, "-", draft.price > draft.minimumPrice, mouseX, mouseY);
        drawStepButton(graphics, x + areaWidth - 20, y + 132, "+",
            draft.price < draft.maximumPrice, mouseX, mouseY);
        long gross = totalListingPrice(draft);
        long expected = TradingRules.sellerProceeds(gross);
        Component expectedLabel = Component.translatable("trading_op.xero_delta.expected");
        graphics.drawString(font, expectedLabel, x, y + 170, 0xFFFFD36A, false);
        int expectedAmountX = x + font.width(expectedLabel) + 6;
        TradingUi.drawDetailedAmount(graphics, font, expected, expectedAmountX, y + 169, 0xFFFFD36A);
        IncomeInfoLayout incomeInfo = incomeInfoLayout(draft, x, y, areaWidth);
        drawIncomeInfoIcon(graphics, incomeInfo.centerX(), incomeInfo.centerY());
        int buttonY = y + 198;
        drawButton(graphics, x, buttonY, areaWidth, 24,
            Component.translatable("trading_op.xero_delta.list_now"), theme.accent(), mouseX, mouseY);
        graphics.drawString(font, Component.translatable("trading_op.xero_delta.duration",
            draft.durationDays, draft.maximumDurationDays), x, y + 232, theme.muted(), false);
    }

    private IncomeInfoLayout incomeInfoLayout(Draft draft, int x, int y, int areaWidth) {
        Component expectedLabel = Component.translatable("trading_op.xero_delta.expected");
        long expected = TradingRules.sellerProceeds(totalListingPrice(draft));
        int amountX = x + font.width(expectedLabel) + 6;
        int desiredCenterX = amountX + TradingUi.detailedAmountWidth(font, expected) + 10;
        return new IncomeInfoLayout(Math.min(x + areaWidth - 7, desiredCenterX), y + 175);
    }

    private void drawIncomeInfoIcon(GuiGraphics graphics, int centerX, int centerY) {
        graphics.blit(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
            "xero_delta", "textures/gui/action/info.png"),
            centerX - 7, centerY - 7, 0, 0, 14, 14, 16, 16);
    }


    private void drawIncomeTooltipIfHovered(GuiGraphics graphics, int mouseX, int mouseY) {
        Draft draft = selectedDraft();
        if (draft == null) return;
        DetailLayout detail = detailLayout();
        int contentY = detail.contentY() - detailScroll;
        IncomeInfoLayout info = incomeInfoLayout(draft, detail.contentX(), contentY, detail.areaWidth());
        if (!inside(mouseX, mouseY, info.centerX() - 7, info.centerY() - 7, 14, 14)
            || info.centerY() < detail.panelY() + 8
            || info.centerY() > detail.panelY() + detail.panelHeight() - 8) return;

        long gross = totalListingPrice(draft);
        long fee = TradingRules.tax(gross);
        long guarantee = TradingRules.guarantee(gross);
        long expected = TradingRules.sellerProceeds(gross);
        Component[] labels = {
            Component.translatable("trading_op.xero_delta.income_total"),
            Component.translatable("trading_op.xero_delta.income_fee"),
            Component.translatable("trading_op.xero_delta.income_guarantee"),
            Component.translatable("trading_op.xero_delta.income_expected")
        };
        long[] amounts = {gross, fee, guarantee, expected};
        int tooltipWidth = 148;
        for (int index = 0; index < labels.length; index++) {
            tooltipWidth = Math.max(tooltipWidth,
                font.width(labels[index]) + TradingUi.detailedAmountWidth(font, amounts[index]) + 26);
        }
        tooltipWidth = Math.min(width - 16, tooltipWidth);
        int tooltipHeight = 72;
        int tooltipX = Math.max(8, Math.min(width - tooltipWidth - 8,
            info.centerX() - tooltipWidth / 2));
        int tooltipY = Math.max(8, info.centerY() - tooltipHeight - 10);
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 650);
        graphics.fill(tooltipX, tooltipY, tooltipX + tooltipWidth, tooltipY + tooltipHeight, 0xF20B1115);
        graphics.renderOutline(tooltipX, tooltipY, tooltipWidth, tooltipHeight, theme.border());
        for (int index = 0; index < labels.length; index++) {
            int rowY = tooltipY + 8 + index * 16;
            if (index == labels.length - 1) {
                graphics.fill(tooltipX + 6, rowY - 4, tooltipX + tooltipWidth - 6, rowY - 3, theme.border());
            }
            int color = index == labels.length - 1 ? 0xFFFFD36A : theme.muted();
            graphics.drawString(font, labels[index], tooltipX + 8, rowY, color, false);
            int amountWidth = TradingUi.detailedAmountWidth(font, amounts[index]);
            TradingUi.drawDetailedAmount(graphics, font, amounts[index],
                tooltipX + tooltipWidth - amountWidth - 8, rowY - 1, color);
        }
        graphics.pose().popPose();
    }

    private void drawStepButton(GuiGraphics graphics, int x, int y, String label, boolean enabled,
                                int mouseX, int mouseY) {
        int color = !enabled ? 0xFF263338
            : inside(mouseX, mouseY, x, y, 20, 20) ? theme.panelAlt() : 0xFF40545B;
        graphics.fill(x, y, x + 20, y + 20, color);
        graphics.drawCenteredString(font, label, x + 10, y + 7, enabled ? theme.text() : theme.muted());
    }

    private void drawQualityName(GuiGraphics graphics, String text, int x, int y,
                                 int availableWidth, int qualityColor, long phase) {
        int textWidth = Math.min(availableWidth, font.width(text));
        graphics.fill(x - 2, y - 2, x + textWidth + 3, y + 10,
            0xB0000000 | qualityColor & 0x00FFFFFF);
        TradingUi.drawMarquee(graphics, font, text, x, y, availableWidth,
            theme.text(), phase, uiViewport);
    }

    private List<PriceBar> matchingPriceBars(ItemStack stack) {
        TreeMap<Long, Integer> values = new TreeMap<>();
        for (TradingSyncPacket.ListingView listing : state.listings()) {
            if (!listing.expired() && ItemStack.isSameItemSameComponents(stack, listing.stack())) {
                long unitPrice = Math.max(1L,
                    (listing.price() + Math.max(1, listing.stack().getCount()) - 1L)
                        / Math.max(1, listing.stack().getCount()));
                values.merge(unitPrice, listing.stack().getCount(),
                    (first, second) -> Math.min(Integer.MAX_VALUE, first + second));
            }
        }
        List<PriceBar> result = new ArrayList<>();
        values.forEach((price, amount) -> result.add(new PriceBar(price, amount)));
        return result;
    }

    private List<PriceBar> priceBarsWithDraft(Draft draft) {
        List<PriceBar> market = matchingPriceBars(draft.stack);
        if (market.isEmpty()) return List.of(new PriceBar(draft.price, draft.amount));
        long lowest = market.getFirst().price();
        long highest = market.getLast().price();
        if (draft.price > lowest && draft.price < highest) return market;
        TreeMap<Long, Integer> values = new TreeMap<>();
        for (PriceBar bar : market) values.put(bar.price(), bar.amount());
        values.merge(draft.price, draft.amount,
            (first, second) -> Math.min(Integer.MAX_VALUE, first + second));
        List<PriceBar> result = new ArrayList<>();
        values.forEach((price, amount) -> result.add(new PriceBar(price, amount)));
        return result;
    }

    private static List<PriceBar> visiblePriceBars(List<PriceBar> prices) {
        if (prices.size() <= 8) return prices;
        List<PriceBar> result = new ArrayList<>(8);
        for (int i = 0; i < 8; i++) {
            PriceBar price = prices.get((int) Math.round(i * (prices.size() - 1) / 7.0D));
            if (result.isEmpty() || result.getLast().price() != price.price()) result.add(price);
        }
        return result;
    }

    private static long nearestPrice(List<PriceBar> prices, long value) {
        long nearest = prices.getFirst().price();
        long distance = Math.abs(nearest - value);
        for (PriceBar price : prices) {
            long currentDistance = Math.abs(price.price() - value);
            if (currentDistance < distance) {
                nearest = price.price();
                distance = currentDistance;
            }
        }
        return nearest;
    }

    private void drawButton(GuiGraphics graphics, int x, int y, int width, int height, Component label,
                            int color, int mouseX, int mouseY) {
        graphics.fill(x, y, x + width, y + height,
            inside(mouseX, mouseY, x, y, width, height) ? brighten(color) : color);
        graphics.drawCenteredString(font, label, x + width / 2, y + 8,
            color == theme.accent() ? 0xFF071214 : theme.text());
    }

    private void drawToast(GuiGraphics graphics) {
        if (System.currentTimeMillis() >= toastUntil || toast.isBlank()) return;
        int toastWidth = Math.min(width - 40, font.width(toast) + 30);
        int x = (width - toastWidth) / 2;
        int y = detailMode ? 7 : height - 38;
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 500);
        graphics.fill(x, y, x + toastWidth, y + 28, toastSuccess ? 0xF02B4942 : 0xF05A2928);
        graphics.drawCenteredString(font, toast, width / 2, y + 10, theme.text());
        graphics.pose().popPose();
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        mouseX = uiViewport.mouseXDouble(mouseX);
        mouseY = uiViewport.mouseYDouble(mouseY);
        if (ItemDetailOverlay.mouseClicked(this, mouseX, mouseY, button)) return true;
        if (listingUnlockDialogOpen) return handleListingUnlockDialogClick(mouseX, mouseY, button);
        if (!datePickerOpen && TradingUiScale.zoomOutClicked(mouseX, mouseY, width)) {
            adjustPageZoom(-1);
            return true;
        }
        if (!datePickerOpen && TradingUiScale.zoomInClicked(mouseX, mouseY, width)) {
            adjustPageZoom(1);
            return true;
        }
        if (!datePickerOpen && TradingUi.backButtonClicked(mouseX, mouseY, width)) {
            if (datePickerOpen) closeDatePicker();
            else if (detailMode) leaveDetail();
            else navigateBack();
            return true;
        }
        if (detailMode) return batchRelistMode
            ? batchRelistMouseClicked(mouseX, mouseY, button)
            : detailMouseClicked(mouseX, mouseY, button);
        if (datePickerOpen) {
            EditBox activeEditor = activeCalendarEditor();
            if (activeEditor != null && inside(mouseX, mouseY, activeEditor.getX(), activeEditor.getY(),
                activeEditor.getWidth(), activeEditor.getHeight())) {
                return super.mouseClicked(mouseX, mouseY, button);
            }
            return handleDatePickerClick(mouseX, mouseY, button);
        }
        if (handleWorkspaceTabClick(mouseX, mouseY)) return true;
        int multiSelectX = multiSelectX();
        Component ownedTitle = Component.translatable("trading_op.xero_delta.owned_title",
            ownedListingCount(), state.playerListingSlots());
        int unlockButtonX = Math.min(multiSelectX - 24, 18 + font.width(ownedTitle));
        if (inside(mouseX, mouseY, unlockButtonX, 80, 20, 20)) {
            listingUnlockMatches = FtbQuestIntegration.findListingSlotUnlocks();
            listingUnlockDialogOpen = true;
            return true;
        }
        if (inside(mouseX, mouseY, multiSelectX, 80, 48, 20)) {
            ownedMultiSelect = !ownedMultiSelect;
            selectedOwnedListings.clear();
            draftScroll = Math.min(draftScroll, draftMaxScroll());
            draftScrollTarget = draftScroll;
            return true;
        }
        if (handleSourceCategoryClick(mouseX, mouseY)) return true;
        if (ownedMultiSelect && handleOwnedBatchClick(mouseX, mouseY)) return true;
        int sourceControlX = width - 104;
        if (inside(mouseX, mouseY, sourceControlX, 52, 20, 20) && sourceColumns > 1) {
            sourceColumns--;
            TradingUiPreferences.setOperatorSourceColumns(sourceColumns);
            sourceScroll = 0;
            sourceScrollTarget = 0.0D;
            return true;
        }
        int ownedControlX = splitX() - 104;
        if (inside(mouseX, mouseY, splitX() - 160, 80, 48, 20)) {
            openDatePicker();
            return true;
        }
        if (inside(mouseX, mouseY, ownedControlX, 80, 20, 20) && ownedColumns > 1) {
            ownedColumns--;
            TradingUiPreferences.setOperatorOwnedColumns(ownedColumns);
            draftScroll = 0;
            draftScrollTarget = 0.0D;
            return true;
        }
        if (inside(mouseX, mouseY, ownedControlX + 50, 80, 20, 20) && ownedColumns < 3) {
            ownedColumns++;
            TradingUiPreferences.setOperatorOwnedColumns(ownedColumns);
            draftScroll = 0;
            draftScrollTarget = 0.0D;
            return true;
        }
        if (inside(mouseX, mouseY, sourceControlX + 50, 52, 20, 20) && sourceColumns < 10) {
            sourceColumns++;
            TradingUiPreferences.setOperatorSourceColumns(sourceColumns);
            sourceScroll = 0;
            sourceScrollTarget = 0.0D;
            return true;
        }
        if (button == 0 && !touchReleaseClick && workspaceListAt(mouseX, mouseY) != 0) {
            touchPressed = true;
            touchDragging = false;
            touchList = workspaceListAt(mouseX, mouseY);
            touchStartX = mouseX;
            touchStartY = mouseY;
            touchLastY = mouseY;
            return true;
        }
        if (mouseX < splitX() && mouseY >= WORKSPACE_TOP && mouseY < ownedListBottom()) {
            int left = 12;
            int right = splitX() - 10;
            int columns = ownedGridColumns(left, right);
            int cardWidth = Math.max(100, (right - left - OWNED_ROW_GAP * (columns - 1) - 5) / columns);
            int localX = (int) mouseX - left;
            int localY = (int) mouseY - WORKSPACE_TOP + draftScroll;
            int column = localX / (cardWidth + OWNED_ROW_GAP);
            int row = localY / (OWNED_ROW_HEIGHT + OWNED_ROW_GAP);
            int withinX = localX % (cardWidth + OWNED_ROW_GAP);
            int index = row * columns + column;
            int within = localY % (OWNED_ROW_HEIGHT + OWNED_ROW_GAP);
            List<TradingSyncPacket.ListingView> owned = filteredOwnedListings();
            if (column >= 0 && column < columns && withinX < cardWidth
                && index >= 0 && index < owned.size() && within < OWNED_ROW_HEIGHT) {
                TradingSyncPacket.ListingView listing = owned.get(index);
                int rowY = WORKSPACE_TOP + row * (OWNED_ROW_HEIGHT + OWNED_ROW_GAP) - draftScroll;
                int cardX = left + column * (cardWidth + OWNED_ROW_GAP);
                OwnedAction action = ownedActionAt(mouseX, mouseY, cardX + cardWidth - 4, rowY, listing);
                if (action != null) {
                    performOwnedAction(action, listing);
                } else if (ownedMultiSelect) {
                    if (!selectedOwnedListings.add(listing.id())) selectedOwnedListings.remove(listing.id());
                }
                return true;
            }
        }
        if (mouseX >= splitX() && mouseY >= WORKSPACE_TOP) {
            int left = splitX() + 12;
            int right = width - 12;
            SourceLayout layout = sourceLayout(left, right);
            for (SourceCardPlacement card : layout.cards()) {
                int y = WORKSPACE_TOP + card.y() - sourceScroll;
                if (inside(mouseX, mouseY, card.x(), y, card.width(), SOURCE_CELL_HEIGHT)) {
                    addDraft(card.source());
                    return true;
                }
            }
        }
        if (beginScrollbarDrag(mouseX, mouseY)) return true;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean handleListingUnlockDialogClick(double mouseX, double mouseY, int button) {
        if (button != 0) return true;
        int visibleTasks = Math.min(3, listingUnlockMatches.size());
        int panelWidth = Math.min(390, width - 32);
        int panelHeight = 154 + visibleTasks * 27;
        int x = (width - panelWidth) / 2;
        int y = (height - panelHeight) / 2;
        if (inside(mouseX, mouseY, x + panelWidth - 28, y + 6, 20, 20)) {
            listingUnlockDialogOpen = false;
            return true;
        }
        int rowY = y + 87;
        for (int index = 0; index < visibleTasks; index++) {
            if (inside(mouseX, mouseY, x + 12, rowY, panelWidth - 24, 22)) {
                if (!FtbQuestIntegration.open(listingUnlockMatches.get(index))) {
                    showLocalToast("market.xero_delta.quest_open_failed", false);
                } else {
                    listingUnlockDialogOpen = false;
                }
                return true;
            }
            rowY += 27;
        }
        if (state.playerListingSlots() < state.maxPlayerListingSlots()
            && inside(mouseX, mouseY, x + 12, y + panelHeight - 34, panelWidth - 24, 24)) {
            ModNetwork.sendToServer(TradingActionPacket.unlockListingSlot());
            listingUnlockDialogOpen = false;
        }
        return true;
    }

    private int workspaceListAt(double mouseX, double mouseY) {
        int split = splitX();
        if (mouseX >= 12 && mouseX < split - 16
            && mouseY >= WORKSPACE_TOP && mouseY < ownedListBottom()) return 1;
        if (mouseX >= split + 12 && mouseX < width - 20
            && mouseY >= WORKSPACE_TOP && mouseY < height - 12) return 2;
        return 0;
    }

    private void openDatePicker() {
        datePickerOpen = true;
        calendarEditTarget = CalendarEditTarget.NONE;
        datePopupOpenedAt = System.currentTimeMillis();
        calendarSelectedDates.clear();
        calendarSelectedDates.addAll(dateFilters);
        calendarSelectionAnchor = calendarSelectedDates.stream().reduce((first, second) -> second).orElse(null);
        calendarMonth = calendarSelectionAnchor == null
            ? YearMonth.now() : YearMonth.from(calendarSelectionAnchor);
        sourceSearch.visible = false;
        draftSearch.visible = false;
        calendarYearBox.visible = false;
        calendarMonthBox.visible = false;
        setFocused(null);
    }

    private boolean handleDatePickerClick(double mouseX, double mouseY, int button) {
        if (button != 0) return true;
        CalendarLayout layout = calendarLayout();
        CalendarHeaderLayout header = calendarHeaderLayout(layout);
        int x = layout.panelX();
        int y = layout.panelY();
        if (inside(mouseX, mouseY, x + layout.panelWidth() - 30, y + 7, 20, 18)) {
            closeDatePicker();
            return true;
        }
        if (inside(mouseX, mouseY, header.yearX(), header.y(), header.yearWidth(), 20)) {
            beginCalendarEdit(CalendarEditTarget.YEAR);
            return true;
        }
        if (inside(mouseX, mouseY, header.monthX(), header.y(), header.monthWidth(), 20)) {
            beginCalendarEdit(CalendarEditTarget.MONTH);
            return true;
        }
        applyCalendarEditor();
        if (inside(mouseX, mouseY, x + 14, y + 25, 24, 20)) {
            calendarMonth = calendarMonth.minusMonths(1);
            return true;
        }
        if (inside(mouseX, mouseY, x + layout.panelWidth() - 38, y + 25, 24, 20)) {
            calendarMonth = calendarMonth.plusMonths(1);
            return true;
        }
        int gridWidth = layout.cellWidth() * 7;
        int gridHeight = layout.cellHeight() * 6;
        if (inside(mouseX, mouseY, layout.gridX(), layout.gridY(), gridWidth, gridHeight)) {
            int column = Math.min(6, Math.max(0,
                ((int) mouseX - layout.gridX()) / layout.cellWidth()));
            int row = Math.min(5, Math.max(0,
                ((int) mouseY - layout.gridY()) / layout.cellHeight()));
            LocalDate selected = calendarStartDate().plusDays(row * 7L + column);
            calendarSelectionAnchor = CalendarDateSelection.select(calendarSelectedDates,
                calendarSelectionAnchor, selected, hasControlDown(), hasShiftDown());
            calendarMonth = YearMonth.from(selected);
            return true;
        }
        int gap = 10;
        int buttonWidth = (layout.panelWidth() - 32 - gap * 2) / 3;
        if (inside(mouseX, mouseY, x + 16, layout.footerY(), buttonWidth, 24)) {
            LocalDate today = LocalDate.now();
            calendarSelectedDates.clear();
            calendarSelectedDates.add(today);
            calendarSelectionAnchor = today;
            calendarMonth = YearMonth.from(today);
            return true;
        }
        if (inside(mouseX, mouseY, x + 16 + buttonWidth + gap,
            layout.footerY(), buttonWidth, 24)) {
            dateFilters.clear();
            calendarSelectedDates.clear();
            calendarSelectionAnchor = null;
            resetOwnedDateFilterScroll();
            closeDatePicker();
            return true;
        }
        if (inside(mouseX, mouseY, x + 16 + (buttonWidth + gap) * 2,
            layout.footerY(), buttonWidth, 24)) {
            dateFilters.clear();
            dateFilters.addAll(calendarSelectedDates);
            resetOwnedDateFilterScroll();
            closeDatePicker();
            return true;
        }
        return true;
    }

    private void resetOwnedDateFilterScroll() {
        draftScroll = 0;
        draftScrollTarget = 0.0D;
        selectedOwnedListings.clear();
    }

    private void closeDatePicker() {
        applyCalendarEditor();
        datePickerOpen = false;
        calendarEditTarget = CalendarEditTarget.NONE;
        calendarYearBox.visible = false;
        calendarMonthBox.visible = false;
        calendarSelectedDates.clear();
        calendarSelectionAnchor = null;
        sourceSearch.visible = true;
        draftSearch.visible = true;
        setFocused(null);
    }

    private void beginCalendarEdit(CalendarEditTarget target) {
        applyCalendarEditor();
        calendarEditTarget = target;
        positionCalendarEditors(calendarHeaderLayout(calendarLayout()));
        calendarYearBox.visible = target == CalendarEditTarget.YEAR;
        calendarMonthBox.visible = target == CalendarEditTarget.MONTH;
        EditBox editor = activeCalendarEditor();
        if (editor == null) return;
        editor.setValue(String.valueOf(target == CalendarEditTarget.YEAR
            ? calendarMonth.getYear() : calendarMonth.getMonthValue()));
        editor.setFocused(true);
        setFocused(editor);
    }

    private void applyCalendarEditor() {
        EditBox editor = activeCalendarEditor();
        CalendarEditTarget target = calendarEditTarget;
        if (editor != null && !editor.getValue().isBlank()) {
            if (target == CalendarEditTarget.YEAR) {
                int year = (int) clamp(parseLong(editor.getValue(), calendarMonth.getYear()), 1L, 9999L);
                calendarMonth = YearMonth.of(year, calendarMonth.getMonthValue());
            } else if (target == CalendarEditTarget.MONTH) {
                int month = (int) clamp(parseLong(editor.getValue(), calendarMonth.getMonthValue()), 1L, 12L);
                calendarMonth = YearMonth.of(calendarMonth.getYear(), month);
            }
        }
        hideCalendarEditors();
    }

    private void cancelCalendarEditor() {
        hideCalendarEditors();
    }

    private void hideCalendarEditors() {
        calendarEditTarget = CalendarEditTarget.NONE;
        if (calendarYearBox != null) {
            calendarYearBox.visible = false;
            calendarYearBox.setFocused(false);
        }
        if (calendarMonthBox != null) {
            calendarMonthBox.visible = false;
            calendarMonthBox.setFocused(false);
        }
        setFocused(null);
    }

    private EditBox activeCalendarEditor() {
        return switch (calendarEditTarget) {
            case YEAR -> calendarYearBox;
            case MONTH -> calendarMonthBox;
            case NONE -> null;
        };
    }

    private boolean handleWorkspaceTabClick(double mouseX, double mouseY) {
        int tabWidth = Math.max(54, Math.min(82, width / 8));
        int startX = (width - tabWidth * 3 - 8) / 2;
        if (inside(mouseX, mouseY, startX, 8, tabWidth, 28)) {
            state.rememberCursor();
            ModNetwork.sendToServer(TradingActionPacket.openMarket("buy"));
            return true;
        }
        int historyX = startX + 2 * (tabWidth + 4);
        if (inside(mouseX, mouseY, historyX, 8, tabWidth, 28)) {
            state.rememberCursor();
            ModNetwork.sendToServer(TradingActionPacket.openMarket("history"));
            return true;
        }
        return false;
    }

    private boolean handleOwnedBatchClick(double mouseX, double mouseY) {
        int split = splitX();
        int y = height - 38;
        int buttonWidth = Math.max(48, Math.min(76, (split - 112) / 3));
        int cancelX = split - 12 - buttonWidth;
        int relistX = cancelX - 6 - buttonWidth;
        int selectAllX = relistX - 6 - buttonWidth;
        if (inside(mouseX, mouseY, selectAllX, y, buttonWidth, 22)) {
            List<TradingSyncPacket.ListingView> values = filteredOwnedListings();
            if (allFilteredOwnedSelected()) {
                for (TradingSyncPacket.ListingView listing : values) selectedOwnedListings.remove(listing.id());
            } else {
                for (TradingSyncPacket.ListingView listing : values) selectedOwnedListings.add(listing.id());
            }
            return true;
        }
        List<TradingSyncPacket.ListingView> selected = selectedOwnedViews();
        if (inside(mouseX, mouseY, relistX, y, buttonWidth, 22)) {
            List<TradingSyncPacket.ListingView> expired = selected.stream().filter(this::isExpired).toList();
            if (!expired.isEmpty()) openBatchRelistDrafts(expired);
            return true;
        }
        if (inside(mouseX, mouseY, cancelX, y, buttonWidth, 22)) {
            if (!selected.isEmpty()) {
                for (TradingSyncPacket.ListingView listing : selected) {
                    ModNetwork.sendToServer(TradingActionPacket.listing(TradingActionPacket.Action.CANCEL, listing.id()));
                }
                finishOwnedBatchAction();
            }
            return true;
        }
        return false;
    }

    private void finishOwnedBatchAction() {
        selectedOwnedListings.clear();
        ownedMultiSelect = false;
        draftScroll = Math.min(draftScroll, draftMaxScroll());
        draftScrollTarget = draftScroll;
    }

    private List<TradingSyncPacket.ListingView> selectedOwnedViews() {
        if (selectedOwnedListings.isEmpty()) return List.of();
        List<TradingSyncPacket.ListingView> selected = new ArrayList<>();
        for (TradingSyncPacket.ListingView listing : filteredOwnedListings()) {
            if (selectedOwnedListings.contains(listing.id())) selected.add(listing);
        }
        return selected;
    }

    private boolean allFilteredOwnedSelected() {
        List<TradingSyncPacket.ListingView> values = filteredOwnedListings();
        return !values.isEmpty() && values.stream()
            .allMatch(listing -> selectedOwnedListings.contains(listing.id()));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        mouseX = uiViewport.mouseXDouble(mouseX);
        mouseY = uiViewport.mouseYDouble(mouseY);
        if (!datePickerOpen && hasControlDown() && scrollY != 0.0D) {
            adjustPageZoom(scrollY > 0.0D ? 1 : -1);
            return true;
        }
        if (sourceCategoryOpen && inside(mouseX, mouseY, sourceCategoryControlX(),
            104, sourceCategoryControlWidth(), sourceCategoryVisibleRows() * 24 + 6)) {
            int maximum = Math.max(0, sourceCategoryRows().size() - sourceCategoryVisibleRows());
            sourceCategoryScroll = Math.max(0, Math.min(maximum,
                sourceCategoryScroll - (int) Math.signum(scrollY)));
            return true;
        }
        if (datePickerOpen) {
            applyCalendarEditor();
            if (scrollY > 0.0D) calendarMonth = calendarMonth.minusMonths(1);
            else if (scrollY < 0.0D) calendarMonth = calendarMonth.plusMonths(1);
            return true;
        }
        if (detailMode) {
            if (batchRelistMode) {
                DetailLayout layout = detailLayout();
                if (mouseX < layout.divider()) {
                    batchRelistScrollTarget = Math.max(0.0D, Math.min(batchRelistMaxScroll(),
                        batchRelistScrollTarget - Math.signum(scrollY) * 38.0D));
                    return true;
                }
                batchEditorScrollTarget = Math.max(0.0D, Math.min(batchEditorMaxScroll(),
                    batchEditorScrollTarget - Math.signum(scrollY) * 30.0D));
                return true;
            }
            DetailLayout layout = detailLayout();
            if (mouseX >= layout.divider() && mouseX <= layout.panelX() + layout.panelWidth()) {
                detailScrollTarget = Math.max(0.0D,
                    Math.min(detailMaxScroll(), detailScrollTarget - Math.signum(scrollY) * 34.0D));
                return true;
            }
            return false;
        }
        if (mouseX < splitX()) {
            draftScrollTarget = Math.max(0.0D,
                Math.min(draftMaxScroll(), draftScrollTarget - Math.signum(scrollY) * 42.0D));
        } else {
            sourceScrollTarget = Math.max(0.0D,
                Math.min(sourceMaxScroll(), sourceScrollTarget - Math.signum(scrollY) * 42.0D));
        }
        return true;
    }

    private boolean detailMouseClicked(double mouseX, double mouseY, int button) {
        if (beginDetailScrollbarDrag(mouseX, mouseY)) return true;
        DetailLayout layout = detailLayout();
        int panelWidth = layout.panelWidth();
        int panelHeight = layout.panelHeight();
        int panelX = layout.panelX();
        int panelY = layout.panelY();
        int divider = layout.divider();
        int x = layout.contentX();
        int y = layout.contentY() - detailScroll;
        int areaWidth = layout.areaWidth();
        Draft draft = selectedDraft();
        if (draft != null) {
            int detailItemX = x + areaWidth / 2 - 16;
            int detailItemY = y + 20;
            if (button == 0 && inside(mouseX, mouseY, detailItemX, detailItemY, 32, 32)) {
                ItemDetailOverlay.openSourceOnly(this, draft.stack,
                    detailItemX, detailItemY, 32, 32);
                hoveredStack = ItemStack.EMPTY;
                return true;
            }
            if (document.actions().contains("decrease-amount")
                && inside(mouseX, mouseY, x, y + 88, 20, 20)) {
                stepAmount(draft, -1);
                return true;
            }
            if (document.actions().contains("increase-amount")
                && inside(mouseX, mouseY, x + areaWidth - 20, y + 88, 20, 20)) {
                stepAmount(draft, 1);
                return true;
            }
            if (document.actions().contains("decrease-price")
                && inside(mouseX, mouseY, x, y + 132, 20, 20)) {
                stepPrice(draft, -1);
                return true;
            }
            if (document.actions().contains("increase-price")
                && inside(mouseX, mouseY, x + areaWidth - 20, y + 132, 20, 20)) {
                stepPrice(draft, 1);
                return true;
            }
            if (inside(mouseX, mouseY, amountSlider.getX(), amountSlider.getY(),
                amountSlider.getWidth(), amountSlider.getHeight())) {
                draggingAmountSlider = true;
                amountSlider.setFromMouse(mouseX);
                return true;
            }
            if (inside(mouseX, mouseY, durationSlider.getX(), durationSlider.getY(),
                durationSlider.getWidth(), durationSlider.getHeight())) {
                draggingDurationSlider = true;
                durationSlider.setFromMouse(mouseX);
                return true;
            }
            long clickedPrice = clickedChartPrice(mouseX, mouseY, draft, panelX + 18, panelY + 18,
                divider - panelX - 36, panelHeight - 36);
            if (clickedPrice >= 0L) {
                draft.price = clamp(clickedPrice, draft.minimumPrice, draft.maximumPrice);
                syncingFields = true;
                priceBox.setValue(String.valueOf(draft.price));
                syncingFields = false;
                return true;
            }
        }
        if (document.actions().contains("list-current")
            && inside(mouseX, mouseY, x, y + 198, areaWidth, 24)) {
            submitSelectedDraft();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean batchRelistMouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && beginBatchScrollbarDrag(mouseX, mouseY)) return true;
        DetailLayout layout = detailLayout();
        int panelX = layout.panelX();
        int panelY = layout.panelY();
        int divider = layout.divider();
        int listX = panelX + 14;
        int listY = panelY + 16;
        int listWidth = divider - panelX - 28;
        int listBottom = panelY + layout.panelHeight() - 16;
        if (creativeBatchMode && creativeBatchSearch != null
            && creativeBatchSearch.isMouseOver(mouseX, mouseY)) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        if (creativeBatchMode) {
            int controlsX = listX + listWidth - 74;
            if (inside(mouseX, mouseY, controlsX, listY, 20, 20) && creativeBatchColumns > 1) {
                creativeBatchColumns--;
                resetCreativeBatchScroll();
                return true;
            }
            if (inside(mouseX, mouseY, controlsX + 54, listY, 20, 20)
                && creativeBatchColumns < CREATIVE_BATCH_MAX_COLUMNS) {
                creativeBatchColumns++;
                resetCreativeBatchScroll();
                return true;
            }
            int gridTop = listY + 28;
            if (button == 0 && inside(mouseX, mouseY, listX, gridTop,
                listWidth, listBottom - gridTop)) {
                if (!touchReleaseClick) {
                    touchPressed = true;
                    touchDragging = false;
                    touchList = 3;
                    touchStartX = mouseX;
                    touchStartY = mouseY;
                    touchLastY = mouseY;
                    return true;
                }
                Draft clicked = creativeBatchDraftAt(mouseX, mouseY, listX, gridTop, listWidth);
                if (clicked != null) {
                    updateCreativeBatchSelection(clicked);
                    return true;
                }
            }
        } else
        if (inside(mouseX, mouseY, listX, listY, listWidth, listBottom - listY)) {
            int index = ((int) mouseY - listY + batchRelistScroll) / 76;
            if (index >= 0 && index < drafts.size()) {
                Draft draft = drafts.get(index);
                int rowY = listY + index * 76 - batchRelistScroll;
                if (inside(mouseX, mouseY, listX + 8, rowY + 20, 28, 28)) {
                    if (!selectedBatchDraftIds.add(draft.id)) selectedBatchDraftIds.remove(draft.id);
                } else {
                    selectDraft(draft);
                }
                return true;
            }
        }
        Draft draft = selectedDraft();
        if (draft == null) return true;
        int x = layout.contentX();
        int y = layout.contentY();
        int areaWidth = layout.areaWidth();
        int contentY = y - batchEditorScroll;
        if (inside(mouseX, mouseY, x, contentY, areaWidth, 22)) {
            toggleAllBatchDrafts();
            return true;
        }
        if (creativeBatchMode && inside(mouseX, mouseY, x, contentY + 130, 20, 20)) {
            stepAmount(draft, -1);
            return true;
        }
        if (creativeBatchMode && inside(mouseX, mouseY, x + areaWidth - 20, contentY + 130, 20, 20)) {
            stepAmount(draft, 1);
            return true;
        }
        if (creativeBatchMode && inside(mouseX, mouseY, amountSlider.getX(), amountSlider.getY(),
            amountSlider.getWidth(), amountSlider.getHeight())) {
            draggingAmountSlider = true;
            amountSlider.setFromMouse(mouseX);
            return true;
        }
        if (inside(mouseX, mouseY, x, contentY + 188, 20, 20)) {
            stepPrice(draft, -1);
            return true;
        }
        if (inside(mouseX, mouseY, x + areaWidth - 20, contentY + 188, 20, 20)) {
            stepPrice(draft, 1);
            return true;
        }
        int buttonY = panelY + layout.panelHeight() - 46;
        if (inside(mouseX, mouseY, x, buttonY, areaWidth, 24)
            && !selectedBatchDraftIds.isEmpty()) {
            submitBatchRelistDrafts();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private int batchRelistMaxScroll() {
        DetailLayout layout = detailLayout();
        if (creativeBatchMode) {
            int listWidth = layout.divider() - layout.panelX() - 28;
            int columns = creativeBatchGridColumns(listWidth);
            int rows = (filteredCreativeBatchDrafts().size() + columns - 1) / columns;
            int contentHeight = rows * (CREATIVE_BATCH_CARD_HEIGHT + CREATIVE_BATCH_CARD_GAP);
            return Math.max(0, contentHeight - (layout.panelHeight() - 60));
        }
        return Math.max(0, drafts.size() * 76 - (layout.panelHeight() - 32));
    }

    private int batchEditorMaxScroll() {
        DetailLayout layout = detailLayout();
        int viewport = Math.max(1, layout.areaHeight() - 36);
        return Math.max(0, BATCH_EDITOR_CONTENT_HEIGHT - viewport);
    }

    private void submitBatchRelistDrafts() {
        applyEditor();
        List<Draft> submitted = drafts.stream()
            .filter(draft -> selectedBatchDraftIds.contains(draft.id))
            .toList();
        for (Draft draft : submitted) sendDraft(draft);
        drafts.clear();
        selectedBatchDraftIds.clear();
        batchRelistMode = false;
        creativeBatchMode = false;
        selectedDraftId = -1L;
        leaveDetail();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (datePickerOpen && calendarEditTarget != CalendarEditTarget.NONE) {
            if (keyCode == 257 || keyCode == 335 || keyCode == 258) {
                applyCalendarEditor();
                return true;
            }
            if (keyCode == 256) {
                cancelCalendarEditor();
                return true;
            }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        if (keyCode == 256) {
            if (ItemDetailOverlay.handleEscape(this)) return true;
            if (listingUnlockDialogOpen) listingUnlockDialogOpen = false;
            else if (datePickerOpen) closeDatePicker();
            else if (detailMode) leaveDetail();
            else navigateBack();
            return true;
        }
        if (minecraft != null && minecraft.options.keyInventory.matches(keyCode, scanCode)) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void stepAmount(Draft draft, int direction) {
        int requested = (int) clamp(draft.amount + direction, 1L, draft.maximumAmount);
        if (creativeBatchMode) {
            for (Draft selected : selectedBatchDrafts()) {
                selected.amount = (int) clamp(requested, 1L, selected.maximumAmount);
            }
        } else {
            draft.amount = requested;
        }
        syncingFields = true;
        amountSlider.setLongValue(requested);
        syncingFields = false;
    }

    private void stepPrice(Draft draft, int direction) {
        if (creativeBatchMode) {
            applyCreativeBatchOffset(draft.priceOffset + direction * draft.priceStep);
            return;
        }
        draft.price = clamp(draft.price + direction * draft.priceStep,
            draft.minimumPrice, draft.maximumPrice);
        syncingFields = true;
        priceBox.setValue(String.valueOf(draft.price));
        syncingFields = false;
    }

    private long clickedChartPrice(double mouseX, double mouseY, Draft draft,
                                   int x, int y, int areaWidth, int areaHeight) {
        List<PriceBar> prices = visiblePriceBars(priceBarsWithDraft(draft));
        if (prices.isEmpty()) return -1L;
        int chartTop = y + 48;
        int chartBottom = y + areaHeight - 26;
        if (mouseY < chartTop - 12 || mouseY > chartBottom + 24) return -1L;
        int gap = Math.max(5, areaWidth / Math.max(20, prices.size() * 5));
        int barWidth = Math.max(14, (areaWidth - gap * (prices.size() + 1)) / prices.size());
        for (int i = 0; i < prices.size(); i++) {
            int barX = x + gap + i * (barWidth + gap);
            if (mouseX >= barX - gap / 2.0D && mouseX < barX + barWidth + gap / 2.0D) {
                return prices.get(i).price();
            }
        }
        return -1L;
    }


    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        mouseX = uiViewport.mouseXDouble(mouseX);
        mouseY = uiViewport.mouseYDouble(mouseY);
        dragX = uiViewport.deltaX(dragX);
        dragY = uiViewport.deltaY(dragY);
        if (draggingAmountSlider) {
            amountSlider.setFromMouse(mouseX);
            return true;
        }
        if (draggingDurationSlider) {
            durationSlider.setFromMouse(mouseX);
            return true;
        }
        if (draggingScrollbar != 0) {
            updateDraggedScroll(mouseY);
            return true;
        }
        if (touchPressed && button == 0) {
            double movedX = mouseX - touchStartX;
            double movedY = mouseY - touchStartY;
            if (!touchDragging && movedX * movedX + movedY * movedY >= 25.0D) touchDragging = true;
            if (touchDragging) {
                int amount = (int) Math.round(mouseY - touchLastY);
                if (touchList == 1) {
                    draftScroll = Mth.clamp(draftScroll - amount, 0, draftMaxScroll());
                    draftScrollTarget = draftScroll;
                } else if (touchList == 2) {
                    sourceScroll = Mth.clamp(sourceScroll - amount, 0, sourceMaxScroll());
                    sourceScrollTarget = sourceScroll;
                } else if (touchList == 3) {
                    batchRelistScroll = Mth.clamp(batchRelistScroll - amount,
                        0, batchRelistMaxScroll());
                    batchRelistScrollTarget = batchRelistScroll;
                }
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
        if (draggingAmountSlider) {
            draggingAmountSlider = false;
            amountSlider.setFromMouse(mouseX);
            return true;
        }
        if (draggingDurationSlider) {
            draggingDurationSlider = false;
            durationSlider.setFromMouse(mouseX);
            return true;
        }
        if (draggingScrollbar != 0) {
            draggingScrollbar = 0;
            return true;
        }
        if (touchPressed && button == 0) {
            boolean click = !touchDragging;
            touchPressed = false;
            touchDragging = false;
            touchList = 0;
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

    private void addDraft(TradingSyncPacket.SourceView source) {
        creativeBatchMode = false;
        if (!source.sellable()) {
            showLocalToast(source.blockedReason().isBlank()
                ? "market.xero_delta.error.backpack_not_empty" : source.blockedReason(), false);
            return;
        }
        if (!TradingItemEligibility.canList(source.stack())) {
            showLocalToast(BoundItemPolicy.isBound(source.stack())
                ? "market.xero_delta.error.bound_item"
                : TradingItemEligibility.isKnifeSkin(source.stack())
                    ? "market.xero_delta.error.knife_skin"
                    : "market.xero_delta.error.unsupported_item", false);
            return;
        }
        if (ownedListingCount() >= state.playerListingSlots()) {
            showLocalToast("market.xero_delta.error.player_listing_slots", false);
            return;
        }
        drafts.clear();
        selectedDraftId = -1L;
        long unitValue = TradingRules.normalizeItemValue(ClientDataCache.INSTANCE.getPrice(source.stack()));
        long minimumPrice = Math.max(1L, TradingRules.minListingPrice(unitValue));
        long maximumPrice = Math.min(MAX_PRICE, TradingRules.maxListingPrice(unitValue));
        long suggested = clamp(unitValue, minimumPrice, maximumPrice);
        int stackLimit = TradingRules.maxAmountPerListing(source.stack().getMaxStackSize());
        int maxDurationDays = Math.max(1, state.maxListingDays());
        Draft draft = new Draft(nextDraftId++, source.id(), null, source.stack().copy(), 1,
            suggested, Math.max(1, Math.min(Math.min(MAX_AMOUNT, stackLimit), source.availableCount())),
            minimumPrice, Math.max(minimumPrice, maximumPrice),
            Math.max(1L, (unitValue + 99L) / 100L),
            Math.min(TradingRules.DEFAULT_LISTING_DAYS, maxDurationDays), maxDurationDays);
        drafts.add(draft);
        selectDraft(draft);
    }

    private void openRelistDraft(TradingSyncPacket.ListingView listing) {
        drafts.clear();
        selectedDraftId = -1L;
        batchRelistMode = false;
        creativeBatchMode = false;
        selectedBatchDraftIds.clear();
        Draft draft = createRelistDraft(listing);
        drafts.add(draft);
        selectDraft(draft);
    }

    private void openBatchRelistDrafts(List<TradingSyncPacket.ListingView> listings) {
        drafts.clear();
        selectedDraftId = -1L;
        selectedBatchDraftIds.clear();
        batchRelistMode = true;
        creativeBatchMode = false;
        batchRelistScroll = 0;
        batchRelistScrollTarget = 0.0D;
        batchEditorScroll = 0;
        batchEditorScrollTarget = 0.0D;
        for (TradingSyncPacket.ListingView listing : listings) {
            Draft draft = createRelistDraft(listing);
            drafts.add(draft);
            selectedBatchDraftIds.add(draft.id);
        }
        finishOwnedBatchAction();
        if (!drafts.isEmpty()) selectDraft(drafts.getFirst());
    }

    private void openCreativeDrafts(List<ItemStack> stacks) {
        drafts.clear();
        selectedDraftId = -1L;
        selectedBatchDraftIds.clear();
        batchRelistMode = true;
        creativeBatchMode = true;
        batchRelistScroll = 0;
        batchRelistScrollTarget = 0.0D;
        batchEditorScroll = 0;
        batchEditorScrollTarget = 0.0D;
        creativeBatchSelectionAnchor = -1L;
        for (ItemStack raw : stacks) {
            if (raw == null || raw.isEmpty()) continue;
            ItemStack stack = raw.copyWithCount(1);
            long unitValue = TradingRules.normalizeItemValue(ClientDataCache.INSTANCE.getPrice(stack));
            long minimumPrice = Math.max(1L, TradingRules.minListingPrice(unitValue));
            long maximumPrice = Math.min(MAX_PRICE, TradingRules.maxListingPrice(unitValue));
            long suggested = clamp(unitValue, minimumPrice, maximumPrice);
            int maximumAmount = MAX_CREATIVE_AMOUNT;
            int maximumDuration = Math.max(1, state.maxListingDays());
            String source = "creative:" + BuiltInRegistries.ITEM.getKey(stack.getItem());
            Draft draft = new Draft(nextDraftId++, source, null, stack, 1, suggested,
                Math.max(1, maximumAmount), minimumPrice, Math.max(minimumPrice, maximumPrice),
                Math.max(1L, (unitValue + 99L) / 100L),
                Math.min(TradingRules.DEFAULT_LISTING_DAYS, maximumDuration), maximumDuration);
            drafts.add(draft);
        }
        if (drafts.isEmpty()) {
            batchRelistMode = false;
            creativeBatchMode = false;
            showLocalToast("market.xero_delta.error.source", false);
            return;
        }
        Draft first = drafts.getFirst();
        selectedBatchDraftIds.add(first.id);
        creativeBatchSelectionAnchor = first.id;
        selectDraft(first);
    }

    private void openCreativeEditDraft(TradingSyncPacket.ListingView listing) {
        if (!listing.virtualSupply()) {
            showLocalToast("market.xero_delta.error.creative_edit_world_only", false);
            return;
        }
        drafts.clear();
        selectedDraftId = -1L;
        selectedBatchDraftIds.clear();
        batchRelistMode = false;
        creativeBatchMode = false;
        int listedAmount = Math.max(1, listing.stack().getCount());
        long unitValue = Math.max(1L, (listing.itemValue() + listedAmount - 1L) / listedAmount);
        long minimumPrice = Math.max(1L, TradingRules.minListingPrice(unitValue));
        long maximumPrice = Math.min(MAX_PRICE, TradingRules.maxListingPrice(unitValue));
        long currentUnitPrice = Math.max(1L, (listing.price() + listedAmount - 1L) / listedAmount);
        int maximumDuration = Math.max(1, state.maxListingDays());
        long durationTicks = Math.max(1L, listing.expiresAt() - listing.createdAt());
        int durationDays = (int) Math.max(1L, Math.min(maximumDuration,
            (durationTicks + 23_999L) / 24_000L));
        Draft draft = new Draft(nextDraftId++, "creative-edit:" + listing.id(), listing.id(),
            listing.stack().copyWithCount(1), listedAmount,
            clamp(currentUnitPrice, minimumPrice, Math.max(minimumPrice, maximumPrice)),
            MAX_CREATIVE_AMOUNT, minimumPrice, Math.max(minimumPrice, maximumPrice),
            Math.max(1L, (unitValue + 99L) / 100L), durationDays, maximumDuration);
        drafts.add(draft);
        selectDraft(draft);
    }

    private Draft createRelistDraft(TradingSyncPacket.ListingView listing) {
        int listedAmount = Math.max(1, listing.stack().getCount());
        long unitValue = Math.max(1L, (listing.itemValue() + listedAmount - 1L) / listedAmount);
        long minimumPrice = Math.max(1L, TradingRules.minListingPrice(unitValue));
        long maximumPrice = Math.min(MAX_PRICE, TradingRules.maxListingPrice(unitValue));
        long currentUnitPrice = Math.max(1L, (listing.price() + listedAmount - 1L) / listedAmount);
        long durationTicks = Math.max(1L, listing.expiresAt() - listing.createdAt());
        int maximumDurationDays = Math.max(1, state.maxListingDays());
        int durationDays = (int) Math.max(1L, Math.min(maximumDurationDays,
            (durationTicks + 23_999L) / 24_000L));
        return new Draft(nextDraftId++, "", listing.id(), listing.stack().copy(), listedAmount,
            clamp(currentUnitPrice, minimumPrice, maximumPrice), listedAmount,
            minimumPrice, Math.max(minimumPrice, maximumPrice),
            Math.max(1L, (unitValue + 99L) / 100L), durationDays, maximumDurationDays);
    }

    private void removeDraft(Draft draft) {
        drafts.remove(draft);
        if (selectedDraftId == draft.id) {
            selectedDraftId = -1L;
            setEditorVisible(false);
        }
    }

    private void selectDraft(Draft draft) {
        selectedDraftId = draft.id;
        syncingFields = true;
        amountSlider.setRange(1L, draft.maximumAmount);
        durationSlider.setRange(1L, draft.maximumDurationDays);
        priceBox.setValue(creativeBatchMode ? signedOffset(draft.priceOffset) : String.valueOf(draft.price));
        amountSlider.setLongValue(draft.amount);
        durationSlider.setLongValue(draft.durationDays);
        syncingFields = false;
        setEditorVisible(true);
        enterDetail();
    }

    private void applyEditor() {
        Draft draft = selectedDraft();
        if (draft == null) return;
        if (!creativeBatchMode) {
            draft.price = clamp(parseLong(priceBox.getValue(), draft.price),
                draft.minimumPrice, draft.maximumPrice);
        }
        draft.amount = (int) clamp(draft.amount, 1L, draft.maximumAmount);
        draft.durationDays = (int) clamp(draft.durationDays, 1L, draft.maximumDurationDays);
        selectDraft(draft);
        showLocalToast("trading_op.xero_delta.applied", true);
    }

    private void submitDrafts() {
        if (drafts.isEmpty()) return;
        applyEditor();
        List<Draft> submitted = List.copyOf(drafts);
        // Several listing packets can be coalesced into one client render revision.
        // One successful acknowledgement is enough to return after the submitted batch.
        drafts.clear();
        selectedDraftId = -1L;
        setEditorVisible(false);
        for (Draft draft : submitted) {
            sendDraft(draft);
        }
    }

    private void submitSelectedDraft() {
        Draft draft = selectedDraft();
        if (draft == null) return;
        applyEditor();
        sendDraft(draft);
        drafts.remove(draft);
        selectedDraftId = -1L;
        leaveDetail();
    }

    private void sendDraft(Draft draft) {
        if (draft.sourceId.startsWith("creative-edit:") && draft.relistListingId != null) {
            ModNetwork.sendToServer(TradingActionPacket.creativeEdit(draft.relistListingId,
                draft.amount, totalListingPrice(draft), draft.durationDays));
        } else if (draft.relistListingId != null) {
            ModNetwork.sendToServer(TradingActionPacket.relist(draft.relistListingId, draft.amount,
                totalListingPrice(draft), draft.durationDays));
        } else if (draft.sourceId.startsWith("creative:")) {
            ModNetwork.sendToServer(new CreativeListingPacket(draft.stack.copyWithCount(1),
                draft.amount, totalListingPrice(draft), draft.durationDays));
        } else {
            ModNetwork.sendToServer(TradingActionPacket.list(draft.sourceId, draft.amount,
                totalListingPrice(draft), draft.durationDays));
        }
    }

    private void enterDetail() {
        detailMode = true;
        detailScroll = 0;
        detailScrollTarget = 0.0D;
        sourceSearch.visible = false;
        if (draftSearch != null) draftSearch.visible = false;
        if (creativeBatchSearch != null) creativeBatchSearch.visible = creativeBatchMode;
        positionDetailWidgets();
        setEditorVisible(true);
    }

    private void leaveDetail() {
        if (batchRelistMode) {
            drafts.clear();
            selectedBatchDraftIds.clear();
            batchRelistMode = false;
            creativeBatchMode = false;
            batchRelistScroll = 0;
            batchRelistScrollTarget = 0.0D;
            batchEditorScroll = 0;
            batchEditorScrollTarget = 0.0D;
            creativeBatchSelectionAnchor = -1L;
            selectedDraftId = -1L;
        }
        detailMode = false;
        draggingAmountSlider = false;
        draggingDurationSlider = false;
        detailScroll = 0;
        detailScrollTarget = 0.0D;
        sourceSearch.visible = true;
        if (draftSearch != null) draftSearch.visible = true;
        if (creativeBatchSearch != null) {
            creativeBatchSearch.visible = false;
            creativeBatchSearch.setValue("");
        }
        setEditorVisible(false);
    }

    private void positionDetailWidgets() {
        DetailLayout layout = detailLayout();
        int x = layout.contentX();
        int y = layout.contentY() - detailScroll;
        int controlWidth = layout.areaWidth();
        if (batchRelistMode) {
            int contentY = layout.contentY() - batchEditorScroll;
            priceBox.setX(x + 24);
            priceBox.setY(contentY + 188);
            priceBox.setWidth(Math.max(40, controlWidth - 48));
            priceBox.visible = true;
            amountSlider.setX(x + 24);
            amountSlider.setY(contentY + 130);
            amountSlider.setWidth(Math.max(40, controlWidth - 48));
            amountSlider.visible = creativeBatchMode;
            durationSlider.visible = false;
            if (creativeBatchSearch != null) creativeBatchSearch.visible = creativeBatchMode;
            updateDetailWidgetVisibility(layout);
            return;
        }
        amountSlider.setX(x + 24);
        amountSlider.setY(y + 88);
        amountSlider.setWidth(Math.max(40, controlWidth - 48));
        priceBox.setX(x + 24);
        priceBox.setY(y + 132);
        priceBox.setWidth(Math.max(40, controlWidth - 48));
        durationSlider.setX(x);
        durationSlider.setY(y + 250);
        durationSlider.setWidth(controlWidth);
        updateDetailWidgetVisibility(layout);
    }

    private void onAmountSlider(long value) {
        if (syncingFields) return;
        syncingFields = true;
        Draft draft = selectedDraft();
        if (draft != null) {
            if (creativeBatchMode) {
                for (Draft selected : selectedBatchDrafts()) {
                    selected.amount = (int) clamp(value, 1L, selected.maximumAmount);
                }
            } else {
                draft.amount = (int) value;
            }
        }
        syncingFields = false;
    }

    private void onDurationSlider(long value) {
        if (syncingFields) return;
        Draft draft = selectedDraft();
        if (draft != null) draft.durationDays = (int) value;
    }

    private void onPriceText(String value) {
        if (syncingFields || value.isEmpty()) return;
        Draft draft = selectedDraft();
        if (creativeBatchMode) {
            if (value.equals("+") || value.equals("-")) return;
            applyCreativeBatchOffset(parseSignedLong(value, draft == null ? 0L : draft.priceOffset));
            return;
        }
        long minimum = draft == null ? 1L : draft.minimumPrice;
        long maximum = draft == null ? MAX_PRICE : draft.maximumPrice;
        long parsed = clamp(parseLong(value, minimum), minimum, maximum);
        syncingFields = true;
        if (draft != null) draft.price = parsed;
        syncingFields = false;
    }

    private void setEditorVisible(boolean visible) {
        if (priceBox == null) return;
        if (!visible) {
            priceBox.visible = false;
            amountSlider.visible = false;
            durationSlider.visible = false;
            if (creativeBatchSearch != null) creativeBatchSearch.visible = false;
            return;
        }
        positionDetailWidgets();
    }

    private void updateDetailWidgetVisibility(DetailLayout layout) {
        if (batchRelistMode) {
            int top = layout.contentY();
            int bottom = layout.contentY() + layout.areaHeight() - 36;
            priceBox.visible = detailMode && priceBox.getY() >= top
                && priceBox.getY() + priceBox.getHeight() <= bottom;
            amountSlider.visible = detailMode && creativeBatchMode && amountSlider.getY() >= top
                && amountSlider.getY() + amountSlider.getHeight() <= bottom;
            durationSlider.visible = false;
            if (creativeBatchSearch != null) creativeBatchSearch.visible = detailMode && creativeBatchMode;
            return;
        }
        int top = layout.panelY() + 3;
        int bottom = layout.panelY() + layout.panelHeight() - 3;
        priceBox.visible = detailMode && priceBox.getY() >= top && priceBox.getY() + priceBox.getHeight() <= bottom;
        amountSlider.visible = detailMode && amountSlider.getY() >= top
            && amountSlider.getY() + amountSlider.getHeight() <= bottom;
        durationSlider.visible = detailMode && durationSlider.getY() >= top
            && durationSlider.getY() + durationSlider.getHeight() <= bottom;
    }

    private List<TradingSyncPacket.SourceView> filteredSources() {
        String query = sourceSearch == null ? "" : sourceSearch.getValue().trim().toLowerCase(Locale.ROOT);
        List<TradingSyncPacket.SourceView> result = new ArrayList<>();
        for (TradingSyncPacket.SourceView value : state.sources()) {
            if (!TradingCreativeCategoryGroups.matches(selectedSourceCreativeSection, value.stack())) continue;
            boolean favorite = state.favorites().contains(ModDataStorage.getKey(value.stack()));
            if (!TradingItemCategory.matches(sourceCategory, value.stack(), favorite)) continue;
            TradingSyncPacket.SourceGroupView group = sourceGroup(value.groupId());
            boolean groupMatches = group != null
                && sourceGroupLabel(group, value.groupId()).getString().toLowerCase(Locale.ROOT).contains(query);
            if (query.isBlank() || matches(value.stack(), query) || groupMatches) result.add(value);
        }
        return result;
    }

    private void initializeSourceCategories() {
        List<TradingHtmlThemeParser.CategorySection> sections = sourceCategorySections();
        if (expandedSourceCategorySection.isBlank() && !sections.isEmpty()) {
            expandedSourceCategorySection = sections.getFirst().id();
        }
        if (!selectedSourceCreativeSection.isBlank()
            && sections.stream().noneMatch(section -> section.id().equals(selectedSourceCreativeSection))) {
            selectedSourceCreativeSection = "";
            sourceCategory = TradingCategory.ALL;
        }
    }

    private List<TradingHtmlThemeParser.CategorySection> sourceCategorySections() {
        return TradingCreativeCategoryGroups.sections(List.of(TradingCategory.values()));
    }

    private List<SourceCategoryRow> sourceCategoryRows() {
        List<SourceCategoryRow> rows = new ArrayList<>();
        rows.add(new SourceCategoryRow("", TradingCategory.ALL, false));
        rows.add(new SourceCategoryRow("", TradingCategory.FAVORITES, false));
        for (TradingHtmlThemeParser.CategorySection section : sourceCategorySections()) {
            rows.add(new SourceCategoryRow(section.id(), null, true));
            if (!section.id().equals(expandedSourceCategorySection)) continue;
            for (String categoryId : section.categories()) {
                try {
                    TradingCategory value = TradingCategory.valueOf(categoryId.toUpperCase(Locale.ROOT));
                    rows.add(new SourceCategoryRow(section.id(), value, false));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        return List.copyOf(rows);
    }

    private Component selectedSourceCategoryLabel() {
        if (selectedSourceCreativeSection.isBlank()) {
            return Component.translatable("market.xero_delta.category."
                + sourceCategory.name().toLowerCase(Locale.ROOT));
        }
        Component section = TradingCreativeCategoryGroups.label(selectedSourceCreativeSection);
        if (sourceCategory == TradingCategory.ALL) return section;
        Component child = Component.translatable("market.xero_delta.category."
            + sourceCategory.name().toLowerCase(Locale.ROOT));
        return Component.literal(section.getString() + " / " + child.getString());
    }

    private ItemStack selectedSourceCategoryIcon() {
        return sourceCategory == TradingCategory.ALL && !selectedSourceCreativeSection.isBlank()
            ? TradingCreativeCategoryGroups.icon(selectedSourceCreativeSection)
            : TradingCreativeCategoryGroups.categoryIcon(selectedSourceCreativeSection, sourceCategory);
    }

    private int sourceCategoryControlX() {
        return splitX() + 12;
    }

    private int sourceCategoryControlWidth() {
        int right = width - 20;
        return Math.max(80, Math.min(240, right - sourceCategoryControlX()));
    }

    private int sourceCategoryVisibleRows() {
        return Math.max(1, Math.min(12, (height - 104 - 18) / 24));
    }

    private boolean handleSourceCategoryClick(double mouseX, double mouseY) {
        int x = sourceCategoryControlX();
        int width = sourceCategoryControlWidth();
        if (inside(mouseX, mouseY, x, 80, width, 20)) {
            sourceCategoryOpen = !sourceCategoryOpen;
            return true;
        }
        if (!sourceCategoryOpen) return false;
        int popupY = 104;
        int visibleRows = sourceCategoryVisibleRows();
        if (inside(mouseX, mouseY, x, popupY, width, visibleRows * 24 + 6)) {
            int visibleIndex = Math.max(0, ((int) mouseY - popupY - 3) / 24);
            int index = sourceCategoryScroll + visibleIndex;
            List<SourceCategoryRow> rows = sourceCategoryRows();
            if (index < rows.size()) {
                SourceCategoryRow row = rows.get(index);
                if (row.sectionHeader()) {
                    expandedSourceCategorySection = row.sectionId().equals(expandedSourceCategorySection)
                        ? "" : row.sectionId();
                    selectedSourceCreativeSection = row.sectionId();
                    sourceCategory = TradingCategory.ALL;
                } else {
                    selectedSourceCreativeSection = row.sectionId();
                    sourceCategory = row.category();
                    sourceCategoryOpen = false;
                }
                sourceScroll = 0;
                sourceScrollTarget = 0.0D;
            }
            return true;
        }
        sourceCategoryOpen = false;
        return false;
    }

    private SourceLayout sourceLayout(int left, int right) {
        int columns = sourceGridColumns(left, right);
        int cellWidth = sourceCellWidth(left, right, columns);
        Map<String, List<TradingSyncPacket.SourceView>> grouped = new LinkedHashMap<>();
        for (TradingSyncPacket.SourceGroupView group : state.sourceGroups()) {
            grouped.put(group.id(), new ArrayList<>());
        }
        for (TradingSyncPacket.SourceView source : filteredSources()) {
            grouped.computeIfAbsent(source.groupId(), ignored -> new ArrayList<>()).add(source);
        }
        List<SourceHeaderPlacement> headers = new ArrayList<>();
        List<SourceCardPlacement> cards = new ArrayList<>();
        int y = 0;
        for (Map.Entry<String, List<TradingSyncPacket.SourceView>> entry : grouped.entrySet()) {
            if (entry.getValue().isEmpty()) continue;
            TradingSyncPacket.SourceGroupView group = sourceGroup(entry.getKey());
            headers.add(new SourceHeaderPlacement(entry.getKey(), group, y));
            y += SOURCE_GROUP_HEADER_HEIGHT + SOURCE_CELL_GAP;
            List<TradingSyncPacket.SourceView> sources = entry.getValue();
            for (int index = 0; index < sources.size(); index++) {
                int x = left + (index % columns) * (cellWidth + SOURCE_CELL_GAP);
                int cardY = y + (index / columns) * (SOURCE_CELL_HEIGHT + SOURCE_CELL_GAP);
                cards.add(new SourceCardPlacement(sources.get(index), x, cardY, cellWidth));
            }
            int rows = (sources.size() + columns - 1) / columns;
            y += rows * (SOURCE_CELL_HEIGHT + SOURCE_CELL_GAP) + SOURCE_GROUP_GAP;
        }
        return new SourceLayout(List.copyOf(headers), List.copyOf(cards), Math.max(0, y - SOURCE_GROUP_GAP));
    }

    private TradingSyncPacket.SourceGroupView sourceGroup(String groupId) {
        for (TradingSyncPacket.SourceGroupView group : state.sourceGroups()) {
            if (group.id().equals(groupId)) return group;
        }
        return null;
    }

    private Component sourceGroupLabel(TradingSyncPacket.SourceGroupView group, String groupId) {
        if (group == null || groupId.equals("inventory")) {
            return Component.translatable("trading_op.xero_delta.source_group.inventory");
        }
        return Component.translatable("trading_op.xero_delta.source_group.backpack",
            group.ordinal(), group.icon().getHoverName());
    }

    private List<Draft> filteredDrafts() {
        String query = draftSearch == null ? "" : draftSearch.getValue().trim().toLowerCase(Locale.ROOT);
        if (query.isBlank()) return List.copyOf(drafts);
        return drafts.stream().filter(draft -> matches(draft.stack, query)).toList();
    }

    private List<TradingSyncPacket.ListingView> filteredOwnedListings() {
        if (minecraft == null || minecraft.player == null) return List.of();
        UUID playerId = minecraft.player.getUUID();
        String query = draftSearch == null ? "" : draftSearch.getValue().trim().toLowerCase(Locale.ROOT);
        List<TradingSyncPacket.ListingView> result = new ArrayList<>();
        for (TradingSyncPacket.ListingView listing : state.listings()) {
            boolean searchMatches = query.isBlank() || matches(listing.stack(), query)
                || listing.publicId().toLowerCase(Locale.ROOT).contains(query);
            LocalDate listedDate = dateFilters.isEmpty() ? null : listingDate(listing.publicId());
            boolean dateMatches = dateFilters.isEmpty() || dateFilters.contains(listedDate);
            if (listing.sellerId().equals(playerId) && searchMatches && dateMatches) result.add(listing);
        }
        result.sort((first, second) -> {
            int expired = Boolean.compare(isExpired(first), isExpired(second));
            return expired != 0 ? expired : Long.compare(second.createdAt(), first.createdAt());
        });
        return result;
    }

    private int ownedListingCount() {
        if (minecraft == null || minecraft.player == null) return 0;
        UUID playerId = minecraft.player.getUUID();
        int count = 0;
        for (TradingSyncPacket.ListingView listing : state.listings()) {
            if (listing.sellerId().equals(playerId)) count++;
        }
        return count;
    }

    private List<OwnedAction> ownedActions(TradingSyncPacket.ListingView listing) {
        if (isExpired(listing)) return List.of(OwnedAction.RELIST, OwnedAction.VIEW, OwnedAction.CANCEL);
        return List.of(OwnedAction.VIEW, OwnedAction.CANCEL);
    }

    private boolean isExpired(TradingSyncPacket.ListingView listing) {
        return listing.expired() || remainingTicks(listing) <= 0L;
    }

    private long remainingTicks(TradingSyncPacket.ListingView listing) {
        if (minecraft == null || minecraft.level == null) return listing.expired() ? 0L : 1L;
        return Math.max(0L, listing.expiresAt() - minecraft.level.getGameTime());
    }

    private Component remainingTime(TradingSyncPacket.ListingView listing) {
        long ticks = Math.min(TradingRules.LISTING_LIFETIME_TICKS, remainingTicks(listing));
        long totalGameSeconds = (ticks * 18L + 4L) / 5L;
        long days = totalGameSeconds / 86_400L;
        long withinDay = totalGameSeconds % 86_400L;
        long hours = withinDay / 3_600L;
        long minutes = withinDay % 3_600L / 60L;
        long seconds = withinDay % 60L;
        String clock = String.format(Locale.ROOT, "%02d:%02d:%02d", hours, minutes, seconds);
        return days > 0L
            ? Component.translatable("trading_op.xero_delta.remaining_days", days, clock)
            : Component.translatable("trading_op.xero_delta.remaining", clock);
    }

    private static String listedTime(TradingSyncPacket.ListingView listing) {
        long epochMillis = TradingMarketData.publicIdCreatedAtMillis(listing.publicId());
        if (epochMillis < 0L) return "";
        return LISTED_TIME_FORMAT.format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()));
    }

    private OwnedAction ownedActionAt(double mouseX, double mouseY, int right, int rowY,
                                      TradingSyncPacket.ListingView listing) {
        List<OwnedAction> actions = ownedActions(listing);
        int buttonWidth = 54;
        int buttonHeight = 18;
        int gap = 3;
        int x = right - buttonWidth;
        int y = rowY + 7;
        for (OwnedAction action : actions) {
            if (inside(mouseX, mouseY, x, y, buttonWidth, buttonHeight)) return action;
            y += buttonHeight + gap;
        }
        return null;
    }

    private void performOwnedAction(OwnedAction action, TradingSyncPacket.ListingView listing) {
        String htmlAction = switch (action) {
            case RELIST -> "relist-listing";
            case VIEW -> "view-listing";
            case CANCEL -> "cancel-listing";
        };
        if (!document.actions().contains(htmlAction)) return;
        if (action == OwnedAction.RELIST) {
            openRelistDraft(listing);
        } else if (action == OwnedAction.VIEW) {
            state.rememberTransition("operator");
            ModNetwork.sendToServer(TradingActionPacket.openMarketDetail(listing.id()));
        } else {
            ModNetwork.sendToServer(TradingActionPacket.listing(TradingActionPacket.Action.CANCEL, listing.id()));
        }
    }

    private static boolean matches(ItemStack stack, String query) {
        if (query.isBlank()) return true;
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().toLowerCase(Locale.ROOT);
        return id.contains(query) || stack.getHoverName().getString().toLowerCase(Locale.ROOT).contains(query);
    }

    private List<Draft> filteredCreativeBatchDrafts() {
        String query = creativeBatchSearch == null ? ""
            : creativeBatchSearch.getValue().trim().toLowerCase(Locale.ROOT);
        if (query.isBlank()) return List.copyOf(drafts);
        List<Draft> result = new ArrayList<>();
        for (Draft draft : drafts) if (matches(draft.stack, query)) result.add(draft);
        return result;
    }

    private int creativeBatchGridColumns(int areaWidth) {
        int available = Math.max(1, (areaWidth + CREATIVE_BATCH_CARD_GAP) / 58);
        return Math.max(1, Math.min(creativeBatchColumns,
            Math.min(CREATIVE_BATCH_MAX_COLUMNS, available)));
    }

    private Draft creativeBatchDraftAt(double mouseX, double mouseY, int x, int top, int areaWidth) {
        int columns = creativeBatchGridColumns(areaWidth);
        int cardWidth = Math.max(42,
            (areaWidth - CREATIVE_BATCH_CARD_GAP * (columns - 1)) / columns);
        int localX = (int) mouseX - x;
        int localY = (int) mouseY - top + batchRelistScroll;
        if (localX < 0 || localY < 0) return null;
        int column = localX / (cardWidth + CREATIVE_BATCH_CARD_GAP);
        int row = localY / (CREATIVE_BATCH_CARD_HEIGHT + CREATIVE_BATCH_CARD_GAP);
        if (column < 0 || column >= columns
            || localX % (cardWidth + CREATIVE_BATCH_CARD_GAP) >= cardWidth
            || localY % (CREATIVE_BATCH_CARD_HEIGHT + CREATIVE_BATCH_CARD_GAP)
                >= CREATIVE_BATCH_CARD_HEIGHT) return null;
        int index = row * columns + column;
        List<Draft> values = filteredCreativeBatchDrafts();
        return index >= 0 && index < values.size() ? values.get(index) : null;
    }

    private void updateCreativeBatchSelection(Draft clicked) {
        List<Draft> values = filteredCreativeBatchDrafts();
        Draft previous = selectedDraft();
        int sharedAmount = previous == null ? clicked.amount : previous.amount;
        long sharedOffset = previous == null ? clicked.priceOffset : previous.priceOffset;
        boolean extending = hasControlDown() || hasShiftDown();
        if (hasShiftDown() && creativeBatchSelectionAnchor >= 0L) {
            int anchor = indexOfDraft(values, creativeBatchSelectionAnchor);
            int target = indexOfDraft(values, clicked.id);
            if (!hasControlDown()) selectedBatchDraftIds.clear();
            if (anchor >= 0 && target >= 0) {
                for (int index = Math.min(anchor, target); index <= Math.max(anchor, target); index++) {
                    selectedBatchDraftIds.add(values.get(index).id);
                }
            } else {
                selectedBatchDraftIds.add(clicked.id);
            }
        } else if (hasControlDown()) {
            if (!selectedBatchDraftIds.add(clicked.id)) selectedBatchDraftIds.remove(clicked.id);
            creativeBatchSelectionAnchor = clicked.id;
        } else {
            selectedBatchDraftIds.clear();
            selectedBatchDraftIds.add(clicked.id);
            creativeBatchSelectionAnchor = clicked.id;
        }
        if (extending && !selectedBatchDraftIds.isEmpty()) {
            for (Draft selected : selectedBatchDrafts()) {
                selected.amount = (int) clamp(sharedAmount, 1L, selected.maximumAmount);
                selected.priceOffset = sharedOffset;
                selected.price = clamp(selected.basePrice + sharedOffset,
                    selected.minimumPrice, selected.maximumPrice);
            }
        }
        selectDraft(clicked);
    }

    private static int indexOfDraft(List<Draft> values, long id) {
        for (int index = 0; index < values.size(); index++) {
            if (values.get(index).id == id) return index;
        }
        return -1;
    }

    private void toggleAllBatchDrafts() {
        boolean allSelected = !drafts.isEmpty()
            && drafts.stream().allMatch(value -> selectedBatchDraftIds.contains(value.id));
        selectedBatchDraftIds.clear();
        if (!allSelected) {
            Draft active = selectedDraft();
            int sharedAmount = active == null ? 1 : active.amount;
            long sharedOffset = active == null ? 0L : active.priceOffset;
            for (Draft draft : drafts) {
                selectedBatchDraftIds.add(draft.id);
                draft.amount = (int) clamp(sharedAmount, 1L, draft.maximumAmount);
                draft.priceOffset = sharedOffset;
                draft.price = clamp(draft.basePrice + sharedOffset,
                    draft.minimumPrice, draft.maximumPrice);
            }
        }
    }

    private List<Draft> selectedBatchDrafts() {
        List<Draft> result = new ArrayList<>();
        for (Draft draft : drafts) if (selectedBatchDraftIds.contains(draft.id)) result.add(draft);
        return result;
    }

    private void applyCreativeBatchOffset(long offset) {
        Draft active = selectedDraft();
        if (active == null) return;
        List<Draft> selected = selectedBatchDrafts();
        if (selected.isEmpty()) selected = List.of(active);
        for (Draft draft : selected) {
            draft.priceOffset = offset;
            draft.price = clamp(draft.basePrice + offset, draft.minimumPrice, draft.maximumPrice);
        }
        syncingFields = true;
        priceBox.setValue(signedOffset(offset));
        syncingFields = false;
    }

    private void resetCreativeBatchScroll() {
        batchRelistScroll = 0;
        batchRelistScrollTarget = 0.0D;
    }

    private static String signedOffset(long value) {
        return value > 0L ? "+" + value : String.valueOf(value);
    }

    private static long parseSignedLong(String value, long fallback) {
        try {
            if (value.equals("+") || value.equals("-")) return fallback;
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private void navigateBack() {
        transition.beginClose(() -> navigateToPrevious(false));
    }

    private void navigateToPrevious(boolean fallbackToSell) {
        String previous = state.popPreviousScreen();
        state.rememberCursor();
        if (previous.startsWith("market:")) {
            ModNetwork.sendToServer(TradingActionPacket.openMarket(previous.substring(7)));
        } else if (previous.startsWith("recycling")) {
            String source = previous.contains(":") ? previous.substring(previous.indexOf(':') + 1) : "";
            ModNetwork.sendToServer(TradingActionPacket.openRecycling(source));
        } else if (previous.startsWith("operator")) {
            ModNetwork.sendToServer(TradingActionPacket.openOperator());
        } else if (fallbackToSell) {
            ModNetwork.sendToServer(TradingActionPacket.openMarket("sell"));
        } else {
            onClose();
        }
    }

    private Draft selectedDraft() {
        for (Draft draft : drafts) if (draft.id == selectedDraftId) return draft;
        return null;
    }

    private int splitX() {
        return Math.max(1, width / 2);
    }

    private int multiSelectX() {
        return Math.max(14, splitX() - 216);
    }

    private DetailLayout detailLayout() {
        int panelWidth = Math.max(240, Math.min(900, width - 24));
        int panelHeight = Math.max(220, Math.min(480, height - 32));
        int panelX = (width - panelWidth) / 2;
        int panelY = Math.min(Math.max(64, (height - panelHeight) / 2),
            Math.max(8, height - panelHeight - 6));
        int rightWidth = Math.max(120, Math.min(panelWidth - 120, panelWidth * 40 / 100));
        int divider = panelX + panelWidth - rightWidth;
        return new DetailLayout(panelWidth, panelHeight, panelX, panelY, rightWidth, divider,
            divider + 18, panelY + 18, rightWidth - 36, panelHeight - 36);
    }

    private int sourceMaxScroll() {
        int left = splitX() + 12;
        int right = width - 12;
        return Math.max(0, sourceLayout(left, right).totalHeight() - (height - WORKSPACE_TOP - 12));
    }

    private int draftMaxScroll() {
        int count = filteredOwnedListings().size();
        int columns = ownedGridColumns(12, splitX() - 10);
        int rows = (count + columns - 1) / columns;
        return Math.max(0, rows * (OWNED_ROW_HEIGHT + OWNED_ROW_GAP)
            - (ownedListBottom() - WORKSPACE_TOP));
    }

    private int ownedListBottom() {
        return ownedMultiSelect ? height - 44 : height - 12;
    }

    private int detailMaxScroll() {
        return Math.max(0, DETAIL_CONTENT_HEIGHT - detailLayout().areaHeight());
    }

    private int ownedGridColumns(int left, int right) {
        int available = Math.max(1, (right - left + OWNED_ROW_GAP) / 110);
        return Math.max(1, Math.min(ownedColumns, Math.min(3, available)));
    }

    private int sourceGridColumns(int left, int right) {
        int available = Math.max(1, (right - left + SOURCE_CELL_GAP) / 64);
        return Math.max(1, Math.min(sourceColumns, available));
    }

    private int sourceCellWidth(int left, int right, int columns) {
        return Math.max(44, (right - left - SOURCE_CELL_GAP * (columns - 1)) / columns);
    }

    private void drawScrollbar(GuiGraphics graphics, int max, int value, int top, int bottom, int x) {
        if (max <= 0) return;
        int track = bottom - top;
        int thumb = Math.max(20, track * track / (track + max));
        int y = top + (track - thumb) * value / max;
        graphics.fill(x, top, x + 2, bottom, 0x55405258);
        graphics.fill(x, y, x + 2, y + thumb, theme.accent());
    }

    private boolean beginScrollbarDrag(double mouseX, double mouseY) {
        if (detailMode || mouseY < WORKSPACE_TOP || mouseY >= height - 12) return false;
        int ownedX = splitX() - 12;
        if (draftMaxScroll() > 0
            && inside(mouseX, mouseY, ownedX - 6, WORKSPACE_TOP, 10,
                ownedListBottom() - WORKSPACE_TOP)) {
            draggingScrollbar = 1;
            updateDraggedScroll(mouseY);
            return true;
        }
        int sourceX = width - 14;
        if (sourceMaxScroll() > 0
            && inside(mouseX, mouseY, sourceX - 6, WORKSPACE_TOP, 10, height - WORKSPACE_TOP - 12)) {
            draggingScrollbar = 2;
            updateDraggedScroll(mouseY);
            return true;
        }
        return false;
    }

    private boolean beginDetailScrollbarDrag(double mouseX, double mouseY) {
        if (!detailMode || detailMaxScroll() <= 0) return false;
        DetailLayout layout = detailLayout();
        int x = layout.panelX() + layout.panelWidth() - 9;
        if (!inside(mouseX, mouseY, x, layout.panelY() + 6, 10, layout.panelHeight() - 12)) return false;
        draggingScrollbar = 3;
        updateDraggedScroll(mouseY);
        return true;
    }

    private boolean beginBatchScrollbarDrag(double mouseX, double mouseY) {
        if (!detailMode || !batchRelistMode) return false;
        DetailLayout layout = detailLayout();
        int leftTrackX = layout.divider() - 5;
        int leftTop = layout.panelY() + (creativeBatchMode ? 44 : 16);
        int leftBottom = layout.panelY() + layout.panelHeight() - 16;
        if (batchRelistMaxScroll() > 0
            && inside(mouseX, mouseY, leftTrackX - 5, leftTop, 10, leftBottom - leftTop)) {
            draggingScrollbar = 4;
            updateDraggedScroll(mouseY);
            return true;
        }
        int rightTop = layout.contentY();
        int rightBottom = layout.contentY() + layout.areaHeight() - 36;
        int rightTrackX = layout.contentX() + layout.areaWidth() - 3;
        if (batchEditorMaxScroll() > 0
            && inside(mouseX, mouseY, rightTrackX - 5, rightTop, 10, rightBottom - rightTop)) {
            draggingScrollbar = 5;
            updateDraggedScroll(mouseY);
            return true;
        }
        return false;
    }

    private void updateDraggedScroll(double mouseY) {
        int top = WORKSPACE_TOP;
        int track = Math.max(1, height - WORKSPACE_TOP - 12);
        if (draggingScrollbar == 1) {
            int max = draftMaxScroll();
            int ownedTrack = Math.max(1, ownedListBottom() - WORKSPACE_TOP);
            draftScroll = Math.max(0, Math.min(max,
                (int) Math.round((mouseY - top) * max / ownedTrack)));
            draftScrollTarget = draftScroll;
        } else if (draggingScrollbar == 2) {
            int max = sourceMaxScroll();
            sourceScroll = Math.max(0, Math.min(max, (int) Math.round((mouseY - top) * max / track)));
            sourceScrollTarget = sourceScroll;
        } else if (draggingScrollbar == 3) {
            DetailLayout layout = detailLayout();
            int max = detailMaxScroll();
            int detailTop = layout.panelY() + 8;
            int detailTrack = Math.max(1, layout.panelHeight() - 16);
            detailScroll = Math.max(0, Math.min(max,
                (int) Math.round((mouseY - detailTop) * max / detailTrack)));
            detailScrollTarget = detailScroll;
            positionDetailWidgets();
        } else if (draggingScrollbar == 4) {
            DetailLayout layout = detailLayout();
            int batchTop = layout.panelY() + (creativeBatchMode ? 44 : 16);
            int batchTrack = Math.max(1, layout.panelY() + layout.panelHeight() - 16 - batchTop);
            int max = batchRelistMaxScroll();
            batchRelistScroll = Math.max(0, Math.min(max,
                (int) Math.round((mouseY - batchTop) * max / batchTrack)));
            batchRelistScrollTarget = batchRelistScroll;
        } else if (draggingScrollbar == 5) {
            DetailLayout layout = detailLayout();
            int editorTop = layout.contentY();
            int editorTrack = Math.max(1, layout.areaHeight() - 36);
            int max = batchEditorMaxScroll();
            batchEditorScroll = Math.max(0, Math.min(max,
                (int) Math.round((mouseY - editorTop) * max / editorTrack)));
            batchEditorScrollTarget = batchEditorScroll;
            positionDetailWidgets();
        }
    }

    private void updateSmoothScroll() {
        sourceScrollTarget = Math.max(0.0D, Math.min(sourceMaxScroll(), sourceScrollTarget));
        draftScrollTarget = Math.max(0.0D, Math.min(draftMaxScroll(), draftScrollTarget));
        detailScrollTarget = Math.max(0.0D, Math.min(detailMaxScroll(), detailScrollTarget));
        batchRelistScrollTarget = Math.max(0.0D,
            Math.min(batchRelistMaxScroll(), batchRelistScrollTarget));
        batchEditorScrollTarget = Math.max(0.0D,
            Math.min(batchEditorMaxScroll(), batchEditorScrollTarget));
        sourceScroll = approachScroll(sourceScroll, sourceScrollTarget);
        draftScroll = approachScroll(draftScroll, draftScrollTarget);
        int nextDetailScroll = approachScroll(detailScroll, detailScrollTarget);
        if (nextDetailScroll != detailScroll) {
            detailScroll = nextDetailScroll;
            if (detailMode) positionDetailWidgets();
        }
        batchRelistScroll = approachScroll(batchRelistScroll, batchRelistScrollTarget);
        int nextBatchEditorScroll = approachScroll(batchEditorScroll, batchEditorScrollTarget);
        if (nextBatchEditorScroll != batchEditorScroll) {
            batchEditorScroll = nextBatchEditorScroll;
            if (detailMode && batchRelistMode) positionDetailWidgets();
        }
    }

    private static int approachScroll(int current, double target) {
        double difference = target - current;
        if (Math.abs(difference) < 1.0D) return (int) Math.round(target);
        int step = Math.max(1, (int) Math.ceil(Math.abs(difference) * 0.32D));
        return current + (difference > 0.0D ? step : -step);
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

    private void showLocalToast(String key, boolean success) {
        toast = Component.translatable(key).getString();
        toastSuccess = success;
        toastUntil = System.currentTimeMillis() + 2500L;
    }

    private static int brighten(int color) {
        int a = color & 0xFF000000;
        int r = Math.min(255, ((color >> 16) & 0xFF) + 22);
        int g = Math.min(255, ((color >> 8) & 0xFF) + 22);
        int b = Math.min(255, (color & 0xFF) + 22);
        return a | r << 16 | g << 8 | b;
    }

    private static long parseLong(String value, long fallback) {
        try {
            return Long.parseLong(value);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static long clamp(long value, long minimum, long maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static long totalListingPrice(Draft draft) {
        if (draft.amount <= 0 || draft.price <= 0) return 0L;
        if (draft.price > TradingRules.MAX_CURRENCY / draft.amount) return TradingRules.MAX_CURRENCY;
        return draft.price * draft.amount;
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private enum OwnedAction {
        RELIST("trading_op.xero_delta.relist"),
        VIEW("trading_op.xero_delta.view"),
        CANCEL("trading_op.xero_delta.cancel");

        private final String translationKey;

        OwnedAction(String translationKey) {
            this.translationKey = translationKey;
        }
    }

    private static final class Draft {
        private final long id;
        private final String sourceId;
        private final UUID relistListingId;
        private final ItemStack stack;
        private final int maximumAmount;
        private final long minimumPrice;
        private final long maximumPrice;
        private final long priceStep;
        private final int maximumDurationDays;
        private final long basePrice;
        private int amount;
        private long price;
        private long priceOffset;
        private int durationDays;

        private Draft(long id, String sourceId, UUID relistListingId, ItemStack stack, int amount, long price,
                      int maximumAmount, long minimumPrice, long maximumPrice, long priceStep,
                      int durationDays, int maximumDurationDays) {
            this.id = id;
            this.sourceId = sourceId;
            this.relistListingId = relistListingId;
            this.stack = stack;
            this.amount = amount;
            this.price = price;
            this.basePrice = price;
            this.priceOffset = 0L;
            this.maximumAmount = maximumAmount;
            this.minimumPrice = minimumPrice;
            this.maximumPrice = maximumPrice;
            this.priceStep = priceStep;
            this.durationDays = durationDays;
            this.maximumDurationDays = maximumDurationDays;
        }
    }

    private record CalendarLayout(int panelX, int panelY, int panelWidth, int panelHeight,
                                  int gridX, int gridY, int cellWidth, int cellHeight,
                                  int footerY) {
    }

    private record CalendarHeaderLayout(int yearX, int monthX, int separatorX, int y,
                                        int yearWidth, int monthWidth) {
    }

    private enum CalendarEditTarget {
        NONE,
        YEAR,
        MONTH
    }

    private record IncomeInfoLayout(int centerX, int centerY) {
    }

    private record DetailLayout(int panelWidth, int panelHeight, int panelX, int panelY,
                                int rightWidth, int divider, int contentX, int contentY,
                                int areaWidth, int areaHeight) {
    }

    private record SourceLayout(List<SourceHeaderPlacement> headers,
                                List<SourceCardPlacement> cards, int totalHeight) {
    }

    private record SourceHeaderPlacement(String groupId,
                                         TradingSyncPacket.SourceGroupView group, int y) {
    }

    private record SourceCategoryRow(String sectionId, TradingCategory category,
                                     boolean sectionHeader) {
    }

    private record SourceCardPlacement(TradingSyncPacket.SourceView source,
                                       int x, int y, int width) {
    }

    private final class ValueSlider extends AbstractSliderButton {
        private long minimum;
        private long maximum;
        private final LongConsumer consumer;

        private ValueSlider(int x, int y, int width, int height, long minimum, long maximum,
                            long initial, LongConsumer consumer) {
            super(x, y, width, height, Component.empty(), toFraction(initial, minimum, maximum));
            this.minimum = minimum;
            this.maximum = maximum;
            this.consumer = consumer;
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.literal(TradingUi.format(longValue())));
        }

        @Override
        protected void applyValue() {
            consumer.accept(longValue());
        }

        private long longValue() {
            return minimum + Math.round(value * (maximum - minimum));
        }

        private void setRange(long minimum, long maximum) {
            long current = longValue();
            this.minimum = minimum;
            this.maximum = Math.max(minimum, maximum);
            setLongValue(current);
        }

        private void setLongValue(long newValue) {
            value = toFraction(clamp(newValue, minimum, maximum), minimum, maximum);
            updateMessage();
        }

        private void setFromMouse(double mouseX) {
            double trackWidth = Math.max(1.0D, getWidth() - 8.0D);
            value = Mth.clamp((mouseX - getX() - 4.0D) / trackWidth, 0.0D, 1.0D);
            long snapped = longValue();
            value = toFraction(snapped, minimum, maximum);
            updateMessage();
            applyValue();
        }

        @Override
        public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            int centerY = getY() + getHeight() / 2;
            graphics.fill(getX(), centerY - 1, getX() + getWidth(), centerY + 2, 0xFF394A50);
            int divisions = (int) Math.min(10L, Math.max(0L, maximum - minimum));
            for (int i = 0; i <= divisions && divisions > 0; i++) {
                int tickX = getX() + 4 + i * Math.max(1, getWidth() - 8) / divisions;
                graphics.fill(tickX, centerY - 3, tickX + 1, centerY + 4, 0xAA91A2A5);
            }
            int knobX = getX() + (int) Math.round(value * (getWidth() - 8));
            graphics.fill(getX(), centerY - 1, knobX + 4, centerY + 2, theme.accent());
            graphics.fill(knobX, centerY - 5, knobX + 8, centerY + 6,
                isHoveredOrFocused() ? 0xFF75E2C0 : theme.accent());
        }

        private static double toFraction(long value, long minimum, long maximum) {
            if (maximum <= minimum) return 0.0D;
            return Mth.clamp((double) (value - minimum) / (double) (maximum - minimum), 0.0D, 1.0D);
        }
    }

    private record PriceBar(long price, int amount) {
    }
}
