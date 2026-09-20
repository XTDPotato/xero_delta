package com.xtdpotato.xero_delta.screen;

import com.xtdpotato.xero_delta.screen.material.Material2Icon;
import com.xtdpotato.xero_delta.screen.material.Material2Drawing;
import com.xtdpotato.xero_delta.screen.material.Material3Theme;

import com.xtdpotato.xero_delta.client.PlayerHudRenderer;
import com.xtdpotato.xero_delta.client.DeltaInventoryLayout;
import com.xtdpotato.xero_delta.client.GridItemRenderer;
import com.xtdpotato.xero_delta.client.ItemDetailOverlay;
import com.xtdpotato.xero_delta.client.ItemDetailLayout;
import com.xtdpotato.xero_delta.client.InventoryLayoutScale;
import com.xtdpotato.xero_delta.client.LoadoutLabelRenderer;
import com.xtdpotato.xero_delta.client.ContextInteractionHudRenderer;
import com.xtdpotato.xero_delta.client.DownedHudRenderer;
import com.xtdpotato.xero_delta.client.RescueProgressHudRenderer;
import com.xtdpotato.xero_delta.client.ScreenTransition;
import com.xtdpotato.xero_delta.client.StatusEffectHudRenderer;
import com.xtdpotato.xero_delta.client.StatusEffectHudState;
import com.xtdpotato.xero_delta.client.StaminaHudRenderer;
import com.xtdpotato.xero_delta.data.ConfigPaths;
import com.xtdpotato.xero_delta.item.DeltaPackItem;
import com.xtdpotato.xero_delta.item.SafetyBoxItem;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import top.theillusivec4.curios.api.CuriosApi;

/** Visual drag, scale, coordinate and snapping editor for effect and health HUD controls. */
public final class StatusEffectHudConfigScreen extends Screen {
    private static final int PANEL_WIDTH = 278;
    private static final int PANEL_HEIGHT = 264;
    private static final int COLLAPSED_SIZE = 24;
    private static final int PANEL_HEADER_HEIGHT = 54;
    private static final int TARGET_LIST_HEIGHT = 50;
    private static final int PANEL_FOOTER_HEIGHT = 30;
    private static final int PANEL_BODY_BOTTOM_PADDING = 4;
    private static final int HEADER_FOLDER_X = 215;
    private static final int HEADER_COLLAPSE_X = 236;
    private static final int HEADER_CLOSE_X = 257;
    private static final int HEADER_ICON_SIZE = 18;
    private static final int HISTORY_LIMIT = 100;
    private static final int NO_GUIDE = Integer.MIN_VALUE;
    private final Screen parent;
    private final Runnable saveListener;
    private final ScreenTransition transition = new ScreenTransition();
    private StatusEffectHudRenderer.Bounds effectPreview = StatusEffectHudRenderer.Bounds.EMPTY;
    private StatusEffectHudRenderer.Bounds healthPreview = StatusEffectHudRenderer.Bounds.EMPTY;
    private StatusEffectHudRenderer.Bounds introPreview = StatusEffectHudRenderer.Bounds.EMPTY;
    private StatusEffectHudRenderer.Bounds staminaPreview = StatusEffectHudRenderer.Bounds.EMPTY;
    private StatusEffectHudRenderer.Bounds downedPreview = StatusEffectHudRenderer.Bounds.EMPTY;
    private StatusEffectHudRenderer.Bounds rescuePreview = StatusEffectHudRenderer.Bounds.EMPTY;
    private StatusEffectHudRenderer.Bounds contextPreview = StatusEffectHudRenderer.Bounds.EMPTY;
    private StatusEffectHudRenderer.Bounds inventoryEffectPreview =
        StatusEffectHudRenderer.Bounds.EMPTY;
    private StatusEffectHudRenderer.Bounds inventoryLayoutPreview =
        StatusEffectHudRenderer.Bounds.EMPTY;
    private StatusEffectHudRenderer.Bounds itemDetailPreview =
        StatusEffectHudRenderer.Bounds.EMPTY;
    private EditBox xField;
    private EditBox yField;
    private EditBox primaryField;
    private EditBox secondaryField;
    private boolean draggingPreview;
    private boolean resizingPreview;
    private int resizeEdge;
    private int resizeStartScale;
    private StatusEffectHudRenderer.Bounds resizeStartBounds = StatusEffectHudRenderer.Bounds.EMPTY;
    private boolean draggingPanel;
    private boolean panelDragMoved;
    private boolean draggingPanelScrollbar;
    private boolean draggingTargetScrollbar;
    private int draggingAdjustmentSlider;
    private int dragStartX;
    private int dragStartY;
    private int originX;
    private int originY;
    private HudTarget selectedTarget = HudTarget.EFFECTS;
    private Scene selectedScene = Scene.HUD;
    private InventoryTarget selectedInventoryTarget = InventoryTarget.EFFECTS;
    private int snapGuideX = NO_GUIDE;
    private int snapGuideY = NO_GUIDE;
    private double panelScroll;
    private double panelTargetScroll;
    private double panelScrollbarDragStartY;
    private double panelScrollbarDragStartScroll;
    private double targetScroll;
    private double targetTargetScroll;
    private double targetScrollbarDragStartY;
    private double targetScrollbarDragStartScroll;
    private final Deque<String> undoHistory = new ArrayDeque<>();
    private final Deque<String> redoHistory = new ArrayDeque<>();
    private String savedSnapshot;
    private String observedSnapshot;
    private int unchangedSnapshotTicks;
    private boolean editorInitialized;

    public StatusEffectHudConfigScreen(Screen parent) {
        this(parent, () -> { });
    }

    public StatusEffectHudConfigScreen(Screen parent, Runnable saveListener) {
        super(Component.translatable("status_effect_hud.xero_delta.title"));
        this.parent = parent;
        this.saveListener = saveListener == null ? () -> { } : saveListener;
    }

    @Override
    protected void init() {
        if (!editorInitialized) {
            savedSnapshot = StatusEffectHudState.editorSnapshot();
            undoHistory.addLast(savedSnapshot);
            editorInitialized = true;
        }
        StatusEffectHudState.adaptToScreen(width, height);
        commitCurrentSnapshot();
        observedSnapshot = StatusEffectHudState.editorSnapshot();
        int[] panel = panelPosition();
        xField = addRenderableWidget(new Material3CompactEditBox(font, panel[0] + 30, panel[1] + 115,
            92, 18, Component.empty()));
        yField = addRenderableWidget(new Material3CompactEditBox(font, panel[0] + 156, panel[1] + 115,
            92, 18, Component.empty()));
        xField.setMaxLength(6);
        yField.setMaxLength(6);
        xField.setFilter(StatusEffectHudConfigScreen::isSignedInteger);
        yField.setFilter(StatusEffectHudConfigScreen::isSignedInteger);
        xField.setResponder(value -> applyCoordinate(value, true));
        yField.setResponder(value -> applyCoordinate(value, false));
        primaryField = addRenderableWidget(new Material3CompactEditBox(font,
            panel[0] + PANEL_WIDTH - 66, panel[1] + 84, 52, 18, Component.empty()));
        secondaryField = addRenderableWidget(new Material3CompactEditBox(font,
            panel[0] + PANEL_WIDTH - 66, panel[1] + 128, 52, 18, Component.empty()));
        primaryField.setMaxLength(6);
        secondaryField.setMaxLength(6);
        primaryField.setFilter(StatusEffectHudConfigScreen::isSignedInteger);
        secondaryField.setFilter(StatusEffectHudConfigScreen::isSignedInteger);
        primaryField.setResponder(value -> applyAdjustmentValue(value, true));
        secondaryField.setResponder(value -> applyAdjustmentValue(value, false));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        tickPanelScroll();
        graphics.fill(0, 0, width, height, Material3Theme.BACKGROUND);
        transition.push(graphics);
        try {
            graphics.drawCenteredString(font, title, width / 2, 20, 0xFFF1F5F3);
            drawSnapGuides(graphics);
            if (selectedScene == Scene.HUD) {
                effectPreview = StatusEffectHudRenderer.renderPreview(
                    graphics, font, width, height, mouseX, mouseY);
                introPreview = StatusEffectHudRenderer.renderIntroPreview(
                    graphics, minecraft, effectPreview.x(), effectPreview.y());
                staminaPreview = StaminaHudRenderer.renderPreview(graphics, minecraft);
                healthPreview = PlayerHudRenderer.renderPreview(graphics, minecraft);
                downedPreview = DownedHudRenderer.renderPreview(graphics, minecraft);
                rescuePreview = RescueProgressHudRenderer.renderPreview(graphics, minecraft);
                contextPreview = ContextInteractionHudRenderer.renderPreview(graphics, minecraft);
                inventoryEffectPreview = StatusEffectHudRenderer.Bounds.EMPTY;
                inventoryLayoutPreview = StatusEffectHudRenderer.Bounds.EMPTY;
            } else {
                effectPreview = StatusEffectHudRenderer.Bounds.EMPTY;
                introPreview = StatusEffectHudRenderer.Bounds.EMPTY;
                staminaPreview = StatusEffectHudRenderer.Bounds.EMPTY;
                healthPreview = StatusEffectHudRenderer.Bounds.EMPTY;
                downedPreview = StatusEffectHudRenderer.Bounds.EMPTY;
                rescuePreview = StatusEffectHudRenderer.Bounds.EMPTY;
                contextPreview = StatusEffectHudRenderer.Bounds.EMPTY;
                if (selectedInventoryTarget == InventoryTarget.LAYOUT) {
                    inventoryEffectPreview = StatusEffectHudRenderer.Bounds.EMPTY;
                    inventoryLayoutPreview = renderInventoryLayoutPreview(graphics);
                    itemDetailPreview = StatusEffectHudRenderer.Bounds.EMPTY;
                } else if (selectedInventoryTarget == InventoryTarget.ITEM_DETAIL) {
                    inventoryEffectPreview = StatusEffectHudRenderer.Bounds.EMPTY;
                    inventoryLayoutPreview = StatusEffectHudRenderer.Bounds.EMPTY;
                    itemDetailPreview = renderItemDetailPreview(graphics);
                } else {
                    inventoryEffectPreview = renderInventoryEffectPreview(graphics);
                    inventoryLayoutPreview = StatusEffectHudRenderer.Bounds.EMPTY;
                    itemDetailPreview = StatusEffectHudRenderer.Bounds.EMPTY;
                }
            }
            StatusEffectHudRenderer.Bounds selected = activePreview();
            if (selected.width() > 0) {
                border(graphics, selected.x() - 2, selected.y() - 2,
                    selected.width() + 4, selected.height() + 4, 0xFFF1F5F3);
                drawResizeHandles(graphics, selected, mouseX, mouseY);
            }
            syncCoordinateFields();
            syncAdjustmentFields();
            // Keep the editor controls above every preview surface and HUD sample.
            graphics.pose().pushPose();
            graphics.pose().translate(0.0F, 0.0F, 1200.0F);
            drawControls(graphics, mouseX, mouseY, partialTick);
            graphics.pose().popPose();
            HudTarget hovered = hoveredTarget(mouseX, mouseY);
            if (hovered != null && !draggingPanel && !StatusEffectHudState.panelCollapsed()) {
                String tooltipKey = switch (hovered) {
                    case HEALTH -> "status_effect_hud.xero_delta.preview_health";
                    case INTRO -> "status_effect_hud.xero_delta.preview_intro";
                    case STAMINA -> "status_effect_hud.xero_delta.preview_stamina";
                    case DOWNED -> "status_effect_hud.xero_delta.preview_downed";
                    case RESCUE -> "status_effect_hud.xero_delta.preview_rescue";
                    case CONTEXT -> "status_effect_hud.xero_delta.preview_context";
                    default -> "status_effect_hud.xero_delta.preview_effects";
                };
                graphics.pose().pushPose();
                graphics.pose().translate(0.0F, 0.0F, 3000.0F);
                graphics.renderTooltip(font, Component.translatable(tooltipKey), mouseX, mouseY);
                graphics.pose().popPose();
            }
        } finally {
            transition.pop(graphics);
            transition.drawFade(graphics, width, height);
        }
    }

