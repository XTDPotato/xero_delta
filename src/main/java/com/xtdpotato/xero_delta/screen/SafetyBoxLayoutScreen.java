package com.xtdpotato.xero_delta.screen;

import com.xtdpotato.xero_delta.screen.material.Material3Theme;

import com.xtdpotato.xero_delta.Config;
import com.xtdpotato.xero_delta.XeroDelta;
import com.xtdpotato.xero_delta.client.SafetyBoxOverlayRenderer;
import com.xtdpotato.xero_delta.client.ClientDataCache;
import com.xtdpotato.xero_delta.client.DeltaGridCellRenderer;
import com.xtdpotato.xero_delta.client.TradingUiPreferences;
import com.xtdpotato.xero_delta.client.TradingUiScale;
import com.xtdpotato.xero_delta.client.WidgetImageCache;
import com.xtdpotato.xero_delta.grid.GridGeometry;
import com.xtdpotato.xero_delta.screen.material.Material2Drawing;
import com.xtdpotato.xero_delta.tag.ModTags;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import com.xtdpotato.xero_delta.screen.material.Material3Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.nio.file.Path;
import java.util.*;

public class SafetyBoxLayoutScreen extends Screen {
    private final Screen parent;

    private static final String[] LAYOUTS = {"TOP", "BOTTOM", "LEFT", "RIGHT"};
    private static final int ROW_H = 40;
    private static final int BOX_ROW_H = 32;
    private static final int SEARCH_Y = 78;
    private static final int SEARCH_HEIGHT = 22;
    private static final int SECTION_SPACING = 6;
    private static final int BOX_LIST_TOP = SEARCH_Y + SEARCH_HEIGHT + SECTION_SPACING;
    private static final int BOX_LIST_VISIBLE = 3;
    private static final int BOX_LIST_BOTTOM = BOX_LIST_TOP + BOX_LIST_VISIBLE * BOX_ROW_H;
    private static final int BOTTOM_BAR = 34;
    private static final int CTRL_HEADER_OFFSET = 26;
    private static final int CTRL_TOP = BOX_LIST_BOTTOM + SECTION_SPACING + CTRL_HEADER_OFFSET;
    private static final int SCREEN_MARGIN = 12;
    private static final int SECTION_GAP = 12;
    private int PANEL_X = SCREEN_MARGIN;
    private int PANEL_W = 320;
    private static final int TREE_TOOLBAR_H = 64;
    private static final int HISTORY_LIMIT = 100;
    private static final int UI_SCALE_BUTTON_Y = 6;
    private static final int UI_SCALE_BUTTON_SIZE = 24;
    private static final int UI_SCALE_BUTTON_GAP = 4;

    private TradingUiScale.Viewport uiViewport = TradingUiScale.viewport(1, 1, -1);

    private List<Item> availableBoxes;
    private List<String> boxIds;
    private int selectedBoxIndex = 0;
    private double boxScrollPixels;
    private EditBox searchBox;
    private String searchFilter = "";

    private double ctrlScrollPixels;
    private double treeScrollPixels;
    private boolean draggingBoxScrollbar;
    private boolean draggingCtrlScrollbar;
    private boolean draggingTreeScrollbar;
    private boolean updating;
    private final List<EditBox> editBoxes = new ArrayList<>();
    private EditBox activeEditBox;
    private int activeEditRow = -1;

    private int selectedWidget = -1;
    private boolean draggingWidget, panningPreview, resizingWidget;
    private int resizeEdge; // 1=right, 2=bottom, 3=corner
    private int resizeStartW, resizeStartH;
    private double resizeStartScale;
    private int dragStartMX, dragStartMY, dragStartOffX, dragStartOffY;
    private int panStartPX, panStartPY, panStartMX, panStartMY;

    private float previewZoom = 2.0f;
    private int previewPanX, previewPanY;
    private static final float ZOOM_MIN = 0.5f, ZOOM_MAX = 4.0f, ZOOM_STEP = 0.05f;
    private int previewX, previewY, previewW, previewH;
    private int treeX, treeY, treeW, treeH;
    private SafetyBoxOverlayRenderer.HeaderResult lastResult;
    private GridGeometry previewGeom;

    private List<SafetyBoxLayoutPack.LayoutData> layoutDataList;
    private SafetyBoxLayoutPack.LayoutData currentLayout;
    private SafetyBoxLayoutPack.WidgetNode selectedNode;
    private final Map<SafetyBoxLayoutPack.WidgetNode, int[]> widgetPreviewBounds = new IdentityHashMap<>();
    private final Deque<HistoryEntry> undoHistory = new ArrayDeque<>();
    private final Deque<HistoryEntry> redoHistory = new ArrayDeque<>();
    private SafetyBoxLayoutPack.WidgetNode treeDragNode;
    private int treeDragStartX, treeDragStartY;
    private boolean draggingTreeNode;
    private boolean modified;
    private Path packDir;

    private record TreeEntry(SafetyBoxLayoutPack.WidgetNode node, int depth) {
    }

    private record HistoryEntry(SafetyBoxLayoutPack.LayoutData layout, String selectedNodeId) {
    }

