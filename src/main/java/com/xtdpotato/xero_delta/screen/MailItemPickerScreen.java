package com.xtdpotato.xero_delta.screen;

import com.xtdpotato.xero_delta.Config;
import com.xtdpotato.xero_delta.screen.material.Material2Drawing;
import com.xtdpotato.xero_delta.screen.material.Material3Theme;
import com.xtdpotato.xero_delta.trading.TradingCategory;
import com.xtdpotato.xero_delta.trading.TradingCreativeCategoryGroups;
import com.xtdpotato.xero_delta.trading.TradingCategorySectionPresentation;
import com.xtdpotato.xero_delta.trading.TradingHtmlThemeParser;
import com.xtdpotato.xero_delta.trading.TradingHtmlTemplate;
import com.xtdpotato.xero_delta.trading.TradingItemCategory;
import com.xtdpotato.xero_delta.trading.TradingItemEligibility;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** Searchable inventory/all-items picker with Ctrl and Shift multi-selection. */
public final class MailItemPickerScreen extends Screen {
    private static final int CATEGORY_WIDTH = 142;
    private static final int CATEGORY_ROW_HEIGHT = 32;
    private static final int GRID_CELL = 36;
    private final Screen parent;
    private final Consumer<List<ItemStack>> callback;
    private final List<ItemStack> inventoryCatalog;
    private final boolean creativeCategories;
    private final List<TradingHtmlThemeParser.CategorySection> configuredCategorySections;
    private final Predicate<ItemStack> itemFilter;
    private final boolean toggleSelection;
    private final LinkedHashMap<StackKey, ItemStack> selected = new LinkedHashMap<>();
    private final Set<String> expandedCreativeSections = new LinkedHashSet<>();
    private Bounds categoryAnchor;
    private Bounds gridAnchor;
    private Material3CompactEditBox searchBox;
    private com.xtdpotato.xero_delta.screen.material.Material3Button confirmButton;
    private boolean inventoryOnly;
    private double gridScrollPixels;
    private double gridTargetPixels;
    private long gridAnimationNanos;
    private double categoryScrollPixels;
    private double categoryTargetPixels;
    private long categoryAnimationNanos;
    private DragArea dragArea = DragArea.NONE;
    private boolean dragMoved;
    private double dragPressY;
    private double dragPressScroll;
    private double dragLastY;
    private long dragLastNanos;
    private double dragVelocity;
    private double scrollbarGrabOffset;
    private boolean creativeSectionsInitialized;
    private String selectedCreativeSection = "";
    private TradingCategory selectedCreativeCategory = TradingCategory.ALL;
    private StackKey selectionAnchor;
    private ItemStack hovered = ItemStack.EMPTY;

    public MailItemPickerScreen(Screen parent, boolean inventoryOnly, Consumer<List<ItemStack>> callback) {
        this(parent, inventoryOnly, List.of(), false, stack -> true, false, Set.of(), callback);
    }

    public static MailItemPickerScreen attachment(Screen parent, boolean inventoryOnly,
                                                   Consumer<List<ItemStack>> callback) {
        return new MailItemPickerScreen(parent, inventoryOnly, List.of(), true,
            stack -> true, false, Set.of(), callback);
    }

    public static MailItemPickerScreen creativeListing(Screen parent, List<ItemStack> inventoryCatalog,
                                                        Consumer<List<ItemStack>> callback) {
        return new MailItemPickerScreen(parent, false, inventoryCatalog, true,
            stack -> true, false, Set.of(), callback);
    }

    private MailItemPickerScreen(Screen parent, boolean inventoryOnly, List<ItemStack> inventoryCatalog,
                                 boolean creativeCategories, Predicate<ItemStack> itemFilter,
                                 boolean toggleSelection, Set<String> initialSelected,
                                 Consumer<List<ItemStack>> callback) {
        super(Component.translatable("mail.xero_delta.pick_items"));
        this.parent = parent;
        this.inventoryOnly = inventoryOnly;
        this.inventoryCatalog = inventoryCatalog == null ? List.of()
            : inventoryCatalog.stream().filter(stack -> stack != null && !stack.isEmpty())
                .map(stack -> stack.copyWithCount(1)).toList();
        this.creativeCategories = creativeCategories;
        this.itemFilter = itemFilter == null ? stack -> true : itemFilter;
        this.toggleSelection = toggleSelection;
        TradingHtmlThemeParser.Document tradingDocument = creativeCategories
            ? TradingHtmlTemplate.loadDocument() : null;
        this.configuredCategorySections = tradingDocument != null
            && tradingDocument.customCategorySections()
            ? tradingDocument.categorySections() : List.of();
        this.callback = callback;
        if (initialSelected != null) {
            for (String id : initialSelected) {
                ResourceLocation resource = ResourceLocation.tryParse(id);
                if (resource == null || !BuiltInRegistries.ITEM.containsKey(resource)) continue;
                ItemStack stack = BuiltInRegistries.ITEM.get(resource).getDefaultInstance();
                if (this.itemFilter.test(stack)) {
                    selected.put(StackKey.of(stack), stack.copyWithCount(1));
                }
            }
        }

        rebuildMaterialFrame();
    }