    private void drawControls(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int[] panel = panelPosition();
        int panelX = panel[0], panelY = panel[1];
        if (StatusEffectHudState.panelCollapsed()) {
            setAllFieldsVisible(false);
            boolean hovered = inside(mouseX, mouseY, panelX, panelY,
                COLLAPSED_SIZE, COLLAPSED_SIZE);
            Material2Drawing.roundedRect(graphics, panelX, panelY, COLLAPSED_SIZE,
                COLLAPSED_SIZE, Material3Theme.RADIUS_SMALL,
                hovered ? Material3Theme.SURFACE_CONTAINER_HIGHEST
                    : Material3Theme.SURFACE_CONTAINER_HIGH);
            Material2Icon.TUNE.render(graphics, panelX + COLLAPSED_SIZE / 2,
                panelY + COLLAPSED_SIZE / 2,
                hovered ? Material3Theme.PRIMARY : Material3Theme.TEXT);
            return;
        }

        clampPanelScroll();
        int panelHeight = panelHeight();
        Material2Drawing.roundedRect(graphics, panelX, panelY, PANEL_WIDTH, panelHeight,
            Material3Theme.RADIUS_SMALL, Material3Theme.SURFACE_CONTAINER);
        Material2Drawing.outlineRoundedRect(graphics, panelX, panelY, PANEL_WIDTH, panelHeight,
            Material3Theme.RADIUS_SMALL, 1.0F, Material3Theme.OUTLINE_VARIANT);
        Material2Drawing.roundedRect(graphics, panelX + 1, panelY + 1, PANEL_WIDTH - 2,
            PANEL_HEADER_HEIGHT, Material3Theme.RADIUS_SMALL,
            Material3Theme.SURFACE_CONTAINER_HIGH);
        drawButton(graphics, panelX + 4, panelY + 3, 18, 18, "\u21B6",
            inside(mouseX, mouseY, panelX + 4, panelY + 3, 18, 18));
        drawButton(graphics, panelX + 25, panelY + 3, 18, 18, "\u21B7",
            inside(mouseX, mouseY, panelX + 25, panelY + 3, 18, 18));
        String heading = font.plainSubstrByWidth(title.getString(), 150);
        graphics.drawString(font, heading, panelX + 51, panelY + 8,
            Material3Theme.TEXT, false);
        drawTargetButton(graphics, panelX + 8, panelY + 29, 127, 20,
            Component.translatable("status_effect_hud.xero_delta.scene_hud"),
            selectedScene == Scene.HUD, mouseX, mouseY);
        drawTargetButton(graphics, panelX + 143, panelY + 29, 127, 20,
            Component.translatable("status_effect_hud.xero_delta.scene_inventory"),
            selectedScene == Scene.INVENTORY, mouseX, mouseY);
        drawHeaderIconButton(graphics, panelX + HEADER_FOLDER_X, panelY + 3,
            Material2Icon.OPEN_IN_NEW, mouseX, mouseY);
        drawButton(graphics, panelX + HEADER_COLLAPSE_X, panelY + 3,
            HEADER_ICON_SIZE, HEADER_ICON_SIZE, "-",
            inside(mouseX, mouseY, panelX + HEADER_COLLAPSE_X, panelY + 3,
                HEADER_ICON_SIZE, HEADER_ICON_SIZE));
        drawHeaderIconButton(graphics, panelX + HEADER_CLOSE_X, panelY + 3,
            Material2Icon.CLOSE, mouseX, mouseY);

        int listY = panelY + PANEL_HEADER_HEIGHT;
        drawTargetList(graphics, panelX, listY, mouseX, mouseY);
        int bodyY = listY + TARGET_LIST_HEIGHT;
        int bodyHeight = panelBodyHeight();
        int contentY = bodyY + 6 - (int) Math.round(panelScroll);
        graphics.enableScissor(panelX + 1, bodyY, panelX + PANEL_WIDTH - 1,
            bodyY + bodyHeight);
        drawSectionHeader(graphics, panelX, contentY,
            Component.translatable("status_effect_hud.xero_delta.section_appearance"));
        if (isItemDetailTarget()) {
            drawAdjustmentRow(graphics, panelX, contentY + 16,
                Component.translatable("status_effect_hud.xero_delta.item_detail_width"),
                itemDetailValue(StatusEffectHudState.itemDetailWidth(),
                    StatusEffectHudState.itemDetailAutoWidth()),
                StatusEffectHudState.itemDetailWidth(),
                StatusEffectHudState.itemDetailAutoWidth() ? null : primaryRange(),
                mouseX, mouseY);
            drawAdjustmentRow(graphics, panelX, contentY + 60,
                Component.translatable("status_effect_hud.xero_delta.item_detail_height"),
                itemDetailValue(StatusEffectHudState.itemDetailHeight(),
                    StatusEffectHudState.itemDetailAutoHeight()),
                StatusEffectHudState.itemDetailHeight(),
                StatusEffectHudState.itemDetailAutoHeight() ? null : secondaryRange(),
                mouseX, mouseY);
            drawAdjustmentRow(graphics, panelX, contentY + 104,
                Component.translatable("status_effect_hud.xero_delta.item_detail_scale"),
                StatusEffectHudState.itemDetailScalePercent() + "%",
                StatusEffectHudState.itemDetailScalePercent(), new ValueRange(50, 200, 5),
                mouseX, mouseY);
        } else {
            drawAdjustmentRow(graphics, panelX, contentY + 16,
                primaryAdjustmentLabel(), primaryAdjustmentValue(), activeScalePercent(),
                isAutoItemDetail() ? null : primaryRange(), mouseX, mouseY);
            drawAdjustmentRow(graphics, panelX, contentY + 60,
                secondaryAdjustmentLabel(), secondaryAdjustmentValue(), activeOpacityPercent(),
                isAutoItemDetail() ? null : secondaryRange(), mouseX, mouseY);
        }
        moveAdjustmentFields(panelX, contentY, bodyY, bodyY + bodyHeight);
        if (primaryField.visible) primaryField.render(graphics, mouseX, mouseY, partialTick);
        if (secondaryField.visible) secondaryField.render(graphics, mouseX, mouseY, partialTick);

        if (selectedScene == Scene.INVENTORY
            && selectedInventoryTarget == InventoryTarget.LAYOUT) {
            setCoordinateFieldsVisible(false);
            graphics.drawString(font, Component.translatable(
                "status_effect_hud.xero_delta.inventory_scale_hint"),
                panelX + 14, contentY + 148, Material3Theme.TEXT_MUTED, false);
            drawAdjustmentRow(graphics, panelX, contentY + 104,
                Component.translatable("status_effect_hud.xero_delta.inventory_margin"),
                StatusEffectHudState.inventoryLayoutMarginPercent() + "%",
                StatusEffectHudState.inventoryLayoutMarginPercent(),
                new ValueRange(0, 40, 5), mouseX, mouseY);
            graphics.disableScissor();
            drawPanelScrollbar(graphics, panelX, bodyY, bodyHeight, mouseX, mouseY);
            drawPanelFooter(graphics, panelX, panelY, mouseX, mouseY);
            drawHeaderTooltips(graphics, panelX, panelY, mouseX, mouseY);
            return;
        }

        int coordinateY;
        if (selectedScene == Scene.INVENTORY
            && selectedInventoryTarget == InventoryTarget.ITEM_DETAIL) {
            int half = (PANEL_WIDTH - 32) / 2;
            drawToggle(graphics, panelX + 14, contentY + 148, half, 22,
                Component.translatable("status_effect_hud.xero_delta.item_detail_auto_width"),
                StatusEffectHudState.itemDetailAutoWidth(), mouseX, mouseY);
            drawToggle(graphics, panelX + 18 + half, contentY + 148, half, 22,
                Component.translatable("status_effect_hud.xero_delta.item_detail_auto_height"),
                StatusEffectHudState.itemDetailAutoHeight(), mouseX, mouseY);
            coordinateY = contentY + 180;
        } else if (selectedScene == Scene.HUD && selectedTarget == HudTarget.INTRO) {
            drawSectionHeader(graphics, panelX, contentY + 108,
                Component.translatable("status_effect_hud.xero_delta.section_animation"));
            drawAdjustmentRow(graphics, panelX, contentY + 120,
                Component.translatable("status_effect_hud.xero_delta.fade_in_duration"),
                durationLabel(StatusEffectHudState.effectIntroFadeInDurationMs()),
                StatusEffectHudState.effectIntroFadeInDurationMs(), new ValueRange(50, 2_000, 50),
                mouseX, mouseY);
            drawAdjustmentRow(graphics, panelX, contentY + 164,
                Component.translatable("status_effect_hud.xero_delta.hold_duration"),
                durationLabel(StatusEffectHudState.effectIntroHoldDurationMs()),
                StatusEffectHudState.effectIntroHoldDurationMs(), new ValueRange(100, 5_000, 50),
                mouseX, mouseY);
            drawAdjustmentRow(graphics, panelX, contentY + 208,
                Component.translatable("status_effect_hud.xero_delta.fade_out_duration"),
                durationLabel(StatusEffectHudState.effectIntroFadeOutDurationMs()),
                StatusEffectHudState.effectIntroFadeOutDurationMs(), new ValueRange(50, 2_000, 50),
                mouseX, mouseY);
            coordinateY = contentY + 252;
        } else if (selectedScene == Scene.HUD
            && (selectedTarget == HudTarget.HEALTH || selectedTarget == HudTarget.STAMINA)) {
            drawSectionHeader(graphics, panelX, contentY + 108,
                Component.translatable("status_effect_hud.xero_delta.section_animation"));
            if (selectedTarget == HudTarget.HEALTH) {
                drawAdjustmentRow(graphics, panelX, contentY + 120,
                    Component.translatable("status_effect_hud.xero_delta.animation_duration"),
                    durationLabel(StatusEffectHudState.healthAnimationDurationMs()),
                    StatusEffectHudState.healthAnimationDurationMs(), new ValueRange(50, 500, 50),
                    mouseX, mouseY);
            } else {
                drawReadOnlyValueRow(graphics, panelX, contentY + 120,
                    Component.translatable("status_effect_hud.xero_delta.animation_duration"),
                    activeAnimationLabel());
            }
            coordinateY = contentY + 164;
        } else {
            coordinateY = contentY + 108;
        }

        drawSectionHeader(graphics, panelX, coordinateY,
            Component.translatable("status_effect_hud.xero_delta.section_position"));
        int fieldY = coordinateY + 16;
        graphics.drawString(font, "X", panelX + 16, fieldY + 5, Material3Theme.TEXT, false);
        graphics.drawString(font, "Y", panelX + 142, fieldY + 5, Material3Theme.TEXT, false);
        moveFields(panelX, fieldY, bodyY, bodyY + bodyHeight);
        if (xField.visible) xField.render(graphics, mouseX, mouseY, partialTick);
        if (yField.visible) yField.render(graphics, mouseX, mouseY, partialTick);
        int snapY = coordinateY + 42;
        int halfWidth = (PANEL_WIDTH - 32) / 2;
        drawToggle(graphics, panelX + 14, snapY, halfWidth, 22,
            Component.translatable("status_effect_hud.xero_delta.snap_x"),
            StatusEffectHudState.snapX(), mouseX, mouseY);
        drawToggle(graphics, panelX + 18 + halfWidth, snapY, halfWidth, 22,
            Component.translatable("status_effect_hud.xero_delta.snap_y"),
            StatusEffectHudState.snapY(), mouseX, mouseY);
        int resetY = coordinateY + 70;
        drawResetButton(graphics, panelX + 14, resetY, PANEL_WIDTH - 28, 24,
            mouseX, mouseY);
        graphics.disableScissor();
        drawPanelScrollbar(graphics, panelX, bodyY, bodyHeight, mouseX, mouseY);
        drawPanelFooter(graphics, panelX, panelY, mouseX, mouseY);
        drawHeaderTooltips(graphics, panelX, panelY, mouseX, mouseY);
    }

    private Component primaryAdjustmentLabel() {
        if (selectedScene == Scene.INVENTORY) {
            if (selectedInventoryTarget == InventoryTarget.LAYOUT) {
                return Component.translatable("status_effect_hud.xero_delta.inventory_slot_scale");
            }
            if (selectedInventoryTarget == InventoryTarget.ITEM_DETAIL) {
                return Component.translatable("status_effect_hud.xero_delta.item_detail_width");
            }
        }
        return Component.translatable("status_effect_hud.xero_delta.scale");
    }

    private Component secondaryAdjustmentLabel() {
        if (selectedScene == Scene.INVENTORY) {
            if (selectedInventoryTarget == InventoryTarget.LAYOUT) {
                return Component.translatable("status_effect_hud.xero_delta.inventory_text_scale");
            }
            if (selectedInventoryTarget == InventoryTarget.ITEM_DETAIL) {
                return Component.translatable("status_effect_hud.xero_delta.item_detail_height");
            }
        }
        return Component.translatable("status_effect_hud.xero_delta.opacity");
    }

    private String primaryAdjustmentValue() {
        if (isAutoItemDetail()) {
            return Component.translatable(
                "status_effect_hud.xero_delta.item_detail_auto").getString();
        }
        return activeScalePercent() + (selectedScene == Scene.INVENTORY
            && selectedInventoryTarget == InventoryTarget.ITEM_DETAIL ? "px" : "%");
    }