    public SafetyBoxLayoutScreen(Screen parent) {
        super(Component.translatable("screen." + XeroDelta.MOD_ID + ".layout"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        uiViewport = TradingUiScale.viewport(width, height,
            TradingUiPreferences.safetyBoxEditorScaleLevel());
        width = uiViewport.logicalWidth();
        height = uiViewport.logicalHeight();
        if (!(parent instanceof XeroDeltaMaterialSettingsScreen)) {
            Material3Theme.refreshFromConfig();
        }
        clearWidgets();
        editBoxes.clear();
        activeEditBox = null; activeEditRow = -1;
        SafetyBoxLayoutPack.ensureDefaultPack();
        packDir = SafetyBoxLayoutPack.getDefaultPackDir();
        if (layoutDataList == null) {
            layoutDataList = SafetyBoxLayoutPack.loadLayouts(packDir);
            refreshBoxList();
        } else if (availableBoxes == null || boxIds == null) {
            refreshBoxList();
        } else {
            rebuildBoxFilter();
        }

        if (currentLayout == null && !layoutDataList.isEmpty()) {
            String targetId = filteredBoxIds.isEmpty() ? null : filteredBoxIds.get(Math.min(selectedBoxIndex, filteredBoxIds.size() - 1));
            var initMatch = layoutDataList.stream().filter(ld -> ld.boxId != null && ld.boxId.equals(targetId)).findFirst();
            if (initMatch.isPresent()) {
                currentLayout = initMatch.get();
            } else {
                currentLayout = SafetyBoxLayoutPack.createDefaultLayout(targetId != null ? targetId : "xero_delta:safety_box_3x3");
                currentLayout.boxId = targetId != null ? targetId : "xero_delta:safety_box_3x3";
                layoutDataList.add(currentLayout);
            }
            SafetyBoxLayoutPack.applyLayoutToConfig(currentLayout);
        } else if (currentLayout == null) {
            currentLayout = new SafetyBoxLayoutPack.LayoutData();
        }
        SafetyBoxLayoutPack.normalizeLayout(currentLayout);
        if (selectedNode == null || SafetyBoxLayoutPack.findWidget(currentLayout, selectedNode.id) == null) {
            selectedNode = currentLayout.root;
        }

        int preferredPanelWidth = Math.round(width * 0.32F);
        int maximumPanelWidth = Math.max(240,
            width - SCREEN_MARGIN * 2 - SECTION_GAP - 260);
        PANEL_W = Math.max(240, Math.min(440,
            Math.min(preferredPanelWidth, maximumPanelWidth)));
        previewX = PANEL_X + PANEL_W + SECTION_GAP;
        previewY = 44;
        previewW = width - previewX - SCREEN_MARGIN;
        previewH = Math.max(90, (height - 68) / 2);
        treeX = previewX;
        treeY = previewY + previewH + 6;
        treeW = previewW;
        treeH = Math.max(50, height - BOTTOM_BAR - treeY - 2);

        // Layout direction buttons
        int directionGap = 6;
        int directionWidth = Math.max(1, (PANEL_W - directionGap * 3) / 4);
        for (int i = 0; i < LAYOUTS.length; i++) {
            String lt = LAYOUTS[i];
            addRenderableWidget(Material3Button.builder(Component.translatable("screen.xero_delta.layout." + lt.toLowerCase()), b -> {
                recordHistory();
                currentLayout.layout = lt; Config.INSTANCE.overlayLayout.set(lt); modified = true;
            }).pos(PANEL_X + i * (directionWidth + directionGap), 48)
                .size(directionWidth, 24).build());
        }

        // Search box
        searchBox = new Material3CompactEditBox(font, PANEL_X, SEARCH_Y, PANEL_W, SEARCH_HEIGHT,
            Component.translatable("screen.xero_delta.search"));
        searchBox.setHint(Component.translatable("screen.xero_delta.filter_hint"));
        searchBox.setResponder(s -> { searchFilter = s; rebuildBoxFilter(); });
        searchBox.setValue(searchFilter);
        addRenderableWidget(searchBox);

        int scaleButtonX = SCREEN_MARGIN;
        addRenderableWidget(Material3Button.builder(Component.literal("-"),
            button -> adjustEditorScale(-1))
            .pos(scaleButtonX, UI_SCALE_BUTTON_Y)
            .size(UI_SCALE_BUTTON_SIZE, UI_SCALE_BUTTON_SIZE).build());
        addRenderableWidget(Material3Button.builder(Component.literal("+"),
            button -> adjustEditorScale(1))
            .pos(scaleButtonX + UI_SCALE_BUTTON_SIZE + UI_SCALE_BUTTON_GAP, UI_SCALE_BUTTON_Y)
            .size(UI_SCALE_BUTTON_SIZE, UI_SCALE_BUTTON_SIZE).build());

        // Bottom buttons
        int btnY = height - BOTTOM_BAR + 4;
        int bottomGap = width < 720 ? 4 : 8;
        int bottomWidth = Math.max(1,
            (width - SCREEN_MARGIN * 2 - bottomGap * 5) / 6);
        addRenderableWidget(Material3Button.builder(
            Component.translatable("screen.xero_delta.safety_box.cancel"),
            b -> onCancel())
            .pos(SCREEN_MARGIN, btnY).size(bottomWidth, 24).build());
        addRenderableWidget(Material3Button.builder(
            Component.translatable("screen.xero_delta.safety_box.save"),
            b -> saveCurrent())
            .pos(SCREEN_MARGIN + (bottomWidth + bottomGap), btnY).size(bottomWidth, 24).build());
        addRenderableWidget(Material3Button.builder(
            Component.translatable("screen.xero_delta.layout.save_all"),
            b -> saveAll())
            .pos(SCREEN_MARGIN + (bottomWidth + bottomGap) * 2, btnY).size(bottomWidth, 24).build());
        addRenderableWidget(Material3Button.builder(
            Component.translatable("gui.done"),
            b -> { saveCurrent(); closeParent(); })
            .pos(SCREEN_MARGIN + (bottomWidth + bottomGap) * 3, btnY).size(bottomWidth, 24).build());
        addRenderableWidget(Material3Button.builder(
            Component.translatable("screen.xero_delta.layout.reset"),
            b -> {
                recordHistory();
                String selectedId = currentLayout == null ? null : currentLayout.boxId;
                List<SafetyBoxLayoutPack.LayoutData> defaults = new ArrayList<>();
                for (String boxId : boxIds) defaults.add(SafetyBoxLayoutPack.createDefaultLayout(boxId));
                layoutDataList = defaults;
                if (!layoutDataList.isEmpty()) {
                    String targetId = selectedId != null ? selectedId
                        : filteredBoxIds.isEmpty() ? null : filteredBoxIds.get(Math.min(selectedBoxIndex, filteredBoxIds.size() - 1));
                    var resetMatch = layoutDataList.stream().filter(ld -> ld.boxId != null && ld.boxId.equals(targetId)).findFirst();
                    if (resetMatch.isPresent()) {
                        currentLayout = resetMatch.get();
                    } else if (!layoutDataList.isEmpty()) {
                        currentLayout = layoutDataList.get(0);
                    }
                    selectNode(currentLayout.root);
                    SafetyBoxLayoutPack.applyLayoutToConfig(currentLayout);
                }
                modified = true;
            })
            .pos(SCREEN_MARGIN + (bottomWidth + bottomGap) * 4, btnY).size(bottomWidth, 24).build());
        addRenderableWidget(Material3Button.builder(
            Component.translatable("screen.xero_delta.layout.apply_all"),
            b -> applyToAll())
            .pos(SCREEN_MARGIN + (bottomWidth + bottomGap) * 5, btnY).size(bottomWidth, 24).build());



        rebuildControlPanel();
    }

    private List<String> filteredBoxIds = new ArrayList<>();
    private List<Item> filteredBoxes = new ArrayList<>();

    private void refreshBoxList() {
        LinkedHashMap<String, Item> boxes = new LinkedHashMap<>();
        for (String boxId : SafetyBoxLayoutPack.editorBoxIds(layoutDataList)) {
            ResourceLocation id = ResourceLocation.tryParse(boxId);
            if (id != null && BuiltInRegistries.ITEM.containsKey(id)) {
                boxes.put(boxId, BuiltInRegistries.ITEM.get(id));
            }
        }

        // Tags are synchronized with a world connection. They supplement the
        // file-backed list, but must not be the editor's only source.
        var tag = BuiltInRegistries.ITEM.getTag(ModTags.SAFETY_BOX);
        tag.ifPresent(itemTag -> itemTag.forEach(holder -> {
            boxes.putIfAbsent(holder.getKey().location().toString(), holder.value());
        }));
        if (boxes.isEmpty()) boxes.put("minecraft:chest", Items.CHEST);
        boxIds = new ArrayList<>(boxes.keySet());
        availableBoxes = new ArrayList<>(boxes.values());
        rebuildBoxFilter();
    }

    private void rebuildBoxFilter() {
        filteredBoxIds.clear(); filteredBoxes.clear();
        String lower = searchFilter != null ? searchFilter.toLowerCase(Locale.ROOT) : "";
        for (int i = 0; i < boxIds.size(); i++) {
            String id = boxIds.get(i); Item item = availableBoxes.get(i);
            String name = item.getDescription().getString().toLowerCase(Locale.ROOT);
            if (lower.isEmpty() || id.toLowerCase(Locale.ROOT).contains(lower) || name.contains(lower)) {
                filteredBoxIds.add(id); filteredBoxes.add(item);
            }
        }
        boxScrollPixels = 0;
        if (selectedBoxIndex >= filteredBoxIds.size()) selectedBoxIndex = Math.max(0, filteredBoxIds.size() - 1);
    }

    private Item getBox() { return filteredBoxes.isEmpty() ? Items.CHEST : filteredBoxes.get(clamp(selectedBoxIndex, 0, filteredBoxes.size() - 1)); }
    private int getGW() { Item b = getBox(); return b instanceof com.xtdpotato.xero_delta.item.SafetyBoxItem sbi ? sbi.getGridWidth() : 3; }
    private int getGH() { Item b = getBox(); return b instanceof com.xtdpotato.xero_delta.item.SafetyBoxItem sbi ? sbi.getGridHeight() : 3; }

    private void saveCurrent() {
        if (currentLayout != null && currentLayout.boxId != null) {
            SafetyBoxLayoutPack.saveLayout(packDir, currentLayout);
            saveCurrentLayoutToConfig();
            Config.SPEC.save();
        }
        modified = false;
    }
    private void saveAll() {
        saveCurrent();
        if (currentLayout.boxId == null) return;
        for (var ld : layoutDataList) {
            if (ld.boxId != null && !ld.boxId.equals(currentLayout.boxId)) SafetyBoxLayoutPack.saveLayout(packDir, ld);
        }
        Config.SPEC.save();
        modified = false;
    }

    private void onCancel() {
        if (modified) minecraft.setScreen(new ConfirmScreen(c -> {
            if (c) discardAndClose(); else minecraft.setScreen(this);
        }, Component.translatable("screen.xero_delta.unsaved_title"), Component.translatable("screen.xero_delta.unsaved_msg")));
        else closeParent();
    }

    private void applyToAll() {
        String curId = currentLayout.boxId;
        for (var ld : layoutDataList) { if (!ld.boxId.equals(curId)) copyLayoutExceptId(ld); }
        modified = true;
    }
    private void copyLayoutExceptId(SafetyBoxLayoutPack.LayoutData ld) {
        ld.layout = currentLayout.layout; ld.verticalText = currentLayout.verticalText;
        ld.iconScale = currentLayout.iconScale; ld.iconOffX = currentLayout.iconOffX; ld.iconOffY = currentLayout.iconOffY;
        ld.textScale = currentLayout.textScale; ld.textOffX = currentLayout.textOffX; ld.textOffY = currentLayout.textOffY;
        ld.textPad = currentLayout.textPad; ld.borderPad = currentLayout.borderPad;
        ld.borderOffX = currentLayout.borderOffX; ld.borderOffY = currentLayout.borderOffY;
        ld.bgW = currentLayout.bgW; ld.bgH = currentLayout.bgH;
        ld.panelPadLeft = currentLayout.panelPadLeft; ld.panelPadRight = currentLayout.panelPadRight;
        ld.panelPadTop = currentLayout.panelPadTop;
        ld.panelPadBottom = currentLayout.panelPadBottom; ld.panelAlpha = currentLayout.panelAlpha;
        ld.globalOffX = currentLayout.globalOffX; ld.globalOffY = currentLayout.globalOffY;
        ld.centerX = currentLayout.centerX; ld.centerY = currentLayout.centerY;
        ld.gridOffX = currentLayout.gridOffX; ld.gridOffY = currentLayout.gridOffY; ld.gridScale = currentLayout.gridScale;
        SafetyBoxLayoutPack.copyWidgetStructure(currentLayout, ld);
    }

    private void recordHistory() {
        if (currentLayout == null) return;
        undoHistory.addLast(new HistoryEntry(SafetyBoxLayoutPack.copyLayout(currentLayout),
            selectedNode == null ? null : selectedNode.id));
        while (undoHistory.size() > HISTORY_LIMIT) undoHistory.removeFirst();
        redoHistory.clear();
    }

    private void undo() {
        if (undoHistory.isEmpty() || currentLayout == null) return;
        redoHistory.addLast(new HistoryEntry(SafetyBoxLayoutPack.copyLayout(currentLayout),
            selectedNode == null ? null : selectedNode.id));
        restoreHistory(undoHistory.removeLast());
    }

    private void redo() {
        if (redoHistory.isEmpty() || currentLayout == null) return;
        undoHistory.addLast(new HistoryEntry(SafetyBoxLayoutPack.copyLayout(currentLayout),
            selectedNode == null ? null : selectedNode.id));
        restoreHistory(redoHistory.removeLast());
    }

    private void restoreHistory(HistoryEntry entry) {
        String boxId = currentLayout.boxId;
        SafetyBoxLayoutPack.LayoutData restored = SafetyBoxLayoutPack.copyLayout(entry.layout());
        for (int index = 0; index < layoutDataList.size(); index++) {
            if (Objects.equals(layoutDataList.get(index).boxId, boxId)) {
                layoutDataList.set(index, restored);
                break;
            }
        }
        currentLayout = restored;
        SafetyBoxLayoutPack.WidgetNode restoredSelection = SafetyBoxLayoutPack.findWidget(restored, entry.selectedNodeId());
        selectNode(restoredSelection == null ? restored.root : restoredSelection);
        SafetyBoxLayoutPack.applyLayoutToConfig(restored);
        modified = true;
    }

    // ---- Control panel with edit boxes + buttons ----

    @FunctionalInterface interface IntSetter { void set(int v); }
    @FunctionalInterface interface DoubleSetter { void set(double v); }
    @FunctionalInterface interface BooleanSetter { void set(boolean v); }

    private int ctrlBottom() {
        return height - BOTTOM_BAR - 4;
    }

    private void rebuildControlPanel() {
        for (EditBox eb : editBoxes) removeWidget(eb);
        editBoxes.clear();
        activeEditBox = null; activeEditRow = -1;
        int visible = (height - BOTTOM_BAR - CTRL_TOP) / ROW_H;
        // Control rows are rendered in render(), widgets are added here for interaction
        // We use custom rendering + mouse click handling instead of widget-based controls
    }

    private static String fd(double v) { return v == (int)v ? String.valueOf((int)v) : String.format("%.1f", v); }

    private static boolean inside(double mouseX, double mouseY,
                                  int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width
            && mouseY >= y && mouseY < y + height;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        g.fill(0, 0, uiViewport.physicalWidth(), uiViewport.physicalHeight(),
            Material3Theme.BACKGROUND);
        mx = uiViewport.mouseX(mx);
        my = uiViewport.mouseY(my);
        g.pose().pushPose();
        uiViewport.apply(g);
        try {
            Material2Drawing.roundedRect(g, PANEL_X - 6, 40, PANEL_W + 12,
                height - BOTTOM_BAR - 46, Material3Theme.RADIUS_LARGE,
                Material3Theme.SURFACE_CONTAINER_LOW);
            g.drawString(font, Component.translatable("screen.xero_delta.layout.select_layout"),
                PANEL_X, 31, Material3Theme.TEXT_MUTED, false);

        // ---- Box list ----
        int boxListHeight = BOX_LIST_VISIBLE * BOX_ROW_H;
        int firstBox = Math.max(0, (int)(boxScrollPixels / BOX_ROW_H));
        int boxPixelOffset = (int)(boxScrollPixels % BOX_ROW_H);
        enableUiScissor(g, PANEL_X, BOX_LIST_TOP,
            PANEL_X + PANEL_W, BOX_LIST_TOP + boxListHeight);
        for (int i = 0; i < BOX_LIST_VISIBLE + 2; i++) {
            int idx = i + firstBox;
            if (idx >= filteredBoxes.size()) break;
            int ry = BOX_LIST_TOP + i * BOX_ROW_H - boxPixelOffset;
            boolean sel = idx == selectedBoxIndex;
            Material2Drawing.roundedRect(g, PANEL_X, ry + 2, PANEL_W - 8, BOX_ROW_H - 4,
                Material3Theme.RADIUS_SMALL, sel ? Material3Theme.PRIMARY_CONTAINER
                    : Material3Theme.SURFACE_CONTAINER);
            g.renderItem(new ItemStack(filteredBoxes.get(idx)), PANEL_X + 6, ry + 6);
            String name = filteredBoxes.get(idx).getDescription().getString();
            g.drawString(font, font.plainSubstrByWidth(name, PANEL_W - 42), PANEL_X + 30,
                ry + 10, sel ? Material3Theme.ON_PRIMARY_CONTAINER : Material3Theme.TEXT);
        }
        g.disableScissor();
        double maxBoxScroll = Math.max(0, filteredBoxes.size() * BOX_ROW_H - boxListHeight);
        renderScrollbar(g, PANEL_X + PANEL_W - 5, BOX_LIST_TOP, boxListHeight, boxScrollPixels, maxBoxScroll);

        // ---- Control panel ----
        int ctrlBottom = ctrlBottom();
        String selHintKey = selectedWidget == 0 ? "screen.xero_delta.layout.widget_bg" : selectedWidget == 1 ? "screen.xero_delta.layout.widget_icon" : selectedWidget == 2 ? "screen.xero_delta.layout.widget_text" : selectedWidget == 3 ? "screen.xero_delta.layout.widget_grid" : "screen.xero_delta.layout.widget_all";

        Material2Drawing.roundedRect(g, PANEL_X, CTRL_TOP - 26, PANEL_W - 8, 22,
            Material3Theme.RADIUS_FULL, Material3Theme.SECONDARY_CONTAINER);
        g.drawString(font, Component.translatable(selHintKey), PANEL_X + 10, CTRL_TOP - 19,
            Material3Theme.ON_SECONDARY_CONTAINER, false);

        int total = countCtrlRows();
        int visiblePixels = Math.max(1, ctrlBottom - CTRL_TOP);
        int visible = visiblePixels / ROW_H;
        enableUiScissor(g, PANEL_X, CTRL_TOP, PANEL_X + PANEL_W - 6, ctrlBottom);
        renderCtrlPanel(g, mx, my, visible);
        g.disableScissor();
        double maxCtrlScroll = Math.max(0, total * ROW_H - visiblePixels);
        renderScrollbar(g, PANEL_X + PANEL_W - 5, CTRL_TOP, visiblePixels, ctrlScrollPixels, maxCtrlScroll);

        // ---- Preview ----
        Material2Drawing.roundedRect(g, previewX, previewY, previewW, previewH,
            Material3Theme.RADIUS_LARGE, Material3Theme.SURFACE_CONTAINER_LOW);
        Material2Drawing.outlineRoundedRect(g, previewX, previewY, previewW, previewH,
            Material3Theme.RADIUS_LARGE, 1.0F, Material3Theme.OUTLINE_VARIANT);
        renderPreview(g, mx, my);
        int zoomY = previewY + 4;
        // Zoom out button
        Material2Drawing.roundedRect(g, previewX + previewW - 84, zoomY,
            24, 20, Material3Theme.RADIUS_FULL, Material3Theme.SECONDARY_CONTAINER);
        g.drawCenteredString(font, "-", previewX + previewW - 72, zoomY + 6,
            Material3Theme.ON_SECONDARY_CONTAINER);
        // Zoom display
        Material2Drawing.roundedRect(g, previewX + previewW - 58, zoomY,
            32, 20, Material3Theme.RADIUS_SMALL, Material3Theme.SURFACE_CONTAINER_HIGH);
        g.drawCenteredString(font, String.format("%.0f%%", previewZoom * 100),
            previewX + previewW - 42, zoomY + 6, Material3Theme.TEXT);
        // Zoom in button
        Material2Drawing.roundedRect(g, previewX + previewW - 24, zoomY,
            20, 20, Material3Theme.RADIUS_FULL, Material3Theme.SECONDARY_CONTAINER);
        g.drawCenteredString(font, "+", previewX + previewW - 14, zoomY + 6,
            Material3Theme.ON_SECONDARY_CONTAINER);
        g.drawString(font, Component.translatable("screen.xero_delta.layout.sel_hint",
            Component.translatable(selHintKey).getString()), previewX + 12, previewY + 10,
            Material3Theme.PRIMARY);
        g.drawString(font, Component.translatable("screen.xero_delta.layout.click_hint"),
            previewX + 12, previewY + previewH - 18, Material3Theme.TEXT_MUTED);

            renderWidgetTree(g, mx, my);
            super.render(g, mx, my, pt);
            g.drawString(font, Math.round(uiViewport.scale() * 100.0F) + "%",
                SCREEN_MARGIN + (UI_SCALE_BUTTON_SIZE + UI_SCALE_BUTTON_GAP) * 2 + 4,
                UI_SCALE_BUTTON_Y + 8, Material3Theme.TEXT_MUTED, false);
            g.drawCenteredString(font, this.title, width / 2, 16, Material3Theme.TEXT);
        } finally {
            g.pose().popPose();
        }
    }

    private void enableUiScissor(GuiGraphics graphics,
                                 int left, int top, int right, int bottom) {
        uiViewport.enableScissor(graphics, left, top, right, bottom);
    }

    @Override
    protected void renderBlurredBackground(float partialTick) {
    }

    private void renderWidgetTree(GuiGraphics g, int mouseX, int mouseY) {
        Material2Drawing.roundedRect(g, treeX, treeY, treeW, treeH,
            Material3Theme.RADIUS_LARGE, Material3Theme.SURFACE_CONTAINER_LOW);
        Material2Drawing.outlineRoundedRect(g, treeX, treeY, treeW, treeH,
            Material3Theme.RADIUS_LARGE, 1.0F, Material3Theme.OUTLINE_VARIANT);
        int toolbarY = treeY + 8;
        String[] labels = {
            "screen.xero_delta.layout.add_text",
            "screen.xero_delta.layout.add_image",
            "screen.xero_delta.layout.add_layout",
            "screen.xero_delta.layout.add_grid",
            "screen.xero_delta.layout.delete"
        };
        int toolbarGap = 6;
        int buttonWidth = Math.max(1,
            (treeW - 16 - toolbarGap * (labels.length - 1)) / labels.length);
        for (int index = 0; index < labels.length; index++) {
            int bx = treeX + 8 + index * (buttonWidth + toolbarGap);
            boolean hovered = mouseX >= bx && mouseX < bx + buttonWidth
                && mouseY >= toolbarY && mouseY < toolbarY + 22;
            Material2Drawing.roundedRect(g, bx, toolbarY, buttonWidth, 22,
                Material3Theme.RADIUS_SMALL, hovered
                    ? Material3Theme.SURFACE_CONTAINER_HIGHEST
                    : Material3Theme.SURFACE_CONTAINER_HIGH);
            String label = Component.translatable(labels[index]).getString();
            g.drawCenteredString(font, font.plainSubstrByWidth(label, buttonWidth - 10),
                bx + buttonWidth / 2, toolbarY + 7,
                index == 4 ? Material3Theme.ERROR : Material3Theme.TEXT);
        }

        String[] moveLabels = {
            "screen.xero_delta.layout.move_up",
            "screen.xero_delta.layout.move_down",
            "screen.xero_delta.layout.move_out",
            "screen.xero_delta.layout.move_in",
            "screen.xero_delta.layout.widget_properties"
        };
        int moveY = toolbarY + 28;
        for (int index = 0; index < moveLabels.length; index++) {
            int bx = treeX + 8 + index * (buttonWidth + toolbarGap);
            boolean hovered = mouseX >= bx && mouseX < bx + buttonWidth
                && mouseY >= moveY && mouseY < moveY + 22;
            Material2Drawing.roundedRect(g, bx, moveY, buttonWidth, 22,
                Material3Theme.RADIUS_SMALL, hovered
                    ? Material3Theme.SURFACE_CONTAINER_HIGHEST
                    : Material3Theme.SURFACE_CONTAINER_HIGH);
            String label = Component.translatable(moveLabels[index]).getString();
            g.drawCenteredString(font, font.plainSubstrByWidth(label, buttonWidth - 10),
                bx + buttonWidth / 2, moveY + 7, Material3Theme.TEXT);
        }

        List<TreeEntry> entries = treeEntries();
        int listTop = treeListTop();
        int listHeight = Math.max(1, treeY + treeH - listTop - 2);
        double maxScroll = Math.max(0, entries.size() * 18 - listHeight);
        treeScrollPixels = Math.max(0, Math.min(maxScroll, treeScrollPixels));
        int first = Math.max(0, (int)(treeScrollPixels / 18));
        int pixelOffset = (int)(treeScrollPixels % 18);
        enableUiScissor(g, treeX + 1, listTop,
            treeX + treeW - 1, listTop + listHeight);
        for (int visibleIndex = 0; visibleIndex < entries.size() - first; visibleIndex++) {
            int index = first + visibleIndex;
            int rowY = listTop + visibleIndex * 18 - pixelOffset;
            if (rowY >= listTop + listHeight) break;
            TreeEntry entry = entries.get(index);
            boolean selected = selectedNode == entry.node();
            Material2Drawing.roundedRect(g, treeX + 4, rowY + 1,
                treeW - 12, 16, Material3Theme.RADIUS_EXTRA_SMALL,
                selected ? Material3Theme.PRIMARY_CONTAINER
                    : Material3Theme.SURFACE_CONTAINER);
            String prefix = "layout".equals(entry.node().type) ? "▾ " : "• ";
            int textX = treeX + 5 + entry.depth() * 12;
            g.drawString(font, font.plainSubstrByWidth(prefix + nodeDisplayName(entry.node()),
                Math.max(20, treeW - (textX - treeX) - 14)), textX, rowY + 5,
                selected ? Material3Theme.ON_PRIMARY_CONTAINER : Material3Theme.TEXT);
        }
        g.disableScissor();
        renderScrollbar(g, treeX + treeW - 6, listTop, listHeight, treeScrollPixels, maxScroll);
    }

    private int treeListTop() {
        return treeY + TREE_TOOLBAR_H + 2;
    }

    private int toolbarIndexAt(double mouseX, int buttonWidth, int gap) {
        int localX = (int) mouseX - treeX - 8;
        if (localX < 0) return -1;
        int stride = buttonWidth + gap;
        int index = localX / stride;
        return index < 5 && localX % stride < buttonWidth ? index : -1;
    }

    private String nodeDisplayName(SafetyBoxLayoutPack.WidgetNode node) {
        if (node == null) return "";
        String key = switch (node.name == null ? "" : node.name) {
            case "Root Layout" -> "root_layout";
            case "Background Layout" -> "background_layout";
            case "Layout Widget" -> "layout_widget";
            case "Text Widget" -> "text_widget";
            case "Image Widget", "Icon Widget" -> "image_widget";
            case "Grid Widget" -> "grid_widget";
            default -> null;
        };
        return key == null ? (node.name == null ? node.type : node.name)
            : Component.translatable("screen.xero_delta.layout.name." + key).getString();
    }

    private List<TreeEntry> treeEntries() {
        List<TreeEntry> entries = new ArrayList<>();
        if (currentLayout != null && currentLayout.root != null) collectTreeEntries(currentLayout.root, 0, entries);
        return entries;
    }

    private void collectTreeEntries(SafetyBoxLayoutPack.WidgetNode node, int depth, List<TreeEntry> entries) {
        entries.add(new TreeEntry(node, depth));
        for (SafetyBoxLayoutPack.WidgetNode child : node.children) collectTreeEntries(child, depth + 1, entries);
    }

    private void selectNode(SafetyBoxLayoutPack.WidgetNode node) {
        selectedNode = node;
        // Legacy bridge nodes use the same highlight geometry as the original
        // preview controls; custom nodes use their recorded widget bounds.
        selectedWidget = node == null || node == currentLayout.root
            ? -1 : node.legacyBridge ? legacyWidgetIndex(node) : -2;
        ctrlScrollPixels = 0;
        rebuildControlPanel();
    }

    private int legacyWidgetIndex(SafetyBoxLayoutPack.WidgetNode node) {
        if (node == null || node == currentLayout.root) return -1;
        if ("grid".equals(node.type)) return 3;
        if ("text".equals(node.type)) return 2;
        if ("image".equals(node.type) && "Icon Widget".equals(node.name)) return 1;
        if ("layout".equals(node.type) && "Background Layout".equals(node.name)) return 0;
        return -2;
    }

    private void addWidget(String type) {
        recordHistory();
        SafetyBoxLayoutPack.WidgetNode parentNode = selectedNode != null && "layout".equals(selectedNode.type)
            ? selectedNode : SafetyBoxLayoutPack.findParent(currentLayout, selectedNode);
        SafetyBoxLayoutPack.WidgetNode added = SafetyBoxLayoutPack.addWidget(currentLayout, parentNode, type);
        selectNode(added);
        modified = true;
    }

    private void deleteSelectedWidget() {
        if (selectedNode == null || selectedNode == currentLayout.root) return;
        recordHistory();
        if (SafetyBoxLayoutPack.removeWidget(currentLayout, selectedNode)) {
            selectedNode = currentLayout.root;
            selectedWidget = -1;
            modified = true;
        }
    }

    private void moveSelectedWidget(int action) {
        if (selectedNode == null || selectedNode == currentLayout.root) return;
        SafetyBoxLayoutPack.WidgetNode parentNode = SafetyBoxLayoutPack.findParent(currentLayout, selectedNode);
        if (parentNode == null) return;
        boolean canMove = switch (action) {
            case 0 -> parentNode.children.indexOf(selectedNode) > 0;
            case 1 -> parentNode.children.indexOf(selectedNode) < parentNode.children.size() - 1;
            case 2 -> parentNode != currentLayout.root;
            case 3 -> {
                int index = parentNode.children.indexOf(selectedNode);
                yield index > 0 && "layout".equals(parentNode.children.get(index - 1).type);
            }
            default -> false;
        };
        if (!canMove) return;
        recordHistory();
        boolean moved = switch (action) {
            case 0 -> SafetyBoxLayoutPack.moveWidgetBy(currentLayout, selectedNode, -1);
            case 1 -> SafetyBoxLayoutPack.moveWidgetBy(currentLayout, selectedNode, 1);
            case 2 -> {
                SafetyBoxLayoutPack.WidgetNode grandParent = SafetyBoxLayoutPack.findParent(currentLayout, parentNode);
                int parentIndex = grandParent == null ? -1 : grandParent.children.indexOf(parentNode);
                yield grandParent != null && SafetyBoxLayoutPack.moveWidget(currentLayout, selectedNode, grandParent, parentIndex + 1);
            }
            case 3 -> {
                int index = parentNode.children.indexOf(selectedNode);
                SafetyBoxLayoutPack.WidgetNode previous = parentNode.children.get(index - 1);
                yield SafetyBoxLayoutPack.moveWidget(currentLayout, selectedNode, previous, previous.children.size());
            }
            default -> false;
        };
        if (moved) {
            SafetyBoxLayoutPack.normalizeLayout(currentLayout);
            modified = true;
        } else if (!undoHistory.isEmpty()) {
            undoHistory.removeLast();
        }
    }

    private void renderScrollbar(GuiGraphics g, int sx, int sy, int sh, double offset, double maxOff) {
        if (maxOff <= 0) return;
        float pct = (float)(offset / maxOff);
        int thumbH = Math.max(10, (int)(sh * (sh / (sh + maxOff))));
        int thumbY = sy + (int)((sh - thumbH) * pct);
        Material2Drawing.roundedRect(g, sx, sy, 5, sh,
            Material3Theme.RADIUS_FULL, Material3Theme.OUTLINE_VARIANT);
        Material2Drawing.roundedRect(g, sx, thumbY, 5, thumbH,
            Material3Theme.RADIUS_FULL, Material3Theme.PRIMARY);
    }

    private int countCtrlRows() {
        if (selectedWidget == -2 && selectedNode != null) return nodeControlKeys().size();
        int n = 2; // centerX, centerY
        if (selectedWidget == -1) n += 2;
        if (selectedWidget == -1 || selectedWidget == 0) n += 4;
        if (selectedWidget == -1 || selectedWidget == 1) n += 3;
        if (selectedWidget == -1 || selectedWidget == 2) n += 4;
        if (selectedWidget == -1) n += 8;
        if (selectedWidget == 3) n += 8;
        return n;
    }

    private void renderCtrlPanel(GuiGraphics g, int mx, int my, int visible) {
        if (selectedWidget == -2 && selectedNode != null) {
            int row = 0;
            for (String key : nodeControlKeys()) {
                if ("nodeProperties".equals(key)) {
                    row = ctrlAction(g, nodeLabel(key), Component.translatable("screen.xero_delta.layout.edit_full_properties").getString(), row, visible, mx, my);
                } else if (key.startsWith("nodeString:")) {
                    row = ctrlString(g, key.substring("nodeString:".length()), row, visible, mx, my);
                } else if ("nodeVerticalText".equals(key)) {
                    row = ctrlToggle(g, nodeLabel(key), selectedNode.verticalText, row,
                        value -> { selectedNode.verticalText = value; syncLegacyFromNode(selectedNode); modified = true; }, mx, my);
                } else if ("nodeOuterEnabled".equals(key)) {
                    row = ctrlToggle(g, nodeLabel(key), selectedNode.outerBorder.enabled, row,
                        value -> { selectedNode.outerBorder.enabled = value; modified = true; }, mx, my);
                } else if ("nodeInnerEnabled".equals(key)) {
                    row = ctrlToggle(g, nodeLabel(key), selectedNode.innerBorder.enabled, row,
                        value -> { selectedNode.innerBorder.enabled = value; modified = true; }, mx, my);
                } else if ("nodeFontSize".equals(key)) {
                    row = ctrlDouble(g, nodeLabel(key), selectedNode.fontSize, 0.2, 8.0, row, visible,
                        value -> { selectedNode.fontSize = value; modified = true; }, mx, my);
                } else {
                    row = ctrlInt(g, nodeLabel(key), nodeIntValue(key), nodeMin(key), nodeMax(key), row, visible,
                        value -> setNodeIntValue(key, value), mx, my);
                }
            }
            return;
        }
        int row = 0;
        row = ctrlInt(g, "screen.xero_delta.layout.center_x", currentLayout.centerX, -1, 100, row, visible, v -> { currentLayout.centerX = v; Config.INSTANCE.overlayCenterX.set(v); modified = true; }, mx, my);
        row = ctrlInt(g, "screen.xero_delta.layout.center_y", currentLayout.centerY, -1, 100, row, visible, v -> { currentLayout.centerY = v; Config.INSTANCE.overlayCenterY.set(v); modified = true; }, mx, my);
        if (selectedWidget == -1) {
            row = ctrlInt(g, "screen.xero_delta.layout.global_x", currentLayout.globalOffX, -100, 100, row, visible, v -> { currentLayout.globalOffX = v; Config.INSTANCE.overlayGlobalOffsetX.set(v); modified = true; }, mx, my);
            row = ctrlInt(g, "screen.xero_delta.layout.global_y", currentLayout.globalOffY, -100, 100, row, visible, v -> { currentLayout.globalOffY = v; Config.INSTANCE.overlayGlobalOffsetY.set(v); modified = true; }, mx, my);
        }
        if (selectedWidget == -1 || selectedWidget == 0) {
            row = ctrlInt(g, "screen.xero_delta.layout.bg_w", currentLayout.bgW, 0, 200, row, visible, v -> { currentLayout.bgW = v; Config.INSTANCE.overlayBgWidth.set(v); modified = true; }, mx, my);
            row = ctrlInt(g, "screen.xero_delta.layout.bg_h", currentLayout.bgH, 0, 200, row, visible, v -> { currentLayout.bgH = v; Config.INSTANCE.overlayBgHeight.set(v); modified = true; }, mx, my);
            row = ctrlInt(g, "screen.xero_delta.layout.border_x", currentLayout.borderOffX, -30, 30, row, visible, v -> { currentLayout.borderOffX = v; Config.INSTANCE.overlayBorderOffsetX.set(v); modified = true; }, mx, my);
            row = ctrlInt(g, "screen.xero_delta.layout.border_y", currentLayout.borderOffY, -30, 30, row, visible, v -> { currentLayout.borderOffY = v; Config.INSTANCE.overlayBorderOffsetY.set(v); modified = true; }, mx, my);
        }
        if (selectedWidget == -1 || selectedWidget == 1) {
            row = ctrlDouble(g, "screen.xero_delta.layout.icon_scale", currentLayout.iconScale, 0.2, 2.0, row, visible, v -> { currentLayout.iconScale = v; Config.INSTANCE.overlayIconScale.set(v); modified = true; }, mx, my);
            row = ctrlInt(g, "screen.xero_delta.layout.icon_x", currentLayout.iconOffX, -30, 30, row, visible, v -> { currentLayout.iconOffX = v; Config.INSTANCE.overlayIconOffsetX.set(v); modified = true; }, mx, my);
            row = ctrlInt(g, "screen.xero_delta.layout.icon_y", currentLayout.iconOffY, -30, 30, row, visible, v -> { currentLayout.iconOffY = v; Config.INSTANCE.overlayIconOffsetY.set(v); modified = true; }, mx, my);
        }
        if (selectedWidget == -1 || selectedWidget == 2) {
            row = ctrlDouble(g, "screen.xero_delta.layout.text_scale", currentLayout.textScale, 0.2, 2.0, row, visible, v -> { currentLayout.textScale = v; Config.INSTANCE.overlayTextScale.set(v); modified = true; }, mx, my);
            row = ctrlInt(g, "screen.xero_delta.layout.text_x", currentLayout.textOffX, -30, 30, row, visible, v -> { currentLayout.textOffX = v; Config.INSTANCE.overlayTextOffsetX.set(v); modified = true; }, mx, my);
            row = ctrlInt(g, "screen.xero_delta.layout.text_y", currentLayout.textOffY, -30, 30, row, visible, v -> { currentLayout.textOffY = v; Config.INSTANCE.overlayTextOffsetY.set(v); modified = true; }, mx, my);
            row = ctrlInt(g, "screen.xero_delta.layout.text_pad", currentLayout.textPad, 0, 12, row, visible, v -> { currentLayout.textPad = v; Config.INSTANCE.overlayTextPadding.set(v); modified = true; }, mx, my);
        }
        if (selectedWidget == -1 || selectedWidget == 3) {
            row = ctrlDouble(g, "screen.xero_delta.layout.grid_scale", currentLayout.gridScale, 0.5, 2.0, row, visible, v -> { currentLayout.gridScale = v; Config.INSTANCE.overlayGridScale.set(v); modified = true; }, mx, my);
            row = ctrlInt(g, "screen.xero_delta.layout.grid_x", currentLayout.gridOffX, -30, 30, row, visible, v -> { currentLayout.gridOffX = v; Config.INSTANCE.overlayGridOffsetX.set(v); modified = true; }, mx, my);
            row = ctrlInt(g, "screen.xero_delta.layout.grid_y", currentLayout.gridOffY, -30, 30, row, visible, v -> { currentLayout.gridOffY = v; Config.INSTANCE.overlayGridOffsetY.set(v); modified = true; }, mx, my);
            row = ctrlInt(g, "screen.xero_delta.layout.panel_alpha", currentLayout.panelAlpha, 0, 100, row, visible, v -> { currentLayout.panelAlpha = clamp(v, 0, 100); modified = true; }, mx, my);
            row = ctrlInt(g, "screen.xero_delta.layout.panel_pad_left", currentLayout.panelPadLeft, 0, 60, row, visible, v -> { currentLayout.panelPadLeft = clamp(v, 0, 60); modified = true; }, mx, my);
            row = ctrlInt(g, "screen.xero_delta.layout.panel_pad_right", currentLayout.panelPadRight, 0, 60, row, visible, v -> { currentLayout.panelPadRight = clamp(v, 0, 60); modified = true; }, mx, my);
            row = ctrlInt(g, "screen.xero_delta.layout.panel_pad_top", currentLayout.panelPadTop, 0, 60, row, visible, v -> { currentLayout.panelPadTop = clamp(v, 0, 60); modified = true; }, mx, my);
            row = ctrlInt(g, "screen.xero_delta.layout.panel_pad_bottom", currentLayout.panelPadBottom, 0, 80, row, visible, v -> { currentLayout.panelPadBottom = clamp(v, 0, 80); modified = true; }, mx, my);
        }
    }

    private int ctrlInt(GuiGraphics g, String key, int val, int min, int max, int row, int visible, IntSetter setter, int mx, int my) {
        int ry = CTRL_TOP + row * ROW_H - (int)ctrlScrollPixels;
        if (ry + ROW_H <= CTRL_TOP || ry >= ctrlBottom()) return row + 1;
        drawControlItem(g, ry, inside(mx, my, controlRowX(), ry + 3,
            controlRowWidth(), ROW_H - 6));
        String label = Component.translatable(key).getString();
        int ebX = controlValueX();
        int ebW = controlFieldWidth();
        g.drawString(font, font.plainSubstrByWidth(label, controlLabelWidth()),
            controlRowX() + 10, ry + 15, Material3Theme.TEXT);
        if (activeEditRow != row) {
            drawCompactField(g, ebX, ry + 8, ebW, 24, String.valueOf(val),
                inside(mx, my, ebX, ry + 8, ebW, 24));
        }
        int btnX = controlStepButtonsX();
        int stepWidth = controlStepButtonWidth();
        boolean hoverMinus = inside(mx, my, btnX, ry + 8, stepWidth, 24);
        drawStepButton(g, btnX, ry + 8, stepWidth, 24, "-", hoverMinus,
            Material3Theme.ERROR);
        boolean hoverPlus = inside(mx, my, btnX + stepWidth + 4, ry + 8,
            stepWidth, 24);
        drawStepButton(g, btnX + stepWidth + 4, ry + 8, stepWidth, 24, "+", hoverPlus,
            Material3Theme.SUCCESS);
        return row + 1;
    }

    private int ctrlDouble(GuiGraphics g, String key, double val, double min, double max, int row, int visible, DoubleSetter setter, int mx, int my) {
        int ry = CTRL_TOP + row * ROW_H - (int)ctrlScrollPixels;
        if (ry + ROW_H <= CTRL_TOP || ry >= ctrlBottom()) return row + 1;
        drawControlItem(g, ry, inside(mx, my, controlRowX(), ry + 3,
            controlRowWidth(), ROW_H - 6));
        String label = Component.translatable(key).getString();
        int ebX = controlValueX();
        int ebW = controlFieldWidth();
        g.drawString(font, font.plainSubstrByWidth(label, controlLabelWidth()),
            controlRowX() + 10, ry + 15, Material3Theme.TEXT);
        if (activeEditRow != row) {
            drawCompactField(g, ebX, ry + 8, ebW, 24, fd(val),
                inside(mx, my, ebX, ry + 8, ebW, 24));
        }
        int btnX = controlStepButtonsX();
        int stepWidth = controlStepButtonWidth();
        boolean hoverMinus = inside(mx, my, btnX, ry + 8, stepWidth, 24);
        drawStepButton(g, btnX, ry + 8, stepWidth, 24, "-", hoverMinus,
            Material3Theme.ERROR);
        boolean hoverPlus = inside(mx, my, btnX + stepWidth + 4, ry + 8,
            stepWidth, 24);
        drawStepButton(g, btnX + stepWidth + 4, ry + 8, stepWidth, 24, "+", hoverPlus,
            Material3Theme.SUCCESS);
        return row + 1;
    }

    private int ctrlToggle(GuiGraphics g, String key, boolean value, int row,
                           BooleanSetter setter, int mx, int my) {
        int ry = CTRL_TOP + row * ROW_H - (int)ctrlScrollPixels;
        if (ry + ROW_H <= CTRL_TOP || ry >= ctrlBottom()) return row + 1;
        drawControlItem(g, ry, inside(mx, my, controlRowX(), ry + 3,
            controlRowWidth(), ROW_H - 6));
        String label = Component.translatable(key).getString();
        g.drawString(font, font.plainSubstrByWidth(label, controlLabelWidth()),
            controlRowX() + 10, ry + 15, Material3Theme.TEXT);
        int switchX = controlRight() - 42;
        int switchY = ry + 12;
        int switchW = 42;
        int switchH = 16;
        boolean hovered = mx >= switchX && mx < switchX + switchW && my >= switchY && my < switchY + switchH;
        int background = value ? Material3Theme.PRIMARY
            : Material3Theme.SURFACE_CONTAINER_HIGHEST;
        Material2Drawing.roundedRect(g, switchX, switchY, switchW, switchH,
            Material3Theme.RADIUS_FULL, background);
        if (!value) {
            Material2Drawing.outlineRoundedRect(g, switchX, switchY, switchW,
                switchH, Material3Theme.RADIUS_FULL, 1.0F, Material3Theme.OUTLINE);
        }
        if (hovered) {
            Material2Drawing.roundedRect(g, switchX, switchY, switchW, switchH,
                Material3Theme.RADIUS_FULL, Material3Theme.stateLayer(
                    value ? Material3Theme.ON_PRIMARY : Material3Theme.PRIMARY,
                    Material3Theme.STATE_HOVER_ALPHA));
        }
        int handleX = value ? switchX + switchW - 8 : switchX + 8;
        Material2Drawing.circle(g, handleX, switchY + switchH / 2,
            value ? 6 : 5, value ? Material3Theme.ON_PRIMARY : Material3Theme.TEXT_MUTED);
        String state = Component.translatable(value
            ? "screen.xero_delta.layout.toggle_on"
            : "screen.xero_delta.layout.toggle_off").getString();
        int stateX = value ? switchX + 4 : switchX + 15;
        g.drawString(font, state, stateX, switchY + 4,
            value ? Material3Theme.ON_PRIMARY : Material3Theme.TEXT);
        return row + 1;
    }

    private int ctrlAction(GuiGraphics g, String key, String value, int row, int visible, int mx, int my) {
        int ry = CTRL_TOP + row * ROW_H - (int)ctrlScrollPixels;
        if (ry + ROW_H <= CTRL_TOP || ry >= ctrlBottom()) return row + 1;
        drawControlItem(g, ry, inside(mx, my, controlRowX(), ry + 3,
            controlRowWidth(), ROW_H - 6));
        g.drawString(font, font.plainSubstrByWidth(Component.translatable(key).getString(),
            controlLabelWidth()), controlRowX() + 10, ry + 15, Material3Theme.TEXT);
        int x = controlValueX();
        boolean hovered = inside(mx, my, x, ry + 8, controlRight() - x, 24);
        int width = controlRight() - x;
        Material2Drawing.roundedRect(g, x, ry + 8, width, 24,
            Material3Theme.RADIUS_FULL, Material3Theme.SECONDARY_CONTAINER);
        if (hovered) {
            Material2Drawing.roundedRect(g, x, ry + 8, width, 24,
                Material3Theme.RADIUS_FULL, Material3Theme.stateLayer(
                    Material3Theme.PRIMARY, Material3Theme.STATE_HOVER_ALPHA));
        }
        g.drawCenteredString(font, font.plainSubstrByWidth(value, width - 12),
            x + width / 2, ry + 15,
            Material3Theme.ON_SECONDARY_CONTAINER);
        return row + 1;
    }

    private int ctrlString(GuiGraphics g, String key, int row, int visible, int mx, int my) {
        int ry = CTRL_TOP + row * ROW_H - (int)ctrlScrollPixels;
        if (ry + ROW_H <= CTRL_TOP || ry >= ctrlBottom()) return row + 1;
        drawControlItem(g, ry, inside(mx, my, controlRowX(), ry + 3,
            controlRowWidth(), ROW_H - 6));
        String value;
        String label;
        switch (key) {
            case "nodeName" -> { label = nodeLabel("nodeName"); value = selectedNode.name; }
            case "nodeContent" -> { label = nodeLabel("nodeContent"); value = "text".equals(selectedNode.type) ? selectedNode.text : selectedNode.imagePath; }
            case "nodeTextColor" -> { label = nodeLabel("nodeTextColor"); value = selectedNode.textColor; }
            case "nodeBackgroundColor" -> { label = nodeLabel("nodeBackgroundColor"); value = String.format("#%08X", selectedNode.backgroundColor); }
            case "nodeOuterColor" -> { label = nodeLabel("nodeOuterColor"); value = selectedNode.outerBorder.color; }
            case "nodeInnerColor" -> { label = nodeLabel("nodeInnerColor"); value = selectedNode.innerBorder.color; }
            case "nodeParent" -> {
                label = nodeLabel("nodeParent");
                SafetyBoxLayoutPack.WidgetNode parentNode = SafetyBoxLayoutPack.findParent(currentLayout, selectedNode);
                value = parentNode == null ? Component.translatable("screen.xero_delta.layout.parent_root").getString() : nodeDisplayName(parentNode);
            }
            default -> { label = key; value = ""; }
        }
        g.drawString(font, font.plainSubstrByWidth(Component.translatable(label).getString(),
            controlLabelWidth()), controlRowX() + 10, ry + 15, Material3Theme.TEXT);
        int x = controlValueX();
        int width = controlRight() - x;
        drawCompactField(g, x, ry + 8, width, 24,
            font.plainSubstrByWidth(value == null ? "" : value, width - 12),
            inside(mx, my, x, ry + 8, width, 24));
        return row + 1;
    }

    private int controlRowX() {
        return PANEL_X + 4;
    }

    private int controlRowWidth() {
        return Math.max(1, PANEL_W - 16);
    }

    private int controlRight() {
        return PANEL_X + PANEL_W - 14;
    }

    private int controlValueX() {
        int minimum = PANEL_X + 124;
        int preferred = PANEL_X + Math.round(PANEL_W * 0.48F);
        return Math.min(controlRight() - 118, Math.max(minimum, preferred));
    }

    private int controlLabelWidth() {
        return Math.max(36, controlValueX() - controlRowX() - 18);
    }

    private int controlStepButtonWidth() {
        return 24;
    }

    private int controlFieldWidth() {
        return Math.max(42, controlStepButtonsX() - controlValueX() - 4);
    }

    private int controlStepButtonsX() {
        return controlRight() - controlStepButtonWidth() * 2 - 4;
    }

    private void drawControlItem(GuiGraphics graphics, int rowY, boolean hovered) {
        Material2Drawing.roundedRect(graphics, controlRowX(), rowY + 3,
            controlRowWidth(), ROW_H - 6, Material3Theme.RADIUS_SMALL,
            Material3Theme.SURFACE_CONTAINER);
        if (hovered) {
            Material2Drawing.roundedRect(graphics, controlRowX(), rowY + 3,
                controlRowWidth(), ROW_H - 6, Material3Theme.RADIUS_SMALL,
                Material3Theme.stateLayer(Material3Theme.PRIMARY,
                    Material3Theme.STATE_HOVER_ALPHA));
        }
    }

    private void drawCompactField(GuiGraphics graphics, int x, int y,
                                  int width, int height, String value,
                                  boolean hovered) {
        Material2Drawing.roundedRect(graphics, x, y, width, height,
            Material3Theme.RADIUS_EXTRA_SMALL, Material3Theme.SURFACE_CONTAINER_HIGH);
        if (hovered) {
            Material2Drawing.roundedRect(graphics, x, y, width, height,
                Material3Theme.RADIUS_EXTRA_SMALL, Material3Theme.stateLayer(
                    Material3Theme.PRIMARY, Material3Theme.STATE_HOVER_ALPHA));
        }
        graphics.fill(x, y + height - 1, x + width, y + height,
            Material3Theme.OUTLINE);
        graphics.drawString(font, value, x + 6,
            y + (height - font.lineHeight) / 2, Material3Theme.TEXT, false);
    }

    private void drawStepButton(GuiGraphics graphics, int x, int y,
                                int width, int height, String label,
                                boolean hovered, int hoverColor) {
        Material2Drawing.roundedRect(graphics, x, y, width, height,
            Material3Theme.RADIUS_EXTRA_SMALL, Material3Theme.SECONDARY_CONTAINER);
        if (hovered) {
            Material2Drawing.roundedRect(graphics, x, y, width, height,
                Material3Theme.RADIUS_EXTRA_SMALL, Material3Theme.stateLayer(
                    hoverColor, Material3Theme.STATE_HOVER_ALPHA));
        }
        graphics.drawCenteredString(font, label, x + width / 2,
            y + (height - font.lineHeight) / 2,
            hovered ? hoverColor : Material3Theme.ON_SECONDARY_CONTAINER);
    }

    private void renderPreview(GuiGraphics g, int mouseX, int mouseY) {
        float scale = previewZoom;
        ItemStack iconStack = new ItemStack(getBox());
        if (iconStack.isEmpty()) iconStack = new ItemStack(Items.CHEST);
        String title = iconStack.getHoverName().getString();
        int gw = getGW(), gh = getGH();
        int cx = previewX + previewW / 2 + previewPanX, cy = previewY + previewH / 2 + previewPanY;
        Config.Layout layout = Config.Layout.valueOf(currentLayout.layout);
        float gs = (float) currentLayout.gridScale;
        int bp = (int)(currentLayout.borderPad * scale);

        GridGeometry base = new GridGeometry(gw, gh, 0, 0, scale, gs);
        var header = SafetyBoxOverlayRenderer.computeHeaderSize(font, base, currentLayout, scale, title);
        int headerW = header.width();
        int headerH = header.height();
        int contentW, contentH;
        switch (layout) {
            case LEFT, RIGHT -> {
                contentW = headerW + (int)(4 * scale) + base.pixelWidth();
                contentH = Math.max(headerH, base.pixelHeight());
            }
            case BOTTOM, TOP -> {
                contentW = Math.max(headerW, base.pixelWidth());
                contentH = headerH + (int)(4 * scale) + base.pixelHeight();
            }
            default -> {
                contentW = Math.max(headerW, base.pixelWidth());
                contentH = headerH + (int)(4 * scale) + base.pixelHeight();
            }
        }

        int originX = cx - contentW / 2;
        int originY = cy - contentH / 2;
        int goffX = (int)(currentLayout.gridOffX * scale);
        int goffY = (int)(currentLayout.gridOffY * scale);
        int gpx, gpy;
        switch (layout) {
            case LEFT -> {
                gpx = originX + headerW + (int)(4 * scale) + goffX;
                gpy = originY + Math.max(0, (contentH - base.pixelHeight()) / 2) + goffY;
            }
            case RIGHT -> {
                gpx = originX + goffX;
                gpy = originY + Math.max(0, (contentH - base.pixelHeight()) / 2) + goffY;
            }
            case BOTTOM -> {
                gpx = originX + Math.max(0, (contentW - base.pixelWidth()) / 2) + goffX;
                gpy = originY + goffY;
            }
            default -> {
                gpx = originX + Math.max(0, (contentW - base.pixelWidth()) / 2) + goffX;
                gpy = originY + headerH + (int)(4 * scale) + goffY;
            }
        }

        GridGeometry geom = new GridGeometry(gw, gh, gpx, gpy, scale, gs);
        previewGeom = geom;

        int panelPadLeft = (int)(clamp(currentLayout.panelPadLeft, 0, 60) * scale);
        int panelPadRight = (int)(clamp(currentLayout.panelPadRight, 0, 60) * scale);
        int panelPadTop = (int)(clamp(currentLayout.panelPadTop, 0, 60) * scale);
        int panelPadBottom = (int)(clamp(currentLayout.panelPadBottom, 0, 80) * scale);
        int panelX1 = geom.gridX() - panelPadLeft;
        int panelY1 = geom.gridY() - panelPadTop;
        int panelX2 = geom.gridX() + geom.pixelWidth() + panelPadRight;
        int panelY2 = geom.gridY() + geom.pixelHeight() + panelPadBottom;
        int panelAlpha = clamp(currentLayout.panelAlpha, 0, 100) * 255 / 100;
        if (panelAlpha > 0) {
            int fill = (panelAlpha << 24) | 0x101010;
            int border = (panelAlpha << 24) | 0x373737;
            g.fill(panelX1, panelY1, panelX2, panelY2, fill);
            g.fill(panelX1, panelY1, panelX2, panelY1 + 1, border);
            g.fill(panelX1, panelY2 - 1, panelX2, panelY2, border);
            g.fill(panelX1, panelY1, panelX1 + 1, panelY2, border);
            g.fill(panelX2 - 1, panelY1, panelX2, panelY2, border);
        }

        var hr = SafetyBoxOverlayRenderer.renderHeader(g, font, geom, currentLayout, scale, iconStack, title);
        lastResult = hr;

        DeltaGridCellRenderer.renderGrid(g, geom);

        if (geom.isSplit()) {
            int sepX = geom.separatorX();
            g.fill(sepX, geom.gridY(), sepX + 1,
                geom.gridY() + geom.pixelHeight(), Material3Theme.OUTLINE_VARIANT);
        }

        // Render items at cell positions (from container if available, else empty)
        var items = getPreviewItems(gw, gh);
        var pose = g.pose();
        for (int gy = 0; gy < gh; gy++) {
            for (int gx = 0; gx < gw; gx++) {
                int idx = gy * gw + gx;
                if (idx < items.size() && !items.get(idx).isEmpty()) {
                    int ix = geom.cellX(gx) + 1, iy = geom.cellY(gy) + 1;
                    pose.pushPose();
                    pose.translate(ix, iy, 0);
                    pose.scale(scale * gs, scale * gs, 1);
                    g.renderItem(items.get(idx), 0, 0);
                    g.renderItemDecorations(font, items.get(idx), 0, 0);
                    pose.popPose();
                }
            }
        }

        // Widget selection highlights (using HeaderResult + GridGeometry)
        if (selectedWidget != -1 && hr != null) {
            var gPose = g.pose();
            gPose.pushPose();
            gPose.translate(0, 0, 400);
            if (selectedWidget == 0) { g.renderOutline(hr.headerX(), hr.headerY(), hr.headerW(), hr.headerH(), 0xFFFFFF00); }
            if (selectedWidget == 1) { int sz = hr.iconSize(); g.renderOutline(hr.iconCX() - sz/2 - 1, hr.iconCY() - sz/2 - 1, sz + 2, sz + 2, 0xFFFFFF00); }
            if (selectedWidget == 2) { g.renderOutline(hr.textX() - 2, hr.textY() - 2, hr.textW() + 4, hr.textH() + 4, 0xFFFFFF00); }
            if (selectedWidget == 3 && previewGeom != null) {
                g.renderOutline(panelX1, panelY1, panelX2 - panelX1, panelY2 - panelY1, 0xFF00FFFF);
                g.renderOutline(previewGeom.gridX(), previewGeom.gridY(), previewGeom.pixelWidth(), previewGeom.pixelHeight(), 0xFFFFFF00);
            }
            gPose.popPose();
        }
        int widgetOriginX = Math.min(panelX1, hr.headerX());
        int widgetOriginY = Math.min(panelY1, hr.headerY());
        int widgetParentWidth = Math.max(panelX2, hr.headerX() + hr.headerW()) - widgetOriginX;
        int widgetParentHeight = Math.max(panelY2, hr.headerY() + hr.headerH()) - widgetOriginY;
        widgetPreviewBounds.clear();
        renderAdditionalWidgets(g, currentLayout.root, widgetOriginX, widgetOriginY,
            widgetParentWidth, widgetParentHeight, iconStack, title, scale);
        drawSelectedResizeHandles(g, mouseX, mouseY);
    }

    private void renderAdditionalWidgets(GuiGraphics graphics, SafetyBoxLayoutPack.WidgetNode parent,
                                         int originX, int originY, int parentWidth, int parentHeight,
                                         ItemStack iconStack, String title, float scale) {
        List<SafetyBoxLayoutPack.WidgetNode> children = new ArrayList<>(parent.children);
        children.sort(Comparator.comparingInt(node -> node.z));
        for (SafetyBoxLayoutPack.WidgetNode node : children) {
            int widthPixels = widgetWidth(node, parentWidth, scale, title);
            int heightPixels = widgetHeight(node, parentHeight, scale);
            int nodeX = originX + Math.round(node.x * scale);
            int nodeY = originY + Math.round(node.y * scale);
            if (!node.legacyBridge) {
                widgetPreviewBounds.put(node, new int[]{nodeX, nodeY, widthPixels, heightPixels});
                var pose = graphics.pose();
                pose.pushPose();
                pose.translate(nodeX + widthPixels / 2.0, nodeY + heightPixels / 2.0, node.z);
                pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(node.rotation));
                pose.translate(-widthPixels / 2.0, -heightPixels / 2.0, 0);
                if ((node.backgroundColor >>> 24) != 0) fillRounded(graphics, 0, 0, widthPixels, heightPixels,
                    node.cornerRadius, node.backgroundColor);
                renderWidgetContent(graphics, node, widthPixels, heightPixels, iconStack, title, scale);
                renderWidgetBorders(graphics, node, widthPixels, heightPixels);
                if (node == selectedNode) graphics.renderOutline(0, 0, widthPixels, heightPixels, 0xFFFFFF00);
                pose.popPose();
            }
            renderAdditionalWidgets(graphics, node, nodeX, nodeY, widthPixels, heightPixels, iconStack, title, scale);
        }
    }

    private void renderWidgetContent(GuiGraphics graphics, SafetyBoxLayoutPack.WidgetNode node,
                                     int widthPixels, int heightPixels, ItemStack iconStack,
                                     String title, float scale) {
        if ("text".equals(node.type)) {
            String text = "#delta_pack_name".equals(node.text) ? title : node.text == null ? "" : node.text;
            int color = "#quality_color".equals(node.textColor)
                ? Config.INSTANCE.getQualityColor(ClientDataCache.INSTANCE.getQuality(iconStack)) | 0xFF000000
                : parseWidgetColor(node.textColor, 0xFFFFFFFF);
            float fontScale = (float)Math.max(0.2, node.fontSize * scale);
            graphics.pose().pushPose();
            graphics.pose().scale(fontScale, fontScale, 1);
            if (node.verticalText) {
                int line = 0;
                for (int codePoint : text.codePoints().toArray()) {
                    graphics.drawString(font, new String(Character.toChars(codePoint)), 0,
                        line++ * font.lineHeight, color, false);
                }
            } else {
                graphics.drawString(font, text, 0, 0, color, false);
            }
            graphics.pose().popPose();
        } else if ("image".equals(node.type)) {
            if ("#delta_pack_icon".equals(node.imagePath)) {
                graphics.pose().pushPose();
                float itemScale = Math.max(0.1f, Math.min(widthPixels, heightPixels) / 16.0f);
                graphics.pose().scale(itemScale, itemScale, 1);
                graphics.renderItem(iconStack, 0, 0);
                graphics.pose().popPose();
            } else if (node.imagePath != null && node.imagePath.startsWith("item:")) {
                try {
                    ItemStack stack = BuiltInRegistries.ITEM.get(
                        net.minecraft.resources.ResourceLocation.parse(node.imagePath.substring(5))).getDefaultInstance();
                    graphics.pose().pushPose();
                    float itemScale = Math.max(0.1f, Math.min(widthPixels, heightPixels) / 16.0f);
                    graphics.pose().scale(itemScale, itemScale, 1);
                    graphics.renderItem(stack, 0, 0);
                    graphics.pose().popPose();
                } catch (Exception ignored) {
                    graphics.fill(0, 0, widthPixels, heightPixels, 0x403F7FBF);
                }
            } else if (WidgetImageCache.render(graphics, node.imagePath, 0, 0, widthPixels, heightPixels)) {
            } else {
                graphics.fill(0, 0, widthPixels, heightPixels, 0x403F7FBF);
                graphics.drawString(font, font.plainSubstrByWidth(node.imagePath == null ? "Image" : node.imagePath,
                    Math.max(1, widthPixels - 4)), 2, 2, 0xFFFFFFFF, false);
            }
        } else if ("grid".equals(node.type)) {
            int cellWidth = Math.max(1, widthPixels / Math.max(1, node.gridColumns));
            int cellHeight = Math.max(1, heightPixels / Math.max(1, node.gridRows));
            for (int row = 0; row < node.gridRows; row++) {
                for (int column = 0; column < node.gridColumns; column++) {
                    graphics.renderOutline(column * cellWidth, row * cellHeight, cellWidth, cellHeight, 0xFF777777);
                }
            }
        }
    }

    private void renderWidgetBorders(GuiGraphics graphics, SafetyBoxLayoutPack.WidgetNode node,
                                     int widthPixels, int heightPixels) {
        if (node.outerBorder.enabled && node.outerBorder.size > 0) {
            int color = parseWidgetColor(node.outerBorder.color, 0xFFFFFFFF);
            for (int offset = 0; offset < node.outerBorder.size; offset++) {
                graphics.renderOutline(-offset, -offset, widthPixels + offset * 2, heightPixels + offset * 2, color);
            }
        }
        if (node.innerBorder.enabled && node.innerBorder.size > 0) {
            int color = parseWidgetColor(node.innerBorder.color, 0xFFFFFFFF);
            for (int offset = 0; offset < node.innerBorder.size; offset++) {
                int inset = offset + 1;
                if (widthPixels > inset * 2 && heightPixels > inset * 2) {
                    graphics.renderOutline(inset, inset, widthPixels - inset * 2, heightPixels - inset * 2, color);
                }
            }
        }
    }

    private int widgetWidth(SafetyBoxLayoutPack.WidgetNode node, int parentWidth, float scale, String title) {
        if (node.width == -1) return Math.max(1, parentWidth);
        if (node.width > 0) return Math.max(1, Math.round(node.width * scale));
        if ("layout".equals(node.type)) {
            int extent = 1;
            for (SafetyBoxLayoutPack.WidgetNode child : node.children) {
                extent = Math.max(extent, Math.round(child.x * scale) + widgetWidth(child, parentWidth, scale, title));
            }
            return extent;
        }
        if ("text".equals(node.type)) {
            String text = "#delta_pack_name".equals(node.text) ? title : node.text;
            if (node.verticalText) {
                int widest = (text == null ? "" : text).codePoints()
                    .map(codePoint -> font.width(new String(Character.toChars(codePoint))))
                    .max().orElse(1);
                return Math.max(1, Math.round(widest * (float)node.fontSize * scale));
            }
            return Math.max(1, Math.round(font.width(text == null ? "" : text) * (float)node.fontSize * scale));
        }
        if ("grid".equals(node.type)) return Math.max(1, Math.round(node.gridColumns * 18 * scale));
        return Math.max(1, Math.round(16 * scale));
    }

    private int widgetHeight(SafetyBoxLayoutPack.WidgetNode node, int parentHeight, float scale) {
        if (node.height == -1) return Math.max(1, parentHeight);
        if (node.height > 0) return Math.max(1, Math.round(node.height * scale));
        if ("layout".equals(node.type)) {
            int extent = 1;
            for (SafetyBoxLayoutPack.WidgetNode child : node.children) {
                extent = Math.max(extent, Math.round(child.y * scale) + widgetHeight(child, parentHeight, scale));
            }
            return extent;
        }
        if ("text".equals(node.type)) {
            String text = node.text == null ? "" : node.text;
            int lines = node.verticalText ? Math.max(1, (int)text.codePoints().count()) : 1;
            return Math.max(1, Math.round(font.lineHeight * lines * (float)node.fontSize * scale));
        }
        if ("grid".equals(node.type)) return Math.max(1, Math.round(node.gridRows * 18 * scale));
        return Math.max(1, Math.round(16 * scale));
    }

    private static int parseWidgetColor(String value, int fallback) {
        if (value == null || value.isBlank() || value.startsWith("#quality_")) return fallback;
        try {
            String normalized = value.startsWith("#") ? "0x" + value.substring(1) : value;
            return (int)Long.decode(normalized).longValue();
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static void fillRounded(GuiGraphics graphics, int x, int y, int width, int height,
                                    int radius, int color) {
        int actualRadius = Math.max(0, Math.min(Math.min(width, height) / 2, radius));
        if (actualRadius == 0) {
            graphics.fill(x, y, x + width, y + height, color);
            return;
        }
        graphics.fill(x + actualRadius, y, x + width - actualRadius, y + height, color);
        graphics.fill(x, y + actualRadius, x + width, y + height - actualRadius, color);
        for (int offset = 0; offset < actualRadius; offset++) {
            int inset = actualRadius - (int)Math.sqrt(actualRadius * actualRadius - offset * offset);
            graphics.fill(x + inset, y + offset, x + width - inset, y + offset + 1, color);
            graphics.fill(x + inset, y + height - offset - 1, x + width - inset, y + height - offset, color);
        }
    }

    private List<ItemStack> getPreviewItems(int gw, int gh) {
        List<ItemStack> items = new java.util.ArrayList<>();
        for (int i = 0; i < gw * gh; i++) items.add(ItemStack.EMPTY);
        return items;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        mx = uiViewport.mouseXDouble(mx);
        my = uiViewport.mouseYDouble(my);
        commitActiveEdit();
        if (super.mouseClicked(mx, my, button)) return true;

        int boxListHeight = BOX_LIST_VISIBLE * BOX_ROW_H;
        if (button == 0 && mx >= PANEL_X + PANEL_W - 8 && mx <= PANEL_X + PANEL_W
            && my >= BOX_LIST_TOP && my <= BOX_LIST_TOP + boxListHeight) {
            draggingBoxScrollbar = true;
            updateBoxScrollFromMouse(my);
            return true;
        }
        if (button == 0 && mx >= PANEL_X + PANEL_W - 8 && mx <= PANEL_X + PANEL_W
            && my >= CTRL_TOP && my <= ctrlBottom()) {
            draggingCtrlScrollbar = true;
            updateCtrlScrollFromMouse(my);
            return true;
        }
        if (button == 0 && mx >= treeX + treeW - 9 && mx <= treeX + treeW
            && my >= treeListTop() && my <= treeY + treeH) {
            draggingTreeScrollbar = true;
            updateTreeScrollFromMouse(my);
            return true;
        }

        if (mx >= treeX && mx <= treeX + treeW && my >= treeY && my <= treeY + treeH) {
            int toolbarY = treeY + 8;
            int toolbarGap = 6;
            int buttonWidth = Math.max(1,
                (treeW - 16 - toolbarGap * 4) / 5);
            if (my >= toolbarY && my < toolbarY + 22) {
                int toolbarIndex = toolbarIndexAt(mx, buttonWidth, toolbarGap);
                if (toolbarIndex >= 0 && toolbarIndex <= 4) {
                    switch (toolbarIndex) {
                        case 0 -> addWidget("text");
                        case 1 -> addWidget("image");
                        case 2 -> addWidget("layout");
                        case 3 -> addWidget("grid");
                        case 4 -> deleteSelectedWidget();
                    }
                    return true;
                }
            }
            int moveY = toolbarY + 28;
            if (my >= moveY && my < moveY + 22) {
                int toolbarIndex = toolbarIndexAt(mx, buttonWidth, toolbarGap);
                if (toolbarIndex >= 0 && toolbarIndex <= 4) {
                    if (toolbarIndex == 4) openSelectedNodeEditor();
                    else moveSelectedWidget(toolbarIndex);
                    return true;
                }
            }
            int listTop = treeListTop();
            int index = (int)((my - listTop + treeScrollPixels) / 18);
            List<TreeEntry> entries = treeEntries();
            if (index >= 0 && index < entries.size()) {
                selectNode(entries.get(index).node());
                if (button == 1) openSelectedNodeEditor();
                else if (button == 0) {
                    treeDragNode = selectedNode;
                    treeDragStartX = (int)mx;
                    treeDragStartY = (int)my;
                    draggingTreeNode = false;
                }
                return true;
            }
        }

        // Box list
        if (mx >= PANEL_X && mx <= PANEL_X + PANEL_W && my >= BOX_LIST_TOP && my <= BOX_LIST_TOP + BOX_LIST_VISIBLE * BOX_ROW_H) {
            int idx = (int)((my - BOX_LIST_TOP + boxScrollPixels) / BOX_ROW_H);
            if (idx >= 0 && idx < filteredBoxes.size()) {
                selectedBoxIndex = idx;
                String realId = filteredBoxIds.get(idx);
                var match = layoutDataList.stream().filter(ld -> ld.boxId != null && ld.boxId.equals(realId)).findFirst();
                if (match.isPresent()) {
                    currentLayout = match.get();
                } else {
                    currentLayout = SafetyBoxLayoutPack.createDefaultLayout(realId);
                    layoutDataList.add(currentLayout);
                }
                SafetyBoxLayoutPack.normalizeLayout(currentLayout);
                selectNode(currentLayout.root);
                undoHistory.clear();
                redoHistory.clear();
                SafetyBoxLayoutPack.applyLayoutToConfig(currentLayout);
                return true;
            }
        }

        // Control panel
        if (mx >= PANEL_X && mx <= PANEL_X + PANEL_W && my >= CTRL_TOP && my <= ctrlBottom()) {
            int row = (int)((my - CTRL_TOP + ctrlScrollPixels) / ROW_H);
            handleCtrlClick(row, mx, button);
            return true;
        }

        // Zoom
        int zoomY = previewY + 4;
        if (my >= zoomY && my <= zoomY + 20) {
            if (mx >= previewX + previewW - 84 && mx <= previewX + previewW - 60) { previewZoom = Math.max(ZOOM_MIN, previewZoom - ZOOM_STEP); return true; }
            if (mx >= previewX + previewW - 24 && mx <= previewX + previewW - 4) { previewZoom = Math.min(ZOOM_MAX, previewZoom + ZOOM_STEP); return true; }
        }

        // Preview
        if (mx >= previewX && mx <= previewX + previewW && my >= previewY && my <= previewY + previewH) {
            if (button == 2) { panningPreview = true; panStartMX = (int)mx; panStartMY = (int)my; panStartPX = previewPanX; panStartPY = previewPanY; return true; }
            if (button == 1) {
                SafetyBoxLayoutPack.WidgetNode customNode = hitTestCustomNode((int)mx, (int)my);
                if (customNode != null) { selectNode(customNode); openSelectedNodeEditor(); return true; }
                int w = hitTestWidget((int)mx, (int)my);
                if (w >= 0) {
                    selectNode(nodeForLegacyWidget(w));
                    openSelectedNodeEditor();
                }
                return true;
            }
            if (button == 0) {
                SafetyBoxLayoutPack.WidgetNode customNode = hitTestCustomNode((int)mx, (int)my);
                if (customNode != null) {
                    selectNode(customNode);
                    recordHistory();
                    int edge = hitTestWidgetEdge((int)mx, (int)my, -2);
                    if (edge > 0) {
                        resizingWidget = true;
                        resizeEdge = edge;
                        storeResizeStartSize(-2);
                    } else {
                        draggingWidget = true;
                        resizingWidget = false;
                        storeDragStartOffsets();
                    }
                    dragStartMX = (int)mx;
                    dragStartMY = (int)my;
                    return true;
                }
            }
            if (button == 0) { int old = selectedWidget; selectedWidget = hitTestWidget((int)mx, (int)my);
                if (selectedWidget != -1) {
                    selectedNode = nodeForLegacyWidget(selectedWidget);
                    recordHistory();
                    int edge = hitTestWidgetEdge((int)mx, (int)my, selectedWidget);
                    if (edge > 0) {
                        resizingWidget = true; resizeEdge = edge;
                        storeResizeStartSize(selectedWidget);
                        dragStartMX = (int)mx; dragStartMY = (int)my;
                    } else {
                        draggingWidget = true; resizingWidget = false;
                        dragStartMX = (int)mx; dragStartMY = (int)my;
                        storeDragStartOffsets();
                    }
                } else { draggingWidget = false; resizingWidget = false; }
                return true; }

        }
        return false;
    }

    private void handleCtrlClick(int row, double mx, int button) {
        if (selectedWidget == -2 && selectedNode != null) {
            String key = getCtrlKeyForRow(row);
            if ("nodeProperties".equals(key) || (key != null && key.startsWith("nodeString:"))) {
                openSelectedNodeEditor();
                return;
            }
            if ("nodeVerticalText".equals(key)) {
                recordHistory();
                selectedNode.verticalText = !selectedNode.verticalText;
                syncLegacyFromNode(selectedNode);
                modified = true;
                return;
            }
            if ("nodeOuterEnabled".equals(key)) {
                recordHistory();
                selectedNode.outerBorder.enabled = !selectedNode.outerBorder.enabled;
                modified = true;
                return;
            }
            if ("nodeInnerEnabled".equals(key)) {
                recordHistory();
                selectedNode.innerBorder.enabled = !selectedNode.innerBorder.enabled;
                modified = true;
                return;
            }
        }
        int btnX = controlStepButtonsX();
        int stepWidth = controlStepButtonWidth();
        boolean isMinus = mx >= btnX && mx < btnX + stepWidth;
        boolean isPlus = mx >= btnX + stepWidth + 4
            && mx < btnX + stepWidth * 2 + 4;
        if (!isMinus && !isPlus) {
            if (mx >= controlValueX() && mx < controlRight()) activateInlineEdit(row);
            return;
        }
        int delta = isMinus ? (button == 0 ? -1 : 1) : (button == 0 ? 1 : -1);
        recordHistory();

        if (selectedWidget == -2 && selectedNode != null) {
            String key = getCtrlKeyForRow(row);
            if (key == null) return;
            if ("nodeFontSize".equals(key)) {
                selectedNode.fontSize = Math.max(0.2, Math.min(8.0, selectedNode.fontSize + (delta > 0 ? 0.1 : -0.1)));
            } else {
                setNodeIntValue(key, nodeIntValue(key) + delta);
            }
            modified = true;
            return;
        }

        if (row == 0) { currentLayout.centerX = clamp(currentLayout.centerX + delta, -1, 100); Config.INSTANCE.overlayCenterX.set(currentLayout.centerX); }
        else if (row == 1) { currentLayout.centerY = clamp(currentLayout.centerY + delta, -1, 100); Config.INSTANCE.overlayCenterY.set(currentLayout.centerY); }
        else handleCtrlRow2(row, delta, delta > 0 ? 0.1 : -0.1);
        modified = true;
    }

    private void handleCtrlRow2(int row, int iDelta, double dDelta) {
        int r = 2;
        if (selectedWidget == -1) {
            if (row == r++) { currentLayout.globalOffX = clamp(currentLayout.globalOffX + iDelta, -100, 100); Config.INSTANCE.overlayGlobalOffsetX.set(currentLayout.globalOffX); return; }
            if (row == r++) { currentLayout.globalOffY = clamp(currentLayout.globalOffY + iDelta, -100, 100); Config.INSTANCE.overlayGlobalOffsetY.set(currentLayout.globalOffY); return; }
        }
        if (selectedWidget == -1 || selectedWidget == 0) {
            if (row == r++) { currentLayout.bgW = clamp(currentLayout.bgW + iDelta, 0, 200); Config.INSTANCE.overlayBgWidth.set(currentLayout.bgW); return; }
            if (row == r++) { currentLayout.bgH = clamp(currentLayout.bgH + iDelta, 0, 200); Config.INSTANCE.overlayBgHeight.set(currentLayout.bgH); return; }
            if (row == r++) { currentLayout.borderOffX = clamp(currentLayout.borderOffX + iDelta, -30, 30); Config.INSTANCE.overlayBorderOffsetX.set(currentLayout.borderOffX); return; }
            if (row == r++) { currentLayout.borderOffY = clamp(currentLayout.borderOffY + iDelta, -30, 30); Config.INSTANCE.overlayBorderOffsetY.set(currentLayout.borderOffY); return; }
        }
        if (selectedWidget == -1 || selectedWidget == 1) {
            if (row == r++) { currentLayout.iconScale = (float)Math.max(0.2, Math.min(2.0, currentLayout.iconScale + dDelta)); Config.INSTANCE.overlayIconScale.set(currentLayout.iconScale); return; }
            if (row == r++) { currentLayout.iconOffX = clamp(currentLayout.iconOffX + iDelta, -30, 30); Config.INSTANCE.overlayIconOffsetX.set(currentLayout.iconOffX); return; }
            if (row == r++) { currentLayout.iconOffY = clamp(currentLayout.iconOffY + iDelta, -30, 30); Config.INSTANCE.overlayIconOffsetY.set(currentLayout.iconOffY); return; }
        }
        if (selectedWidget == -1 || selectedWidget == 2) {
            if (row == r++) { currentLayout.textScale = (float)Math.max(0.2, Math.min(2.0, currentLayout.textScale + dDelta)); Config.INSTANCE.overlayTextScale.set(currentLayout.textScale); return; }
            if (row == r++) { currentLayout.textOffX = clamp(currentLayout.textOffX + iDelta, -30, 30); Config.INSTANCE.overlayTextOffsetX.set(currentLayout.textOffX); return; }
            if (row == r++) { currentLayout.textOffY = clamp(currentLayout.textOffY + iDelta, -30, 30); Config.INSTANCE.overlayTextOffsetY.set(currentLayout.textOffY); return; }
            if (row == r++) { currentLayout.textPad = clamp(currentLayout.textPad + iDelta, 0, 12); Config.INSTANCE.overlayTextPadding.set(currentLayout.textPad); return; }
        }
        if (selectedWidget == -1 || selectedWidget == 3) {
            if (row == r++) { currentLayout.gridScale = (float)Math.max(0.5, Math.min(2.0, currentLayout.gridScale + dDelta)); Config.INSTANCE.overlayGridScale.set(currentLayout.gridScale); return; }
            if (row == r++) { currentLayout.gridOffX = clamp(currentLayout.gridOffX + iDelta, -30, 30); Config.INSTANCE.overlayGridOffsetX.set(currentLayout.gridOffX); return; }
            if (row == r++) { currentLayout.gridOffY = clamp(currentLayout.gridOffY + iDelta, -30, 30); Config.INSTANCE.overlayGridOffsetY.set(currentLayout.gridOffY); return; }
            if (row == r++) { currentLayout.panelAlpha = clamp(currentLayout.panelAlpha + iDelta, 0, 100); return; }
            if (row == r++) { currentLayout.panelPadLeft = clamp(currentLayout.panelPadLeft + iDelta, 0, 60); return; }
            if (row == r++) { currentLayout.panelPadRight = clamp(currentLayout.panelPadRight + iDelta, 0, 60); return; }
            if (row == r++) { currentLayout.panelPadTop = clamp(currentLayout.panelPadTop + iDelta, 0, 60); return; }
            if (row == r++) { currentLayout.panelPadBottom = clamp(currentLayout.panelPadBottom + iDelta, 0, 80); return; }
        }
    }

    private String getCtrlKeyForRow(int row) {
        if (selectedWidget == -2 && selectedNode != null) {
            List<String> keys = nodeControlKeys();
            return row >= 0 && row < keys.size() ? keys.get(row) : null;
        }
        int r = 0;
        if (row == r++) return "centerX"; if (row == r++) return "centerY";
        if (selectedWidget == -1) { if (row == r++) return "globalX"; if (row == r++) return "globalY"; }
        if (selectedWidget == -1 || selectedWidget == 0) { if (row == r++) return "bgW"; if (row == r++) return "bgH"; if (row == r++) return "borderX"; if (row == r++) return "borderY"; }
        if (selectedWidget == -1 || selectedWidget == 1) { if (row == r++) return "iconScale"; if (row == r++) return "iconX"; if (row == r++) return "iconY"; }
        if (selectedWidget == -1 || selectedWidget == 2) { if (row == r++) return "textScale"; if (row == r++) return "textX"; if (row == r++) return "textY"; if (row == r++) return "textPad"; }
        if (selectedWidget == -1 || selectedWidget == 3) { if (row == r++) return "gridScale"; if (row == r++) return "gridX"; if (row == r++) return "gridY"; if (row == r++) return "panelAlpha"; if (row == r++) return "panelPadLeft"; if (row == r++) return "panelPadRight"; if (row == r++) return "panelPadTop"; if (row == r++) return "panelPadBottom"; }
        return null;
    }

    private void activateInlineEdit(int row) {
        commitActiveEdit();
        String key = getCtrlKeyForRow(row);
        if (key == null || key.equals("nodeProperties") || key.startsWith("nodeString:")) return;
        recordHistory();
        int curVal = 0; boolean isDouble = false; double curDbl = 0;
        switch (key) {
            case "centerX": curVal = currentLayout.centerX; break;
            case "centerY": curVal = currentLayout.centerY; break;
            case "globalX": curVal = currentLayout.globalOffX; break;
            case "globalY": curVal = currentLayout.globalOffY; break;
            case "bgW": curVal = currentLayout.bgW; break;
            case "bgH": curVal = currentLayout.bgH; break;
            case "borderX": curVal = currentLayout.borderOffX; break;
            case "borderY": curVal = currentLayout.borderOffY; break;
            case "iconScale": curDbl = currentLayout.iconScale; isDouble = true; break;
            case "iconX": curVal = currentLayout.iconOffX; break;
            case "iconY": curVal = currentLayout.iconOffY; break;
            case "textScale": curDbl = currentLayout.textScale; isDouble = true; break;
            case "textX": curVal = currentLayout.textOffX; break;
            case "textY": curVal = currentLayout.textOffY; break;
            case "textPad": curVal = currentLayout.textPad; break;
            case "gridScale": curDbl = currentLayout.gridScale; isDouble = true; break;
            case "gridX": curVal = currentLayout.gridOffX; break;
            case "gridY": curVal = currentLayout.gridOffY; break;
            case "panelAlpha": curVal = currentLayout.panelAlpha; break;
            case "panelPadLeft": curVal = currentLayout.panelPadLeft; break;
            case "panelPadRight": curVal = currentLayout.panelPadRight; break;
            case "panelPadTop": curVal = currentLayout.panelPadTop; break;
            case "panelPadBottom": curVal = currentLayout.panelPadBottom; break;
            case "nodeX": curVal = selectedNode.x; break;
            case "nodeY": curVal = selectedNode.y; break;
            case "nodeZ": curVal = selectedNode.z; break;
            case "nodeRotation": curVal = selectedNode.rotation; break;
            case "nodeWidth": curVal = selectedNode.width; break;
            case "nodeHeight": curVal = selectedNode.height; break;
            case "nodeCorner": curVal = selectedNode.cornerRadius; break;
            case "nodeOuterSize": curVal = selectedNode.outerBorder.size; break;
            case "nodeInnerSize": curVal = selectedNode.innerBorder.size; break;
            case "nodeGridColumns": curVal = selectedNode.gridColumns; break;
            case "nodeGridRows": curVal = selectedNode.gridRows; break;
            case "nodeGridDepth": curVal = selectedNode.gridDepth; break;
            case "nodeFontSize": curDbl = selectedNode.fontSize; isDouble = true; break;
        }
        String init = isDouble ? fd(curDbl) : String.valueOf(curVal);
        int ry = CTRL_TOP + row * ROW_H - (int)ctrlScrollPixels;
        if (ry + ROW_H <= CTRL_TOP || ry >= ctrlBottom()) return;
        int ebX = controlValueX(), ebW = controlFieldWidth();
        activeEditBox = new Material3CompactEditBox(font, ebX, ry + 8, ebW, 24, Component.literal(""));
        activeEditBox.setValue(init);
        final boolean isDbl = isDouble;
        final String fkey = key;
        activeEditBox.setFilter(s -> s.isEmpty() || s.equals("-") || s.matches(isDbl ? "-?\\d*\\.?\\d*" : "-?\\d+"));
        activeEditBox.setResponder(s -> {
            try {
                if (isDbl) { setCtrlValue(fkey, Double.parseDouble(s)); }
                else { setCtrlValueInt(fkey, Integer.parseInt(s)); }
            } catch (NumberFormatException ignored) {}
        });
        addRenderableWidget(activeEditBox);
        activeEditRow = row;
        activeEditBox.setFocused(true);
        setFocused(activeEditBox);
    }

    private void commitActiveEdit() {
        if (activeEditBox != null) {
            removeWidget(activeEditBox);
            activeEditBox = null;
            activeEditRow = -1;
        }
    }

    private void setCtrlValue(String key, double v) {
        switch (key) {
            case "iconScale": currentLayout.iconScale = (float)Math.max(0.2, Math.min(2.0, v)); Config.INSTANCE.overlayIconScale.set(currentLayout.iconScale); break;
            case "textScale": currentLayout.textScale = (float)Math.max(0.2, Math.min(2.0, v)); Config.INSTANCE.overlayTextScale.set(currentLayout.textScale); break;
            case "gridScale": currentLayout.gridScale = (float)Math.max(0.5, Math.min(2.0, v)); Config.INSTANCE.overlayGridScale.set(currentLayout.gridScale); break;
            case "nodeFontSize": selectedNode.fontSize = Math.max(0.2, Math.min(8.0, v)); break;
        }
        modified = true;
    }

    private void setCtrlValueInt(String key, int v) {
        switch (key) {
            case "centerX": currentLayout.centerX = clamp(v, -1, 100); Config.INSTANCE.overlayCenterX.set(currentLayout.centerX); break;
            case "centerY": currentLayout.centerY = clamp(v, -1, 100); Config.INSTANCE.overlayCenterY.set(currentLayout.centerY); break;
            case "globalX": currentLayout.globalOffX = clamp(v, -100, 100); Config.INSTANCE.overlayGlobalOffsetX.set(currentLayout.globalOffX); break;
            case "globalY": currentLayout.globalOffY = clamp(v, -100, 100); Config.INSTANCE.overlayGlobalOffsetY.set(currentLayout.globalOffY); break;
            case "bgW": currentLayout.bgW = clamp(v, 0, 200); Config.INSTANCE.overlayBgWidth.set(currentLayout.bgW); break;
            case "bgH": currentLayout.bgH = clamp(v, 0, 200); Config.INSTANCE.overlayBgHeight.set(currentLayout.bgH); break;
            case "borderX": currentLayout.borderOffX = clamp(v, -30, 30); Config.INSTANCE.overlayBorderOffsetX.set(currentLayout.borderOffX); break;
            case "borderY": currentLayout.borderOffY = clamp(v, -30, 30); Config.INSTANCE.overlayBorderOffsetY.set(currentLayout.borderOffY); break;
            case "iconX": currentLayout.iconOffX = clamp(v, -30, 30); Config.INSTANCE.overlayIconOffsetX.set(currentLayout.iconOffX); break;
            case "iconY": currentLayout.iconOffY = clamp(v, -30, 30); Config.INSTANCE.overlayIconOffsetY.set(currentLayout.iconOffY); break;
            case "textX": currentLayout.textOffX = clamp(v, -30, 30); Config.INSTANCE.overlayTextOffsetX.set(currentLayout.textOffX); break;
            case "textY": currentLayout.textOffY = clamp(v, -30, 30); Config.INSTANCE.overlayTextOffsetY.set(currentLayout.textOffY); break;
            case "textPad": currentLayout.textPad = clamp(v, 0, 12); Config.INSTANCE.overlayTextPadding.set(currentLayout.textPad); break;
            case "gridX": currentLayout.gridOffX = clamp(v, -30, 30); Config.INSTANCE.overlayGridOffsetX.set(currentLayout.gridOffX); break;
            case "gridY": currentLayout.gridOffY = clamp(v, -30, 30); Config.INSTANCE.overlayGridOffsetY.set(currentLayout.gridOffY); break;
            case "panelAlpha": currentLayout.panelAlpha = clamp(v, 0, 100); break;
            case "panelPadLeft": currentLayout.panelPadLeft = clamp(v, 0, 60); break;
            case "panelPadRight": currentLayout.panelPadRight = clamp(v, 0, 60); break;
            case "panelPadTop": currentLayout.panelPadTop = clamp(v, 0, 60); break;
            case "panelPadBottom": currentLayout.panelPadBottom = clamp(v, 0, 80); break;
            case "nodeX", "nodeY", "nodeZ", "nodeRotation", "nodeWidth", "nodeHeight", "nodeCorner",
                 "nodeOuterEnabled", "nodeOuterSize", "nodeInnerEnabled", "nodeInnerSize",
                 "nodeGridColumns", "nodeGridRows", "nodeGridDepth": setNodeIntValue(key, v); break;
        }
        modified = true;
    }

    private List<String> nodeControlKeys() {
        List<String> keys = new ArrayList<>(List.of("nodeProperties", "nodeString:nodeName", "nodeString:nodeContent",
            "nodeString:nodeTextColor", "nodeString:nodeBackgroundColor", "nodeString:nodeOuterColor",
            "nodeString:nodeInnerColor", "nodeString:nodeParent", "nodeX", "nodeY", "nodeZ", "nodeRotation", "nodeWidth", "nodeHeight"));
        if ("layout".equals(selectedNode.type)) keys.add("nodeCorner");
        if ("text".equals(selectedNode.type)) {
            keys.add("nodeFontSize");
            keys.add("nodeVerticalText");
        }
        if ("grid".equals(selectedNode.type)) {
            keys.add("nodeGridColumns");
            keys.add("nodeGridRows");
            keys.add("nodeGridDepth");
        }
        keys.add("nodeOuterEnabled");
        keys.add("nodeOuterSize");
        keys.add("nodeInnerEnabled");
        keys.add("nodeInnerSize");
        return keys;
    }

    private String nodeLabel(String key) {
        return "screen.xero_delta.layout." + switch (key) {
            case "nodeX" -> "node_x";
            case "nodeY" -> "node_y";
            case "nodeZ" -> "node_z";
            case "nodeRotation" -> "node_rotation";
            case "nodeWidth" -> "grid".equals(selectedNode.type) ? "node_grid_width" : "node_width";
            case "nodeHeight" -> "grid".equals(selectedNode.type) ? "node_grid_height" : "node_height";
            case "nodeCorner" -> "node_corner";
            case "nodeFontSize" -> "node_font_size";
            case "nodeVerticalText" -> "node_vertical_text";
            case "nodeGridColumns" -> "node_grid_columns";
            case "nodeGridRows" -> "node_grid_rows";
            case "nodeGridDepth" -> "node_grid_depth";
            case "nodeOuterEnabled" -> "node_outer_enabled";
            case "nodeOuterSize" -> "node_outer_size";
            case "nodeInnerEnabled" -> "node_inner_enabled";
            case "nodeName" -> "node_name";
            case "nodeContent" -> "node_content";
            case "nodeTextColor" -> "node_text_color";
            case "nodeBackgroundColor" -> "node_background_color";
            case "nodeOuterColor" -> "node_outer_color";
            case "nodeInnerColor" -> "node_inner_color";
            case "nodeParent" -> "parent";
            case "nodeProperties" -> "node_properties";
            default -> "node_inner_size";
        };
    }

    private int nodeIntValue(String key) {
        return switch (key) {
            case "nodeX" -> selectedNode.x;
            case "nodeY" -> selectedNode.y;
            case "nodeZ" -> selectedNode.z;
            case "nodeRotation" -> selectedNode.rotation;
            case "nodeWidth" -> selectedNode.width;
            case "nodeHeight" -> selectedNode.height;
            case "nodeCorner" -> selectedNode.cornerRadius;
            case "nodeGridColumns" -> selectedNode.gridColumns;
            case "nodeGridRows" -> selectedNode.gridRows;
            case "nodeGridDepth" -> selectedNode.gridDepth;
            case "nodeVerticalText" -> selectedNode.verticalText ? 1 : 0;
            case "nodeOuterEnabled" -> selectedNode.outerBorder.enabled ? 1 : 0;
            case "nodeOuterSize" -> selectedNode.outerBorder.size;
            case "nodeInnerEnabled" -> selectedNode.innerBorder.enabled ? 1 : 0;
            case "nodeInnerSize" -> selectedNode.innerBorder.size;
            default -> 0;
        };
    }

    private int nodeMin(String key) {
        return switch (key) {
            case "nodeX", "nodeY" -> -500;
            case "nodeZ" -> -1000;
            case "nodeRotation" -> -360;
            case "nodeWidth", "nodeHeight" -> -2;
            default -> 0;
        };
    }

    private int nodeMax(String key) {
        return switch (key) {
            case "nodeX", "nodeY" -> 500;
            case "nodeZ" -> 1000;
            case "nodeRotation" -> 360;
            case "nodeWidth", "nodeHeight" -> 1000;
            case "nodeGridColumns", "nodeGridRows" -> 16;
            case "nodeGridDepth" -> 64;
            case "nodeVerticalText" -> 1;
            case "nodeOuterEnabled", "nodeInnerEnabled" -> 1;
            case "nodeOuterSize", "nodeInnerSize" -> 20;
            default -> 100;
        };
    }

    private void setNodeIntValue(String key, int value) {
        int clamped = clamp(value, nodeMin(key), nodeMax(key));
        switch (key) {
            case "nodeX" -> selectedNode.x = clamped;
            case "nodeY" -> selectedNode.y = clamped;
            case "nodeZ" -> selectedNode.z = clamped;
            case "nodeRotation" -> selectedNode.rotation = clamped;
            case "nodeWidth" -> selectedNode.width = clamped;
            case "nodeHeight" -> selectedNode.height = clamped;
            case "nodeCorner" -> selectedNode.cornerRadius = clamped;
            case "nodeGridColumns" -> selectedNode.gridColumns = clamped;
            case "nodeGridRows" -> selectedNode.gridRows = clamped;
            case "nodeGridDepth" -> selectedNode.gridDepth = clamped;
            case "nodeVerticalText" -> selectedNode.verticalText = clamped != 0;
            case "nodeOuterEnabled" -> selectedNode.outerBorder.enabled = clamped != 0;
            case "nodeOuterSize" -> selectedNode.outerBorder.size = clamped;
            case "nodeInnerEnabled" -> selectedNode.innerBorder.enabled = clamped != 0;
            case "nodeInnerSize" -> selectedNode.innerBorder.size = clamped;
        }
        SafetyBoxLayoutPack.normalizeLayout(currentLayout);
        syncLegacyFromNode(selectedNode);
        modified = true;
    }

    private void storeDragStartOffsets() {
        if (selectedWidget == -2 && selectedNode != null) { dragStartOffX = selectedNode.x; dragStartOffY = selectedNode.y; }
        if (selectedWidget == 0) { dragStartOffX = currentLayout.borderOffX; dragStartOffY = currentLayout.borderOffY; }
        else if (selectedWidget == 1) { dragStartOffX = currentLayout.iconOffX; dragStartOffY = currentLayout.iconOffY; }
        else if (selectedWidget == 2) { dragStartOffX = currentLayout.textOffX; dragStartOffY = currentLayout.textOffY; }
        else if (selectedWidget == 3) { dragStartOffX = currentLayout.gridOffX; dragStartOffY = currentLayout.gridOffY; }
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        mx = uiViewport.mouseXDouble(mx);
        my = uiViewport.mouseYDouble(my);
        dx = uiViewport.deltaX(dx);
        dy = uiViewport.deltaY(dy);
        if (treeDragNode != null && button == 0) {
            if (Math.abs(mx - treeDragStartX) + Math.abs(my - treeDragStartY) >= 5) draggingTreeNode = true;
            if (draggingTreeNode) return true;
        }
        if (draggingBoxScrollbar && button == 0) { updateBoxScrollFromMouse(my); return true; }
        if (draggingCtrlScrollbar && button == 0) { updateCtrlScrollFromMouse(my); return true; }
        if (draggingTreeScrollbar && button == 0) { updateTreeScrollFromMouse(my); return true; }
        if (panningPreview && button == 2) { previewPanX = panStartPX + (int)(mx - panStartMX); previewPanY = panStartPY + (int)(my - panStartMY); return true; }
        if (resizingWidget && selectedWidget != -1 && button == 0) {
            int ddx = (int)((mx - dragStartMX) / previewZoom), ddy = (int)((my - dragStartMY) / previewZoom);
            if (selectedWidget == 0) {
                if ((resizeEdge & 1) != 0) { int nw = resizeStartW + ddx; currentLayout.bgW = Math.max(0, Math.min(200, nw)); Config.INSTANCE.overlayBgWidth.set(currentLayout.bgW); }
                if ((resizeEdge & 2) != 0) { int nh = resizeStartH + ddy; currentLayout.bgH = Math.max(0, Math.min(200, nh)); Config.INSTANCE.overlayBgHeight.set(currentLayout.bgH); }
            } else if (selectedWidget >= 1 && selectedWidget <= 3) {
                double widthRatio = (resizeStartW + ddx) / (double)Math.max(1, resizeStartW);
                double heightRatio = (resizeStartH + ddy) / (double)Math.max(1, resizeStartH);
                double ratio = resizeEdge == 1 ? widthRatio
                    : resizeEdge == 2 ? heightRatio : Math.min(widthRatio, heightRatio);
                double nextScale = Math.max(0.25, Math.min(4.0, resizeStartScale * ratio));
                if (selectedWidget == 1) {
                    currentLayout.iconScale = (float)nextScale;
                    Config.INSTANCE.overlayIconScale.set(nextScale);
                } else if (selectedWidget == 2) {
                    currentLayout.textScale = (float)nextScale;
                    Config.INSTANCE.overlayTextScale.set(nextScale);
                } else {
                    currentLayout.gridScale = (float)Math.max(0.5, Math.min(2.0, nextScale));
                    Config.INSTANCE.overlayGridScale.set((double)currentLayout.gridScale);
                }
            } else if (selectedWidget == -2 && selectedNode != null) {
                if ((resizeEdge & 1) != 0) selectedNode.width = Math.max(1, resizeStartW + ddx);
                if ((resizeEdge & 2) != 0) selectedNode.height = Math.max(1, resizeStartH + ddy);
            }
            modified = true; return true;
        }
        if (draggingWidget && selectedWidget != -1 && button == 0) {
            int ddx = (int)((mx - dragStartMX) / previewZoom), ddy = (int)((my - dragStartMY) / previewZoom);
            int nx = dragStartOffX + ddx, ny = dragStartOffY + ddy;
            if (selectedWidget == -2 && selectedNode != null) { selectedNode.x = clamp(nx, -500, 500); selectedNode.y = clamp(ny, -500, 500); syncLegacyFromNode(selectedNode); }
            else if (selectedWidget == 0) { currentLayout.borderOffX = clamp(nx, -30, 30); currentLayout.borderOffY = clamp(ny, -30, 30); Config.INSTANCE.overlayBorderOffsetX.set(currentLayout.borderOffX); Config.INSTANCE.overlayBorderOffsetY.set(currentLayout.borderOffY); }
            else if (selectedWidget == 1) { currentLayout.iconOffX = clamp(nx, -30, 30); currentLayout.iconOffY = clamp(ny, -30, 30); Config.INSTANCE.overlayIconOffsetX.set(currentLayout.iconOffX); Config.INSTANCE.overlayIconOffsetY.set(currentLayout.iconOffY); }
            else if (selectedWidget == 2) { currentLayout.textOffX = clamp(nx, -30, 30); currentLayout.textOffY = clamp(ny, -30, 30); Config.INSTANCE.overlayTextOffsetX.set(currentLayout.textOffX); Config.INSTANCE.overlayTextOffsetY.set(currentLayout.textOffY); }
            else if (selectedWidget == 3) { currentLayout.gridOffX = clamp(nx, -60, 60); currentLayout.gridOffY = clamp(ny, -60, 60); Config.INSTANCE.overlayGridOffsetX.set(currentLayout.gridOffX); Config.INSTANCE.overlayGridOffsetY.set(currentLayout.gridOffY); }
            modified = true; return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override public boolean mouseReleased(double mx, double my, int b) {
        mx = uiViewport.mouseXDouble(mx);
        my = uiViewport.mouseYDouble(my);
        if (b == 0 && draggingTreeNode && treeDragNode != null) {
            TreeEntry targetEntry = treeEntryAt(mx, my);
            if (targetEntry != null && targetEntry.node() != treeDragNode) {
                SafetyBoxLayoutPack.WidgetNode target = targetEntry.node();
                SafetyBoxLayoutPack.WidgetNode newParent = "layout".equals(target.type)
                    ? target : SafetyBoxLayoutPack.findParent(currentLayout, target);
                if (newParent != null && !SafetyBoxLayoutPack.isDescendant(treeDragNode, newParent)) {
                    int newIndex = "layout".equals(target.type) ? newParent.children.size() : newParent.children.indexOf(target);
                    recordHistory();
                    if (SafetyBoxLayoutPack.moveWidget(currentLayout, treeDragNode, newParent, newIndex)) {
                        SafetyBoxLayoutPack.normalizeLayout(currentLayout);
                        selectNode(treeDragNode);
                        modified = true;
                    } else if (!undoHistory.isEmpty()) {
                        undoHistory.removeLast();
                    }
                }
            }
        }
        treeDragNode = null;
        draggingTreeNode = false;
        draggingWidget = false;
        resizingWidget = false;
        panningPreview = false;
        draggingBoxScrollbar = false;
        draggingCtrlScrollbar = false;
        draggingTreeScrollbar = false;
        return super.mouseReleased(mx, my, b);
    }

    private TreeEntry treeEntryAt(double mouseX, double mouseY) {
        int listTop = treeListTop();
        if (mouseX < treeX || mouseX > treeX + treeW || mouseY < listTop || mouseY > treeY + treeH) return null;
        int index = (int)((mouseY - listTop + treeScrollPixels) / 18);
        List<TreeEntry> entries = treeEntries();
        return index >= 0 && index < entries.size() ? entries.get(index) : null;
    }

    private int hitTestWidgetEdge(int mx, int my, int widget) {
        if (lastResult == null) return 0;
        int threshold = 8;
        if (widget == 0) {
            int bp = (int)(currentLayout.borderPad * previewZoom);
            int right = lastResult.headerX() + lastResult.headerW() + bp;
            int bottom = lastResult.headerY() + lastResult.headerH() + bp;
            int left = lastResult.headerX() - bp;
            int top = lastResult.headerY() - bp;
            boolean nearRight = Math.abs(mx - right) <= threshold && my >= top && my <= bottom;
            boolean nearBottom = Math.abs(my - bottom) <= threshold && mx >= left && mx <= right;
            if (nearRight && nearBottom) return 3;
            if (nearRight) return 1;
            if (nearBottom) return 2;
        } else if (widget == 3) {
            int right = previewGeom.gridX() + previewGeom.pixelWidth();
            int bottom = previewGeom.gridY() + previewGeom.pixelHeight();
            boolean nearRight = Math.abs(mx - right) <= threshold && my >= previewGeom.gridY() && my <= bottom;
            boolean nearBottom = Math.abs(my - bottom) <= threshold && mx >= previewGeom.gridX() && mx <= right;
            if (nearRight && nearBottom) return 3;
            if (nearRight) return 1;
            if (nearBottom) return 2;
        } else if (widget == 1 || widget == 2) {
            int[] bounds = legacyWidgetBounds(widget);
            if (bounds == null) return 0;
            int right = bounds[0] + bounds[2];
            int bottom = bounds[1] + bounds[3];
            boolean nearRight = Math.abs(mx - right) <= threshold
                && my >= bounds[1] && my <= bottom;
            boolean nearBottom = Math.abs(my - bottom) <= threshold
                && mx >= bounds[0] && mx <= right;
            if (nearRight && nearBottom) return 3;
            if (nearRight) return 1;
            if (nearBottom) return 2;
        } else if (widget == -2 && selectedNode != null) {
            int[] bounds = widgetPreviewBounds.get(selectedNode);
            if (bounds == null) return 0;
            int right = bounds[0] + bounds[2];
            int bottom = bounds[1] + bounds[3];
            boolean nearRight = Math.abs(mx - right) <= threshold && my >= bounds[1] && my <= bottom;
            boolean nearBottom = Math.abs(my - bottom) <= threshold && mx >= bounds[0] && mx <= right;
            if (nearRight && nearBottom) return 3;
            if (nearRight) return 1;
            if (nearBottom) return 2;
        }
        return 0;
    }

    private void storeResizeStartSize(int widget) {
        if (widget == 0) {
            resizeStartW = currentLayout.bgW;
            resizeStartH = currentLayout.bgH;
        } else if (widget >= 1 && widget <= 3) {
            resizeStartScale = widget == 1 ? currentLayout.iconScale
                : widget == 2 ? currentLayout.textScale : currentLayout.gridScale;
            int[] bounds = legacyWidgetBounds(widget);
            resizeStartW = bounds == null ? 16 : Math.max(1,
                Math.round(bounds[2] / previewZoom));
            resizeStartH = bounds == null ? 16 : Math.max(1,
                Math.round(bounds[3] / previewZoom));
        } else if (widget == -2 && selectedNode != null) {
            int[] bounds = widgetPreviewBounds.get(selectedNode);
            resizeStartW = selectedNode.width > 0 ? selectedNode.width : bounds == null ? 16 : Math.max(1, Math.round(bounds[2] / previewZoom));
            resizeStartH = selectedNode.height > 0 ? selectedNode.height : bounds == null ? 16 : Math.max(1, Math.round(bounds[3] / previewZoom));
        }
    }

    private SafetyBoxLayoutPack.WidgetNode hitTestCustomNode(int mouseX, int mouseY) {
        return widgetPreviewBounds.entrySet().stream()
            .filter(entry -> mouseX >= entry.getValue()[0] && mouseX <= entry.getValue()[0] + entry.getValue()[2]
                && mouseY >= entry.getValue()[1] && mouseY <= entry.getValue()[1] + entry.getValue()[3])
            .sorted((first, second) -> Integer.compare(second.getKey().z, first.getKey().z))
            .map(Map.Entry::getKey)
            .findFirst().orElse(null);
    }

    private void drawSelectedResizeHandles(GuiGraphics graphics, int mouseX, int mouseY) {
        int[] bounds = selectedWidget == -2 && selectedNode != null
            ? widgetPreviewBounds.get(selectedNode) : legacyWidgetBounds(selectedWidget);
        if (bounds == null || bounds[2] <= 0 || bounds[3] <= 0) return;
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 520);
        drawResizeHandle(graphics, bounds[0] + bounds[2] - 2,
            bounds[1] + bounds[3] / 2 - 7, 5, 14, 1, mouseX, mouseY);
        drawResizeHandle(graphics, bounds[0] + bounds[2] / 2 - 7,
            bounds[1] + bounds[3] - 2, 14, 5, 2, mouseX, mouseY);
        drawResizeHandle(graphics, bounds[0] + bounds[2] - 3,
            bounds[1] + bounds[3] - 3, 6, 6, 3, mouseX, mouseY);
        graphics.pose().popPose();
    }

    private void drawResizeHandle(GuiGraphics graphics, int x, int y, int width, int height,
                                  int edge, int mouseX, int mouseY) {
        boolean active = resizingWidget && resizeEdge == edge;
        boolean hovered = mouseX >= x - 2 && mouseX <= x + width + 2
            && mouseY >= y - 2 && mouseY <= y + height + 2;
        int color = active ? Material3Theme.PRIMARY
            : hovered ? Material3Theme.ON_PRIMARY_CONTAINER : Material3Theme.TEXT_MUTED;
        graphics.fill(x, y, x + width, y + height, color);
        graphics.renderOutline(x, y, width, height,
            active ? Material3Theme.ON_PRIMARY : Material3Theme.OUTLINE);
    }

    private int[] legacyWidgetBounds(int widget) {
        if (lastResult == null) return null;
        return switch (widget) {
            case 0 -> {
                int pad = (int)(currentLayout.borderPad * previewZoom);
                yield new int[]{lastResult.headerX() - pad, lastResult.headerY() - pad,
                    lastResult.headerW() + pad * 2, lastResult.headerH() + pad * 2};
            }
            case 1 -> {
                int size = lastResult.iconSize();
                yield new int[]{lastResult.iconCX() - size / 2,
                    lastResult.iconCY() - size / 2, size, size};
            }
            case 2 -> new int[]{lastResult.textX() - 2, lastResult.textY() - 2,
                lastResult.textW() + 4, lastResult.textH() + 4};
            case 3 -> previewGeom == null ? null : new int[]{previewGeom.gridX(),
                previewGeom.gridY(), previewGeom.pixelWidth(), previewGeom.pixelHeight()};
            default -> null;
        };
    }

    private int hitTestWidget(int mx, int my) {
        if (lastResult.textW() > 0 && mx >= lastResult.textX() - 4 && mx <= lastResult.textX() + lastResult.textW() + 4 && my >= lastResult.textY() - 4 && my <= lastResult.textY() + lastResult.textH() + 4) return 2;
        if (lastResult.iconSize() > 0 && mx >= lastResult.iconCX() - lastResult.iconSize()/2 - 4 && mx <= lastResult.iconCX() + lastResult.iconSize()/2 + 4 && my >= lastResult.iconCY() - lastResult.iconSize()/2 - 4 && my <= lastResult.iconCY() + lastResult.iconSize()/2 + 4) return 1;
        int bp = (int)(currentLayout.borderPad * previewZoom);
        if (lastResult.headerW() > 0 && mx >= lastResult.headerX() - bp - 8 && mx <= lastResult.headerX() + lastResult.headerW() + bp + 8 && my >= lastResult.headerY() - bp - 8 && my <= lastResult.headerY() + lastResult.headerH() + bp + 8) return 0;
        if (previewGeom != null && previewGeom.pixelWidth() > 0) {
            int panelPadLeft = (int)(clamp(currentLayout.panelPadLeft, 0, 60) * previewZoom);
            int panelPadRight = (int)(clamp(currentLayout.panelPadRight, 0, 60) * previewZoom);
            int panelPadTop = (int)(clamp(currentLayout.panelPadTop, 0, 60) * previewZoom);
            int panelPadBottom = (int)(clamp(currentLayout.panelPadBottom, 0, 80) * previewZoom);
            int x1 = previewGeom.gridX() - panelPadLeft - 4;
            int y1 = previewGeom.gridY() - panelPadTop - 4;
            int x2 = previewGeom.gridX() + previewGeom.pixelWidth() + panelPadRight + 4;
            int y2 = previewGeom.gridY() + previewGeom.pixelHeight() + panelPadBottom + 4;
            if (mx >= x1 && mx <= x2 && my >= y1 && my <= y2) return 3;
        }
        return -1;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        mx = uiViewport.mouseXDouble(mx);
        my = uiViewport.mouseYDouble(my);
        commitActiveEdit();
        if (mx >= previewX && mx <= previewX + previewW && my >= previewY && my <= previewY + previewH) { previewZoom = sy > 0 ? Math.min(ZOOM_MAX, previewZoom + ZOOM_STEP) : Math.max(ZOOM_MIN, previewZoom - ZOOM_STEP); return true; }
        if (mx >= treeX && mx <= treeX + treeW && my >= treeY && my <= treeY + treeH) {
            double maxScroll = Math.max(0, treeEntries().size() * 18 - Math.max(1, treeY + treeH - treeListTop() - 2));
            treeScrollPixels = clampScroll(treeScrollPixels - sy * 12.0, maxScroll);
            return true;
        }
        if (mx >= PANEL_X && mx <= PANEL_X + PANEL_W) {
            if (my >= BOX_LIST_TOP && my <= BOX_LIST_TOP + BOX_LIST_VISIBLE * BOX_ROW_H) {
                double maxScroll = Math.max(0, filteredBoxes.size() * BOX_ROW_H - BOX_LIST_VISIBLE * BOX_ROW_H);
                boxScrollPixels = clampScroll(boxScrollPixels - sy * 12.0, maxScroll);
                return true;
            }
            if (my >= CTRL_TOP && my <= ctrlBottom()) {
                double maxScroll = Math.max(0, countCtrlRows() * ROW_H - (ctrlBottom() - CTRL_TOP));
                ctrlScrollPixels = clampScroll(ctrlScrollPixels - sy * 12.0, maxScroll);
                return true;
            }
        }
        return super.mouseScrolled(mx, my, sx, sy);
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        super.mouseMoved(uiViewport.mouseXDouble(mouseX),
            uiViewport.mouseYDouble(mouseY));
    }

    private void adjustEditorScale(int direction) {
        int current = TradingUiPreferences.safetyBoxEditorScaleLevel();
        int next = TradingUiScale.adjustLevel(current, direction);
        if (next == current) return;
        TradingUiPreferences.setSafetyBoxEditorScaleLevel(next);
        if (minecraft != null) {
            resize(minecraft, uiViewport.physicalWidth(), uiViewport.physicalHeight());
        }
    }

    private void updateBoxScrollFromMouse(double mouseY) {
        double maxScroll = Math.max(0, filteredBoxes.size() * BOX_ROW_H - BOX_LIST_VISIBLE * BOX_ROW_H);
        boxScrollPixels = scrollFromMouse(mouseY, BOX_LIST_TOP, BOX_LIST_VISIBLE * BOX_ROW_H, maxScroll);
    }

    private void updateCtrlScrollFromMouse(double mouseY) {
        commitActiveEdit();
        int heightPixels = ctrlBottom() - CTRL_TOP;
        double maxScroll = Math.max(0, countCtrlRows() * ROW_H - heightPixels);
        ctrlScrollPixels = scrollFromMouse(mouseY, CTRL_TOP, heightPixels, maxScroll);
    }

    private void updateTreeScrollFromMouse(double mouseY) {
        int listTop = treeListTop();
        int listHeight = Math.max(1, treeY + treeH - listTop - 2);
        double maxScroll = Math.max(0, treeEntries().size() * 18 - listHeight);
        treeScrollPixels = scrollFromMouse(mouseY, listTop, listHeight, maxScroll);
    }

    private static double scrollFromMouse(double mouseY, int top, int height, double maxScroll) {
        if (maxScroll <= 0 || height <= 1) return 0;
        double ratio = Math.max(0, Math.min(1, (mouseY - top) / height));
        return ratio * maxScroll;
    }

    private static double clampScroll(double value, double maxScroll) {
        return Math.max(0, Math.min(maxScroll, value));
    }

    private static void renderPickerScrollbar(GuiGraphics graphics, int x, int top, int height,
                                              double offset, double maxOffset) {
        if (maxOffset <= 0 || height <= 1) return;
        int thumbHeight = Math.max(16, Math.min(height, (int)(height * height / (height + maxOffset))));
        int thumbY = top + (int)((height - thumbHeight) * (offset / maxOffset));
        Material2Drawing.roundedRect(graphics, x, top, 5, height,
            Material3Theme.RADIUS_FULL, Material3Theme.OUTLINE_VARIANT);
        Material2Drawing.roundedRect(graphics, x, thumbY, 5, thumbHeight,
            Material3Theme.RADIUS_FULL, Material3Theme.PRIMARY);
    }

    private static double pickerScrollFromMouse(double mouseY, int top, int height, double maxOffset) {
        if (maxOffset <= 0 || height <= 1) return 0;
        return clampScroll((mouseY - top) * maxOffset / height, maxOffset);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int mods) {
        boolean controlDown = (mods & 2) != 0;
        if (controlDown && searchBox != null && !searchBox.isFocused() && keyCode == 90) {
            commitActiveEdit();
            undo();
            return true;
        }
        if (controlDown && searchBox != null && !searchBox.isFocused() && keyCode == 89) {
            commitActiveEdit();
            redo();
            return true;
        }
        if (activeEditBox != null) {
            if (keyCode == 257 || keyCode == 335) { commitActiveEdit(); return true; } // Enter/NumEnter
            if (keyCode == 256) { commitActiveEdit(); return true; } // Escape
            return super.keyPressed(keyCode, scanCode, mods);
        }
        if (keyCode == 256 && selectedWidget != -1) { selectedWidget = -1; return true; }
        if (selectedWidget == -1) return super.keyPressed(keyCode, scanCode, mods);
        boolean shift = (mods & 1) != 0, ctrl = controlDown;
        float step = ctrl ? 0.1f : shift ? 0.5f : 1.0f;
        int dx = 0, dy = 0; double sd = 0;
        if (keyCode == 263) dx = -1; else if (keyCode == 262) dx = 1; else if (keyCode == 265) dy = -1; else if (keyCode == 264) dy = 1;
        else if (keyCode == 61 || keyCode == 334) sd = step; else if (keyCode == 45 || keyCode == 333) sd = -step;
        else return super.keyPressed(keyCode, scanCode, mods);
        recordHistory();
        if (dx != 0 || dy != 0) { modified = true;
            if (selectedWidget == -2 && selectedNode != null) { selectedNode.x = clamp(selectedNode.x + (int)(dx * step), -500, 500); selectedNode.y = clamp(selectedNode.y + (int)(dy * step), -500, 500); syncLegacyFromNode(selectedNode); }
            else if (selectedWidget == 0) { currentLayout.borderOffX = clamp(currentLayout.borderOffX + (int)(dx*step), -30, 30); currentLayout.borderOffY = clamp(currentLayout.borderOffY + (int)(dy*step), -30, 30); Config.INSTANCE.overlayBorderOffsetX.set(currentLayout.borderOffX); Config.INSTANCE.overlayBorderOffsetY.set(currentLayout.borderOffY); }
            else if (selectedWidget == 1) { currentLayout.iconOffX = clamp(currentLayout.iconOffX + (int)(dx*step), -30, 30); currentLayout.iconOffY = clamp(currentLayout.iconOffY + (int)(dy*step), -30, 30); Config.INSTANCE.overlayIconOffsetX.set(currentLayout.iconOffX); Config.INSTANCE.overlayIconOffsetY.set(currentLayout.iconOffY); }
            else if (selectedWidget == 2) { currentLayout.textOffX = clamp(currentLayout.textOffX + (int)(dx*step), -30, 30); currentLayout.textOffY = clamp(currentLayout.textOffY + (int)(dy*step), -30, 30); Config.INSTANCE.overlayTextOffsetX.set(currentLayout.textOffX); Config.INSTANCE.overlayTextOffsetY.set(currentLayout.textOffY); }
            else if (selectedWidget == 3) { currentLayout.gridOffX = clamp(currentLayout.gridOffX + (int)(dx*step), -60, 60); currentLayout.gridOffY = clamp(currentLayout.gridOffY + (int)(dy*step), -60, 60); Config.INSTANCE.overlayGridOffsetX.set(currentLayout.gridOffX); Config.INSTANCE.overlayGridOffsetY.set(currentLayout.gridOffY); }
        }
        if (sd != 0) { modified = true;
            if (selectedWidget == 1) { currentLayout.iconScale = (float)Math.max(0.2, Math.min(2.0, currentLayout.iconScale + sd)); Config.INSTANCE.overlayIconScale.set(currentLayout.iconScale); }
            else if (selectedWidget == 2) { currentLayout.textScale = (float)Math.max(0.2, Math.min(2.0, currentLayout.textScale + sd)); Config.INSTANCE.overlayTextScale.set(currentLayout.textScale); }
            else if (selectedWidget == 3) { currentLayout.gridScale = (float)Math.max(0.5, Math.min(2.0, currentLayout.gridScale + sd)); Config.INSTANCE.overlayGridScale.set(currentLayout.gridScale); }
        }
        return true;
    }

    private void openWidgetEditor(int w) {
        if (w == 2) { String cur = currentLayout.customText != null ? currentLayout.customText : "";
            minecraft.setScreen(new EditTextScreen(this, cur, r -> { currentLayout.customText = r.isEmpty() ? null : r; modified = true; })); }
    }

    private static int clamp(int v, int min, int max) { return Math.max(min, Math.min(max, v)); }

    private void discardAndClose() {
        if (currentLayout != null && currentLayout.boxId != null) {
            SafetyBoxLayoutPack.applyLayoutToConfig(SafetyBoxLayoutPack.getLayoutForBox(currentLayout.boxId));
        }
        closeParent();
    }

    private SafetyBoxLayoutPack.WidgetNode nodeForLegacyWidget(int widget) {
        if (currentLayout == null || currentLayout.root == null) return null;
        for (SafetyBoxLayoutPack.WidgetNode node : SafetyBoxLayoutPack.flattenWidgets(currentLayout)) {
            if (widget == 3 && "grid".equals(node.type)) return node;
            if (widget == 2 && "text".equals(node.type)) return node;
            if (widget == 1 && "image".equals(node.type) && "Icon Widget".equals(node.name)) return node;
            if (widget == 0 && "layout".equals(node.type) && "Background Layout".equals(node.name)) return node;
        }
        return null;
    }

    private void openSelectedNodeEditor() {
        if (selectedNode == null) return;
        SafetyBoxLayoutPack.WidgetNode original = selectedNode;
        SafetyBoxLayoutPack.WidgetNode draft = SafetyBoxLayoutPack.copyWidget(original);
        minecraft.setScreen(new WidgetNodeEditorScreen(this, draft, () -> applyNodeEditor(original, draft)));
    }

    private void applyNodeEditor(SafetyBoxLayoutPack.WidgetNode original, SafetyBoxLayoutPack.WidgetNode draft) {
        recordHistory();
        SafetyBoxLayoutPack.copyWidgetProperties(draft, original);
        SafetyBoxLayoutPack.normalizeLayout(currentLayout);
        syncLegacyFromNode(original);
        selectedNode = SafetyBoxLayoutPack.findWidget(currentLayout, original.id);
        modified = true;
    }

    private void reparentNode(String nodeId, String parentId) {
        SafetyBoxLayoutPack.WidgetNode node = SafetyBoxLayoutPack.findWidget(currentLayout, nodeId);
        SafetyBoxLayoutPack.WidgetNode target = parentId == null
            ? currentLayout.root : SafetyBoxLayoutPack.findWidget(currentLayout, parentId);
        if (node == null || target == null || node == target) return;
        SafetyBoxLayoutPack.WidgetNode oldParent = SafetyBoxLayoutPack.findParent(currentLayout, node);
        if (oldParent == target || SafetyBoxLayoutPack.isDescendant(node, target)) return;
        if (SafetyBoxLayoutPack.moveWidget(currentLayout, node, target, target.children.size())) {
            SafetyBoxLayoutPack.normalizeLayout(currentLayout);
            selectedNode = node;
        }
    }

    private void syncLegacyFromNode(SafetyBoxLayoutPack.WidgetNode node) {
        if (node == null || !node.legacyBridge) return;
        if ("text".equals(node.type)) {
            currentLayout.textOffX = node.x;
            currentLayout.textOffY = node.y;
            currentLayout.textScale = Math.max(0.2, Math.min(2.0, node.fontSize / 2.0));
            currentLayout.customText = "#delta_pack_name".equals(node.text) ? null : node.text;
            currentLayout.verticalText = node.verticalText;
            Config.INSTANCE.overlayVerticalText.set(node.verticalText);
        } else if ("image".equals(node.type)) {
            currentLayout.iconOffX = node.x;
            currentLayout.iconOffY = node.y;
            if (node.width > 0) currentLayout.iconScale = Math.max(0.2, Math.min(2.0, node.width / 16.0));
        } else if ("grid".equals(node.type)) {
            currentLayout.gridOffX = node.x;
            currentLayout.gridOffY = node.y;
        } else if ("layout".equals(node.type)) {
            currentLayout.borderOffX = node.x;
            currentLayout.borderOffY = node.y;
            if (node.width >= 0) currentLayout.bgW = node.width;
            if (node.height >= 0) currentLayout.bgH = node.height;
        }
    }

    private void closeParent() {
        Minecraft.getInstance().setScreen(parent != null ? parent : null);
    }

    @Override public void onClose() { onCancel(); }


    private void saveCurrentLayoutToConfig() {
        SafetyBoxLayoutPack.applyLayoutToConfig(currentLayout);
    }

    // ---- Edit Text Screen ----
    static class EditTextScreen extends Screen {
        private final Screen parent;
        private final java.util.function.Consumer<String> onDone;
        private EditBox textBox;
        private final String initial;

        EditTextScreen(Screen p, String init, java.util.function.Consumer<String> d) {
            super(Component.translatable("screen.xero_delta.layout.edit_text"));
            parent = p; initial = init; onDone = d;
        }
        @Override protected void init() {
            textBox = new Material3CompactEditBox(font, width/2-100, height/2-10, 200, 20, Component.literal(""));
            textBox.setValue(initial); textBox.setMaxLength(200); addRenderableWidget(textBox);
            addRenderableWidget(Material3Button.builder(Component.translatable("gui.done"), b -> { onDone.accept(textBox.getValue()); minecraft.setScreen(parent); }).pos(width/2-100, height/2+20).size(200,20).build());
            setInitialFocus(textBox);
        }
        @Override public void render(GuiGraphics g, int mx, int my, float pt) {
            g.fill(0, 0, width, height, Material3Theme.BACKGROUND);
            Material2Drawing.roundedRect(g, width / 2 - 116, height / 2 - 52,
                232, 108, Material3Theme.RADIUS_LARGE,
                Material3Theme.SURFACE_CONTAINER_LOW);
            super.render(g, mx, my, pt);
            g.drawCenteredString(font,
                Component.translatable("screen.xero_delta.layout.edit_text_hint").getString(),
                width / 2, height / 2 - 32, Material3Theme.TEXT_MUTED);
        }
    }

    static class WidgetNodeEditorScreen extends Screen {
        private final SafetyBoxLayoutScreen parent;
        private final SafetyBoxLayoutPack.WidgetNode node;
        private final Runnable onSave;
        private EditBox nameBox;
        private EditBox contentBox;
        private EditBox textColorBox;
        private EditBox backgroundColorBox;
        private EditBox outerColorBox;
        private EditBox innerColorBox;
        private EditBox xBox;
        private EditBox yBox;
        private EditBox zBox;
        private EditBox rotationBox;
        private EditBox widthBox;
        private EditBox heightBox;
        private EditBox scaleBox;
        private EditBox cornerBox;
        private EditBox outerSizeBox;
        private EditBox innerSizeBox;
        private EditBox gridColumnsBox;
        private EditBox gridRowsBox;
        private EditBox gridDepthBox;
        private final Map<EditBox, int[]> fieldLayout = new IdentityHashMap<>();
        private final Set<EditBox> unavailableFields = Collections.newSetFromMap(new IdentityHashMap<>());
        private Material3Button browseButton;
        private Material3Button verticalTextButton;
        private Material3Button outerEnabledButton;
        private Material3Button innerEnabledButton;
        private Material3Button parentButton;
        private List<SafetyBoxLayoutPack.WidgetNode> parentCandidates = List.of();
        private int parentIndex;
        private String parentId;
        private int browseBaseX;
        private int browseBaseY;
        private int verticalTextBaseX;
        private int verticalTextBaseY;
        private double propertyScroll;
        private static final int PROPERTY_TOP = 38;
        private static final int PROPERTY_ROW_H = 32;

        WidgetNodeEditorScreen(SafetyBoxLayoutScreen parent, SafetyBoxLayoutPack.WidgetNode node, Runnable onSave) {
            super(Component.translatable("screen.xero_delta.layout.widget_properties"));
            this.parent = parent;
            this.node = node;
            this.onSave = onSave;
        }

        @Override
        protected void init() {
            int left = width / 2 - 172;
            int right = left + 174;
            nameBox = textField(left, PROPERTY_TOP + 10, 344, node.name);
            contentBox = textField(left, PROPERTY_TOP + PROPERTY_ROW_H + 10, 276,
                "text".equals(node.type) ? node.text : node.imagePath);
            textColorBox = textField(left, PROPERTY_TOP + PROPERTY_ROW_H * 2 + 10, 164, node.textColor);
            if (!"text".equals(node.type) && !"image".equals(node.type)) unavailableFields.add(contentBox);
            if (!"text".equals(node.type)) unavailableFields.add(textColorBox);
            backgroundColorBox = textField(right, PROPERTY_TOP + PROPERTY_ROW_H * 2 + 10, 164, colorString(node.backgroundColor));
            outerColorBox = textField(left, PROPERTY_TOP + PROPERTY_ROW_H * 3 + 10, 164, node.outerBorder.color);
            innerColorBox = textField(right, PROPERTY_TOP + PROPERTY_ROW_H * 3 + 10, 164, node.innerBorder.color);
            xBox = numberField(left, PROPERTY_TOP + PROPERTY_ROW_H * 4 + 10, node.x);
            yBox = numberField(right, PROPERTY_TOP + PROPERTY_ROW_H * 4 + 10, node.y);
            zBox = numberField(left, PROPERTY_TOP + PROPERTY_ROW_H * 5 + 10, node.z);
            rotationBox = numberField(right, PROPERTY_TOP + PROPERTY_ROW_H * 5 + 10, node.rotation);
            widthBox = numberField(left, PROPERTY_TOP + PROPERTY_ROW_H * 6 + 10, node.width);
            heightBox = numberField(right, PROPERTY_TOP + PROPERTY_ROW_H * 6 + 10, node.height);
            scaleBox = decimalField(left, PROPERTY_TOP + PROPERTY_ROW_H * 7 + 10, node.fontSize);
            if (!"text".equals(node.type)) unavailableFields.add(scaleBox);

            if ("text".equals(node.type)) {
                verticalTextButton = addRenderableWidget(Material3Button.builder(verticalTextMessage(), button -> {
                    node.verticalText = !node.verticalText;
                    button.setMessage(verticalTextMessage());
                }).pos(right, PROPERTY_TOP + PROPERTY_ROW_H * 7 + 10).size(164, 20).build());
                verticalTextBaseX = right;
                verticalTextBaseY = PROPERTY_TOP + PROPERTY_ROW_H * 7 + 10;
            }

            cornerBox = numberField(left, PROPERTY_TOP + PROPERTY_ROW_H * 8 + 10, node.cornerRadius);
            cornerBox.setFilter(text -> text.isEmpty() || text.matches("\\d+"));
            parentCandidates = new ArrayList<>();
            SafetyBoxLayoutPack.WidgetNode actualNode = SafetyBoxLayoutPack.findWidget(parentLayout(), node.id);
            for (SafetyBoxLayoutPack.WidgetNode candidate : SafetyBoxLayoutPack.flattenWidgets(parentLayout())) {
                if ("layout".equals(candidate.type) && candidate != actualNode
                    && !SafetyBoxLayoutPack.isDescendant(actualNode, candidate)) parentCandidates.add(candidate);
            }
            SafetyBoxLayoutPack.WidgetNode currentParent = SafetyBoxLayoutPack.findParent(parent.currentLayout, actualNode);
            parentId = currentParent == null ? null : currentParent.id;
            parentIndex = Math.max(0, parentCandidates.indexOf(currentParent));
            parentButton = addRenderableWidget(Material3Button.builder(parentMessage(), button -> {
                if (!parentCandidates.isEmpty()) {
                    parentIndex = (parentIndex + 1) % parentCandidates.size();
                    parentId = parentCandidates.get(parentIndex).id;
                    button.setMessage(parentMessage());
                }
            }).pos(right, PROPERTY_TOP + PROPERTY_ROW_H * 8 + 10).size(164, 20).build());

            outerSizeBox = numberField(left, PROPERTY_TOP + PROPERTY_ROW_H * 9 + 10, node.outerBorder.size);
            innerSizeBox = numberField(right, PROPERTY_TOP + PROPERTY_ROW_H * 9 + 10, node.innerBorder.size);
            outerEnabledButton = addRenderableWidget(Material3Button.builder(borderMessage(true), button -> {
                node.outerBorder.enabled = !node.outerBorder.enabled;
                button.setMessage(borderMessage(true));
            }).pos(left, PROPERTY_TOP + PROPERTY_ROW_H * 10 + 10).size(164, 20).build());
            innerEnabledButton = addRenderableWidget(Material3Button.builder(borderMessage(false), button -> {
                node.innerBorder.enabled = !node.innerBorder.enabled;
                button.setMessage(borderMessage(false));
            }).pos(right, PROPERTY_TOP + PROPERTY_ROW_H * 10 + 10).size(164, 20).build());
            if ("grid".equals(node.type)) {
                gridColumnsBox = numberField(left, PROPERTY_TOP + PROPERTY_ROW_H * 11 + 10, node.gridColumns);
                gridRowsBox = numberField(right, PROPERTY_TOP + PROPERTY_ROW_H * 11 + 10, node.gridRows);
                gridDepthBox = numberField(left, PROPERTY_TOP + PROPERTY_ROW_H * 12 + 10, node.gridDepth);
            }

            if ("image".equals(node.type)) {
                browseButton = addRenderableWidget(Material3Button.builder(Component.translatable("screen.xero_delta.layout.image_browse"),
                    button -> minecraft.setScreen(new ImageSourceScreen(this, node.imagePath, selected -> {
                            node.imagePath = selected;
                            contentBox.setValue(selected);
                        }))).pos(left + 282, PROPERTY_TOP + PROPERTY_ROW_H + 10).size(62, 20).build());
                browseBaseX = left + 282;
                browseBaseY = PROPERTY_TOP + PROPERTY_ROW_H + 10;
            }

            addRenderableWidget(Material3Button.builder(Component.translatable("gui.done"), button -> save())
                .pos(width / 2 - 102, height - 26).size(100, 20).build());
            addRenderableWidget(Material3Button.builder(Component.translatable("gui.cancel"), button -> minecraft.setScreen(parent))
                .pos(width / 2 + 2, height - 26).size(100, 20).build());
            updateFieldPositions();
            setInitialFocus(nameBox);
        }

        private Component verticalTextMessage() {
            return Component.translatable(node.verticalText
                ? "screen.xero_delta.layout.vertical_on"
                : "screen.xero_delta.layout.vertical_off");
        }

        private SafetyBoxLayoutPack.LayoutData parentLayout() {
            return parent.currentLayout;
        }

        private Component parentMessage() {
            if (parentId == null) return Component.translatable("screen.xero_delta.layout.parent_root");
            SafetyBoxLayoutPack.WidgetNode selected = SafetyBoxLayoutPack.findWidget(parent.currentLayout, parentId);
            return Component.translatable("screen.xero_delta.layout.parent").append(": ")
                .append(selected == null ? "root" : parent.nodeDisplayName(selected));
        }

        private Component borderMessage(boolean outer) {
            boolean enabled = outer ? node.outerBorder.enabled : node.innerBorder.enabled;
            return Component.translatable(outer ? "screen.xero_delta.layout.node_outer_border" : "screen.xero_delta.layout.node_inner_border")
                .append(": ").append(Component.translatable(enabled
                    ? "screen.xero_delta.layout.toggle_on" : "screen.xero_delta.layout.toggle_off"));
        }

        private EditBox textField(int x, int y, int fieldWidth, String value) {
            EditBox field = new Material3CompactEditBox(font, x, y, fieldWidth, 20, Component.literal(""));
            field.setValue(value == null ? "" : value);
            field.setMaxLength(512);
            addRenderableWidget(field);
            fieldLayout.put(field, new int[]{x, y, fieldWidth});
            return field;
        }

        private EditBox numberField(int x, int y, int value) {
            EditBox field = textField(x, y, 164, String.valueOf(value));
            field.setFilter(text -> text.isEmpty() || text.equals("-") || text.matches("-?\\d+"));
            return field;
        }

        private EditBox decimalField(int x, int y, double value) {
            EditBox field = textField(x, y, 164, fd(value));
            field.setFilter(text -> text.isEmpty() || text.equals("-") || text.matches("-?\\d*\\.?\\d*"));
            return field;
        }

        private void save() {
            node.name = nameBox.getValue().isBlank() ? node.type + " widget" : nameBox.getValue();
            if ("text".equals(node.type)) node.text = contentBox.getValue();
            if ("image".equals(node.type)) node.imagePath = WidgetImageCache.normalizeReference(contentBox.getValue());
            node.textColor = textColorBox.getValue().isBlank() ? "#FFFFFFFF" : textColorBox.getValue();
            node.backgroundColor = parseColor(backgroundColorBox.getValue(), node.backgroundColor);
            node.outerBorder.color = outerColorBox.getValue().isBlank() ? "#FFFFFFFF" : outerColorBox.getValue();
            node.innerBorder.color = innerColorBox.getValue().isBlank() ? "#FFFFFFFF" : innerColorBox.getValue();
            node.x = parseInt(xBox, node.x, -500, 500);
            node.y = parseInt(yBox, node.y, -500, 500);
            node.z = parseInt(zBox, node.z, -1000, 1000);
            node.rotation = parseInt(rotationBox, node.rotation, -360, 360);
            node.width = parseInt(widthBox, node.width, -2, 1000);
            node.height = parseInt(heightBox, node.height, -2, 1000);
            if (scaleBox != null && "text".equals(node.type)) node.fontSize = parseDouble(scaleBox, node.fontSize, 0.2, 8.0);
            node.cornerRadius = parseInt(cornerBox, node.cornerRadius, 0, 100);
            node.outerBorder.size = parseInt(outerSizeBox, node.outerBorder.size, 0, 20);
            node.innerBorder.size = parseInt(innerSizeBox, node.innerBorder.size, 0, 20);
            if ("grid".equals(node.type)) {
                node.gridColumns = parseInt(gridColumnsBox, node.gridColumns, 1, 16);
                node.gridRows = parseInt(gridRowsBox, node.gridRows, 1, 16);
                node.gridDepth = parseInt(gridDepthBox, node.gridDepth, 1, 64);
            }
            onSave.run();
            parent.reparentNode(node.id, parentId);
            minecraft.setScreen(parent);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (button == 1 && "image".equals(node.type)
                && contentBox != null && contentBox.isMouseOver(mouseX, mouseY)) {
                minecraft.setScreen(new ImageSourceScreen(this, contentBox.getValue(), selected -> {
                    node.imagePath = selected;
                    contentBox.setValue(selected);
                }));
                return true;
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }

        private static int parseColor(String value, int fallback) {
            if (value == null || value.isBlank()) return fallback;
            try {
                String normalized = value.startsWith("#") ? "0x" + value.substring(1) : value;
                return (int)Long.decode(normalized).longValue();
            } catch (Exception ignored) {
                return fallback;
            }
        }

        private static String colorString(int color) {
            return String.format("#%08X", color);
        }

        private static int parseInt(EditBox field, int fallback, int min, int max) {
            try { return Math.max(min, Math.min(max, Integer.parseInt(field.getValue()))); }
            catch (NumberFormatException ignored) { return fallback; }
        }

        private static double parseDouble(EditBox field, double fallback, double min, double max) {
            try { return Math.max(min, Math.min(max, Double.parseDouble(field.getValue()))); }
            catch (NumberFormatException ignored) { return fallback; }
        }

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            graphics.fill(0, 0, width, height, Material3Theme.BACKGROUND);
            updateFieldPositions();
            int left = width / 2 - 172;
            int right = left + 174;
            String contentKey = "text".equals(node.type)
                ? "screen.xero_delta.layout.node_text"
                : "image".equals(node.type) ? "screen.xero_delta.layout.node_image_path" : null;
            String[][] labels = {
                {"screen.xero_delta.layout.node_name", null}, {contentKey, null},
                {"text".equals(node.type) ? "screen.xero_delta.layout.node_text_color" : null,
                    "screen.xero_delta.layout.node_background_color"},
                {"screen.xero_delta.layout.node_outer_color", "screen.xero_delta.layout.node_inner_color"},
                {"screen.xero_delta.layout.node_x", "screen.xero_delta.layout.node_y"},
                {"screen.xero_delta.layout.node_z", "screen.xero_delta.layout.node_rotation"},
                {"grid".equals(node.type) ? "screen.xero_delta.layout.node_grid_width" : "screen.xero_delta.layout.node_width",
                    "grid".equals(node.type) ? "screen.xero_delta.layout.node_grid_height" : "screen.xero_delta.layout.node_height"},
                {"text".equals(node.type) ? "screen.xero_delta.layout.node_font_size" : null,
                    "text".equals(node.type) ? "screen.xero_delta.layout.node_vertical_text" : null},
                {"screen.xero_delta.layout.node_corner", "screen.xero_delta.layout.parent"},
                {"screen.xero_delta.layout.node_outer_size", "screen.xero_delta.layout.node_inner_size"},
                {"screen.xero_delta.layout.node_outer_border", "screen.xero_delta.layout.node_inner_border"},
                {"grid".equals(node.type) ? "screen.xero_delta.layout.node_grid_columns" : null,
                    "grid".equals(node.type) ? "screen.xero_delta.layout.node_grid_rows" : null},
                {"grid".equals(node.type) ? "screen.xero_delta.layout.node_grid_depth" : null, null}
            };
            graphics.enableScissor(8, PROPERTY_TOP, width - 8, propertyBottom());
            for (int row = 0; row < labels.length; row++) {
                int y = PROPERTY_TOP + row * PROPERTY_ROW_H - (int)propertyScroll;
                if (labels[row][0] != null) {
                    int itemWidth = row == 0 ? 348 : 170;
                    Material2Drawing.roundedRect(graphics, left - 4, y - 4,
                        itemWidth, PROPERTY_ROW_H - 2, Material3Theme.RADIUS_SMALL,
                        Material3Theme.SURFACE_CONTAINER);
                    graphics.drawString(font, Component.translatable(labels[row][0]),
                        left + 4, y, Material3Theme.TEXT_MUTED);
                }
                if (labels[row][1] != null) {
                    Material2Drawing.roundedRect(graphics, right - 4, y - 4,
                        170, PROPERTY_ROW_H - 2, Material3Theme.RADIUS_SMALL,
                        Material3Theme.SURFACE_CONTAINER);
                    graphics.drawString(font, Component.translatable(labels[row][1]),
                        right + 4, y, Material3Theme.TEXT_MUTED);
                }
            }
            graphics.disableScissor();
            if ("image".equals(node.type)) renderImagePreview(graphics);
            super.render(graphics, mouseX, mouseY, partialTick);
            graphics.drawCenteredString(font, title, width / 2, 14, Material3Theme.TEXT);
        }

        private void renderImagePreview(GuiGraphics graphics) {
            int size = 52;
            int x = width - size - 12;
            int y = PROPERTY_TOP + 2;
            Material2Drawing.roundedRect(graphics, x - 4, y - 4,
                size + 8, size + 8, Material3Theme.RADIUS_SMALL,
                Material3Theme.SURFACE_CONTAINER_HIGH);
            Material2Drawing.outlineRoundedRect(graphics, x - 4, y - 4,
                size + 8, size + 8, Material3Theme.RADIUS_SMALL, 1.0F,
                Material3Theme.OUTLINE_VARIANT);
            String reference = contentBox == null ? node.imagePath : WidgetImageCache.normalizeReference(contentBox.getValue());
            if (reference != null && reference.startsWith("item:")) {
                try {
                    Item item = BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.parse(reference.substring(5)));
                    graphics.pose().pushPose();
                    graphics.pose().translate(x, y, 100);
                    graphics.pose().scale(size / 16.0f, size / 16.0f, 1);
                    graphics.renderItem(item.getDefaultInstance(), 0, 0);
                    graphics.pose().popPose();
                    return;
                } catch (Exception ignored) {
                }
            }
            if (!WidgetImageCache.render(graphics, reference, x, y, size, size)) {
                graphics.drawCenteredString(font, Component.translatable("screen.xero_delta.layout.preview"),
                    x + size / 2, y + size / 2 - 4, Material3Theme.TEXT_MUTED);
            }
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
            if (mouseY >= PROPERTY_TOP && mouseY < propertyBottom()) {
                propertyScroll = clampScroll(propertyScroll - scrollY * 16, maxPropertyScroll());
                updateFieldPositions();
                return true;
            }
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }

        private int propertyBottom() { return height - 34; }

        private double maxPropertyScroll() {
            return Math.max(0, PROPERTY_TOP + PROPERTY_ROW_H * ("grid".equals(node.type) ? 13 : 11) + 30 - propertyBottom());
        }

        private void updateFieldPositions() {
            for (var entry : fieldLayout.entrySet()) {
                int[] base = entry.getValue();
                int y = base[1] - (int)propertyScroll;
                entry.getKey().setPosition(base[0], y);
                entry.getKey().visible = !unavailableFields.contains(entry.getKey())
                    && y + 20 > PROPERTY_TOP && y < propertyBottom();
            }
            if (browseButton != null) {
                int y = browseBaseY - (int)propertyScroll;
                browseButton.setPosition(browseBaseX, y);
                browseButton.visible = y + 20 > PROPERTY_TOP && y < propertyBottom();
            }
            if (verticalTextButton != null) {
                int y = verticalTextBaseY - (int)propertyScroll;
                verticalTextButton.setPosition(verticalTextBaseX, y);
                verticalTextButton.visible = y + 20 > PROPERTY_TOP && y < propertyBottom();
            }
            if (parentButton != null) {
                int y = PROPERTY_TOP + PROPERTY_ROW_H * 8 + 10 - (int)propertyScroll;
                parentButton.setPosition(width / 2 + 2, y);
                parentButton.visible = y + 20 > PROPERTY_TOP && y < propertyBottom();
            }
            if (outerEnabledButton != null) {
                int y = PROPERTY_TOP + PROPERTY_ROW_H * 10 + 10 - (int)propertyScroll;
                outerEnabledButton.setY(y);
                outerEnabledButton.visible = y + 20 > PROPERTY_TOP && y < propertyBottom();
            }
            if (innerEnabledButton != null) {
                int y = PROPERTY_TOP + PROPERTY_ROW_H * 10 + 10 - (int)propertyScroll;
                innerEnabledButton.setY(y);
                innerEnabledButton.visible = y + 20 > PROPERTY_TOP && y < propertyBottom();
            }
        }

        @Override
        public void onClose() {
            minecraft.setScreen(parent);
        }
    }

    /** Chooses a stable image reference instead of persisting machine-specific paths. */
    static class ImageSourceScreen extends Screen {
        private final Screen parent;
        private final String currentValue;
        private final java.util.function.Consumer<String> onSelect;

        ImageSourceScreen(Screen parent, String currentValue, java.util.function.Consumer<String> onSelect) {
            super(Component.translatable("screen.xero_delta.layout.image_source"));
            this.parent = parent;
            this.currentValue = currentValue;
            this.onSelect = onSelect;
        }

        @Override
        protected void init() {
            int x = width / 2 - 78;
            int y = height / 2 - 38;
            addRenderableWidget(Material3Button.builder(Component.translatable("screen.xero_delta.layout.image_source_files"),
                button -> minecraft.setScreen(new ImagePickerScreen(this, currentValue, this::complete)))
                .pos(x, y).size(156, 20).build());
            addRenderableWidget(Material3Button.builder(Component.translatable("screen.xero_delta.layout.image_source_resources"),
                button -> minecraft.setScreen(new ResourceImagePickerScreen(this, this::complete)))
                .pos(x, y + 26).size(156, 20).build());
            addRenderableWidget(Material3Button.builder(Component.translatable("screen.xero_delta.layout.image_source_items"),
                button -> minecraft.setScreen(new ItemImagePickerScreen(this, this::complete)))
                .pos(x, y + 52).size(156, 20).build());
            addRenderableWidget(Material3Button.builder(Component.translatable("gui.cancel"),
                button -> minecraft.setScreen(parent)).pos(x, y + 84).size(156, 20).build());
        }

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            graphics.fill(0, 0, width, height, Material3Theme.BACKGROUND);
            Material2Drawing.roundedRect(graphics, width / 2 - 94,
                height / 2 - 82, 188, 148, Material3Theme.RADIUS_LARGE,
                Material3Theme.SURFACE_CONTAINER_LOW);
            super.render(graphics, mouseX, mouseY, partialTick);
            graphics.drawCenteredString(font, title, width / 2,
                height / 2 - 66, Material3Theme.TEXT);
        }

        private void complete(String value) {
            onSelect.accept(value);
            minecraft.setScreen(parent);
        }

        @Override
        public void onClose() { minecraft.setScreen(parent); }
    }

    static class ImagePickerScreen extends Screen {
        private final Screen parent;
        private final java.util.function.Consumer<String> onSelect;
        private final java.nio.file.Path gameDirectory;
        private java.nio.file.Path currentDirectory;
        private java.nio.file.Path selectedPath;
        private List<java.nio.file.Path> entries = new ArrayList<>();
        private List<java.nio.file.Path> filteredEntries = List.of();
        private EditBox searchBox;
        private double scrollPixels;
        private float imageZoom = 1.0f;
        private int imagePanX;
        private int imagePanY;
        private boolean panning;
        private int panStartX;
        private int panStartY;
        private int panMouseX;
        private int panMouseY;
        private boolean draggingListScrollbar;
        private long lastClickTime;
        private java.nio.file.Path lastClickedPath;

        ImagePickerScreen(Screen parent, String initialValue,
                          java.util.function.Consumer<String> onSelect) {
            super(Component.translatable("screen.xero_delta.layout.image_picker"));
            this.parent = parent;
            this.onSelect = onSelect;
            gameDirectory = net.neoforged.fml.loading.FMLPaths.GAMEDIR.get().toAbsolutePath().normalize();
            java.nio.file.Path initialPath = WidgetImageCache.resolve(initialValue);
            if (initialPath != null && java.nio.file.Files.isRegularFile(initialPath)) {
                currentDirectory = isWithinGameDirectory(initialPath) ? initialPath.getParent() : gameDirectory;
                selectedPath = isWithinGameDirectory(initialPath) ? initialPath : null;
            } else if (initialPath != null && java.nio.file.Files.isDirectory(initialPath)) {
                currentDirectory = isWithinGameDirectory(initialPath) ? initialPath : gameDirectory;
            } else {
                currentDirectory = gameDirectory;
            }
        }

        @Override
        protected void init() {
            refreshEntries();
            addRenderableWidget(Material3Button.builder(Component.translatable("screen.xero_delta.layout.image_up"),
                button -> {
                    if (!currentDirectory.equals(gameDirectory)) {
                        currentDirectory = currentDirectory.getParent();
                        selectedPath = null;
                        scrollPixels = 0;
                        refreshEntries();
                    }
                }).pos(8, 24).size(54, 20).build());
            searchBox = addRenderableWidget(new Material3CompactEditBox(font, 8, 50, Math.max(120, width / 2 - 16), 20,
                Component.translatable("gui.search")));
            searchBox.setResponder(value -> {
                applySearch(value);
                scrollPixels = 0;
            });
            addRenderableWidget(Material3Button.builder(Component.translatable("screen.xero_delta.layout.image_select"),
                button -> chooseSelected()).pos(width - 116, height - 26).size(52, 20).build());
            addRenderableWidget(Material3Button.builder(Component.translatable("gui.cancel"),
                button -> minecraft.setScreen(parent)).pos(width - 60, height - 26).size(52, 20).build());
        }

        private void refreshEntries() {
            entries = new ArrayList<>();
            try (var paths = java.nio.file.Files.list(currentDirectory)) {
                paths.filter(path -> java.nio.file.Files.isDirectory(path)
                        || path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".png"))
                    .sorted((first, second) -> {
                        boolean firstDirectory = java.nio.file.Files.isDirectory(first);
                        boolean secondDirectory = java.nio.file.Files.isDirectory(second);
                        if (firstDirectory != secondDirectory) return firstDirectory ? -1 : 1;
                        return first.getFileName().toString().compareToIgnoreCase(second.getFileName().toString());
                    })
                    .forEach(entries::add);
            } catch (Exception ignored) {
            }
            applySearch(searchBox == null ? "" : searchBox.getValue());
        }

        private void applySearch(String query) {
            String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
            filteredEntries = entries.stream()
                .filter(path -> needle.isEmpty() || path.getFileName().toString().toLowerCase(Locale.ROOT).contains(needle))
                .toList();
        }

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            graphics.fill(0, 0, width, height, Material3Theme.BACKGROUND);
            String relativeDirectory = gameDirectory.relativize(currentDirectory).toString().replace('\\', '/');

            int listX = 8;
            int listY = 76;
            int listWidth = Math.max(120, width / 2 - 12);
            int listHeight = height - 110;
            double maxScroll = Math.max(0, filteredEntries.size() * 18 - listHeight);
            Material2Drawing.roundedRect(graphics, listX, listY, listWidth,
                listHeight, Material3Theme.RADIUS_LARGE,
                Material3Theme.SURFACE_CONTAINER_LOW);
            graphics.enableScissor(listX, listY, listX + listWidth, listY + listHeight);
            int first = Math.max(0, (int)(scrollPixels / 18));
            int offset = (int)(scrollPixels % 18);
            for (int visibleIndex = 0; visibleIndex < filteredEntries.size() - first; visibleIndex++) {
                int index = first + visibleIndex;
                int rowY = listY + visibleIndex * 18 - offset;
                if (rowY >= listY + listHeight) break;
                java.nio.file.Path path = filteredEntries.get(index);
                boolean selected = path.equals(selectedPath);
                Material2Drawing.roundedRect(graphics, listX + 4, rowY + 1,
                    listWidth - 12, 16, Material3Theme.RADIUS_EXTRA_SMALL,
                    selected ? Material3Theme.PRIMARY_CONTAINER
                        : Material3Theme.SURFACE_CONTAINER);
                String prefix = java.nio.file.Files.isDirectory(path) ? "[+] " : "[PNG] ";
                graphics.drawString(font, font.plainSubstrByWidth(prefix + path.getFileName(), listWidth - 10),
                    listX + 8, rowY + 5, selected
                        ? Material3Theme.ON_PRIMARY_CONTAINER : Material3Theme.TEXT);
            }
            graphics.disableScissor();
            renderPickerScrollbar(graphics, listX + listWidth - 6, listY, listHeight, scrollPixels, maxScroll);

            int previewX = listX + listWidth + 8;
            int previewY = listY;
            int previewWidth = width - previewX - 8;
            int previewHeight = listHeight;
            Material2Drawing.roundedRect(graphics, previewX, previewY,
                previewWidth, previewHeight, Material3Theme.RADIUS_LARGE,
                Material3Theme.SURFACE_CONTAINER_LOW);
            Material2Drawing.outlineRoundedRect(graphics, previewX, previewY,
                previewWidth, previewHeight, Material3Theme.RADIUS_LARGE, 1.0F,
                Material3Theme.OUTLINE_VARIANT);
            if (selectedPath != null && java.nio.file.Files.isRegularFile(selectedPath)) {
                int imageWidth = Math.max(1, Math.round((previewWidth - 16) * imageZoom));
                int imageHeight = Math.max(1, Math.round((previewHeight - 16) * imageZoom));
                int imageX = previewX + (previewWidth - imageWidth) / 2 + imagePanX;
                int imageY = previewY + (previewHeight - imageHeight) / 2 + imagePanY;
                graphics.enableScissor(previewX + 1, previewY + 1, previewX + previewWidth - 1, previewY + previewHeight - 1);
                WidgetImageCache.render(graphics, relativePath(selectedPath), imageX, imageY, imageWidth, imageHeight);
                graphics.disableScissor();
            }
            super.render(graphics, mouseX, mouseY, partialTick);
            graphics.drawCenteredString(font, title, width / 2, 8, Material3Theme.TEXT);
            graphics.drawString(font, font.plainSubstrByWidth(
                    relativeDirectory.isEmpty() ? "." : relativeDirectory, width - 80),
                68, 30, Material3Theme.TEXT_MUTED);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            int listX = 8;
            int listY = 76;
            int listWidth = Math.max(120, width / 2 - 12);
            int listHeight = height - 110;
            if (button == 0 && mouseX >= listX + listWidth - 8 && mouseX <= listX + listWidth
                && mouseY >= listY && mouseY <= listY + listHeight) {
                draggingListScrollbar = true;
                scrollPixels = pickerScrollFromMouse(mouseY, listY, listHeight,
                    Math.max(0, filteredEntries.size() * 18 - listHeight));
                return true;
            }
            if (mouseX >= listX && mouseX <= listX + listWidth && mouseY >= listY && mouseY <= listY + listHeight) {
                int index = (int)((mouseY - listY + scrollPixels) / 18);
                if (index >= 0 && index < filteredEntries.size()) {
                    java.nio.file.Path path = filteredEntries.get(index);
                    long now = System.currentTimeMillis();
                    boolean doubleClick = path.equals(lastClickedPath) && now - lastClickTime <= 350;
                    selectedPath = path;
                    lastClickedPath = path;
                    lastClickTime = now;
                    if (doubleClick) {
                        if (java.nio.file.Files.isDirectory(path)) {
                            currentDirectory = path;
                            selectedPath = null;
                            scrollPixels = 0;
                            refreshEntries();
                        } else {
                            chooseSelected();
                        }
                    }
                    return true;
                }
            }
            int previewX = listX + listWidth + 8;
            if (button == 0 && mouseX >= previewX && mouseY >= listY && mouseY <= listY + listHeight) {
                panning = true;
                panMouseX = (int)mouseX;
                panMouseY = (int)mouseY;
                panStartX = imagePanX;
                panStartY = imagePanY;
                return true;
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }

        @Override
        public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
            if (draggingListScrollbar && button == 0) {
                int listHeight = height - 110;
                scrollPixels = pickerScrollFromMouse(mouseY, 76, listHeight,
                    Math.max(0, filteredEntries.size() * 18 - listHeight));
                return true;
            }
            if (panning && button == 0) {
                imagePanX = panStartX + (int)mouseX - panMouseX;
                imagePanY = panStartY + (int)mouseY - panMouseY;
                return true;
            }
            return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
        }

        @Override
        public boolean mouseReleased(double mouseX, double mouseY, int button) {
            panning = false;
            draggingListScrollbar = false;
            return super.mouseReleased(mouseX, mouseY, button);
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
            int listWidth = Math.max(120, width / 2 - 12);
            if (mouseX <= 8 + listWidth) {
                double maxScroll = Math.max(0, filteredEntries.size() * 18 - (height - 110));
                scrollPixels = clampScroll(scrollPixels - scrollY * 12, maxScroll);
            } else {
                imageZoom = (float)Math.max(0.25, Math.min(8.0, imageZoom + scrollY * 0.1));
            }
            return true;
        }

        private void chooseSelected() {
            if (selectedPath == null || !java.nio.file.Files.isRegularFile(selectedPath)) return;
            if (!isWithinGameDirectory(selectedPath)) return;
            onSelect.accept(relativePath(selectedPath));
        }

        private String relativePath(java.nio.file.Path path) {
            return gameDirectory.relativize(path).toString().replace('\\', '/');
        }

        private boolean isWithinGameDirectory(java.nio.file.Path path) {
            return path != null && path.toAbsolutePath().normalize().startsWith(gameDirectory);
        }

        @Override
        public void onClose() {
            minecraft.setScreen(parent);
        }
    }