    public static MailItemPickerScreen filtered(Screen parent, Predicate<ItemStack> filter,
                                                Set<String> initialSelected,
                                                Consumer<List<ItemStack>> callback) {
        return new MailItemPickerScreen(parent, false, List.of(), false, filter, true,
            initialSelected, callback);
    }

    private void syncConfirmState() {
        if (confirmButton != null) confirmButton.active = toggleSelection || !selected.isEmpty();
    }

    private void confirmSelection() {
        if (selected.isEmpty() && !toggleSelection) return;
        List<ItemStack> result = new ArrayList<>();
        for (ItemStack stack : selected.values()) result.add(stack.copyWithCount(1));
        callback.accept(List.copyOf(result));
        minecraft.setScreen(parent);
    }

    @Override
    protected void init() {
        Material3Theme.refreshFromConfig();
        rebuildCreativeTabs();
        rebuildMaterialFrame();
    }

    private void rebuildMaterialFrame() {
        if (minecraft == null || width <= 0) return;
        String query = searchBox == null ? "" : searchBox.getValue();
        clearWidgets();
        int categoryWidth = showCategorySidebar() ? Math.min(CATEGORY_WIDTH, width / 3) : 0;
        int gridLeft = categoryWidth == 0 ? 12 : categoryWidth + 20;
        int top = 76, bottom = Math.max(top + 24, height - 40);
        categoryAnchor = categoryWidth == 0 ? null : new Bounds(8, top, categoryWidth, bottom - top);
        gridAnchor = new Bounds(gridLeft, top, Math.max(GRID_CELL + 18, width - gridLeft - 12), bottom - top);
        int segmentWidth = Math.min(96, (width - 32) / 2);
        addRenderableWidget(com.xtdpotato.xero_delta.screen.material.Material3Button.builder(
            Component.translatable("mail.xero_delta.inventory"), button -> switchCatalog(true))
            .bounds(12, 28, segmentWidth, 22).variant(inventoryOnly
                ? com.xtdpotato.xero_delta.screen.material.Material2Button.Variant.FILLED
                : com.xtdpotato.xero_delta.screen.material.Material2Button.Variant.TONAL).build());
        addRenderableWidget(com.xtdpotato.xero_delta.screen.material.Material3Button.builder(
            Component.translatable("mail.xero_delta.all_items"), button -> switchCatalog(false))
            .bounds(16 + segmentWidth, 28, segmentWidth, 22).variant(!inventoryOnly
                ? com.xtdpotato.xero_delta.screen.material.Material2Button.Variant.FILLED
                : com.xtdpotato.xero_delta.screen.material.Material2Button.Variant.TONAL).build());
        searchBox = new Material3CompactEditBox(font, gridLeft, 52, (int) gridAnchor.width, 20,
            Component.translatable("mail.xero_delta.search"));
        searchBox.setMaxLength(128);
        searchBox.setHint(Component.translatable("mail.xero_delta.search"));
        searchBox.setValue(query);
        searchBox.setResponder(value -> resetGridScroll());
        addRenderableWidget(searchBox);
        addRenderableWidget(com.xtdpotato.xero_delta.screen.material.Material3Button.builder(
            Component.translatable("gui.back"), button -> onClose()).bounds(12, height - 28, 66, 22)
            .variant(com.xtdpotato.xero_delta.screen.material.Material2Button.Variant.TEXT).build());
        confirmButton = addRenderableWidget(com.xtdpotato.xero_delta.screen.material.Material3Button.builder(
            Component.translatable("mail.xero_delta.confirm"), button -> confirmSelection())
            .bounds(width - 88, height - 28, 76, 22)
            .variant(com.xtdpotato.xero_delta.screen.material.Material2Button.Variant.FILLED).build());
        syncConfirmState();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, Material3Theme.BACKGROUND);
        graphics.fill(0, 0, width, 24, Material3Theme.SURFACE);
        graphics.drawString(font, title, 12, 8, Material3Theme.TEXT, false);
        graphics.fill(0, height - 34, width, height, Material3Theme.SURFACE);
        String count = Component.translatable("mail.xero_delta.selected_items", selected.size()).getString();
        graphics.drawString(font, font.plainSubstrByWidth(count, Math.max(0, width - 186)),
            86, height - 21, Material3Theme.PRIMARY, false);
        super.render(graphics, mouseX, mouseY, partialTick);
        hovered = ItemStack.EMPTY;
        drawCreativeCategories(graphics, mouseX, mouseY);
        drawItemGrid(graphics, mouseX, mouseY);
        if (!hovered.isEmpty()) renderTooltipSafely(graphics, hovered, mouseX, mouseY);
    }

    private record Bounds(float x, float y, float width, float height) {
        Bounds getBounds() { return this; }
        boolean isEmpty() { return width <= 0 || height <= 0; }
        boolean in(double px, double py) { return px >= x && px < x + width && py >= y && py < y + height; }
    }

    private void drawItemGrid(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = gridX();
        int y = gridY();
        int width = gridWidth();
        int viewportHeight = gridViewportHeight();
        int bottom = y + viewportHeight;
        Material2Drawing.roundedRect(graphics, x, y, width, viewportHeight, 12,
            Material3Theme.SURFACE_CONTAINER);

        List<ItemStack> values = filtered();
        int columns = Math.max(1, (width - 18) / GRID_CELL);
        int totalRows = (values.size() + columns - 1) / columns;
        double maxGridScroll = Math.max(0.0D,
            totalRows * (double) GRID_CELL - viewportHeight + 12.0D);
        updateGridScroll(maxGridScroll);
        graphics.enableScissor(x + 6, y + 6, x + columns * GRID_CELL + 6, bottom - 6);
        int firstRow = Math.max(0, (int) Math.floor(gridScrollPixels / GRID_CELL));
        double rowOffset = gridScrollPixels - firstRow * GRID_CELL;
        int first = firstRow * columns;
        int visibleRows = Math.max(1, viewportHeight / GRID_CELL + 2);
        int last = Math.min(values.size(), first + visibleRows * columns);
        for (int i = first; i < last; i++) {
            ItemStack stack = values.get(i);
            int local = i - first;
            int cellX = x + 8 + local % columns * GRID_CELL;
            int cellY = y + 8 + (int) Math.round(local / columns * GRID_CELL - rowOffset);
            StackKey id = StackKey.of(stack);
            boolean chosen = selected.containsKey(id);
            boolean itemHovered = dragArea == DragArea.NONE
                && inside(mouseX, mouseY, cellX, cellY, GRID_CELL - 6, GRID_CELL - 6)
                && cellY >= y + 6 && cellY + GRID_CELL - 6 <= bottom - 6;
            int fill = chosen ? Material3Theme.PRIMARY_CONTAINER
                : itemHovered ? Material3Theme.SURFACE_CONTAINER_HIGHEST
                : Material3Theme.SURFACE_CONTAINER_HIGH;
            Material2Drawing.roundedRect(graphics, cellX, cellY,
                GRID_CELL - 6, GRID_CELL - 6, 8, fill);
            if (chosen) {
                Material2Drawing.outlineRoundedRect(graphics, cellX, cellY,
                    GRID_CELL - 6, GRID_CELL - 6, 8, 1.0F,
                    Material3Theme.PRIMARY);
            }
            renderItemSafely(graphics, stack, cellX + 10, cellY + 10);
            if (itemHovered) hovered = stack;
        }
        graphics.disableScissor();
        drawGridScrollbar(graphics, x, y, width, viewportHeight,
            maxGridScroll, mouseX, mouseY);
    }

    /**
     * Some third-party dynamic item models read their client config while being
     * baked. During the title screen that config can still be loading; isolate
     * that failure so one icon cannot crash the entire configuration screen.
     */
    private static void renderItemSafely(GuiGraphics graphics, ItemStack stack, int x, int y) {
        try {
            graphics.renderItem(stack, x, y);
        } catch (IllegalStateException ignored) {
            // The item remains selectable; only its preview is unavailable for this frame.
        }
    }

    private void renderTooltipSafely(GuiGraphics graphics, ItemStack stack, int mouseX, int mouseY) {
        try {
            graphics.renderTooltip(font, stack, mouseX, mouseY);
        } catch (IllegalStateException ignored) {
            // A third-party tooltip may read a config that is still loading on the title screen.
        }
    }

    private void drawCreativeCategories(GuiGraphics g, int mouseX, int mouseY) {
        if (!showCategorySidebar() || categoryAnchor == null) return;
        List<CreativeCategoryRow> rows = creativeCategoryRows();
        Bounds bounds = categoryAnchor.getBounds();
        int x = Math.round(bounds.x);
        int top = Math.round(bounds.y);
        int width = Math.max(1, Math.round(bounds.width));
        int bottom = top + Math.max(1, Math.round(bounds.height));
        int totalRows = rows.size();
        int viewportHeight = Math.max(1, bottom - top);
        double maxScroll = Math.max(0.0D, totalRows * (double) CATEGORY_ROW_HEIGHT - viewportHeight);
        updateCategoryScroll(maxScroll);
        Material2Drawing.roundedRect(g, x, top, width, viewportHeight, 12,
            Material3Theme.SURFACE_CONTAINER);
        g.enableScissor(x + 4, top + 4, x + width - 4, bottom - 4);
        int firstIndex = Math.max(0, (int) Math.floor(categoryScrollPixels / CATEGORY_ROW_HEIGHT));
        double rowOffset = categoryScrollPixels - firstIndex * CATEGORY_ROW_HEIGHT;
        int visibleRows = Math.max(1, viewportHeight / CATEGORY_ROW_HEIGHT + 2);
        for (int visible = 0; visible < visibleRows; visible++) {
            int index = firstIndex + visible;
            if (index >= totalRows) break;
            CreativeCategoryRow row = rows.get(index);
            int rowY = top + (int) Math.round(visible * CATEGORY_ROW_HEIGHT - rowOffset);
            boolean header = row.sectionHeader();
            boolean selectedRow = header
                ? row.sectionId().equals(selectedCreativeSection)
                : selectedCreativeCategory == row.category()
                    && (row.sectionId().isBlank()
                        ? selectedCreativeSection.isBlank()
                        : row.sectionId().equals(selectedCreativeSection));
            int rowX = !header && !row.sectionId().isBlank() ? x + 16 : x + 8;
            int rowWidth = x + width - 14 - rowX;
            boolean hoveredRow = dragArea == DragArea.NONE && rowY >= top && rowY + CATEGORY_ROW_HEIGHT <= bottom
                && inside(mouseX, mouseY, rowX, rowY + 3,
                    rowWidth, CATEGORY_ROW_HEIGHT - 6);
            Material2Drawing.roundedRect(g, rowX, rowY + 3,
                rowWidth, CATEGORY_ROW_HEIGHT - 6, 8,
                selectedRow ? Material3Theme.SECONDARY_CONTAINER
                    : hoveredRow ? Material3Theme.SURFACE_CONTAINER_HIGHEST
                    : Material3Theme.SURFACE_CONTAINER_HIGH);
            ItemStack icon = header
                ? TradingCategorySectionPresentation.icon(row.sectionId(), creativeCategorySections())
                : TradingCreativeCategoryGroups.categoryIcon(row.sectionId(), row.category());
            if (!icon.isEmpty()) renderItemSafely(g, icon, rowX + 8, rowY + 10);
            Component label = header
                ? TradingCategorySectionPresentation.label(row.sectionId(), creativeCategorySections())
                : Component.translatable("market.xero_delta.category."
                    + row.category().name().toLowerCase(Locale.ROOT));
            g.drawString(font, font.plainSubstrByWidth(label.getString(), width - 58),
                rowX + 30, rowY + 14, selectedRow
                    ? Material3Theme.ON_SECONDARY_CONTAINER : Material3Theme.TEXT, false);
            if (header && creativeSectionCategories(row.sectionId()).size() > 1) g.drawCenteredString(font,
                expandedCreativeSections.contains(row.sectionId()) ? "▲" : "▼",
                x + width - 22, rowY + 14, Material3Theme.TEXT_MUTED);
        }
        g.disableScissor();
        drawCategoryScrollbar(g, x, top, width, viewportHeight, maxScroll, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (button != 0) return false;
        int gridX = gridX(), gridY = gridY(), gridW = gridWidth();
        int gridBottom = gridY + gridViewportHeight();
        if (showCategorySidebar() && categoryContains(mouseX, mouseY)) {
            if (beginCategoryScrollbarDrag(mouseX, mouseY)) return true;
            beginContentDrag(DragArea.CATEGORY_CONTENT, mouseY, categoryScrollPixels);
            return true;
        }
        if (beginGridScrollbarDrag(mouseX, mouseY)) return true;
        int columns = Math.max(1, (gridW - 18) / GRID_CELL);
        if (!inside(mouseX, mouseY, gridX + 6, gridY + 6,
            columns * GRID_CELL, gridBottom - gridY - 12)) return false;
        beginContentDrag(DragArea.GRID_CONTENT, mouseY, gridScrollPixels);
        return true;
    }

    private void selectGridItem(double mouseX, double mouseY) {
        List<ItemStack> values = filtered();
        int gridX = gridX() + 8, gridY = gridY() + 8, gridW = gridWidth();
        int columns = Math.max(1, (gridW - 18) / GRID_CELL);
        int localX = (int) mouseX - gridX;
        double localY = mouseY - gridY + gridScrollPixels;
        if (localX < 0 || localY < 0.0D
            || localX % GRID_CELL >= GRID_CELL - 6
            || localY % GRID_CELL >= GRID_CELL - 6) return;
        int column = localX / GRID_CELL;
        int row = (int) Math.floor(localY / GRID_CELL);
        int index = row * columns + column;
        if (column < 0 || column >= columns || index < 0 || index >= values.size()) return;
        StackKey clicked = StackKey.of(values.get(index));
        if (Screen.hasShiftDown() && selectionAnchor != null) {
            int anchor = indexOf(values, selectionAnchor);
            if (anchor >= 0) {
                if (!Screen.hasControlDown()) selected.clear();
                for (int i = Math.min(anchor, index); i <= Math.max(anchor, index); i++) {
                    ItemStack stack = values.get(i);
                    selected.put(StackKey.of(stack), stack.copyWithCount(1));
                }
            }
        } else if (toggleSelection || Screen.hasControlDown()) {
            if (selected.remove(clicked) == null) selected.put(clicked, values.get(index).copyWithCount(1));
            selectionAnchor = clicked;
        } else {
            selected.clear(); selected.put(clicked, values.get(index).copyWithCount(1)); selectionAnchor = clicked;
        }
        syncConfirmState();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (showCategorySidebar() && categoryContains(mouseX, mouseY)) {
            categoryTargetPixels += scrollY < 0 ? CATEGORY_ROW_HEIGHT * 2.0D : -CATEGORY_ROW_HEIGHT * 2.0D;
            categoryAnimationNanos = System.nanoTime();
        } else if (gridContains(mouseX, mouseY)) {
            gridTargetPixels += scrollY < 0 ? 60.0D : -60.0D;
            gridAnimationNanos = System.nanoTime();
        } else {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button != 0 || dragArea == DragArea.NONE) {
            return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
        }
        double distance = mouseY - dragPressY;
        if (Math.abs(distance) > 3.0D) dragMoved = true;
        long now = System.nanoTime();
        double elapsed = Math.max(1.0D, (now - dragLastNanos) / 1_000_000.0D);
        if (dragArea == DragArea.GRID_CONTENT || dragArea == DragArea.CATEGORY_CONTENT) {
            double next = dragPressScroll - distance;
            if (dragArea == DragArea.GRID_CONTENT) {
                gridScrollPixels = gridTargetPixels = next;
                gridAnimationNanos = now;
            } else {
                categoryScrollPixels = categoryTargetPixels = next;
                categoryAnimationNanos = now;
            }
            dragVelocity = -(mouseY - dragLastY) / elapsed * 16.0D;
        } else {
            dragMoved = true;
            updateScrollbarDrag(mouseY);
        }
        dragLastY = mouseY;
        dragLastNanos = now;
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button != 0 || dragArea == DragArea.NONE) {
            return super.mouseReleased(mouseX, mouseY, button);
        }
        DragArea released = dragArea;
        dragArea = DragArea.NONE;
        if (!dragMoved) {
            if (released == DragArea.GRID_CONTENT) selectGridItem(mouseX, mouseY);
            else if (released == DragArea.CATEGORY_CONTENT) selectCreativeCategory(mouseX, mouseY);
        } else if (released == DragArea.GRID_CONTENT) {
            gridTargetPixels = gridScrollPixels + dragVelocity * 5.0D;
            gridAnimationNanos = System.nanoTime();
        } else if (released == DragArea.CATEGORY_CONTENT) {
            categoryTargetPixels = categoryScrollPixels + dragVelocity * 5.0D;
            categoryAnimationNanos = System.nanoTime();
        }
        dragVelocity = 0.0D;
        return true;
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);

    }

    private List<ItemStack> filtered() {
        LinkedHashMap<StackKey, ItemStack> values = new LinkedHashMap<>();
        if (inventoryOnly && minecraft.player != null) {
            List<ItemStack> source = inventoryCatalog.isEmpty()
                ? minecraft.player.getInventory().items : inventoryCatalog;
            for (ItemStack stack : source) {
                if (!stack.isEmpty() && itemFilter.test(stack)) {
                    values.putIfAbsent(StackKey.of(stack), stack.copyWithCount(1));
                }
            }
        } else {
            if (creativeCategories) {
                for (ItemStack stack : TradingCreativeCategoryGroups.allStacks()) {
                    if (!TradingItemEligibility.canList(stack)) continue;
                    if (!TradingCreativeCategoryGroups.matches(selectedCreativeSection, stack)) continue;
                    if (!TradingItemCategory.matches(selectedCreativeCategory, stack, false)) continue;
                    if (itemFilter.test(stack)) {
                        values.putIfAbsent(StackKey.of(stack), stack.copyWithCount(1));
                    }
                }
            }
            for (ResourceLocation id : BuiltInRegistries.ITEM.keySet()) {
                if (creativeCategories && !TradingItemEligibility.canList(id)) continue;
                var item = BuiltInRegistries.ITEM.get(id);
                if (item == Items.AIR) continue;
                ItemStack stack = item.getDefaultInstance();
                if (creativeCategories
                    && !TradingCreativeCategoryGroups.matches(selectedCreativeSection, stack)) continue;
                if (creativeCategories
                    && !TradingItemCategory.matches(selectedCreativeCategory, stack, false)) continue;
                if (itemFilter.test(stack)) values.putIfAbsent(StackKey.of(stack), stack);
            }
        }
        String query = searchBox == null ? "" : searchBox.getValue().trim().toLowerCase(Locale.ROOT);
        return values.entrySet().stream().filter(entry -> query.isEmpty()
            || entry.getKey().itemId().toString().toLowerCase(Locale.ROOT).contains(query)
            || entry.getValue().getHoverName().getString().toLowerCase(Locale.ROOT).contains(query))
            .map(java.util.Map.Entry::getValue).toList();
    }

    private void rebuildCreativeTabs() {
        if (!creativeCategories) return;
        TradingCreativeCategoryGroups.refresh(minecraft);
        List<TradingHtmlThemeParser.CategorySection> sections = creativeCategorySections();
        if (!creativeSectionsInitialized) {
            for (TradingHtmlThemeParser.CategorySection section : sections) {
                if (section.expanded() && creativeSectionCategories(section).size() > 1) {
                    expandedCreativeSections.add(section.id());
                }
            }
            if (expandedCreativeSections.isEmpty()) {
                for (TradingHtmlThemeParser.CategorySection section : sections) {
                    if (creativeSectionCategories(section).size() > 1) {
                        expandedCreativeSections.add(section.id());
                        break;
                    }
                }
            }
            creativeSectionsInitialized = true;
        }
        expandedCreativeSections.removeIf(id -> sections.stream().noneMatch(section -> section.id().equals(id)));
    }

    private List<TradingHtmlThemeParser.CategorySection> creativeCategorySections() {
        if (!creativeCategories) return List.of();
        return configuredCategorySections.isEmpty()
            ? TradingCreativeCategoryGroups.sections(List.of(TradingCategory.values()))
            : configuredCategorySections;
    }

    private List<CreativeCategoryRow> creativeCategoryRows() {
        List<CreativeCategoryRow> rows = new ArrayList<>();
        rows.add(new CreativeCategoryRow("", TradingCategory.ALL, false));
        for (TradingHtmlThemeParser.CategorySection section : creativeCategorySections()) {
            rows.add(new CreativeCategoryRow(section.id(), null, true));
            List<TradingCategory> children = creativeSectionCategories(section);
            if (children.size() > 1 && expandedCreativeSections.contains(section.id())) {
                for (TradingCategory value : children) {
                    rows.add(new CreativeCategoryRow(section.id(), value, false));
                }
            }
        }
        return List.copyOf(rows);
    }

    private boolean selectCreativeCategory(double mouseX, double mouseY) {
        if (!showCategorySidebar() || categoryAnchor == null) return false;
        Bounds bounds = categoryAnchor.getBounds();
        int top = Math.round(bounds.y);
        int height = Math.max(0, Math.round(bounds.height));
        if (height == 0 || !bounds.in(mouseX, mouseY)) return false;
        int index = (int) Math.floor((mouseY - top + categoryScrollPixels) / CATEGORY_ROW_HEIGHT);
        List<CreativeCategoryRow> rows = creativeCategoryRows();
        if (index < 0 || index >= rows.size()) return true;
        CreativeCategoryRow row = rows.get(index);
        if (row.sectionHeader()) {
            List<TradingCategory> children = creativeSectionCategories(row.sectionId());
            if (children.size() == 1) {
                selectedCreativeSection = row.sectionId();
                selectedCreativeCategory = children.getFirst();
            } else if (children.size() > 1) {
                if (!expandedCreativeSections.remove(row.sectionId())) {
                    expandedCreativeSections.add(row.sectionId());
                }
            }
        } else {
            selectedCreativeSection = row.sectionId();
            selectedCreativeCategory = row.category();
        }
        resetGridScroll();
        selectionAnchor = null;
        return true;
    }

    private List<TradingCategory> creativeSectionCategories(
        TradingHtmlThemeParser.CategorySection section) {
        List<TradingCategory> result = new ArrayList<>();
        for (String id : section.categories()) {
            try {
                TradingCategory value = TradingCategory.valueOf(id.toUpperCase(Locale.ROOT));
                if (!result.contains(value)) result.add(value);
            } catch (IllegalArgumentException ignored) {
            }
        }
        return List.copyOf(result);
    }

    private List<TradingCategory> creativeSectionCategories(String sectionId) {
        for (TradingHtmlThemeParser.CategorySection section : creativeCategorySections()) {
            if (section.id().equals(sectionId)) return creativeSectionCategories(section);
        }
        return List.of();
    }

    private void switchCatalog(boolean inventory) {
        if (inventoryOnly == inventory) return;
        inventoryOnly = inventory;
        resetGridScroll();
        resetCategoryScroll();
        selectionAnchor = null;
        minecraft.execute(this::rebuildMaterialFrame);
    }

    private boolean showCategorySidebar() {
        return creativeCategories && !inventoryOnly;
    }

    private int gridX() {
        return gridAnchor == null ? 0 : Math.round(gridAnchor.getBounds().x);
    }

    private int gridY() {
        return gridAnchor == null ? 0 : Math.round(gridAnchor.getBounds().y);
    }

    private int gridWidth() {
        return gridAnchor == null ? 0 : Math.max(0, Math.round(gridAnchor.getBounds().width));
    }

    private int gridViewportHeight() {
        return gridAnchor == null ? 0 : Math.max(0, Math.round(gridAnchor.getBounds().height));
    }

    private boolean gridContains(double x, double y) {
        return gridAnchor != null && !gridAnchor.getBounds().isEmpty()
            && gridAnchor.getBounds().in(x, y);
    }

    private boolean categoryContains(double x, double y) {
        return categoryAnchor != null && !categoryAnchor.getBounds().isEmpty()
            && categoryAnchor.getBounds().in(x, y);
    }

    private static int indexOf(List<ItemStack> values, StackKey id) {
        for (int i = 0; i < values.size(); i++) {
            if (StackKey.of(values.get(i)).equals(id)) return i;
        }
        return -1;
    }

    private void beginContentDrag(DragArea area, double mouseY, double scroll) {
        dragArea = area;
        dragMoved = false;
        dragPressY = dragLastY = mouseY;
        dragPressScroll = scroll;
        dragLastNanos = System.nanoTime();
        dragVelocity = 0.0D;
    }

    private boolean beginGridScrollbarDrag(double mouseX, double mouseY) {
        int top = gridY(), viewport = gridViewportHeight();
        double max = maxGridScroll();
        int trackX = gridX() + gridWidth() - 6;
        if (max <= 0.0D || !inside(mouseX, mouseY, trackX - 2, top, 8, viewport)) return false;
        int thumb = scrollbarThumb(viewport, max);
        double thumbY = top + (viewport - thumb) * clamp(gridScrollPixels, 0.0D, max) / max;
        dragArea = DragArea.GRID_SCROLLBAR;
        dragMoved = true;
        scrollbarGrabOffset = mouseY >= thumbY && mouseY <= thumbY + thumb ? mouseY - thumbY : thumb / 2.0D;
        updateScrollbarDrag(mouseY);
        return true;
    }

    private boolean beginCategoryScrollbarDrag(double mouseX, double mouseY) {
        if (categoryAnchor == null) return false;
        Bounds bounds = categoryAnchor.getBounds();
        int top = Math.round(bounds.y), viewport = Math.max(0, Math.round(bounds.height));
        double max = maxCategoryScroll();
        int trackX = Math.round(bounds.x + bounds.width) - 7;
        if (max <= 0.0D || !inside(mouseX, mouseY, trackX - 2, top, 8, viewport)) return false;
        int thumb = scrollbarThumb(viewport, max);
        double thumbY = top + (viewport - thumb) * clamp(categoryScrollPixels, 0.0D, max) / max;
        dragArea = DragArea.CATEGORY_SCROLLBAR;
        dragMoved = true;
        scrollbarGrabOffset = mouseY >= thumbY && mouseY <= thumbY + thumb ? mouseY - thumbY : thumb / 2.0D;
        updateScrollbarDrag(mouseY);
        return true;
    }

    private void updateScrollbarDrag(double mouseY) {
        boolean grid = dragArea == DragArea.GRID_SCROLLBAR;
        Bounds bounds = grid || categoryAnchor == null
            ? gridAnchor.getBounds() : categoryAnchor.getBounds();
        int top = Math.round(bounds.y), viewport = Math.max(0, Math.round(bounds.height));
        double max = grid ? maxGridScroll() : maxCategoryScroll();
        int thumb = scrollbarThumb(viewport, max);
        double track = Math.max(1.0D, viewport - thumb);
        double value = clamp((mouseY - top - scrollbarGrabOffset) / track, 0.0D, 1.0D) * max;
        if (grid) gridScrollPixels = gridTargetPixels = value;
        else categoryScrollPixels = categoryTargetPixels = value;
    }

    private void updateGridScroll(double max) {
        gridTargetPixels = clamp(gridTargetPixels, 0.0D, max);
        gridScrollPixels = animateScroll(gridScrollPixels, gridTargetPixels, gridAnimationNanos);
        gridScrollPixels = clamp(gridScrollPixels, 0.0D, max);
    }

    private void updateCategoryScroll(double max) {
        categoryTargetPixels = clamp(categoryTargetPixels, 0.0D, max);
        categoryScrollPixels = animateScroll(categoryScrollPixels, categoryTargetPixels, categoryAnimationNanos);
        categoryScrollPixels = clamp(categoryScrollPixels, 0.0D, max);
    }

    private static double animateScroll(double current, double target, long startedAt) {
        if (Math.abs(target - current) < 0.1D) return target;
        double elapsed = Math.max(0.0D, (System.nanoTime() - startedAt) / 1_000_000_000.0D);
        double factor = 1.0D - Math.exp(-Math.max(0.02D, elapsed) * 18.0D);
        return current + (target - current) * clamp(factor, 0.08D, 0.45D);
    }

    private void drawGridScrollbar(GuiGraphics g, int x, int y, int width, int viewport, double max,
                                   int mouseX, int mouseY) {
        drawScrollbar(g, x + width - 6, y, viewport, max, gridScrollPixels,
            dragArea == DragArea.GRID_SCROLLBAR, mouseX, mouseY);
    }

    private void drawCategoryScrollbar(GuiGraphics g, int x, int y, int width, int viewport, double max,
                                       int mouseX, int mouseY) {
        drawScrollbar(g, x + width - 7, y, viewport, max, categoryScrollPixels,
            dragArea == DragArea.CATEGORY_SCROLLBAR, mouseX, mouseY);
    }

    private void drawScrollbar(GuiGraphics g, int x, int y, int viewport, double max, double value,
                               boolean dragging, int mouseX, int mouseY) {
        if (max <= 0.0D) return;
        int thumb = scrollbarThumb(viewport, max);
        int thumbY = y + (int) Math.round((viewport - thumb) * clamp(value, 0.0D, max) / max);
        boolean hover = inside(mouseX, mouseY, x - 2, y, 8, viewport);
        Material2Drawing.roundedRect(g, x, y, 4, viewport, 2,
            Material3Theme.OUTLINE_VARIANT);
        Material2Drawing.roundedRect(g, x, thumbY, 4, thumb, 2,
            (dragging || hover ? Material3Theme.PRIMARY : Material3Theme.TEXT_MUTED));
    }

    private static int scrollbarThumb(int viewport, double max) {
        return Math.max(20, (int) Math.round(viewport * viewport / (viewport + max)));
    }

    private double maxGridScroll() {
        int columns = Math.max(1, (gridWidth() - 18) / GRID_CELL);
        int rows = (filtered().size() + columns - 1) / columns;
        return Math.max(0.0D, rows * (double) GRID_CELL - gridViewportHeight() + 12.0D);
    }

    private double maxCategoryScroll() {
        int viewport = categoryAnchor == null ? 0
            : Math.max(0, Math.round(categoryAnchor.getBounds().height));
        return Math.max(0.0D, creativeCategoryRows().size() * (double) CATEGORY_ROW_HEIGHT
            - viewport);
    }

    private void resetGridScroll() {
        gridScrollPixels = gridTargetPixels = 0.0D;
        gridAnimationNanos = System.nanoTime();
    }

    private void resetCategoryScroll() {
        categoryScrollPixels = categoryTargetPixels = 0.0D;
        categoryAnimationNanos = System.nanoTime();
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private record CreativeCategoryRow(String sectionId, TradingCategory category,
                                       boolean sectionHeader) {
    }

    private record StackKey(ResourceLocation itemId, DataComponentPatch components) {
        private static StackKey of(ItemStack stack) {
            return new StackKey(BuiltInRegistries.ITEM.getKey(stack.getItem()),
                stack.getComponentsPatch());
        }
    }

    private enum DragArea {
        NONE,
        GRID_CONTENT,
        CATEGORY_CONTENT,
        GRID_SCROLLBAR,
        CATEGORY_SCROLLBAR
    }
}