    private String secondaryAdjustmentValue() {
        if (isAutoItemDetail()) {
            return Component.translatable(
                "status_effect_hud.xero_delta.item_detail_auto").getString();
        }
        return activeOpacityPercent() + (selectedScene == Scene.INVENTORY
            && selectedInventoryTarget == InventoryTarget.ITEM_DETAIL ? "px" : "%");
    }
    private void drawAdjustmentRow(GuiGraphics graphics, int panelX, int rowY,
                                   Component label, String value, int numericValue,
                                   ValueRange range, int mouseX, int mouseY) {
        String labelText = font.plainSubstrByWidth(label.getString(), PANEL_WIDTH - 96);
        graphics.drawString(font, Component.literal(labelText), panelX + 14, rowY + 4,
            range == null ? Material3Theme.TEXT_MUTED : Material3Theme.TEXT, false);
        boolean inputVisible = (primaryField != null && primaryField.visible
            && primaryField.getY() == rowY + 1)
            || (secondaryField != null && secondaryField.visible
                && secondaryField.getY() == rowY + 1);
        if (!inputVisible) {
            int valueWidth = Math.max(38, font.width(value) + 12);
            int valueX = panelX + PANEL_WIDTH - 14 - valueWidth;
            Material2Drawing.roundedRect(graphics, valueX, rowY + 1, valueWidth, 18,
                Material3Theme.RADIUS_FULL, Material3Theme.SURFACE_CONTAINER_HIGHEST);
            graphics.drawCenteredString(font, value, valueX + valueWidth / 2, rowY + 6,
                range == null ? Material3Theme.TEXT_MUTED : Material3Theme.TEXT);
        }

        int trackX = panelX + 14;
        int trackY = rowY + 27;
        int trackWidth = PANEL_WIDTH - 28;
        boolean hovered = inside(mouseX, mouseY, trackX - 2, trackY - 6,
            trackWidth + 4, 14);
        int inactive = range == null ? Material3Theme.alpha(Material3Theme.OUTLINE_VARIANT, 110)
            : hovered ? Material3Theme.OUTLINE : Material3Theme.OUTLINE_VARIANT;
        Material2Drawing.roundedRect(graphics, trackX, trackY, trackWidth, 4,
            Material3Theme.RADIUS_FULL, inactive);
        if (range != null) {
            float fraction = range.fraction(numericValue);
            int activeWidth = Math.max(2, Math.round(trackWidth * fraction));
            Material2Drawing.roundedRect(graphics, trackX, trackY, activeWidth, 4,
                Material3Theme.RADIUS_FULL, Material3Theme.PRIMARY);
            int thumbX = trackX + activeWidth;
            Material2Drawing.circle(graphics, thumbX, trackY + 2,
                hovered ? 5 : 4, Material3Theme.PRIMARY);
        }
    }

    private void drawReadOnlyValueRow(GuiGraphics graphics, int panelX, int rowY,
                                      Component label, String value) {
        drawAdjustmentRow(graphics, panelX, rowY, label, value, 0, null, -10_000, -10_000);
    }

    private void drawSectionHeader(GuiGraphics graphics, int panelX, int y, Component label) {
        graphics.drawString(font, label, panelX + 14, y + 1, Material3Theme.PRIMARY, false);
        int dividerX = panelX + 20 + font.width(label);
        if (dividerX < panelX + PANEL_WIDTH - 14) {
            graphics.fill(dividerX, y + 5, panelX + PANEL_WIDTH - 14, y + 6,
                Material3Theme.OUTLINE_VARIANT);
        }
    }

    private void moveAdjustmentFields(int panelX, int contentY, int visibleTop, int visibleBottom) {
        if (primaryField == null || secondaryField == null) return;
        primaryField.setX(panelX + PANEL_WIDTH - 66);
        primaryField.setY(contentY + 17);
        secondaryField.setX(panelX + PANEL_WIDTH - 66);
        secondaryField.setY(contentY + 61);
        primaryField.visible = (!isItemDetailTarget() || !StatusEffectHudState.itemDetailAutoWidth())
            && contentY + 35 > visibleTop && contentY + 16 < visibleBottom;
        secondaryField.visible = (!isItemDetailTarget() || !StatusEffectHudState.itemDetailAutoHeight())
            && contentY + 79 > visibleTop && contentY + 60 < visibleBottom;
        if (!primaryField.visible) primaryField.setFocused(false);
        if (!secondaryField.visible) secondaryField.setFocused(false);
    }

    private void syncAdjustmentFields() {
        if (primaryField == null || secondaryField == null) return;
        if (!isItemDetailTarget()) {
            if (!primaryField.isFocused()) primaryField.setValue(Integer.toString(activeScalePercent()));
            if (!secondaryField.isFocused()) secondaryField.setValue(Integer.toString(activeOpacityPercent()));
            return;
        }
        if (!primaryField.isFocused() && !StatusEffectHudState.itemDetailAutoWidth()) {
            primaryField.setValue(Integer.toString(StatusEffectHudState.itemDetailWidth()));
        }
        if (!secondaryField.isFocused() && !StatusEffectHudState.itemDetailAutoHeight()) {
            secondaryField.setValue(Integer.toString(StatusEffectHudState.itemDetailHeight()));
        }
    }

    private boolean isItemDetailTarget() {
        return selectedScene == Scene.INVENTORY
            && selectedInventoryTarget == InventoryTarget.ITEM_DETAIL;
    }

    private boolean isAutoItemDetail() {
        return isItemDetailTarget()
            && StatusEffectHudState.itemDetailAutoWidth()
            && StatusEffectHudState.itemDetailAutoHeight();
    }

    private String itemDetailValue(int value, boolean automatic) {
        return automatic ? Component.translatable(
            "status_effect_hud.xero_delta.item_detail_auto").getString() : value + "px";
    }

    private void materializeItemDetailSize() {
        if (!isAutoItemDetail()) return;
        int resolvedWidth = itemDetailPreview.width() > 0
            ? itemDetailPreview.width() : ItemDetailOverlay.previewWidth(this);
        int resolvedHeight = itemDetailPreview.height() > 0
            ? itemDetailPreview.height() : ItemDetailOverlay.previewHeight(this);
        StatusEffectHudState.setManualItemDetailSize(resolvedWidth, resolvedHeight);
    }

    private void applyAdjustmentValue(String value, boolean primary) {
        if (value == null || value.isBlank() || "-".equals(value)) return;
        try {
            int parsed = Integer.parseInt(value);
            if (primary) setActiveScalePercent(parsed);
            else setActiveOpacityPercent(parsed);
        } catch (NumberFormatException ignored) {
        }
    }
    private void moveFields(int panelX, int fieldY, int visibleTop, int visibleBottom) {
        if (xField == null || yField == null) return;
        xField.setX(panelX + 30);
        xField.setY(fieldY);
        yField.setX(panelX + 156);
        yField.setY(fieldY);
        boolean visible = fieldY + 18 > visibleTop && fieldY < visibleBottom;
        setCoordinateFieldsVisible(visible);
    }

    private void setCoordinateFieldsVisible(boolean visible) {
        if (xField != null) {
            xField.visible = visible;
            if (!visible) xField.setFocused(false);
        }
        if (yField != null) {
            yField.visible = visible;
            if (!visible) yField.setFocused(false);
        }
    }

    private void setAllFieldsVisible(boolean visible) {
        setCoordinateFieldsVisible(visible);
        if (primaryField != null) {
            primaryField.visible = visible;
            if (!visible) primaryField.setFocused(false);
        }
        if (secondaryField != null) {
            secondaryField.visible = visible;
            if (!visible) secondaryField.setFocused(false);
        }
    }

    private void syncCoordinateFields() {
        if (xField == null || yField == null) return;
        StatusEffectHudRenderer.Bounds selected = activePreview();
        if (selected.width() <= 0) return;
        if (!xField.isFocused()) xField.setValue(Integer.toString(selected.x()));
        if (!yField.isFocused()) yField.setValue(Integer.toString(selected.y()));
    }