    static class ResourceImagePickerScreen extends Screen {
        private final Screen parent;
        private final java.util.function.Consumer<String> onSelect;
        private List<net.minecraft.resources.ResourceLocation> allResources = List.of();
        private List<net.minecraft.resources.ResourceLocation> resources = List.of();
        private int selected = -1;
        private double scrollPixels;
        private EditBox searchBox;
        private float imageZoom = 1.0f;
        private int imagePanX, imagePanY;
        private boolean panning, draggingListScrollbar;
        private int panStartX, panStartY, panMouseX, panMouseY;

        ResourceImagePickerScreen(Screen parent, java.util.function.Consumer<String> onSelect) {
            super(Component.translatable("screen.xero_delta.layout.image_resources"));
            this.parent = parent;
            this.onSelect = onSelect;
        }

        @Override
        protected void init() {
            allResources = Minecraft.getInstance().getResourceManager()
                .listResources("textures", id -> id.getPath().endsWith(".png"))
                .keySet().stream().sorted(Comparator.comparing(Object::toString)).toList();
            searchBox = addRenderableWidget(new Material3CompactEditBox(font, 8, 28, Math.max(100, width / 2 - 16), 20,
                Component.translatable("gui.search")));
            searchBox.setResponder(this::applySearch);
            applySearch("");
            addRenderableWidget(Material3Button.builder(Component.translatable("screen.xero_delta.layout.image_select"), button -> {
                if (selected >= 0 && selected < resources.size()) {
                    onSelect.accept(resources.get(selected).toString());
                }
            }).pos(width - 116, height - 26).size(52, 20).build());
            addRenderableWidget(Material3Button.builder(Component.translatable("gui.cancel"), button -> minecraft.setScreen(parent))
                .pos(width - 60, height - 26).size(52, 20).build());
        }

        private void applySearch(String query) {
            String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
            resources = allResources.stream().filter(id -> needle.isEmpty() || id.toString().toLowerCase(Locale.ROOT).contains(needle)).toList();
            selected = -1;
            scrollPixels = 0;
        }

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            graphics.fill(0, 0, width, height, Material3Theme.BACKGROUND);
            int listX = 8, top = 54, bottom = height - 34, rowHeight = 18;
            int listWidth = Math.max(120, width / 2 - 12);
            int listHeight = bottom - top;
            double maxScroll = Math.max(0, resources.size() * rowHeight - listHeight);
            Material2Drawing.roundedRect(graphics, listX, top, listWidth,
                listHeight, Material3Theme.RADIUS_LARGE,
                Material3Theme.SURFACE_CONTAINER_LOW);
            graphics.enableScissor(listX, top, listX + listWidth, bottom);
            int first = Math.max(0, (int)(scrollPixels / rowHeight));
            int offset = (int)(scrollPixels % rowHeight);
            for (int i = first; i < resources.size(); i++) {
                int y = top + (i - first) * rowHeight - offset;
                if (y >= bottom) break;
                boolean rowSelected = i == selected;
                Material2Drawing.roundedRect(graphics, listX + 4, y + 1,
                    listWidth - 12, 16, Material3Theme.RADIUS_EXTRA_SMALL,
                    rowSelected ? Material3Theme.PRIMARY_CONTAINER
                        : Material3Theme.SURFACE_CONTAINER);
                graphics.drawString(font, font.plainSubstrByWidth(resources.get(i).toString(), listWidth - 16),
                    listX + 8, y + 5, rowSelected
                        ? Material3Theme.ON_PRIMARY_CONTAINER : Material3Theme.TEXT);
            }
            graphics.disableScissor();
            renderPickerScrollbar(graphics, listX + listWidth - 6, top, listHeight, scrollPixels, maxScroll);
            int previewX = listX + listWidth + 8;
            int previewWidth = width - previewX - 8;
            Material2Drawing.roundedRect(graphics, previewX, top, previewWidth,
                bottom - top, Material3Theme.RADIUS_LARGE,
                Material3Theme.SURFACE_CONTAINER_LOW);
            Material2Drawing.outlineRoundedRect(graphics, previewX, top,
                previewWidth, bottom - top, Material3Theme.RADIUS_LARGE,
                1.0F, Material3Theme.OUTLINE_VARIANT);
            if (selected >= 0 && selected < resources.size()) {
                net.minecraft.resources.ResourceLocation texture = resources.get(selected);
                int imageWidth = Math.max(1, Math.round((previewWidth - 16) * imageZoom));
                int imageHeight = Math.max(1, Math.round((bottom - top - 16) * imageZoom));
                int imageX = previewX + (previewWidth - imageWidth) / 2 + imagePanX;
                int imageY = top + (bottom - top - imageHeight) / 2 + imagePanY;
                graphics.enableScissor(previewX + 1, top + 1, previewX + previewWidth - 1, bottom - 1);
                graphics.blit(texture, imageX, imageY, 0, 0, imageWidth, imageHeight, imageWidth, imageHeight);
                graphics.disableScissor();
                graphics.drawString(font, font.plainSubstrByWidth(texture.toString(), Math.max(1, previewWidth - 8)),
                    previewX + 8, bottom - 16, Material3Theme.TEXT);
            }
            super.render(graphics, mouseX, mouseY, partialTick);
            graphics.drawCenteredString(font, title, width / 2, 8, Material3Theme.TEXT);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            int listWidth = Math.max(120, width / 2 - 12);
            int top = 54, bottom = height - 34, listHeight = bottom - top;
            if (button == 0 && mouseX >= listWidth && mouseX <= listWidth + 8
                && mouseY >= top && mouseY < bottom) {
                draggingListScrollbar = true;
                scrollPixels = pickerScrollFromMouse(mouseY, top, listHeight,
                    Math.max(0, resources.size() * 18 - listHeight));
                return true;
            }
            if (mouseX >= 8 && mouseX <= 8 + listWidth && mouseY >= 54 && mouseY < height - 34) {
                int index = (int)((mouseY - 54 + scrollPixels) / 18);
                if (index >= 0 && index < resources.size()) selected = index;
                return true;
            }
            int previewX = 8 + listWidth + 8;
            if (button == 0 && mouseX >= previewX && mouseX < width - 8 && mouseY >= top && mouseY < bottom) {
                panning = true;
                panMouseX = (int)mouseX;
                panMouseY = (int)mouseY;
                panStartX = imagePanX;
                panStartY = imagePanY;
                return true;
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }

        @Override
        public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
            if (draggingListScrollbar && button == 0) {
                int top = 54, listHeight = height - 88;
                scrollPixels = pickerScrollFromMouse(mouseY, top, listHeight,
                    Math.max(0, resources.size() * 18 - listHeight));
                return true;
            }
            if (panning && button == 0) {
                imagePanX = panStartX + (int)mouseX - panMouseX;
                imagePanY = panStartY + (int)mouseY - panMouseY;
                return true;
            }
            return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
        }

        @Override
        public boolean mouseReleased(double mouseX, double mouseY, int button) {
            panning = false;
            draggingListScrollbar = false;
            return super.mouseReleased(mouseX, mouseY, button);
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
            int listWidth = Math.max(120, width / 2 - 12);
            if (mouseX <= 8 + listWidth) {
                scrollPixels = clampScroll(scrollPixels - scrollY * 18, Math.max(0, resources.size() * 18 - (height - 88)));
            } else {
                imageZoom = (float)Math.max(0.25, Math.min(8.0, imageZoom + scrollY * 0.1));
            }
            return true;
        }

        @Override
        public void onClose() { minecraft.setScreen(parent); }
    }

    static class ItemImagePickerScreen extends Screen {
        private final Screen parent;
        private final java.util.function.Consumer<String> onSelect;
        private final List<Item> inventoryItems = new ArrayList<>();
        private List<Item> displayedItems = List.of();
        private boolean inventoryTab = true;
        private int selected = -1;
        private double scrollPixels;
        private EditBox searchBox;
        private float imageZoom = 1.0f;
        private int imagePanX, imagePanY;
        private boolean panning, draggingListScrollbar;
        private int panStartX, panStartY, panMouseX, panMouseY;

        ItemImagePickerScreen(Screen parent, java.util.function.Consumer<String> onSelect) {
            super(Component.translatable("screen.xero_delta.layout.image_items"));
            this.parent = parent;
            this.onSelect = onSelect;
        }

        @Override
        protected void init() {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                LinkedHashSet<Item> unique = new LinkedHashSet<>();
                for (ItemStack stack : mc.player.getInventory().items) if (!stack.isEmpty()) unique.add(stack.getItem());
                for (ItemStack stack : mc.player.getInventory().offhand) if (!stack.isEmpty()) unique.add(stack.getItem());
                inventoryItems.addAll(unique);
            }
            refreshTab();
            addRenderableWidget(Material3Button.builder(Component.translatable("screen.xero_delta.layout.image_tab_inventory"), button -> {
                inventoryTab = true; refreshTab();
            }).pos(8, 24).size(72, 20).build());
            addRenderableWidget(Material3Button.builder(Component.translatable("screen.xero_delta.layout.image_tab_all"), button -> {
                inventoryTab = false; refreshTab();
            }).pos(84, 24).size(72, 20).build());
            searchBox = addRenderableWidget(new Material3CompactEditBox(font, 160, 24, Math.max(80, width / 2 - 168), 20,
                Component.translatable("gui.search")));
            searchBox.setResponder(value -> refreshTab());
            addRenderableWidget(Material3Button.builder(Component.translatable("screen.xero_delta.layout.image_select"), button -> choose())
                .pos(width - 116, height - 26).size(52, 20).build());
            addRenderableWidget(Material3Button.builder(Component.translatable("gui.cancel"), button -> minecraft.setScreen(parent))
                .pos(width - 60, height - 26).size(52, 20).build());
        }

        private void refreshTab() {
            String needle = searchBox == null ? "" : searchBox.getValue().trim().toLowerCase(Locale.ROOT);
            displayedItems = (inventoryTab ? inventoryItems.stream() : BuiltInRegistries.ITEM.stream())
                .filter(item -> needle.isEmpty() || BuiltInRegistries.ITEM.getKey(item).toString().toLowerCase(Locale.ROOT).contains(needle)
                    || item.getDescription().getString().toLowerCase(Locale.ROOT).contains(needle))
                .sorted(Comparator.comparing(item -> BuiltInRegistries.ITEM.getKey(item).toString())).toList();
            selected = -1;
            scrollPixels = 0;
        }

        private void choose() {
            if (selected < 0 || selected >= displayedItems.size()) return;
            onSelect.accept("item:" + BuiltInRegistries.ITEM.getKey(displayedItems.get(selected)));
        }

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            graphics.fill(0, 0, width, height, Material3Theme.BACKGROUND);
            int listX = 8, top = 50, bottom = height - 34, rowHeight = 20;
            int listWidth = Math.max(120, width / 2 - 12);
            int listHeight = bottom - top;
            double maxScroll = Math.max(0, displayedItems.size() * rowHeight - listHeight);
            Material2Drawing.roundedRect(graphics, listX, top, listWidth,
                listHeight, Material3Theme.RADIUS_LARGE,
                Material3Theme.SURFACE_CONTAINER_LOW);
            graphics.enableScissor(listX, top, listX + listWidth, bottom);
            int first = Math.max(0, (int)(scrollPixels / rowHeight));
            int offset = (int)(scrollPixels % rowHeight);
            for (int i = first; i < displayedItems.size(); i++) {
                int y = top + (i - first) * rowHeight - offset;
                if (y >= bottom) break;
                Item item = displayedItems.get(i);
                boolean rowSelected = i == selected;
                Material2Drawing.roundedRect(graphics, listX + 4, y + 1,
                    listWidth - 12, 18, Material3Theme.RADIUS_EXTRA_SMALL,
                    rowSelected ? Material3Theme.PRIMARY_CONTAINER
                        : Material3Theme.SURFACE_CONTAINER);
                graphics.renderItem(item.getDefaultInstance(), listX + 3, y + 2);
                graphics.drawString(font, font.plainSubstrByWidth(BuiltInRegistries.ITEM.getKey(item).toString(), listWidth - 28),
                    listX + 23, y + 6, rowSelected
                        ? Material3Theme.ON_PRIMARY_CONTAINER : Material3Theme.TEXT);
            }
            graphics.disableScissor();
            renderPickerScrollbar(graphics, listX + listWidth - 6, top, listHeight, scrollPixels, maxScroll);
            int previewX = listX + listWidth + 8;
            int previewWidth = width - previewX - 8;
            Material2Drawing.roundedRect(graphics, previewX, top, previewWidth,
                bottom - top, Material3Theme.RADIUS_LARGE,
                Material3Theme.SURFACE_CONTAINER_LOW);
            Material2Drawing.outlineRoundedRect(graphics, previewX, top,
                previewWidth, bottom - top, Material3Theme.RADIUS_LARGE,
                1.0F, Material3Theme.OUTLINE_VARIANT);
            if (selected >= 0 && selected < displayedItems.size()) {
                Item item = displayedItems.get(selected);
                float scale = Math.max(1.0F, Math.min(previewWidth - 16, bottom - top - 40) / 32.0F) * imageZoom;
                var pose = graphics.pose();
                pose.pushPose();
                pose.translate(previewX + previewWidth / 2.0F + imagePanX,
                    top + (bottom - top) / 2.0F - 10 + imagePanY, 0);
                pose.scale(scale, scale, 1.0F);
                graphics.renderItem(item.getDefaultInstance(), -8, -8);
                pose.popPose();
                graphics.drawCenteredString(font, font.plainSubstrByWidth(item.getDescription().getString(), Math.max(1, previewWidth - 8)),
                    previewX + previewWidth / 2, bottom - 28, Material3Theme.TEXT);
                graphics.drawString(font, font.plainSubstrByWidth(BuiltInRegistries.ITEM.getKey(item).toString(), Math.max(1, previewWidth - 8)),
                    previewX + 8, bottom - 14, Material3Theme.TEXT_MUTED);
            }
            super.render(graphics, mouseX, mouseY, partialTick);
            graphics.drawCenteredString(font, title, width / 2, 8, Material3Theme.TEXT);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            int listWidth = Math.max(120, width / 2 - 12);
            int top = 50, bottom = height - 34, listHeight = bottom - top;
            if (button == 0 && mouseX >= listWidth && mouseX <= listWidth + 8
                && mouseY >= top && mouseY < bottom) {
                draggingListScrollbar = true;
                scrollPixels = pickerScrollFromMouse(mouseY, top, listHeight,
                    Math.max(0, displayedItems.size() * 20 - listHeight));
                return true;
            }
            if (mouseX >= 8 && mouseX <= 8 + listWidth && mouseY >= 50 && mouseY < height - 34) {
                int index = (int)((mouseY - 50 + scrollPixels) / 20);
                if (index >= 0 && index < displayedItems.size()) selected = index;
                return true;
            }
            int previewX = 8 + listWidth + 8;
            if (button == 0 && mouseX >= previewX && mouseX < width - 8 && mouseY >= top && mouseY < bottom) {
                panning = true;
                panMouseX = (int)mouseX;
                panMouseY = (int)mouseY;
                panStartX = imagePanX;
                panStartY = imagePanY;
                return true;
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }

        @Override
        public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
            if (draggingListScrollbar && button == 0) {
                int top = 50, listHeight = height - 84;
                scrollPixels = pickerScrollFromMouse(mouseY, top, listHeight,
                    Math.max(0, displayedItems.size() * 20 - listHeight));
                return true;
            }
            if (panning && button == 0) {
                imagePanX = panStartX + (int)mouseX - panMouseX;
                imagePanY = panStartY + (int)mouseY - panMouseY;
                return true;
            }
            return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
        }

        @Override
        public boolean mouseReleased(double mouseX, double mouseY, int button) {
            panning = false;
            draggingListScrollbar = false;
            return super.mouseReleased(mouseX, mouseY, button);
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
            int listWidth = Math.max(120, width / 2 - 12);
            if (mouseX <= 8 + listWidth) {
                scrollPixels = clampScroll(scrollPixels - scrollY * 20, Math.max(0, displayedItems.size() * 20 - (height - 84)));
            } else {
                imageZoom = (float)Math.max(0.25, Math.min(8.0, imageZoom + scrollY * 0.1));
            }
            return true;
        }

        @Override
        public void onClose() { minecraft.setScreen(parent); }
    }

    // ---- Widget Properties Screen ----
    static class WidgetPropsScreen extends Screen {
        private final SafetyBoxLayoutScreen parent;
        private final String title;
        private final String[] labels;
        private final double[] initVals, mins, maxs;
        private final java.util.function.Consumer<double[]> onSave;
        private EditBox[] fields;

        WidgetPropsScreen(SafetyBoxLayoutScreen p, String title,
                          String[] labels, double[] initVals, double[] mins, double[] maxs,
                          java.util.function.Consumer<double[]> onSave) {
            super(Component.literal(title));
            this.parent = p; this.title = title;
            this.labels = labels; this.initVals = initVals; this.mins = mins; this.maxs = maxs;
            this.onSave = onSave;
        }

        @Override protected void init() {
            fields = new EditBox[labels.length];
            int startY = 40;
            for (int i = 0; i < labels.length; i++) {
                int y = startY + i * 28;
                fields[i] = new Material3CompactEditBox(font, width / 2 + 10, y, 80, 20, Component.literal(labels[i]));
                String s = initVals[i] == (int)initVals[i] ? String.valueOf((int)initVals[i]) : String.format("%.2f", initVals[i]);
                fields[i].setValue(s);
                addRenderableWidget(fields[i]);
            }
            addRenderableWidget(Material3Button.builder(Component.translatable("gui.done"), b -> {
                double[] vals = new double[labels.length];
                for (int i = 0; i < labels.length; i++) {
                    try { vals[i] = Double.parseDouble(fields[i].getValue()); }
                    catch (Exception e) { vals[i] = initVals[i]; }
                    vals[i] = Math.max(mins[i], Math.min(maxs[i], vals[i]));
                }
                onSave.accept(vals);
                minecraft.setScreen(parent);
            }).pos(width / 2 - 50, startY + labels.length * 28 + 10).size(100, 20).build());
        }

        @Override public void render(GuiGraphics g, int mx, int my, float pt) {
            g.fill(0, 0, width, height, Material3Theme.BACKGROUND);
            super.render(g, mx, my, pt);
            g.drawCenteredString(font, title, width / 2, 12, 0xFFFFFF);
            int startY = 40;
            for (int i = 0; i < labels.length; i++) {
                g.drawString(font, labels[i] + ":", width / 2 - 100, startY + i * 28 + 6, 0xAAAAAA);
            }
        }
    }
}