    private void applyCoordinate(String value, boolean xAxis) {
        StatusEffectHudRenderer.Bounds selected = activePreview();
        if (value == null || value.isBlank() || "-".equals(value) || selected.width() <= 0) return;
        try {
            int parsed = Integer.parseInt(value);
            int nextX = xAxis ? parsed : selected.x();
            int nextY = xAxis ? selected.y() : parsed;
            nextX = Math.max(2, Math.min(width - selected.width() - 2, nextX));
            nextY = Math.max(2, Math.min(height - selected.height() - 2, nextY));
            setActivePosition(nextX, nextY);
        } catch (NumberFormatException ignored) {
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);
        clearSnapGuides();
        int[] panel = panelPosition();
        int panelX = panel[0], panelY = panel[1];
        if (StatusEffectHudState.panelCollapsed()) {
            if (inside(mouseX, mouseY, panelX, panelY, COLLAPSED_SIZE, COLLAPSED_SIZE)) {
                draggingPanel = true;
                panelDragMoved = false;
                startDrag(mouseX, mouseY, panelX, panelY);
                return true;
            }
        } else if (inputFieldAt(mouseX, mouseY) != null) {
            // Forward editor clicks directly so EditBox retains focus and caret handling.
            EditBox field = inputFieldAt(mouseX, mouseY);
            boolean handled = field.mouseClicked(mouseX, mouseY, button);
            setFocused(field);
            field.setFocused(true);
            return handled;
        }
        if (!StatusEffectHudState.panelCollapsed()
            && inside(mouseX, mouseY, panelX + HEADER_CLOSE_X, panelY + 3,
                HEADER_ICON_SIZE, HEADER_ICON_SIZE)) {
            onClose();
            return true;
        }
        if (!StatusEffectHudState.panelCollapsed()
            && inside(mouseX, mouseY, panelX + HEADER_COLLAPSE_X, panelY + 3,
                HEADER_ICON_SIZE, HEADER_ICON_SIZE)) {
            StatusEffectHudState.setPanelCollapsed(true);
            setAllFieldsVisible(false);
            return true;
        }
        if (!StatusEffectHudState.panelCollapsed()
            && inside(mouseX, mouseY, panelX + HEADER_FOLDER_X, panelY + 3,
                HEADER_ICON_SIZE, HEADER_ICON_SIZE)) {
            openConfigFolder();
            return true;
        }
        if (!StatusEffectHudState.panelCollapsed()
            && inside(mouseX, mouseY, panelX + 4, panelY + 3, 18, 18)) {
            undo(); return true;
        }
        if (!StatusEffectHudState.panelCollapsed()
            && inside(mouseX, mouseY, panelX + 25, panelY + 3, 18, 18)) {
            redo(); return true;
        }
        if (!StatusEffectHudState.panelCollapsed()
            && inside(mouseX, mouseY, panelX + 8, panelY + 29, 127, 20)) {
            selectedScene = Scene.HUD;
            selectedTarget = HudTarget.EFFECTS;
            resetTargetScroll();
            resetPanelScroll();
            return true;
        }
        if (!StatusEffectHudState.panelCollapsed()
            && inside(mouseX, mouseY, panelX + 143, panelY + 29, 127, 20)) {
            selectedScene = Scene.INVENTORY;
            selectedTarget = HudTarget.EFFECTS;
            resetTargetScroll();
            resetPanelScroll();
            return true;
        }
        if (!StatusEffectHudState.panelCollapsed()) {
            int footerY = panelY + panelHeight() - PANEL_FOOTER_HEIGHT + 3;
            int footerButtonWidth = (PANEL_WIDTH - 20) / 2;
            if (inside(mouseX, mouseY, panelX + 8, footerY, footerButtonWidth, 22)) {
                saveAndClose(); return true;
            }
            if (inside(mouseX, mouseY, panelX + 12 + footerButtonWidth,
                footerY, footerButtonWidth, 22)) {
                cancelAndClose(); return true;
            }
            int listY = panelY + PANEL_HEADER_HEIGHT;
            if (targetContentHeight() > TARGET_LIST_HEIGHT
                && inside(mouseX, mouseY, panelX + PANEL_WIDTH - 10,
                    listY, 10, TARGET_LIST_HEIGHT)) {
                draggingTargetScrollbar = true;
                targetScrollbarDragStartY = mouseY;
                targetScrollbarDragStartScroll = targetTargetScroll;
                return true;
            }
            if (inside(mouseX, mouseY, panelX + 1, listY,
                PANEL_WIDTH - 10, TARGET_LIST_HEIGHT)) {
                if (selectTargetAt(mouseX, mouseY, panelX, listY)) return true;
            }
            int bodyY = listY + TARGET_LIST_HEIGHT;
            int bodyHeight = panelBodyHeight();
            if (panelContentHeight() > bodyHeight
                && inside(mouseX, mouseY, panelX + PANEL_WIDTH - 10,
                    bodyY, 10, bodyHeight)) {
                draggingPanelScrollbar = true;
                panelScrollbarDragStartY = mouseY;
                panelScrollbarDragStartScroll = panelTargetScroll;
                return true;
            }
        }
        if (!StatusEffectHudState.panelCollapsed()
            && inside(mouseX, mouseY, panelX, panelY, PANEL_WIDTH, PANEL_HEADER_HEIGHT)) {
            draggingPanel = true;
            panelDragMoved = false;
            startDrag(mouseX, mouseY, panelX, panelY);
            return true;
        }
        int contentY = panelY + PANEL_HEADER_HEIGHT + TARGET_LIST_HEIGHT + 6
            - (int) Math.round(panelScroll);
        boolean settingsHit = inside(mouseX, mouseY, panelX,
            panelY + PANEL_HEADER_HEIGHT + TARGET_LIST_HEIGHT,
            PANEL_WIDTH, panelBodyHeight());
        if (!StatusEffectHudState.panelCollapsed() && settingsHit) {
            int slider = sliderControlAt(mouseX, mouseY, panelX, contentY);
            if (slider != 0) {
                clearAdjustmentFieldFocus();
                draggingAdjustmentSlider = slider;
                applySliderValue(slider, mouseX, panelX);
                return true;
            }
        }
        if (settingsHit && !(selectedScene == Scene.INVENTORY
            && selectedInventoryTarget == InventoryTarget.LAYOUT)) {
            boolean itemDetail = selectedScene == Scene.INVENTORY
                && selectedInventoryTarget == InventoryTarget.ITEM_DETAIL;
            if (!StatusEffectHudState.panelCollapsed() && itemDetail) {
                int half = (PANEL_WIDTH - 32) / 2;
                if (inside(mouseX, mouseY, panelX + 14, contentY + 148, half, 22)) {
                    StatusEffectHudState.setItemDetailAutoWidth(
                        !StatusEffectHudState.itemDetailAutoWidth());
                    return true;
                }
                if (inside(mouseX, mouseY, panelX + 18 + half, contentY + 148, half, 22)) {
                    StatusEffectHudState.setItemDetailAutoHeight(
                        !StatusEffectHudState.itemDetailAutoHeight());
                    return true;
                }
            }
            int coordinateY = coordinateSectionY(contentY);
            int snapY = coordinateY + 42;
            int halfWidth = (PANEL_WIDTH - 32) / 2;
            if (!StatusEffectHudState.panelCollapsed()
                && inside(mouseX, mouseY, panelX + 14, snapY, halfWidth, 22)) {
                StatusEffectHudState.setSnapX(!StatusEffectHudState.snapX());
                return true;
            }
            if (!StatusEffectHudState.panelCollapsed()
                && inside(mouseX, mouseY, panelX + 18 + halfWidth, snapY, halfWidth, 22)) {
                StatusEffectHudState.setSnapY(!StatusEffectHudState.snapY());
                return true;
            }
            if (!StatusEffectHudState.panelCollapsed()
                && inside(mouseX, mouseY, panelX + 14, coordinateY + 70,
                    PANEL_WIDTH - 28, 24)) {
                resetActivePosition(); return true;
            }
        }

        int panelWidth = StatusEffectHudState.panelCollapsed() ? COLLAPSED_SIZE : PANEL_WIDTH;
        int panelHeight = StatusEffectHudState.panelCollapsed() ? COLLAPSED_SIZE : panelHeight();
        if (inside(mouseX, mouseY, panelX, panelY, panelWidth, panelHeight)) return true;

        HudTarget clicked = hoveredTarget(mouseX, mouseY);
        int handle = resizeHandleAt(mouseX, mouseY, activePreview());
        if (handle != 0) {
            materializeItemDetailSize();
            resizingPreview = true;
            draggingPreview = false;
            resizeEdge = handle;
            resizeStartScale = activeScalePercent();
            resizeStartBounds = activePreview();
            startDrag(mouseX, mouseY, resizeStartBounds.x(), resizeStartBounds.y());
            return true;
        }
        if (clicked != null) {
            selectTarget(clicked);
            StatusEffectHudRenderer.Bounds selected = activePreview();
            xField.setFocused(false);
            yField.setFocused(false);
            draggingPreview = true;
            startDrag(mouseX, mouseY, selected.x(), selected.y());
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void selectTarget(HudTarget target) {
        selectedTarget = target;
        draggingPreview = false;
        clearSnapGuides();
        resetPanelScroll();
        if (xField != null) xField.setFocused(false);
        if (yField != null) yField.setFocused(false);
    }

    private void startDrag(double mouseX, double mouseY, int x, int y) {
        dragStartX = (int) mouseX;
        dragStartY = (int) mouseY;
        originX = x;
        originY = y;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button != 0) return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
        if (draggingAdjustmentSlider != 0) {
            int[] panel = panelPosition();
            applySliderValue(draggingAdjustmentSlider, mouseX, panel[0]);
            return true;
        }
        if (draggingTargetScrollbar) {
            int content = targetContentHeight();
            int thumbHeight = Math.max(18,
                TARGET_LIST_HEIGHT * TARGET_LIST_HEIGHT / Math.max(TARGET_LIST_HEIGHT + 1, content));
            double delta = mouseY - targetScrollbarDragStartY;
            delta *= Math.max(0, content - TARGET_LIST_HEIGHT)
                / (double) Math.max(1, TARGET_LIST_HEIGHT - thumbHeight);
            targetTargetScroll = targetScrollbarDragStartScroll + delta;
            clampTargetScroll();
            return true;
        }
        if (draggingPanelScrollbar) {
            int visible = panelBodyHeight();
            int content = panelContentHeight();
            int thumbHeight = Math.max(24,
                visible * visible / Math.max(visible + 1, content));
            double delta = mouseY - panelScrollbarDragStartY;
            delta *= Math.max(0, content - visible)
                / (double) Math.max(1, visible - thumbHeight);
            panelTargetScroll = panelScrollbarDragStartScroll + delta;
            clampPanelScroll();
            return true;
        }
        int nextX = originX + (int) mouseX - dragStartX;
        int nextY = originY + (int) mouseY - dragStartY;
        if (draggingPanel) {
            clearSnapGuides();
            if (Math.abs((int) mouseX - dragStartX) > 2
                || Math.abs((int) mouseY - dragStartY) > 2) panelDragMoved = true;
            int panelWidth = StatusEffectHudState.panelCollapsed()
                ? COLLAPSED_SIZE : PANEL_WIDTH;
            int panelHeight = StatusEffectHudState.panelCollapsed()
                ? COLLAPSED_SIZE : panelHeight();
            nextX = Math.max(2, Math.min(width - panelWidth - 2, nextX));
            nextY = Math.max(2, Math.min(height - panelHeight - 2, nextY));
            StatusEffectHudState.setPanelPosition(nextX, nextY);
            return true;
        }
        if (draggingPreview) {
            StatusEffectHudRenderer.Bounds selected = activePreview();
            nextX = Math.max(2, Math.min(width - selected.width() - 2, nextX));
            nextY = Math.max(2, Math.min(height - selected.height() - 2, nextY));
            SnapResult snappedX = StatusEffectHudState.snapX()
                ? snap(nextX, selected.width(), width) : SnapResult.none(nextX);
            SnapResult snappedY = StatusEffectHudState.snapY()
                ? snap(nextY, selected.height(), height) : SnapResult.none(nextY);
            if ((selectedTarget == HudTarget.EFFECTS || selectedTarget == HudTarget.INTRO)
                && staminaPreview.width() > 0) {
                if (StatusEffectHudState.snapX()) {
                    int staminaCenter = staminaPreview.x() + staminaPreview.width() / 2;
                    snappedX = snapToValue(snappedX,
                        staminaCenter - selected.width() / 2, staminaCenter);
                }
                if (StatusEffectHudState.snapY()) {
                    int belowStamina = staminaPreview.y() + staminaPreview.height() + 5;
                    snappedY = snapToValue(snappedY, belowStamina, belowStamina);
                }
            }
            snapGuideX = snappedX.guide();
            snapGuideY = snappedY.guide();
            setActivePosition(snappedX.value(), snappedY.value());
            return true;
        }
        if (resizingPreview && resizeStartBounds.width() > 0 && resizeStartBounds.height() > 0) {
            double dx = mouseX - dragStartX;
            double dy = mouseY - dragStartY;
            if (selectedScene == Scene.INVENTORY
                && selectedInventoryTarget == InventoryTarget.ITEM_DETAIL) {
                if (resizeEdge == 1 || resizeEdge == 3) {
                    StatusEffectHudState.setItemDetailWidth(
                        resizeStartBounds.width() + (int) Math.round(dx));
                }
                if (resizeEdge == 2 || resizeEdge == 3) {
                    StatusEffectHudState.setItemDetailHeight(
                        resizeStartBounds.height() + (int) Math.round(dy));
                }
                return true;
            }
            double ratio = switch (resizeEdge) {
                case 1 -> (resizeStartBounds.width() + dx) / resizeStartBounds.width();
                case 2 -> (resizeStartBounds.height() + dy) / resizeStartBounds.height();
                default -> Math.min(
                    (resizeStartBounds.width() + dx) / resizeStartBounds.width(),
                    (resizeStartBounds.height() + dy) / resizeStartBounds.height());
            };
            setActiveScalePercent((int) Math.round(resizeStartScale * ratio));
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    private static SnapResult snapToValue(SnapResult current, int target, int guide) {
        return Math.abs(current.value() - target) <= 10
            ? new SnapResult(target, guide) : current;
    }

    private static SnapResult snap(int value, int size, int screenSize) {
        int[] targets = {2, (screenSize - size) / 2, Math.max(2, screenSize - size - 2)};
        int[] guides = {2, screenSize / 2, Math.max(2, screenSize - 2)};
        for (int index = 0; index < targets.length; index++) {
            if (Math.abs(value - targets[index]) <= 8) {
                return new SnapResult(targets[index], guides[index]);
            }
        }
        return SnapResult.none(value);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (draggingAdjustmentSlider != 0 && button == 0) {
            draggingAdjustmentSlider = 0;
            commitCurrentSnapshot();
            return true;
        }
        if (draggingTargetScrollbar && button == 0) {
            draggingTargetScrollbar = false;
            return true;
        }
        if (draggingPanelScrollbar && button == 0) {
            draggingPanelScrollbar = false;
            return true;
        }
        if (draggingPanel && button == 0) {
            boolean expand = StatusEffectHudState.panelCollapsed() && !panelDragMoved;
            draggingPanel = false;
            panelDragMoved = false;
            if (expand) {
                StatusEffectHudState.setPanelCollapsed(false);
                clampPanelScroll();
            }
            commitCurrentSnapshot();
            return true;
        }
        if (draggingPreview && button == 0) {
            draggingPreview = false;
            clearSnapGuides();
            commitCurrentSnapshot();
            return true;
        }
        if (resizingPreview && button == 0) {
            resizingPreview = false;
            resizeEdge = 0;
            commitCurrentSnapshot();
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY,
                                 double scrollX, double scrollY) {
        if (!StatusEffectHudState.panelCollapsed()) {
            int[] panel = panelPosition();
            int listY = panel[1] + PANEL_HEADER_HEIGHT;
            if (inside(mouseX, mouseY, panel[0], listY, PANEL_WIDTH, TARGET_LIST_HEIGHT)) {
                targetTargetScroll -= scrollY * 24.0D;
                clampTargetScroll();
                return true;
            }
            int bodyY = listY + TARGET_LIST_HEIGHT;
            if (inside(mouseX, mouseY, panel[0], bodyY, PANEL_WIDTH, panelBodyHeight())) {
                panelTargetScroll -= scrollY * 30.0D;
                clampPanelScroll();
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private void drawSnapGuides(GuiGraphics graphics) {
        if (snapGuideX != NO_GUIDE) {
            for (int y = 0; y < height; y += 9) {
                graphics.fill(snapGuideX, y, snapGuideX + 1, Math.min(height, y + 6),
                    0xD08BE7FF);
            }
        }
        if (snapGuideY != NO_GUIDE) {
            for (int x = 0; x < width; x += 9) {
                graphics.fill(x, snapGuideY, Math.min(width, x + 6), snapGuideY + 1,
                    0xD0FFE08A);
            }
        }
    }

    private void clearSnapGuides() {
        snapGuideX = NO_GUIDE;
        snapGuideY = NO_GUIDE;
    }

    private void movePreview(int deltaX, int deltaY) {
        StatusEffectHudRenderer.Bounds selected = activePreview();
        if (selected.width() <= 0 || selected.height() <= 0) return;
        int nextX = Math.max(2, Math.min(width - selected.width() - 2,
            selected.x() + deltaX));
        int nextY = Math.max(2, Math.min(height - selected.height() - 2,
            selected.y() + deltaY));
        clearSnapGuides();
        setActivePosition(nextX, nextY);
        commitCurrentSnapshot();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        EditBox focused = focusedInputField();
        if (focused != null && focused.keyPressed(keyCode, scanCode, modifiers)) return true;
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) { onClose(); return true; }
        if (Screen.hasControlDown() && keyCode == GLFW.GLFW_KEY_Z) { undo(); return true; }
        if (Screen.hasControlDown() && keyCode == GLFW.GLFW_KEY_Y) { redo(); return true; }
        if (!xField.isFocused() && !yField.isFocused()
            && !primaryField.isFocused() && !secondaryField.isFocused()) {
            int step = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0 ? 10 : 1;
            int deltaX = keyCode == GLFW.GLFW_KEY_LEFT ? -step
                : keyCode == GLFW.GLFW_KEY_RIGHT ? step : 0;
            int deltaY = keyCode == GLFW.GLFW_KEY_UP ? -step
                : keyCode == GLFW.GLFW_KEY_DOWN ? step : 0;
            if (deltaX != 0 || deltaY != 0) {
                movePreview(deltaX, deltaY);
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private StatusEffectHudRenderer.Bounds renderInventoryEffectPreview(GuiGraphics graphics) {
        float scale = StatusEffectHudState.inventoryEffectScale();
        float opacity = StatusEffectHudState.inventoryEffectOpacity();
        int cell = Math.max(12, Math.round(24 * scale));
        int gap = Math.max(1, Math.round(2 * scale));
        int previewWidth = 6 * cell + 5 * gap;
        int previewHeight = 2 * cell + gap;
        int[] position = StatusEffectHudState.inventoryEffectPosition(
            Math.max(12, width / 2 - previewWidth / 2),
            Math.max(52, height / 2 - previewHeight / 2),
            width, height, previewWidth, previewHeight);
        int panelPadding = 10;
        int panelAlpha = Math.max(18, Math.round(180 * opacity));
        graphics.fill(position[0] - panelPadding, position[1] - 24,
            position[0] + previewWidth + panelPadding,
            position[1] + previewHeight + panelPadding,
            (panelAlpha << 24) | 0x00101719);
        graphics.drawString(font, Component.translatable(
            "status_effect_hud.xero_delta.inventory_effects"),
            position[0], position[1] - 17,
            (Math.max(26, Math.round(255 * opacity)) << 24) | 0x00E8EEEB, false);
        for (int index = 0; index < 12; index++) {
            int x = position[0] + index % 6 * (cell + gap);
            int y = position[1] + index / 6 * (cell + gap);
            int alpha = Math.max(20, Math.round(205 * opacity));
            graphics.fill(x, y, x + cell, y + cell,
                (alpha << 24) | (index % 3 == 0 ? 0x0037282C : 0x00192428));
            graphics.renderOutline(x, y, cell, cell,
                (Math.max(24, Math.round(255 * opacity)) << 24)
                    | (index % 3 == 0 ? 0x00E56A62 : 0x0067D99F));
            MobEffectInstance sample = new MobEffectInstance(
                index % 3 == 0 ? MobEffects.POISON : MobEffects.REGENERATION,
                200, index % 4 == 0 ? 1 : 0);
            StatusEffectHudRenderer.drawEffectIconScaled(
                graphics, minecraft, sample, x, y, cell, opacity);
        }
        return new StatusEffectHudRenderer.Bounds(position[0], position[1],
            previewWidth, previewHeight);
    }

    private StatusEffectHudRenderer.Bounds renderInventoryLayoutPreview(GuiGraphics graphics) {
        // Keep this preview in the same logical coordinate space as the real
        // embedded Delta inventory. The old preview used a separate 436x560
        // mock layout, so changing the cell setting only scaled that mock
        // surface and never reflected the actual grid geometry.
        final int logicalWidth = DeltaInventoryLayout.WIDTH;
        final int logicalHeight = DeltaInventoryLayout.HEIGHT;
        int[] panel = panelPosition();
        int available = panel[0] > width / 2
            ? panel[0] - 12 : width - panel[0] - PANEL_WIDTH - 12;
        float requestedScale = StatusEffectHudState.inventoryLayoutScale();
        float fit = DeltaInventoryLayout.fitScale(
            Math.max(1, available), Math.max(1, height - 42),
            InventoryLayoutScale.contentFactor(requestedScale));
        float scale = Math.max(0.35F,
            Math.min(DeltaInventoryLayout.MAX_SCALE, fit));
        int previewWidth = Math.max(1, Math.round(logicalWidth * scale));
        int previewHeight = Math.max(1, Math.round(logicalHeight * scale));
        int x = panel[0] > width / 2
            ? Math.max(8, panel[0] - previewWidth - 12)
            : Math.min(Math.max(8, panel[0] + PANEL_WIDTH + 12),
                Math.max(8, width - previewWidth - 8));
        int y = Math.max(34, (height - previewHeight) / 2);

        ItemStack helmet = new ItemStack(Items.IRON_HELMET);
        ItemStack chest = new ItemStack(Items.IRON_CHESTPLATE);
        ItemStack primary = new ItemStack(Items.IRON_SWORD);
        ItemStack secondary = new ItemStack(Items.BOW);
        ItemStack pistol = new ItemStack(Items.IRON_PICKAXE);
        if (minecraft != null && minecraft.player != null) {
            helmet = minecraft.player.getItemBySlot(EquipmentSlot.HEAD).copy();
            chest = minecraft.player.getItemBySlot(EquipmentSlot.CHEST).copy();
            primary = minecraft.player.getInventory().getItem(0).copy();
            secondary = minecraft.player.getInventory().getItem(1).copy();
            pistol = minecraft.player.getInventory().getItem(2).copy();
        }
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        int cell = InventoryLayoutScale.cellSize(18, requestedScale);
        int gap = 2;
        graphics.fill(0, 0, logicalWidth, logicalHeight, 0xF0080D0F);
        graphics.renderOutline(0, 0, logicalWidth, logicalHeight, 0xFF52615F);
        graphics.fill(0, 0, logicalWidth, DeltaInventoryLayout.HEADER, 0xF00C1416);
        graphics.drawString(font, Component.translatable(
            "status_effect_hud.xero_delta.inventory_preview"), 10, 5, 0xFFE5EAE8, false);
        drawPreviewCard(graphics, helmet, 188, 34, 36, 36, "");
        drawPreviewCard(graphics, chest, 188, 74, 36, 36, "");
        drawPreviewCard(graphics, primary, 152, 114, 72, 36, "1");
        drawPreviewCard(graphics, secondary, 152, 154, 72, 36, "2");
        drawPreviewCard(graphics, pistol, 152, 194, 72, 36, "3");

        // Match the real panel's storage column: x=226, width=174, with
        // sections stacked from the viewport top and separated by 6px.
        int sectionX = 226;
        int sectionWidth = 174;
        int sectionY = 34;
        PreviewStorage chestRig = previewStorage("chest_rig", 4, 6);
        PreviewStorage backpack = previewStorage("backpack", 5, 9);
        PreviewStorage cardHolder = previewStorage("card_holder", 3, 3);
        PreviewStorage safetyBox = previewSafetyBox(3, 3);
        int chestRigHeight = 22 + Math.max(36, chestRig.visualHeight(cell, gap)) + 6;
        // The storage column is a scroll viewport in the live screen. Keep
        // the same clip here so the default 150% layout shows exactly the
        // portion that can be seen before scrolling.
        graphics.enableScissor(sectionX, sectionY, sectionX + sectionWidth,
            logicalHeight - 6);
        drawPreviewSection(graphics, "status_effect_hud.xero_delta.chest_rig_preview",
            sectionX, sectionY, sectionWidth, chestRigHeight, chestRig, cell, gap,
            true, false);
        sectionY += chestRigHeight + 6;
        int pocketsHeight = 46;
        drawPreviewSection(graphics, "status_effect_hud.xero_delta.pockets_preview",
            sectionX, sectionY, sectionWidth, pocketsHeight,
            new PreviewStorage(5, 1, null, true), cell, gap, false, false);
        sectionY += pocketsHeight + 6;
        int backpackHeight = 22 + Math.max(36, backpack.visualHeight(cell, gap)) + 6;
        drawPreviewSection(graphics, "status_effect_hud.xero_delta.backpack_preview",
            sectionX, sectionY, sectionWidth, backpackHeight, backpack, cell, gap,
            true, false);
        sectionY += backpackHeight + 6;
        int cardHolderHeight = 22 + Math.max(36, cardHolder.visualHeight(cell, gap)) + 6;
        drawPreviewSection(graphics, "status_effect_hud.xero_delta.card_holder_preview",
            sectionX, sectionY, sectionWidth, cardHolderHeight, cardHolder, cell, gap,
            false, true);
        sectionY += cardHolderHeight + 6;
        int safetyHeight = 22 + Math.max(42, safetyBox.visualHeight(cell, gap)) + 6;
        drawPreviewSection(graphics, "status_effect_hud.xero_delta.safety_box_preview",
            sectionX, sectionY, sectionWidth, safetyHeight, safetyBox, cell, gap,
            true, false);
        graphics.disableScissor();
        graphics.pose().popPose();
        return new StatusEffectHudRenderer.Bounds(x, y, previewWidth, previewHeight);
    }

    private void drawPreviewCard(GuiGraphics graphics, ItemStack stack, int x, int y,
                                 int width, int height, String key) {
        graphics.fill(x, y, x + width, y + height, 0xE6253033);
        graphics.renderOutline(x, y, width, height, 0xFF64777B);
        GridItemRenderer.renderSizedItem(graphics, font, stack, x + 1, y + 1,
            width - 2, height - 2, false, false, false);
        LoadoutLabelRenderer.render(graphics, font, stack, key, x, y, width, height);
    }

    private void drawPreviewSection(GuiGraphics graphics, String titleKey, int x, int y,
                                    int width, int height, PreviewStorage storage,
                                    int cell, int gap, boolean selector, boolean locked) {
        graphics.fill(x, y, x + width, y + height, 0xE5141D20);
        graphics.renderOutline(x, y, width, height, 0xFF52615F);
        graphics.drawString(font, Component.translatable(titleKey), x + 6, y + 5,
            0xFFE5EAE8, false);
        int gridTop = y + 22;
        int selectorSize = titleKey.endsWith("safety_box_preview") ? 42 : 36;
        if (selector || locked) {
            graphics.fill(x + 6, gridTop, x + 6 + selectorSize,
                gridTop + selectorSize, 0xE6253033);
            graphics.renderOutline(x + 6, gridTop, selectorSize, selectorSize,
                0xFF52615F);
        }
        int gridX = x + ((selector || locked) ? 52 : 6);
        if ("status_effect_hud.xero_delta.safety_box_preview".equals(titleKey)) {
            gridX = x + 58;
        }
        if (storage.regions() != null) {
            PackRegionLayout layout = new PackRegionLayout(storage.regions(), cell, gap);
            for (PackRegionLayout.Rect region : layout.regionBounds()) {
                for (int row = 0; row < region.height(); row += cell) {
                    for (int column = 0; column < region.width(); column += cell) {
                        int left = gridX + region.x() + column;
                        int top = gridTop + region.y() + row;
                        graphics.fill(left, top, left + cell, top + cell, 0xF00B1214);
                        graphics.renderOutline(left, top, cell, cell, 0xFF52615F);
                    }
                }
                graphics.renderOutline(gridX + region.x(), gridTop + region.y(),
                    region.width(), region.height(), 0xFF52615F);
            }
        } else {
            for (int row = 0; row < storage.rows(); row++) {
                for (int column = 0; column < storage.columns(); column++) {
                    int spacing = storage.separatedCells() ? gap : 0;
                    int left = gridX + column * (cell + spacing);
                    int top = gridTop + row * (cell + spacing);
                    graphics.fill(left, top, left + cell, top + cell, 0xF00B1214);
                    graphics.renderOutline(left, top, cell, cell, 0xFF52615F);
                }
            }
        }
    }

    private PreviewStorage previewStorage(String identifier, int fallbackColumns,
                                          int fallbackRows) {
        ItemStack equipped = previewAccessory(identifier);
        if (equipped.getItem() instanceof DeltaPackItem pack
            && identifier.equals(pack.slotIdentifier())) {
            List<PackRegionLayout.LogicalRegion> regions = pack.regions().stream()
                .map(region -> new PackRegionLayout.LogicalRegion(
                    region.x(), region.y(), region.width(), region.height()))
                .toList();
            return new PreviewStorage(pack.gridWidth(), pack.gridHeight(), regions, false);
        }
        if ("backpack".equals(identifier) && !equipped.isEmpty()) {
            try {
                var handler = equipped.getCapability(
                    net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.ITEM);
                if (handler != null && handler.getSlots() > 0) {
                    int columns = Math.min(5, handler.getSlots());
                    int rows = (handler.getSlots() + columns - 1) / columns;
                    return new PreviewStorage(columns, rows, null, false);
                }
            } catch (RuntimeException ignored) {
            }
        }
        return new PreviewStorage(fallbackColumns, fallbackRows, null, false);
    }

    private PreviewStorage previewSafetyBox(int fallbackColumns, int fallbackRows) {
        ItemStack equipped = previewAccessory("safety_box");
        if (equipped.getItem() instanceof SafetyBoxItem box) {
            return new PreviewStorage(box.getGridWidth(), box.getGridHeight(), null, false);
        }
        return new PreviewStorage(fallbackColumns, fallbackRows, null, false);
    }

    private ItemStack previewAccessory(String identifier) {
        if (minecraft == null || minecraft.player == null) return ItemStack.EMPTY;
        try {
            return CuriosApi.getCuriosInventory(minecraft.player)
                .flatMap(handler -> handler.getStacksHandler(identifier))
                .filter(handler -> handler.getSlots() > 0)
                .map(handler -> handler.getStacks().getStackInSlot(0).copy())
                .orElse(ItemStack.EMPTY);
        } catch (RuntimeException ignored) {
            return ItemStack.EMPTY;
        }
    }

    private record PreviewStorage(int columns, int rows,
                                  List<PackRegionLayout.LogicalRegion> regions,
                                  boolean separatedCells) {
        private int visualHeight(int cell, int gap) {
            if (regions != null) return new PackRegionLayout(regions, cell, gap).height();
            return rows * cell + (separatedCells ? Math.max(0, rows - 1) * gap : 0);
        }
    }

    private StatusEffectHudRenderer.Bounds renderItemDetailPreview(GuiGraphics graphics) {
        int previewWidth = ItemDetailOverlay.previewWidth(this);
        int previewHeight = ItemDetailOverlay.previewHeight(this);
        int[] position = StatusEffectHudState.itemDetailPosition(
            Math.max(8, (width - previewWidth) / 2),
            Math.max(36, (height - previewHeight) / 2),
            width, height, previewWidth, previewHeight);
        int x = position[0], y = position[1];
        graphics.fill(x, y, x + previewWidth, y + previewHeight, 0xF40A1114);
        graphics.renderOutline(x, y, previewWidth, previewHeight, 0xFF667577);
        graphics.fill(x, y, x + previewWidth, y + 3, 0xFFE2B94F);
        graphics.fill(x + 1, y + 31, x + previewWidth - 1, y + 32, 0xFF2A3639);
        graphics.drawString(font, Component.translatable(
            "status_effect_hud.xero_delta.item_detail_preview"),
            x + 12, y + 11, 0xFFF2F5F4, false);
        int previewBottom = Math.min(y + previewHeight - 12, y + 116);
        graphics.fill(x + 10, y + 42, x + previewWidth - 10,
            Math.max(y + 43, previewBottom), 0x9A0E171A);
        return new StatusEffectHudRenderer.Bounds(x, y, previewWidth, previewHeight);
    }

    private StatusEffectHudRenderer.Bounds activePreview() {
        if (selectedScene == Scene.INVENTORY) {
            return switch (selectedInventoryTarget) {
                case LAYOUT -> inventoryLayoutPreview;
                case ITEM_DETAIL -> itemDetailPreview;
                default -> inventoryEffectPreview;
            };
        }
        return switch (selectedTarget) {
            case HEALTH -> healthPreview;
            case INTRO -> introPreview;
            case STAMINA -> staminaPreview;
            case DOWNED -> downedPreview;
            case RESCUE -> rescuePreview;
            case CONTEXT -> contextPreview;
            default -> effectPreview;
        };
    }

    private HudTarget hoveredTarget(double mouseX, double mouseY) {
        if (selectedScene == Scene.INVENTORY) {
            if (selectedInventoryTarget == InventoryTarget.LAYOUT) return null;
            StatusEffectHudRenderer.Bounds preview = selectedInventoryTarget == InventoryTarget.ITEM_DETAIL
                ? itemDetailPreview : inventoryEffectPreview;
            return preview.contains(mouseX, mouseY) ? HudTarget.EFFECTS : null;
        }
        if (introPreview.contains(mouseX, mouseY)) return HudTarget.INTRO;
        if (staminaPreview.contains(mouseX, mouseY)) return HudTarget.STAMINA;
        if (healthPreview.contains(mouseX, mouseY)) return HudTarget.HEALTH;
        if (downedPreview.contains(mouseX, mouseY)) return HudTarget.DOWNED;
        if (rescuePreview.contains(mouseX, mouseY)) return HudTarget.RESCUE;
        if (contextPreview.contains(mouseX, mouseY)) return HudTarget.CONTEXT;
        if (effectPreview.contains(mouseX, mouseY)) return HudTarget.EFFECTS;
        return null;
    }

    private int activeScalePercent() {
        if (selectedScene == Scene.INVENTORY) {
            return switch (selectedInventoryTarget) {
                case LAYOUT -> StatusEffectHudState.inventoryLayoutScalePercent();
                case ITEM_DETAIL -> StatusEffectHudState.itemDetailWidth();
                default -> StatusEffectHudState.inventoryEffectScalePercent();
            };
        }
        return switch (selectedTarget) {
            case HEALTH -> StatusEffectHudState.healthScalePercent();
            case INTRO -> StatusEffectHudState.introScalePercent();
            case STAMINA -> StatusEffectHudState.staminaScalePercent();
            case DOWNED -> StatusEffectHudState.downedScalePercent();
            case RESCUE -> StatusEffectHudState.rescueScalePercent();
            case CONTEXT -> StatusEffectHudState.contextScalePercent();
            default -> StatusEffectHudState.scalePercent();
        };
    }

    private void adjustActiveScale(int direction) {
        if (selectedScene == Scene.INVENTORY) {
            if (selectedInventoryTarget == InventoryTarget.LAYOUT) {
                StatusEffectHudState.adjustInventoryLayoutScale(direction);
            } else if (selectedInventoryTarget == InventoryTarget.ITEM_DETAIL) {
                StatusEffectHudState.adjustItemDetailScale(direction);
            } else {
                StatusEffectHudState.adjustInventoryEffectScale(direction);
            }
            return;
        }
        switch (selectedTarget) {
            case HEALTH -> StatusEffectHudState.adjustHealthScale(direction);
            case INTRO -> StatusEffectHudState.adjustIntroScale(direction);
            case STAMINA -> StatusEffectHudState.adjustStaminaScale(direction);
            case DOWNED -> StatusEffectHudState.adjustDownedScale(direction);
            case RESCUE -> StatusEffectHudState.adjustRescueScale(direction);
            case CONTEXT -> StatusEffectHudState.adjustContextScale(direction);
            default -> StatusEffectHudState.adjustScale(direction);
        }
    }

    private void setActiveScalePercent(int value) {
        if (selectedScene == Scene.INVENTORY) {
            if (selectedInventoryTarget == InventoryTarget.LAYOUT) {
                StatusEffectHudState.setInventoryLayoutScalePercent(value);
            } else if (selectedInventoryTarget == InventoryTarget.ITEM_DETAIL) {
                materializeItemDetailSize();
                StatusEffectHudState.setItemDetailWidth(value);
            } else {
                StatusEffectHudState.setInventoryEffectScalePercent(value);
            }
            return;
        }
        int clamped = Math.max(50, Math.min(200, value));
        switch (selectedTarget) {
            case HEALTH -> StatusEffectHudState.setHealthScalePercent(clamped);
            case INTRO -> StatusEffectHudState.setIntroScalePercent(clamped);
            case STAMINA -> StatusEffectHudState.setStaminaScalePercent(clamped);
            case DOWNED -> StatusEffectHudState.setDownedScalePercent(clamped);
            case RESCUE -> StatusEffectHudState.setRescueScalePercent(clamped);
            case CONTEXT -> StatusEffectHudState.setContextScalePercent(clamped);
            default -> StatusEffectHudState.setScalePercent(clamped);
        }
    }

    private void drawResizeHandles(GuiGraphics graphics, StatusEffectHudRenderer.Bounds bounds,
                                   int mouseX, int mouseY) {
        int rightX = bounds.x() + bounds.width() - 2;
        int rightY = bounds.y() + bounds.height() / 2 - 7;
        int bottomX = bounds.x() + bounds.width() / 2 - 7;
        int bottomY = bounds.y() + bounds.height() - 2;
        int cornerX = bounds.x() + bounds.width() - 3;
        int cornerY = bounds.y() + bounds.height() - 3;
        drawResizeHandle(graphics, rightX, rightY, 5, 14, 1, mouseX, mouseY);
        drawResizeHandle(graphics, bottomX, bottomY, 14, 5, 2, mouseX, mouseY);
        drawResizeHandle(graphics, cornerX, cornerY, 6, 6, 3, mouseX, mouseY);
    }

    private void drawResizeHandle(GuiGraphics graphics, int x, int y, int width, int height,
                                  int edge, int mouseX, int mouseY) {
        boolean active = resizingPreview && resizeEdge == edge;
        boolean hovered = inside(mouseX, mouseY, x - 2, y - 2, width + 4, height + 4);
        int color = active ? 0xFF65D6AD : hovered ? 0xFFFFFFFF : 0xFFD5DDDA;
        graphics.fill(x, y, x + width, y + height, color);
        border(graphics, x, y, width, height, active ? 0xFFFFFFFF : 0xFF60747A);
    }

    private int resizeHandleAt(double mouseX, double mouseY,
                               StatusEffectHudRenderer.Bounds bounds) {
        if (bounds.width() <= 0 || bounds.height() <= 0) return 0;
        int rightX = bounds.x() + bounds.width() - 2;
        int rightY = bounds.y() + bounds.height() / 2 - 7;
        int bottomX = bounds.x() + bounds.width() / 2 - 7;
        int bottomY = bounds.y() + bounds.height() - 2;
        int cornerX = bounds.x() + bounds.width() - 3;
        int cornerY = bounds.y() + bounds.height() - 3;
        if (inside(mouseX, mouseY, cornerX - 2, cornerY - 2, 10, 10)) return 3;
        if (inside(mouseX, mouseY, rightX - 2, rightY - 2, 9, 18)) return 1;
        if (inside(mouseX, mouseY, bottomX - 2, bottomY - 2, 18, 9)) return 2;
        return 0;
    }

    private int activeOpacityPercent() {
        if (selectedScene == Scene.INVENTORY) {
            return switch (selectedInventoryTarget) {
                case LAYOUT -> StatusEffectHudState.inventoryLabelScalePercent();
                case ITEM_DETAIL -> StatusEffectHudState.itemDetailHeight();
                default -> StatusEffectHudState.inventoryEffectOpacityPercent();
            };
        }
        return switch (selectedTarget) {
            case HEALTH -> StatusEffectHudState.healthOpacityPercent();
            case INTRO -> StatusEffectHudState.introOpacityPercent();
            case STAMINA -> StatusEffectHudState.staminaOpacityPercent();
            case DOWNED -> StatusEffectHudState.downedOpacityPercent();
            case RESCUE -> StatusEffectHudState.rescueOpacityPercent();
            case CONTEXT -> StatusEffectHudState.contextOpacityPercent();
            default -> StatusEffectHudState.opacityPercent();
        };
    }

    private ValueRange primaryRange() {
        if (selectedScene == Scene.INVENTORY) {
            return switch (selectedInventoryTarget) {
                case LAYOUT -> new ValueRange(60, 500, 5);
                case ITEM_DETAIL -> new ValueRange(ItemDetailLayout.MIN_MANUAL_WIDTH,
                    ItemDetailLayout.MAX_MANUAL_WIDTH, 5);
                default -> new ValueRange(50, 200, 5);
            };
        }
        return new ValueRange(50, 200, 5);
    }

    private ValueRange secondaryRange() {
        if (selectedScene == Scene.INVENTORY) {
            return switch (selectedInventoryTarget) {
                case LAYOUT -> new ValueRange(50, 160, 5);
                case ITEM_DETAIL -> new ValueRange(ItemDetailLayout.MIN_MANUAL_HEIGHT,
                    ItemDetailLayout.MAX_MANUAL_HEIGHT, 5);
                default -> new ValueRange(10, 100, 5);
            };
        }
        return new ValueRange(10, 100, 5);
    }

    private int coordinateSectionY(int contentY) {
        if (selectedScene == Scene.INVENTORY
            && selectedInventoryTarget == InventoryTarget.ITEM_DETAIL) return contentY + 180;
        if (selectedScene == Scene.HUD && selectedTarget == HudTarget.INTRO) {
            return contentY + 252;
        }
        if (selectedScene == Scene.HUD
            && (selectedTarget == HudTarget.HEALTH || selectedTarget == HudTarget.STAMINA)) {
            return contentY + 164;
        }
        return contentY + 108;
    }

    private int sliderControlAt(double mouseX, double mouseY, int panelX, int contentY) {
        if (selectedScene == Scene.INVENTORY
            && selectedInventoryTarget == InventoryTarget.LAYOUT
            && sliderHit(mouseX, mouseY, panelX, contentY + 104)) return 3;
        if (isItemDetailTarget()) {
            if (sliderHit(mouseX, mouseY, panelX, contentY + 104)) return 3;
            if (!StatusEffectHudState.itemDetailAutoWidth()
                && sliderHit(mouseX, mouseY, panelX, contentY + 16)) return 1;
            if (!StatusEffectHudState.itemDetailAutoHeight()
                && sliderHit(mouseX, mouseY, panelX, contentY + 60)) return 2;
        } else if (!isAutoItemDetail()) {
            if (sliderHit(mouseX, mouseY, panelX, contentY + 16)) return 1;
            if (sliderHit(mouseX, mouseY, panelX, contentY + 60)) return 2;
        }
        if (selectedScene != Scene.HUD) return 0;
        if (selectedTarget == HudTarget.INTRO) {
            if (sliderHit(mouseX, mouseY, panelX, contentY + 120)) return 3;
            if (sliderHit(mouseX, mouseY, panelX, contentY + 164)) return 4;
            if (sliderHit(mouseX, mouseY, panelX, contentY + 208)) return 5;
        } else if (selectedTarget == HudTarget.HEALTH
            && sliderHit(mouseX, mouseY, panelX, contentY + 120)) {
            return 3;
        }
        return 0;
    }

    private boolean sliderHit(double mouseX, double mouseY, int panelX, int rowY) {
        return inside(mouseX, mouseY, panelX + 10, rowY + 19,
            PANEL_WIDTH - 20, 20);
    }

    private void applySliderValue(int control, double mouseX, int panelX) {
        ValueRange range = switch (control) {
            case 1 -> primaryRange();
            case 2 -> secondaryRange();
            case 3 -> isItemDetailTarget()
                ? new ValueRange(50, 200, 5)
                : selectedTarget == HudTarget.INTRO
                    ? new ValueRange(50, 2_000, 50) : new ValueRange(50, 1_000, 50);
            case 4 -> new ValueRange(100, 5_000, 50);
            case 5 -> new ValueRange(50, 2_000, 50);
            default -> null;
        };
        if (range == null) return;
        int value = range.valueAt(mouseX, panelX + 14, PANEL_WIDTH - 28);
        switch (control) {
            case 1 -> setActiveScalePercent(value);
            case 2 -> setActiveOpacityPercent(value);
            case 3 -> {
                if (selectedScene == Scene.INVENTORY
                    && selectedInventoryTarget == InventoryTarget.LAYOUT) {
                    StatusEffectHudState.setInventoryLayoutMarginPercent(value);
                } else if (isItemDetailTarget()) {
                    StatusEffectHudState.setItemDetailScalePercent(value);
                } else if (selectedTarget == HudTarget.INTRO) {
                    StatusEffectHudState.setEffectIntroFadeInDurationMs(value);
                } else if (selectedTarget == HudTarget.HEALTH) {
                    StatusEffectHudState.setHealthAnimationDurationMs(value);
                }
            }
            case 4 -> StatusEffectHudState.setEffectIntroHoldDurationMs(value);
            case 5 -> StatusEffectHudState.setEffectIntroFadeOutDurationMs(value);
            default -> { }
        }
    }

    private void adjustActiveOpacity(int direction) {
        if (selectedScene == Scene.INVENTORY) {
            if (selectedInventoryTarget == InventoryTarget.LAYOUT) {
                StatusEffectHudState.adjustInventoryLabelScale(direction);
            } else if (selectedInventoryTarget == InventoryTarget.ITEM_DETAIL) {
                materializeItemDetailSize();
                StatusEffectHudState.adjustItemDetailHeight(direction);
            } else {
                StatusEffectHudState.adjustInventoryEffectOpacity(direction);
            }
            return;
        }
        switch (selectedTarget) {
            case HEALTH -> StatusEffectHudState.adjustHealthOpacity(direction);
            case INTRO -> StatusEffectHudState.adjustIntroOpacity(direction);
            case STAMINA -> StatusEffectHudState.adjustStaminaOpacity(direction);
            case DOWNED -> StatusEffectHudState.adjustDownedOpacity(direction);
            case RESCUE -> StatusEffectHudState.adjustRescueOpacity(direction);
            case CONTEXT -> StatusEffectHudState.adjustContextOpacity(direction);
            default -> StatusEffectHudState.adjustOpacity(direction);
        }
    }

    private void setActiveOpacityPercent(int value) {
        if (selectedScene == Scene.INVENTORY) {
            if (selectedInventoryTarget == InventoryTarget.LAYOUT) {
                StatusEffectHudState.setInventoryLabelScalePercent(value);
            } else if (selectedInventoryTarget == InventoryTarget.ITEM_DETAIL) {
                materializeItemDetailSize();
                StatusEffectHudState.setItemDetailHeight(value);
            } else {
                StatusEffectHudState.setInventoryEffectOpacityPercent(value);
            }
            return;
        }
        switch (selectedTarget) {
            case HEALTH -> StatusEffectHudState.setHealthOpacityPercent(value);
            case INTRO -> StatusEffectHudState.setIntroOpacityPercent(value);
            case STAMINA -> StatusEffectHudState.setStaminaOpacityPercent(value);
            case DOWNED -> StatusEffectHudState.setDownedOpacityPercent(value);
            case RESCUE -> StatusEffectHudState.setRescueOpacityPercent(value);
            case CONTEXT -> StatusEffectHudState.setContextOpacityPercent(value);
            default -> StatusEffectHudState.setOpacityPercent(value);
        }
    }
    private String activeAnimationLabel() {
        if (selectedTarget == HudTarget.STAMINA) return "0.20s";
        if (selectedTarget != HudTarget.HEALTH) return "--";
        return durationLabel(StatusEffectHudState.healthAnimationDurationMs());
    }

    private void adjustActiveAnimation(int direction) {
        if (selectedTarget == HudTarget.HEALTH) {
            StatusEffectHudState.adjustHealthAnimationDuration(direction);
        }
    }

    private void setActivePosition(int x, int y) {
        if (selectedScene == Scene.INVENTORY) {
            if (selectedInventoryTarget == InventoryTarget.EFFECTS) {
                StatusEffectHudState.setInventoryEffectPosition(x, y);
            } else if (selectedInventoryTarget == InventoryTarget.ITEM_DETAIL) {
                StatusEffectHudState.setItemDetailPosition(x, y);
            }
            return;
        }
        switch (selectedTarget) {
            case HEALTH -> StatusEffectHudState.setHealthPosition(x, y);
            case INTRO -> StatusEffectHudState.setIntroPosition(x, y);
            case STAMINA -> StatusEffectHudState.setStaminaPosition(x, y);
            case DOWNED -> StatusEffectHudState.setDownedPosition(x, y);
            case RESCUE -> StatusEffectHudState.setRescuePosition(x, y);
            case CONTEXT -> StatusEffectHudState.setContextPosition(x, y);
            default -> StatusEffectHudState.setPosition(x, y);
        }
    }

    private void resetActivePosition() {
        if (selectedScene == Scene.INVENTORY) {
            if (selectedInventoryTarget == InventoryTarget.EFFECTS) {
                StatusEffectHudState.resetInventoryEffectPosition();
            } else if (selectedInventoryTarget == InventoryTarget.ITEM_DETAIL) {
                StatusEffectHudState.resetItemDetailPosition();
            }
            return;
        }
        switch (selectedTarget) {
            case HEALTH -> StatusEffectHudState.resetHealthPosition();
            case INTRO -> StatusEffectHudState.resetIntroPosition();
            case STAMINA -> StatusEffectHudState.resetStaminaPosition();
            case DOWNED -> StatusEffectHudState.resetDownedPosition();
            case RESCUE -> StatusEffectHudState.resetRescuePosition();
            case CONTEXT -> StatusEffectHudState.resetContextPosition();
            default -> StatusEffectHudState.resetPosition();
        }
    }

    private record SnapResult(int value, int guide) {
        private static SnapResult none(int value) {
            return new SnapResult(value, NO_GUIDE);
        }
    }

    private record ValueRange(int minimum, int maximum, int step) {
        float fraction(int value) {
            if (maximum <= minimum) return 0.0F;
            return Math.max(0.0F, Math.min(1.0F,
                (value - minimum) / (float) (maximum - minimum)));
        }

        int valueAt(double mouseX, int trackX, int trackWidth) {
            double fraction = Math.max(0.0D, Math.min(1.0D,
                (mouseX - trackX) / Math.max(1.0D, trackWidth)));
            int raw = minimum + (int) Math.round(fraction * (maximum - minimum));
            int quantized = minimum + Math.round((raw - minimum) / (float) step) * step;
            return Math.max(minimum, Math.min(maximum, quantized));
        }
    }

    private enum HudTarget { EFFECTS, INTRO, STAMINA, HEALTH, DOWNED, RESCUE, CONTEXT }
    private enum Scene { HUD, INVENTORY }
    private enum InventoryTarget { EFFECTS, LAYOUT, ITEM_DETAIL }

    @Override public void tick() {
        transition.tick(minecraft);
        trackHistory();
    }
    @Override protected void renderBlurredBackground(float partialTick) {}
    @Override public void onClose() {
        commitCurrentSnapshot();
        if (!hasUnsavedChanges()) {
            transition.beginClose(() -> minecraft.setScreen(parent));
            return;
        }
        minecraft.setScreen(new ConfirmScreen(save -> {
            if (save) saveChanges();
            else StatusEffectHudState.restoreEditorSnapshot(savedSnapshot);
            minecraft.setScreen(parent);
        }, Component.translatable("status_effect_hud.xero_delta.save_prompt_title"),
            Component.translatable("status_effect_hud.xero_delta.save_prompt_message")));
    }
    @Override public boolean isPauseScreen() { return false; }

    private int[] panelPosition() {
        int panelWidth = StatusEffectHudState.panelCollapsed() ? COLLAPSED_SIZE : PANEL_WIDTH;
        int panelHeight = StatusEffectHudState.panelCollapsed() ? COLLAPSED_SIZE : panelHeight();
        return StatusEffectHudState.panelPosition(width - panelWidth - 24, 44,
            width, height, panelWidth, panelHeight);
    }

    private void drawTargetList(GuiGraphics graphics, int panelX, int listY,
                                int mouseX, int mouseY) {
        Material2Drawing.roundedRect(graphics, panelX + 1, listY,
            PANEL_WIDTH - 2, TARGET_LIST_HEIGHT, Material3Theme.RADIUS_EXTRA_SMALL,
            Material3Theme.SURFACE_CONTAINER_LOW);
        graphics.enableScissor(panelX + 1, listY, panelX + PANEL_WIDTH - 1,
            listY + TARGET_LIST_HEIGHT);
        int count = targetCount();
        int columns = selectedScene == Scene.HUD ? 4 : 3;
        int gap = 4;
        int innerWidth = PANEL_WIDTH - 16;
        int buttonWidth = (innerWidth - gap * (columns - 1)) / columns;
        for (int index = 0; index < count; index++) {
            int column = index % columns;
            int row = index / columns;
            int buttonX = panelX + 8 + column * (buttonWidth + gap);
            int rowY = listY + 3 + row * 22;
            drawTargetButton(graphics, buttonX, rowY, buttonWidth, 20,
                targetLabel(index), targetSelected(index), mouseX, mouseY);
        }
        graphics.disableScissor();
    }

    private void drawTargetScrollbar(GuiGraphics graphics, int panelX, int listY,
                                     int mouseX, int mouseY) {
        int content = targetContentHeight();
        if (content <= TARGET_LIST_HEIGHT) return;
        int x = panelX + PANEL_WIDTH - 5;
        graphics.fill(x, listY + 2, x + 3, listY + TARGET_LIST_HEIGHT - 2, 0xFF172125);
        int track = TARGET_LIST_HEIGHT - 4;
        int thumb = Math.max(18, track * TARGET_LIST_HEIGHT / content);
        int travel = track - thumb;
        int maximum = Math.max(1, content - TARGET_LIST_HEIGHT);
        int thumbY = listY + 2 + (int) Math.round(travel * targetScroll / maximum);
        boolean hovered = inside(mouseX, mouseY, x - 3, thumbY, 9, thumb);
        graphics.fill(x, thumbY, x + 3, thumbY + thumb,
            hovered || draggingTargetScrollbar ? 0xFFB8C9C5 : 0xFF63777C);
    }

    private void drawPanelFooter(GuiGraphics graphics, int panelX, int panelY,
                                 int mouseX, int mouseY) {
        int y = panelY + panelHeight() - PANEL_FOOTER_HEIGHT;
        Material2Drawing.roundedRect(graphics, panelX + 1, y, PANEL_WIDTH - 2,
            PANEL_FOOTER_HEIGHT - 1, Material3Theme.RADIUS_EXTRA_SMALL,
            Material3Theme.SURFACE_CONTAINER_HIGH);
        int buttonWidth = (PANEL_WIDTH - 20) / 2;
        drawTargetButton(graphics, panelX + 8, y + 3, buttonWidth, 22,
            Component.translatable("status_effect_hud.xero_delta.save"),
            hasUnsavedChanges(), mouseX, mouseY);
        drawTargetButton(graphics, panelX + 12 + buttonWidth, y + 3, buttonWidth, 22,
            Component.translatable("status_effect_hud.xero_delta.cancel"),
            false, mouseX, mouseY);
    }

    private void drawHeaderTooltips(GuiGraphics graphics, int panelX, int panelY,
                                    int mouseX, int mouseY) {
        if (inside(mouseX, mouseY, panelX + 4, panelY + 3, 18, 18)) {
            graphics.renderTooltip(font, Component.translatable(
                "status_effect_hud.xero_delta.undo"), mouseX, mouseY);
        } else if (inside(mouseX, mouseY, panelX + 25, panelY + 3, 18, 18)) {
            graphics.renderTooltip(font, Component.translatable(
                "status_effect_hud.xero_delta.redo"), mouseX, mouseY);
        } else if (inside(mouseX, mouseY, panelX + HEADER_FOLDER_X, panelY + 3,
            HEADER_ICON_SIZE, HEADER_ICON_SIZE)) {
            graphics.renderTooltip(font, Component.translatable(
                "status_effect_hud.xero_delta.open_config_folder"), mouseX, mouseY);
        } else if (inside(mouseX, mouseY, panelX + HEADER_COLLAPSE_X, panelY + 3,
            HEADER_ICON_SIZE, HEADER_ICON_SIZE)) {
            graphics.renderTooltip(font, Component.translatable(
                "status_effect_hud.xero_delta.collapse"), mouseX, mouseY);
        } else if (inside(mouseX, mouseY, panelX + HEADER_CLOSE_X, panelY + 3,
            HEADER_ICON_SIZE, HEADER_ICON_SIZE)) {
            graphics.renderTooltip(font, Component.translatable(
                "status_effect_hud.xero_delta.close"), mouseX, mouseY);
        }
    }

    private int targetCount() {
        return selectedScene == Scene.HUD ? HudTarget.values().length
            : InventoryTarget.values().length;
    }

    private int targetContentHeight() {
        int columns = selectedScene == Scene.HUD ? 4 : 3;
        return (int) Math.ceil(targetCount() / (double) columns) * 22 + 6;
    }

    private Component targetLabel(int index) {
        if (selectedScene == Scene.INVENTORY) {
            return Component.translatable(switch (InventoryTarget.values()[index]) {
                case LAYOUT -> "status_effect_hud.xero_delta.inventory_layout";
                case ITEM_DETAIL -> "status_effect_hud.xero_delta.item_detail";
                default -> "status_effect_hud.xero_delta.inventory_effects";
            });
        }
        HudTarget target = HudTarget.values()[Math.max(0, Math.min(index, HudTarget.values().length - 1))];
        return Component.translatable(switch (target) {
            case INTRO -> "status_effect_hud.xero_delta.target_intro";
            case STAMINA -> "status_effect_hud.xero_delta.target_stamina";
            case HEALTH -> "status_effect_hud.xero_delta.target_health";
            case DOWNED -> "status_effect_hud.xero_delta.target_downed";
            case RESCUE -> "status_effect_hud.xero_delta.target_rescue";
            case CONTEXT -> "status_effect_hud.xero_delta.target_context";
            default -> "status_effect_hud.xero_delta.target_effects";
        });
    }

    private boolean targetSelected(int index) {
        return selectedScene == Scene.HUD
            ? selectedTarget.ordinal() == index : selectedInventoryTarget.ordinal() == index;
    }

    private boolean selectTargetAt(double mouseX, double mouseY, int panelX, int listY) {
        if (!inside(mouseX, mouseY, panelX + 8, listY, PANEL_WIDTH - 16,
            TARGET_LIST_HEIGHT)) return false;
        int columns = selectedScene == Scene.HUD ? 4 : 3;
        int gap = 4;
        int innerWidth = PANEL_WIDTH - 16;
        int buttonWidth = (innerWidth - gap * (columns - 1)) / columns;
        int column = (int) ((mouseX - panelX - 8) / (buttonWidth + gap));
        int row = (int) ((mouseY - listY - 3) / 22.0D);
        if (column < 0 || column >= columns || row < 0) return false;
        int localX = (int) mouseX - (panelX + 8 + column * (buttonWidth + gap));
        if (localX < 0 || localX >= buttonWidth) return false;
        int index = row * columns + column;
        if (index < 0 || index >= targetCount()) return false;
        if (selectedScene == Scene.HUD) {
            selectTarget(HudTarget.values()[index]);
        } else {
            selectedInventoryTarget = InventoryTarget.values()[index];
            draggingPreview = false;
            clearSnapGuides();
            resetPanelScroll();
        }
        return true;
    }

    private void resetTargetScroll() {
        targetScroll = 0.0D;
        targetTargetScroll = 0.0D;
    }

    private void resetPanelScroll() {
        panelScroll = 0.0D;
        panelTargetScroll = 0.0D;
    }

    private void clearAdjustmentFieldFocus() {
        if (primaryField != null) primaryField.setFocused(false);
        if (secondaryField != null) secondaryField.setFocused(false);
    }

    private void saveChanges() {
        commitCurrentSnapshot();
        StatusEffectHudState.save();
        savedSnapshot = StatusEffectHudState.editorSnapshot();
        saveListener.run();
    }

    private void saveAndClose() {
        saveChanges();
        closeToParent();
    }

    private void cancelAndClose() {
        StatusEffectHudState.restoreEditorSnapshot(savedSnapshot);
        closeToParent();
    }

    private void closeToParent() {
        transition.beginClose(() -> minecraft.setScreen(parent));
    }

    private void openConfigFolder() {
        try {
            Util.getPlatform().openFile(ConfigPaths.directory().toFile());
        } catch (Exception ignored) {
        }
    }

    private boolean hasUnsavedChanges() {
        return savedSnapshot != null
            && !savedSnapshot.equals(StatusEffectHudState.editorSnapshot());
    }

    private void commitCurrentSnapshot() {
        String current = StatusEffectHudState.editorSnapshot();
        if (!undoHistory.isEmpty() && current.equals(undoHistory.peekLast())) {
            observedSnapshot = current;
            return;
        }
        undoHistory.addLast(current);
        while (undoHistory.size() > HISTORY_LIMIT) undoHistory.removeFirst();
        redoHistory.clear();
        observedSnapshot = current;
    }

    private void undo() {
        commitCurrentSnapshot();
        if (undoHistory.size() <= 1) return;
        redoHistory.addLast(undoHistory.removeLast());
        StatusEffectHudState.restoreEditorSnapshot(undoHistory.peekLast());
        observedSnapshot = StatusEffectHudState.editorSnapshot();
        unchangedSnapshotTicks = 0;
    }

    private void redo() {
        if (redoHistory.isEmpty()) return;
        String next = redoHistory.removeLast();
        undoHistory.addLast(next);
        StatusEffectHudState.restoreEditorSnapshot(next);
        observedSnapshot = StatusEffectHudState.editorSnapshot();
        unchangedSnapshotTicks = 0;
    }

    private void trackHistory() {
        String current = StatusEffectHudState.editorSnapshot();
        if (!current.equals(observedSnapshot)) {
            observedSnapshot = current;
            unchangedSnapshotTicks = 0;
            return;
        }
        if (draggingPreview || resizingPreview || draggingPanel
            || draggingAdjustmentSlider != 0) return;
        if (++unchangedSnapshotTicks >= 3) {
            commitCurrentSnapshot();
            unchangedSnapshotTicks = 0;
        }
    }

    private void drawPanelScrollbar(GuiGraphics graphics, int panelX, int bodyY, int bodyHeight,
                                    int mouseX, int mouseY) {
        int contentHeight = panelContentHeight();
        if (contentHeight <= bodyHeight) return;
        int x = panelX + PANEL_WIDTH - 5;
        Material2Drawing.roundedRect(graphics, x, bodyY + 2, 3, bodyHeight - 4,
            Material3Theme.RADIUS_FULL, Material3Theme.SURFACE_CONTAINER_HIGHEST);
        int trackHeight = bodyHeight - 4;
        int thumbHeight = Math.max(24, trackHeight * bodyHeight / contentHeight);
        int travel = trackHeight - thumbHeight;
        int max = Math.max(1, contentHeight - bodyHeight);
        int thumbY = bodyY + 2 + (int) Math.round(travel * panelScroll / max);
        boolean hovered = inside(mouseX, mouseY, x - 3, thumbY, 9, thumbHeight);
        Material2Drawing.roundedRect(graphics, x, thumbY, 3, thumbHeight,
            Material3Theme.RADIUS_FULL,
            hovered || draggingPanelScrollbar ? Material3Theme.PRIMARY
                : Material3Theme.OUTLINE);
    }

    private static String durationLabel(int milliseconds) {
        return String.format(java.util.Locale.ROOT, "%.2fs", milliseconds / 1_000.0F);
    }

    private int panelBodyHeight() {
        return panelHeight() - PANEL_HEADER_HEIGHT - TARGET_LIST_HEIGHT
            - PANEL_FOOTER_HEIGHT - PANEL_BODY_BOTTOM_PADDING;
    }

    private int panelHeight() {
        return Math.max(176, Math.min(PANEL_HEIGHT, height - 8));
    }

    private int panelContentHeight() {
        if (selectedScene == Scene.INVENTORY
            && selectedInventoryTarget == InventoryTarget.LAYOUT) return 152;
        if (selectedScene == Scene.INVENTORY
            && selectedInventoryTarget == InventoryTarget.ITEM_DETAIL) return 282;
        if (selectedScene == Scene.HUD && selectedTarget == HudTarget.INTRO) return 354;
        if (selectedScene == Scene.HUD
            && (selectedTarget == HudTarget.HEALTH || selectedTarget == HudTarget.STAMINA)) {
            return 266;
        }
        return 210;
    }

    private void tickPanelScroll() {
        panelScroll += (panelTargetScroll - panelScroll) * 0.24D;
        if (Math.abs(panelTargetScroll - panelScroll) < 0.05D) {
            panelScroll = panelTargetScroll;
        }
        targetScroll += (targetTargetScroll - targetScroll) * 0.24D;
        if (Math.abs(targetTargetScroll - targetScroll) < 0.05D) {
            targetScroll = targetTargetScroll;
        }
    }

    private void clampPanelScroll() {
        int maximum = Math.max(0, panelContentHeight() - panelBodyHeight());
        panelTargetScroll = Math.max(0.0D, Math.min(maximum, panelTargetScroll));
        panelScroll = Math.max(0.0D, Math.min(maximum, panelScroll));
    }

    private void clampTargetScroll() {
        int maximum = Math.max(0, targetContentHeight() - TARGET_LIST_HEIGHT);
        targetTargetScroll = Math.max(0.0D, Math.min(maximum, targetTargetScroll));
        targetScroll = Math.max(0.0D, Math.min(maximum, targetScroll));
    }

    private void drawTargetButton(GuiGraphics g, int x, int y, int w, int h,
                                  Component label, boolean selected, int mouseX, int mouseY) {
        boolean hovered = inside(mouseX, mouseY, x, y, w, h);
        int surface = selected ? Material3Theme.PRIMARY_CONTAINER
            : Material3Theme.SURFACE_CONTAINER_HIGH;
        if (hovered) surface = Material3Theme.mix(surface,
            selected ? Material3Theme.PRIMARY : Material3Theme.TEXT, 0.12F);
        Material2Drawing.roundedRect(g, x, y, w, h, Material3Theme.RADIUS_FULL, surface);
        if (!selected) {
            Material2Drawing.outlineRoundedRect(g, x, y, w, h,
                Material3Theme.RADIUS_FULL, 1.0F, Material3Theme.OUTLINE_VARIANT);
        }
        String text = font.plainSubstrByWidth(label.getString(), Math.max(12, w - 8));
        g.drawCenteredString(font, Component.literal(text), x + w / 2,
            y + (h - font.lineHeight) / 2 + 1,
            selected ? Material3Theme.ON_PRIMARY_CONTAINER : Material3Theme.TEXT_MUTED);
    }

    private void drawToggle(GuiGraphics g, int x, int y, int w, int h,
                            Component label, boolean enabled, int mouseX, int mouseY) {
        boolean hovered = inside(mouseX, mouseY, x, y, w, h);
        int surface = hovered ? Material3Theme.SURFACE_CONTAINER_HIGHEST
            : Material3Theme.SURFACE_CONTAINER_HIGH;
        Material2Drawing.roundedRect(g, x, y, w, h, Material3Theme.RADIUS_SMALL, surface);
        String text = font.plainSubstrByWidth(label.getString(), Math.max(12, w - 42));
        g.drawString(font, Component.literal(text), x + 8,
            y + (h - font.lineHeight) / 2 + 1, Material3Theme.TEXT, false);
        int trackX = x + w - 34;
        int trackY = y + (h - 14) / 2;
        Material2Drawing.roundedRect(g, trackX, trackY, 26, 14,
            Material3Theme.RADIUS_FULL,
            enabled ? Material3Theme.PRIMARY : Material3Theme.OUTLINE_VARIANT);
        Material2Drawing.circle(g, enabled ? trackX + 19 : trackX + 7,
            trackY + 7, 5, enabled ? Material3Theme.ON_PRIMARY : Material3Theme.TEXT_MUTED);
    }

    private void drawButton(GuiGraphics g, int x, int y, int w, int h,
                            String icon, boolean hovered) {
        Material2Drawing.roundedRect(g, x, y, w, h, Material3Theme.RADIUS_FULL,
            hovered ? Material3Theme.SURFACE_CONTAINER_HIGHEST
                : Material3Theme.SURFACE_CONTAINER_HIGH);
        g.drawCenteredString(font, icon, x + w / 2,
            y + (h - font.lineHeight) / 2 + 1,
            hovered ? Material3Theme.PRIMARY : Material3Theme.TEXT);
    }

    private void drawHeaderIconButton(GuiGraphics graphics, int x, int y,
                                      Material2Icon icon, int mouseX, int mouseY) {
        boolean hovered = inside(mouseX, mouseY, x, y, HEADER_ICON_SIZE, HEADER_ICON_SIZE);
        Material2Drawing.roundedRect(graphics, x, y, HEADER_ICON_SIZE, HEADER_ICON_SIZE,
            Material3Theme.RADIUS_FULL,
            hovered ? Material3Theme.SURFACE_CONTAINER_HIGHEST
                : Material3Theme.SURFACE_CONTAINER_HIGH);
        icon.render(graphics, x + HEADER_ICON_SIZE / 2, y + HEADER_ICON_SIZE / 2,
            hovered ? Material3Theme.PRIMARY : Material3Theme.TEXT);
    }

    private void drawResetButton(GuiGraphics graphics, int x, int y, int width, int height,
                                 int mouseX, int mouseY) {
        boolean hovered = inside(mouseX, mouseY, x, y, width, height);
        Material2Drawing.roundedRect(graphics, x, y, width, height,
            Material3Theme.RADIUS_FULL, hovered
                ? Material3Theme.SECONDARY_CONTAINER : Material3Theme.SURFACE_CONTAINER_HIGH);
        Material2Icon.RESTART_ALT.render(graphics, x + 18, y + height / 2,
            hovered ? Material3Theme.PRIMARY : Material3Theme.TEXT_MUTED);
        graphics.drawCenteredString(font,
            Component.translatable("status_effect_hud.xero_delta.reset_position"),
            x + width / 2 + 6, y + (height - font.lineHeight) / 2 + 1,
            hovered ? Material3Theme.ON_SECONDARY_CONTAINER : Material3Theme.TEXT);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        EditBox focused = focusedInputField();
        return focused != null && focused.charTyped(codePoint, modifiers)
            || super.charTyped(codePoint, modifiers);
    }

    private EditBox inputFieldAt(double mouseX, double mouseY) {
        if (xField != null && xField.visible && xField.isMouseOver(mouseX, mouseY)) return xField;
        if (yField != null && yField.visible && yField.isMouseOver(mouseX, mouseY)) return yField;
        if (primaryField != null && primaryField.visible && primaryField.isMouseOver(mouseX, mouseY)) return primaryField;
        if (secondaryField != null && secondaryField.visible && secondaryField.isMouseOver(mouseX, mouseY)) return secondaryField;
        return null;
    }

    private EditBox focusedInputField() {
        if (xField != null && xField.isFocused()) return xField;
        if (yField != null && yField.isFocused()) return yField;
        if (primaryField != null && primaryField.isFocused()) return primaryField;
        if (secondaryField != null && secondaryField.isFocused()) return secondaryField;
        return null;
    }

    private static boolean isSignedInteger(String value) {
        return value == null || value.isEmpty() || "-".equals(value) || value.matches("-?\\d{0,5}");
    }
    private static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
    private static void border(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.renderOutline(x, y, w, h, color);
    }
}

