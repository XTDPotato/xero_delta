package com.xtdpotato.xero_delta.screen;

import com.xtdpotato.xero_delta.screen.material.Material3Theme;

import com.xtdpotato.xero_delta.client.ClientDataCache;
import com.xtdpotato.xero_delta.client.ClientGridRotation;
import com.xtdpotato.xero_delta.client.ContainerGridClickHandler;
import com.xtdpotato.xero_delta.client.DeltaContainerLayoutController;
import com.xtdpotato.xero_delta.client.GridItemRenderer;
import com.xtdpotato.xero_delta.client.ItemDetailOverlay;
import com.xtdpotato.xero_delta.client.PlayerStatusClientState;
import com.xtdpotato.xero_delta.client.TradingClientState;
import com.xtdpotato.xero_delta.data.ItemSize;
import com.xtdpotato.xero_delta.data.PersonalWarehouseData;
import com.xtdpotato.xero_delta.data.WarehouseCategory;
import com.xtdpotato.xero_delta.grid.ContainerGridHelper;
import com.xtdpotato.xero_delta.grid.GridBackingStore;
import com.xtdpotato.xero_delta.menu.PersonalWarehouseMenu;
import com.xtdpotato.xero_delta.network.ModNetwork;
import com.xtdpotato.xero_delta.network.OpenWarehouseCategoryPacket;
import com.xtdpotato.xero_delta.network.RenameWarehousePacket;
import com.xtdpotato.xero_delta.network.WarehouseSlotCategoryTransferPacket;
import com.xtdpotato.xero_delta.network.WarehouseSafetyBoxTransferPacket;
import com.xtdpotato.xero_delta.trading.TradingInventorySources;
import com.xtdpotato.xero_delta.util.SlotFieldUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.EnumMap;
import java.util.Optional;
import java.util.Set;

public final class PersonalWarehouseScreen
    extends AbstractContainerScreen<PersonalWarehouseMenu> {
    private static final int PANEL_MARGIN = 4;
    private static final int WAREHOUSE_WIDTH = 222;
    private static final int VANILLA_PLAYER_WIDTH = 182;
    private static final int SIDEBAR_WIDTH = 40;
    private static final int GRID_LEFT = SIDEBAR_WIDTH + 8;
    private static final int GRID_TOP = WarehouseScrollLayout.GRID_TOP;
    private static final int GRID_COLUMNS = 9;
    private static final int ROW_HEIGHT = WarehouseScrollLayout.ROW_HEIGHT;
    private static final int BUTTON_HEIGHT = 16;
    private static final double WHEEL_PIXELS = 27.0D;
    private static final double SCROLL_RESPONSE = 18.0D;
    private static final double ITEM_DRAG_THRESHOLD_SQUARED = 16.0D;
    private static final EnumMap<WarehouseCategory, ScrollState> CATEGORY_SCROLLS =
        new EnumMap<>(WarehouseCategory.class);

    private int visibleRows = 1;
    private double scrollPixels;
    private double targetScrollPixels;
    private long lastFrameNanos;
    private int selectedSlot = -1;
    private long lastClickAt;
    private int armedSlot = -1;
    private ItemStack armedStack = ItemStack.EMPTY;
    private double itemDragStartX;
    private double itemDragStartY;
    private boolean itemDragging;
    private boolean scrollbarDragging;
    private double scrollbarGrabOffset;
    private EditBox renameBox;
    private long lastTitleClickAt;

    public PersonalWarehouseScreen(PersonalWarehouseMenu menu, Inventory inventory,
                                   Component title) {
        super(menu, inventory, title);
        imageWidth = WAREHOUSE_WIDTH;
        imageHeight = 190;
        titleLabelX = -1000;
        inventoryLabelX = -1000;
    }

    @Override
    protected void init() {
        imageWidth = deltaLayoutEnabled()
            ? WAREHOUSE_WIDTH : WAREHOUSE_WIDTH + VANILLA_PLAYER_WIDTH;
        imageHeight = Math.max(1, height - PANEL_MARGIN * 2);
        visibleRows = WarehouseScrollLayout.visibleRowsForHeight(imageHeight, menu.rows());
        ScrollState remembered = CATEGORY_SCROLLS.get(menu.category());
        if (remembered != null) {
            scrollPixels = remembered.current();
            targetScrollPixels = remembered.target();
        }
        targetScrollPixels = clamp(targetScrollPixels, 0.0D, maxScrollPixels());
        scrollPixels = clamp(scrollPixels, 0.0D, maxScrollPixels());
        super.init();
        renameBox = addRenderableWidget(new Material3CompactEditBox(font,
            warehouseLeft() + GRID_LEFT, topPos + 4, 118, 18,
            Component.translatable("warehouse.xero_delta.rename")));
        renameBox.setMaxLength(PersonalWarehouseData.MAX_NAME_LENGTH);
        renameBox.setVisible(false);
        lastFrameNanos = System.nanoTime();
        layoutSlots();
        TradingClientState.INSTANCE.restoreCursor();
    }

    @Override
    protected void renderBg(GuiGraphics g, float partial, int mouseX, int mouseY) {
        // AbstractContainerScreen's render hook may restore the menu's original
        // slot coordinates immediately before this method. Apply the warehouse
        // layout here so the vanilla player inventory remains in the left panel.
        layoutSlots();
        g.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight,
            Material3Theme.SURFACE);
        g.renderOutline(leftPos, topPos, imageWidth, imageHeight, Material3Theme.OUTLINE_VARIANT);
        if (!deltaLayoutEnabled()) renderVanillaPlayerPanel(g);
        renderGridBackground(g);
        renderChrome(g, mouseX, mouseY);
        if (renameBox != null && renameBox.isVisible()) renameBox.render(g, mouseX, mouseY, partial);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        updateSmoothScroll();
        super.render(g, mouseX, mouseY, partial);

        // Keep partially visible moving slots below the fixed header and footer.
        renderChrome(g, mouseX, mouseY);
        if (selectedSlot >= 0 && selectedSlot < menu.warehouseSlots()) {
            Slot slot = menu.slots.get(selectedSlot);
            ItemSize size = ContainerGridHelper.orientedSize(
                slot.getItem(), ClientDataCache.INSTANCE::getSize);
            int footprintWidth = footprintPixels(size.width());
            int footprintHeight = footprintPixels(size.height());
            boolean visible = slot.x > -500 && !slot.getItem().isEmpty()
                && slot.y + footprintHeight > GRID_TOP
                && slot.y < gridBottom();
            ItemDetailOverlay.updateAnchor(this, leftPos + slot.x, topPos + slot.y,
                footprintWidth, footprintHeight, visible);
            if (slot.getItem().isEmpty()) {
                selectedSlot = -1;
                ItemDetailOverlay.close(this);
            }
        }
        if (!ItemDetailOverlay.isOpen(this) && !itemDragging) {
            renderTooltip(g, mouseX, mouseY);
        }
        renderWarehouseDrag(g, mouseX, mouseY);
        ItemDetailOverlay.render(this, g, mouseX, mouseY);
        renderCategoryTooltip(g, mouseX, mouseY);
    }

    private void renderGridBackground(GuiGraphics g) {
        int viewportTop = topPos + GRID_TOP;
        int scissorTop = viewportTop - 2;
        int viewportBottom = topPos + gridBottom();
        int firstRow = (int) Math.floor(scrollPixels / ROW_HEIGHT);
        int offset = (int) Math.round(scrollPixels - firstRow * ROW_HEIGHT);
        int warehouseLeft = warehouseLeft();
        g.enableScissor(warehouseLeft + GRID_LEFT - 1, scissorTop,
            warehouseLeft + GRID_LEFT + GRID_COLUMNS * ROW_HEIGHT, viewportBottom);
        for (int visibleRow = 0; visibleRow <= visibleRows; visibleRow++) {
            int logicalRow = firstRow + visibleRow;
            if (logicalRow >= menu.rows()) break;
            int y = viewportTop + visibleRow * ROW_HEIGHT - offset;
            for (int column = 0; column < GRID_COLUMNS; column++) {
                int x = warehouseLeft + GRID_LEFT + column * ROW_HEIGHT;
                g.fill(x - 1, y - 1, x + 17, y + 17, 0xDD10191B);
                g.renderOutline(x - 1, y - 1, 18, 18, 0xFF4F615E);
            }
        }
        g.disableScissor();
    }

    private void renderChrome(GuiGraphics g, int mouseX, int mouseY) {
        int left = warehouseLeft();
        int right = left + WAREHOUSE_WIDTH;
        int gridBottom = topPos + gridBottom();
        g.fill(left + 1, topPos + 1, right - 1, topPos + GRID_TOP - 2, 0xF00A1114);
        g.fill(left + 1, gridBottom, right - 1, topPos + imageHeight - 1, 0xF00A1114);
        g.fill(left + 1, topPos + GRID_TOP - 2,
            left + GRID_LEFT - 4, gridBottom, 0xF00A1114);
        if (renameBox == null || !renameBox.isVisible()) {
            g.drawString(font, warehouseTitle(), left + GRID_LEFT, topPos + 8,
                0xFFF1F5F3, false);
        }
        g.drawString(font, menu.rows() + "x9", right - 48, topPos + 8,
            0xFF9FB0AC, false);
        renderCategorySidebar(g, mouseX, mouseY);

        int buttonY = topPos + imageHeight - BUTTON_HEIGHT - 4;
        button(g, left + GRID_LEFT, buttonY, 76, BUTTON_HEIGHT,
            Component.translatable("warehouse.xero_delta.sort"), mouseX, mouseY);
        if (menu.category() == WarehouseCategory.MAIN) {
            button(g, left + GRID_LEFT + 86, buttonY, 76, BUTTON_HEIGHT,
                Component.translatable("warehouse.xero_delta.upgrade"), mouseX, mouseY);
        }

        int trackX = scrollbarTrackX();
        int trackY = scrollbarTrackY();
        int trackHeight = scrollbarTrackHeight();
        g.fill(trackX, trackY, trackX + 3, trackY + trackHeight, 0xFF1B2527);
        int thumbHeight = scrollbarThumbHeight();
        int thumbY = scrollbarThumbY();
        boolean hovered = inside(mouseX, mouseY, trackX - 3, thumbY,
            9, thumbHeight);
        g.fill(trackX, thumbY, trackX + 3, thumbY + thumbHeight,
            scrollbarDragging || hovered ? 0xFFF1F6F3 : 0xFF65D6AD);
    }

    private void updateSmoothScroll() {
        long now = System.nanoTime();
        if (lastFrameNanos == 0L) {
            lastFrameNanos = now;
            return;
        }
        double elapsedSeconds = Math.min(0.1D,
            (now - lastFrameNanos) / 1_000_000_000.0D);
        lastFrameNanos = now;
        double blend = 1.0D - Math.exp(-elapsedSeconds * SCROLL_RESPONSE);
        scrollPixels += (targetScrollPixels - scrollPixels) * blend;
        if (Math.abs(targetScrollPixels - scrollPixels) < 0.02D) {
            scrollPixels = targetScrollPixels;
        }
    }

    private void layoutSlots() {
        int roundedScroll = (int) Math.round(scrollPixels);
        int viewportBottom = gridBottom();
        for (int index = 0; index < menu.warehouseSlots(); index++) {
            Slot slot = menu.slots.get(index);
            int y = GRID_TOP + index / GRID_COLUMNS * ROW_HEIGHT - roundedScroll;
            ItemSize size = ContainerGridHelper.orientedSize(
                slot.getItem(), ClientDataCache.INSTANCE::getSize);
            int renderedHeight = Math.max(ContainerGridHelper.SLOT_FACE_SIZE,
                footprintPixels(size.height()));
            if (y + renderedHeight <= GRID_TOP || y >= viewportBottom) {
                SlotFieldUtil.setX(slot, -1000);
                SlotFieldUtil.setY(slot, -1000);
            } else {
                SlotFieldUtil.setX(slot,
                    warehouseOffsetX() + GRID_LEFT + index % GRID_COLUMNS * ROW_HEIGHT);
                SlotFieldUtil.setY(slot, y);
            }
        }
        layoutVanillaPlayerSlots();
    }

    @Override
    public boolean mouseScrolled(double x, double y, double sx, double sy) {
        if (x >= warehouseLeft() && x < warehouseLeft() + WAREHOUSE_WIDTH
            && y >= topPos + GRID_TOP && y < topPos + gridBottom()) {
            targetScrollPixels = clamp(targetScrollPixels - sy * WHEEL_PIXELS,
                0.0D, maxScrollPixels());
            return true;
        }
        return super.mouseScrolled(x, y, sx, sy);
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        if (button == 0 && beginScrollbarDrag(x, y)) {
            clearItemDrag();
            ItemDetailOverlay.close(this);
            return true;
        }
        boolean warehouseGridClick = button == 0 && warehouseGridAt(x, y)
            && !ItemDetailOverlay.isPopupAt(this, x, y);
        if (warehouseGridClick) {
            // A grid click may replace a detail owned by the embedded player
            // layout. Do not consume the first click merely to close it.
            ItemDetailOverlay.close(this);
        } else if (ItemDetailOverlay.mouseClicked(this, x, y, button)) {
            return true;
        }
        if (button == 0 && inside(x, y, warehouseLeft() + GRID_LEFT, topPos + 3, 120, 20)) {
            long now = System.currentTimeMillis();
            if (now - lastTitleClickAt <= 320L) beginRename();
            lastTitleClickAt = now;
            return true;
        }
        WarehouseCategory category = categoryAt(x, y);
        if (button == 0 && category != null) {
            ItemDetailOverlay.close(this);
            clearItemDrag();
            if (category != menu.category()) {
                rememberScrollState();
                DeltaContainerLayoutController.rememberWarehouseScroll(this);
                TradingClientState.INSTANCE.rememberCursor();
                ModNetwork.sendToServer(new OpenWarehouseCategoryPacket(category.id()));
            }
            return true;
        }
        int buttonY = topPos + imageHeight - BUTTON_HEIGHT - 4;
        if (button == 0 && inside(x, y, warehouseLeft() + GRID_LEFT, buttonY, 76, BUTTON_HEIGHT)) {
            minecraft.gameMode.handleInventoryButtonClick(
                menu.containerId, PersonalWarehouseMenu.SORT);
            return true;
        }
        if (button == 0 && menu.category() == WarehouseCategory.MAIN
            && inside(x, y, warehouseLeft() + GRID_LEFT + 86, buttonY, 76, BUTTON_HEIGHT)) {
            minecraft.gameMode.handleInventoryButtonClick(
                menu.containerId, PersonalWarehouseMenu.UPGRADE);
            return true;
        }
        Slot slot = warehouseItemAt(x, y);
        if (button == 0 && slot != null && menu.getCarried().isEmpty()) {
            DeltaContainerLayoutController.clearDetailSelection(this);
            long now = System.currentTimeMillis();
            if (selectedSlot == slot.index && now - lastClickAt <= 280L) {
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null && mc.gameMode != null) {
                    mc.gameMode.handleInventoryMouseClick(menu.containerId, slot.index,
                        0, ClickType.QUICK_MOVE, mc.player);
                }
                selectedSlot = -1;
                ItemDetailOverlay.close(this);
            } else {
                selectedSlot = slot.index;
                lastClickAt = now;
                armedSlot = slot.index;
                armedStack = slot.getItem().copy();
                itemDragStartX = x;
                itemDragStartY = y;
                itemDragging = false;
                ItemSize size = ContainerGridHelper.orientedSize(
                    slot.getItem(), ClientDataCache.INSTANCE::getSize);
                ItemDetailOverlay.open(this, slot.getItem(), false,
                    TradingInventorySources.sourceIdForWarehouse(
                        menu.category().id(), slot.index, slot.getItem()),
                    leftPos + slot.x, topPos + slot.y,
                    footprintPixels(size.width()), footprintPixels(size.height()));
            }
            return true;
        }
        return super.mouseClicked(x, y, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (renameBox != null && renameBox.isVisible()) {
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                commitRename();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                renameBox.setVisible(false);
                setFocused(null);
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private Slot warehouseItemAt(double mouseX, double mouseY) {
        Slot hit = warehouseCellAt(mouseX, mouseY);
        if (hit == null) return null;
        ContainerGridHelper.prepare(menu, ClientDataCache.INSTANCE::getSize,
            ClientDataCache.INSTANCE.revision());
        Slot anchor = ContainerGridHelper.footprintAnchorFor(
            menu, hit, ClientDataCache.INSTANCE::getSize);
        Slot resolved = anchor == null ? hit : anchor;
        return resolved.index >= 0 && resolved.index < menu.warehouseSlots()
            && !resolved.getItem().isEmpty() ? resolved : null;
    }

    private Slot warehouseCellAt(double mouseX, double mouseY) {
        if (!inside(mouseX, mouseY, warehouseLeft() + GRID_LEFT - 1, topPos + GRID_TOP,
            GRID_COLUMNS * ROW_HEIGHT + 1, visibleRows * ROW_HEIGHT)) return null;
        ContainerGridHelper.prepare(menu, ClientDataCache.INSTANCE::getSize,
            ClientDataCache.INSTANCE.revision());
        Slot hit = ContainerGridHelper.hitSlot(
            menu, mouseX - leftPos, mouseY - topPos);
        return hit != null && hit.index >= 0 && hit.index < menu.warehouseSlots()
            ? hit : null;
    }

    public boolean warehouseGridAt(double mouseX, double mouseY) {
        return warehouseCellAt(mouseX, mouseY) != null;
    }

    public WarehouseDropPlacement warehouseDropPlacement(ItemStack stack,
                                                           double mouseX,
                                                           double mouseY) {
        var placement = resolveWarehouseDropPlacement(stack, mouseX, mouseY);
        return !isDirectWarehouseDrop(placement)
            ? null : new WarehouseDropPlacement(
                placement.anchor().index, placement.rotated());
    }

    /** Relinquishes the warehouse-owned detail when the embedded player layout is clicked. */
    public void clearWarehouseDetailSelection() {
        selectedSlot = -1;
        lastClickAt = 0L;
        clearItemDrag();
    }

    public boolean carrySelectedItem() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.gameMode == null
            || selectedSlot < 0 || selectedSlot >= menu.warehouseSlots()
            || menu.slots.get(selectedSlot).getItem().isEmpty()) return false;
        minecraft.gameMode.handleInventoryMouseClick(menu.containerId, selectedSlot,
            0, ClickType.QUICK_MOVE, minecraft.player);
        selectedSlot = -1;
        clearItemDrag();
        return true;
    }

    /** Draws the target preview for a virtual drag originating in the Delta panel. */
    public void renderPlayerDragPreview(GuiGraphics graphics, int mouseX, int mouseY,
                                        ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;
        var placement = resolveWarehouseDropPlacement(stack, mouseX, mouseY);
        if (placement == null || placement.anchor() == null) return;
        ItemSize previewSize = ContainerGridHelper.orientedSize(
            stack, placement.rotated(), ClientDataCache.INSTANCE::getSize);
        int px = leftPos + placement.anchor().x;
        int py = topPos + placement.anchor().y;
        int pw = footprintPixels(previewSize.width());
        int ph = footprintPixels(previewSize.height());
        boolean accepted = isDirectWarehouseDrop(placement);
        int fill = accepted ? 0x8849D79A : 0x88E05252;
        int border = accepted ? 0xFF6FE8B2 : 0xFFFF6767;
        graphics.enableScissor(warehouseLeft() + GRID_LEFT - 1, topPos + GRID_TOP - 2,
            warehouseLeft() + GRID_LEFT + GRID_COLUMNS * ROW_HEIGHT, topPos + gridBottom());
        graphics.fill(px, py, px + pw, py + ph, fill);
        graphics.renderOutline(px, py, pw, ph, border);
        graphics.disableScissor();
    }

    private ContainerGridHelper.PlacementResult resolveWarehouseDropPlacement(
        ItemStack stack, double mouseX, double mouseY) {
        if (stack == null || stack.isEmpty()) return null;
        Slot target = warehouseCellAt(mouseX, mouseY);
        if (target == null) return null;
        return ContainerGridHelper.resolveCursorPlacement(menu, target,
            stack, slotFractionX(target, mouseX), slotFractionY(target, mouseY),
            ClientGridRotation.allowAutoRotate(), Set.of(),
            ClientDataCache.INSTANCE::getSize);
    }

    private static boolean isDirectWarehouseDrop(
        ContainerGridHelper.PlacementResult placement) {
        return placement != null && placement.anchor() != null
            && (placement.status() == GridBackingStore.PlacementStatus.CAN_PLACE
                || placement.status() == GridBackingStore.PlacementStatus.CAN_STACK);
    }

    @Override
    public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (button == 0 && scrollbarDragging) {
            updateScrollbarDrag(y);
            return true;
        }
        if (button == 0 && armedSlot >= 0) {
            double dragX = x - itemDragStartX;
            double dragY = y - itemDragStartY;
            if (!itemDragging
                && dragX * dragX + dragY * dragY >= ITEM_DRAG_THRESHOLD_SQUARED) {
                itemDragging = true;
                selectedSlot = -1;
                ItemDetailOverlay.close(this);
            }
            if (itemDragging) return true;
        }
        if (ItemDetailOverlay.mouseDragged(this, x, y, button)) return true;
        return super.mouseDragged(x, y, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double x, double y, int button) {
        if (button == 0 && scrollbarDragging) {
            updateScrollbarDrag(y);
            scrollbarDragging = false;
            return true;
        }
        if (button == 0 && armedSlot >= 0) {
            boolean dragged = itemDragging;
            int sourceSlot = armedSlot;
            clearItemDrag();
            if (dragged) moveWarehouseItem(sourceSlot, x, y);
            return true;
        }
        if (ItemDetailOverlay.mouseReleased(this, x, y, button)) return true;
        return super.mouseReleased(x, y, button);
    }

    /** Warehouse scrolling keeps hidden cells at sentinel coordinates. Render from the
     * stable anchor and clip here so those sentinels cannot inflate a multi-cell item. */
    public boolean renderSizedWarehouseItem(GuiGraphics graphics, Slot slot,
                                              ItemStack stack, ItemSize size,
                                              boolean rotated) {
        if (slot == null || slot.index < 0 || slot.index >= menu.warehouseSlots()
            || size.width() <= 1 && size.height() <= 1) return false;
        if (slot.x < -500 || slot.y < -500) return true;
        graphics.enableScissor(warehouseLeft() + GRID_LEFT - 1, topPos + GRID_TOP - 2,
            warehouseLeft() + GRID_LEFT + GRID_COLUMNS * ROW_HEIGHT,
            topPos + gridBottom());
        try {
            GridItemRenderer.renderSizedItem(graphics, font, stack, slot.x, slot.y,
                footprintPixels(size.width()), footprintPixels(size.height()),
                rotated, ClientDataCache.INSTANCE.shouldRotateTexture(stack),
                ClientDataCache.INSTANCE.shouldStretchTexture(stack),
                ClientDataCache.INSTANCE.proportionalTextureScale(stack));
            DeltaContainerLayoutController.renderExternalItemWeightBadge(this, graphics, stack,
                slot.x, slot.y, footprintPixels(size.width()), footprintPixels(size.height()));
        } finally {
            graphics.disableScissor();
        }
        return true;
    }

    public void renderWarehouseItemWeightBadge(GuiGraphics graphics, Slot slot,
                                                ItemStack stack, int width, int height) {
        if (slot == null || slot.index < 0 || slot.index >= menu.warehouseSlots()
            || slot.x < -500 || slot.y < -500) return;
        graphics.enableScissor(warehouseLeft() + GRID_LEFT - 1, topPos + GRID_TOP - 2,
            warehouseLeft() + GRID_LEFT + GRID_COLUMNS * ROW_HEIGHT, topPos + gridBottom());
        try {
            DeltaContainerLayoutController.renderExternalItemWeightBadge(this, graphics, stack,
                slot.x - 1, slot.y - 1, width, height);
        } finally {
            graphics.disableScissor();
        }
    }

    private void renderWarehouseDrag(GuiGraphics graphics, int mouseX, int mouseY) {
        if (!itemDragging || armedStack.isEmpty()) return;
        ItemSize size = ContainerGridHelper.orientedSize(
            armedStack, ClientDataCache.INSTANCE::getSize);
        int width = footprintPixels(size.width());
        int height = footprintPixels(size.height());

        // The warehouse owns the right-hand drag state, while the embedded
        // Delta inventory owns the left-hand targets. Render that shared
        // preview explicitly so dragging to equipment, pockets, carriers or
        // the safety box gives the same feedback as every other container.
        DeltaContainerLayoutController.renderCarriedDropPreview(
            this, graphics, mouseX, mouseY, armedStack);

        Slot target = warehouseCellAt(mouseX, mouseY);
        if (target != null) {
            Set<Slot> ignoredAnchors = armedSlot >= 0 && armedSlot < menu.warehouseSlots()
                ? Set.of(menu.slots.get(armedSlot)) : Set.of();
            var placement = ContainerGridHelper.resolveCursorPlacement(menu, target,
                armedStack, slotFractionX(target, mouseX), slotFractionY(target, mouseY),
                ClientGridRotation.allowAutoRotate(), ignoredAnchors,
                ClientDataCache.INSTANCE::getSize);
            if (placement != null && placement.anchor() != null) {
                ItemSize previewSize = ContainerGridHelper.orientedSize(
                    armedStack, placement.rotated(), ClientDataCache.INSTANCE::getSize);
                int color = placement.isAccepted() ? 0x8849D79A : 0x88E05252;
                int border = placement.isAccepted() ? 0xFF6FE8B2 : 0xFFFF6767;
                int px = leftPos + placement.anchor().x;
                int py = topPos + placement.anchor().y;
                int pw = footprintPixels(previewSize.width());
                int ph = footprintPixels(previewSize.height());
                graphics.enableScissor(warehouseLeft() + GRID_LEFT - 1, topPos + GRID_TOP - 2,
                    warehouseLeft() + GRID_LEFT + GRID_COLUMNS * ROW_HEIGHT,
                    topPos + gridBottom());
                graphics.fill(px, py, px + pw, py + ph, color);
                graphics.renderOutline(px, py, pw, ph, border);
                graphics.disableScissor();
            }
        }

        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 700);
        GridItemRenderer.renderSizedItem(graphics, font, armedStack,
            mouseX - width / 2, mouseY - height / 2, width, height,
            GridBackingStore.isRotated(armedStack),
            ClientDataCache.INSTANCE.shouldRotateTexture(armedStack),
            ClientDataCache.INSTANCE.shouldStretchTexture(armedStack),
            ClientDataCache.INSTANCE.proportionalTextureScale(armedStack));
        graphics.pose().popPose();
    }

    private void moveWarehouseItem(int sourceSlot, double mouseX, double mouseY) {
        if (minecraft == null || minecraft.player == null || minecraft.gameMode == null
            || sourceSlot < 0 || sourceSlot >= menu.warehouseSlots()) return;
        WarehouseCategory categoryTarget = categoryAt(mouseX, mouseY);
        if (categoryTarget != null && categoryTarget != menu.category()) {
            rememberScrollState();
            DeltaContainerLayoutController.rememberWarehouseScroll(this);
            TradingClientState.INSTANCE.rememberCursor();
            ModNetwork.sendToServer(new WarehouseSlotCategoryTransferPacket(
                sourceSlot, categoryTarget.id()));
            return;
        }
        Slot targetBeforePickup = warehouseCellAt(mouseX, mouseY);
        if (targetBeforePickup == null) {
            ItemStack source = menu.slots.get(sourceSlot).getItem().copy();
            DeltaContainerLayoutController.WarehouseSafetyBoxDrop safetyDrop =
                DeltaContainerLayoutController.warehouseSafetyBoxDrop(
                    this, mouseX, mouseY, source);
            if (safetyDrop != null) {
                if (safetyDrop.accepted()) {
                    ModNetwork.sendToServer(new WarehouseSafetyBoxTransferPacket(
                        sourceSlot, safetyDrop.targetCell(), safetyDrop.rotated()));
                }
                return;
            }
            if (!DeltaContainerLayoutController.canAcceptCarriedDrop(
                this, mouseX, mouseY, source)) return;
            minecraft.gameMode.handleInventoryMouseClick(menu.containerId, sourceSlot,
                0, ClickType.PICKUP, minecraft.player);
            if (menu.getCarried().isEmpty()) return;
            if (!DeltaContainerLayoutController.dropCarriedAt(
                this, mouseX, mouseY, source)) {
                restoreWarehouseSource(sourceSlot);
            }
            return;
        }
        Slot occupied = ContainerGridHelper.footprintAnchorFor(
            menu, targetBeforePickup, ClientDataCache.INSTANCE::getSize);
        if ((occupied == null ? targetBeforePickup : occupied).index == sourceSlot) return;

        minecraft.gameMode.handleInventoryMouseClick(menu.containerId, sourceSlot,
            0, ClickType.PICKUP, minecraft.player);
        if (menu.getCarried().isEmpty()) return;
        ContainerGridHelper.invalidate(menu);
        ContainerGridHelper.prepare(menu, ClientDataCache.INSTANCE::getSize,
            ClientDataCache.INSTANCE.revision());
        Slot target = warehouseCellAt(mouseX, mouseY);
        if (target == null) {
            restoreWarehouseSource(sourceSlot);
            return;
        }
        var placement = ContainerGridHelper.resolveCursorPlacement(menu, target,
            menu.getCarried(), slotFractionX(target, mouseX), slotFractionY(target, mouseY),
            ClientGridRotation.allowAutoRotate(), ClientDataCache.INSTANCE::getSize);
        if (placement != null && placement.isAccepted() && placement.anchor() != null) {
            ContainerGridClickHandler.commitPlacement(menu, placement, 0);
        } else {
            restoreWarehouseSource(sourceSlot);
        }
    }

    private void restoreWarehouseSource(int sourceSlot) {
        if (minecraft == null || minecraft.player == null || minecraft.gameMode == null
            || menu.getCarried().isEmpty()) return;
        minecraft.gameMode.handleInventoryMouseClick(menu.containerId, sourceSlot,
            0, ClickType.PICKUP, minecraft.player);
    }

    private double slotFractionX(Slot slot, double mouseX) {
        return clamp((mouseX - leftPos - slot.x) / ROW_HEIGHT, 0.0D, 0.999D);
    }

    private double slotFractionY(Slot slot, double mouseY) {
        return clamp((mouseY - topPos - slot.y) / ROW_HEIGHT, 0.0D, 0.999D);
    }

    private boolean beginScrollbarDrag(double mouseX, double mouseY) {
        int trackX = scrollbarTrackX();
        int trackY = scrollbarTrackY();
        int trackHeight = scrollbarTrackHeight();
        if (!inside(mouseX, mouseY, trackX - 4, trackY, 11, trackHeight)) return false;
        int thumbY = scrollbarThumbY();
        int thumbHeight = scrollbarThumbHeight();
        scrollbarDragging = true;
        if (mouseY >= thumbY && mouseY < thumbY + thumbHeight) {
            scrollbarGrabOffset = mouseY - thumbY;
        } else {
            scrollbarGrabOffset = thumbHeight / 2.0D;
            updateScrollbarDrag(mouseY);
        }
        return true;
    }

    private void updateScrollbarDrag(double mouseY) {
        int travel = scrollbarTrackHeight() - scrollbarThumbHeight();
        double offset = clamp(mouseY - scrollbarTrackY() - scrollbarGrabOffset,
            0.0D, Math.max(0, travel));
        double value = WarehouseScrollLayout.scrollPixelsForThumbOffset(
            offset, travel, maxScrollPixels());
        scrollPixels = value;
        targetScrollPixels = value;
        layoutSlots();
    }

    private int scrollbarTrackX() { return warehouseLeft() + WAREHOUSE_WIDTH - 6; }
    private int scrollbarTrackY() { return topPos + GRID_TOP; }
    private int scrollbarTrackHeight() { return visibleRows * ROW_HEIGHT; }
    private int scrollbarThumbHeight() {
        return WarehouseScrollLayout.thumbHeight(
            scrollbarTrackHeight(), menu.rows() * ROW_HEIGHT);
    }
    private int scrollbarThumbY() {
        int travel = scrollbarTrackHeight() - scrollbarThumbHeight();
        return scrollbarTrackY() + WarehouseScrollLayout.thumbOffset(
            scrollPixels, maxScrollPixels(), travel);
    }

    private void clearItemDrag() {
        armedSlot = -1;
        armedStack = ItemStack.EMPTY;
        itemDragging = false;
    }

    private static int footprintPixels(int cells) {
        return Math.max(1, cells) * ROW_HEIGHT
            - (ROW_HEIGHT - ContainerGridHelper.SLOT_FACE_SIZE);
    }

    public record WarehouseDropPlacement(int slot, boolean rotated) {
    }

    private int gridBottom() {
        return GRID_TOP + visibleRows * ROW_HEIGHT;
    }

    private double maxScrollPixels() {
        return WarehouseScrollLayout.maximumScrollPixels(menu.rows(), visibleRows);
    }

    private void button(GuiGraphics g, int x, int y, int width, int height,
                        Component text, int mouseX, int mouseY) {
        boolean hover = inside(mouseX, mouseY, x, y, width, height);
        g.fill(x, y, x + width, y + height,
            hover ? 0xDD2C5C4C : 0xBB1D4035);
        g.renderOutline(x, y, width, height,
            hover ? 0xFFF1F6F3 : 0xFF65D6AD);
        g.drawCenteredString(font, text, x + width / 2, y + 4, 0xFFFFFFFF);
    }

    private Component warehouseTitle() {
        return menu.warehouseName().isBlank()
            ? Component.translatable("screen.xero_delta.personal_warehouse")
            : Component.literal(menu.warehouseName());
    }

    private void beginRename() {
        if (renameBox == null) return;
        renameBox.setValue(menu.warehouseName());
        renameBox.setVisible(true);
        renameBox.setFocused(true);
        setFocused(renameBox);
    }

    private void commitRename() {
        if (renameBox == null || !renameBox.isVisible()) return;
        String value = renameBox.getValue();
        renameBox.setVisible(false);
        setFocused(null);
        ModNetwork.sendToServer(new RenameWarehousePacket(value));
    }

    private void renderCategorySidebar(GuiGraphics g, int mouseX, int mouseY) {
        int buttonHeight = categoryButtonHeight();
        int x = warehouseLeft() + 5;
        int y = topPos + GRID_TOP;
        for (WarehouseCategory category : WarehouseCategory.values()) {
            boolean selected = category == menu.category();
            boolean hovered = inside(mouseX, mouseY, x, y, SIDEBAR_WIDTH - 10, buttonHeight - 2);
            g.fill(x, y, x + SIDEBAR_WIDTH - 10, y + buttonHeight - 2,
                selected ? 0xDD274A40 : hovered ? 0xCC22332F : 0x9912191B);
            g.renderOutline(x, y, SIDEBAR_WIDTH - 10, buttonHeight - 2,
                selected ? 0xFFF1F6F3 : hovered ? 0xFF65D6AD : 0xFF445451);
            g.drawString(font, category.tier(), x + 2, y + 2,
                selected ? 0xFFFFFFFF : 0xFF98A6A3, false);
            int iconSize = Math.max(10, Math.min(16, buttonHeight - 6));
            g.pose().pushPose();
            float scale = iconSize / 16.0F;
            g.pose().translate(x + (SIDEBAR_WIDTH - 10 - iconSize) / 2.0F,
                y + (buttonHeight - 2 - iconSize) / 2.0F, 12);
            g.pose().scale(scale, scale, 1);
            g.renderItem(category.icon(), 0, 0);
            g.pose().popPose();
            y += buttonHeight;
        }
    }

    private void renderCategoryTooltip(GuiGraphics g, int mouseX, int mouseY) {
        WarehouseCategory category = categoryAt(mouseX, mouseY);
        if (category == null || ItemDetailOverlay.isOpen(this)) return;
        List<Component> lines = List.of(
            Component.translatable(category.translationKey()),
            Component.translatable("warehouse.xero_delta.capacity",
                menu.usedCells(category), menu.totalCells(category)));
        g.renderTooltip(font, lines, Optional.empty(), mouseX, mouseY);
    }

    private WarehouseCategory categoryAt(double mouseX, double mouseY) {
        int x = warehouseLeft() + 5;
        int y = topPos + GRID_TOP;
        int height = categoryButtonHeight();
        for (WarehouseCategory category : WarehouseCategory.values()) {
            if (inside(mouseX, mouseY, x, y, SIDEBAR_WIDTH - 10, height - 2)) {
                return category;
            }
            y += height;
        }
        return null;
    }

    public WarehouseCategory warehouseCategoryAt(double mouseX, double mouseY) {
        return categoryAt(mouseX, mouseY);
    }

    private int categoryButtonHeight() {
        int available = Math.max(126, imageHeight - GRID_TOP - WarehouseScrollLayout.FOOTER_HEIGHT);
        return Math.max(18, Math.min(28, available / WarehouseCategory.values().length));
    }

    private boolean deltaLayoutEnabled() {
        return PlayerStatusClientState.INSTANCE.layoutEnabled();
    }

    private int warehouseOffsetX() {
        return deltaLayoutEnabled() ? 0 : VANILLA_PLAYER_WIDTH;
    }

    private int warehouseLeft() {
        return leftPos + warehouseOffsetX();
    }

    private int vanillaInventoryTop() {
        return Math.max(GRID_TOP + 20, (imageHeight - ROW_HEIGHT * 4) / 2);
    }

    private void layoutVanillaPlayerSlots() {
        if (deltaLayoutEnabled()) return;
        int mainTop = vanillaInventoryTop();
        for (int index = menu.warehouseSlots(); index < menu.slots.size(); index++) {
            Slot slot = menu.slots.get(index);
            int inventoryIndex = slot.getContainerSlot();
            SlotFieldUtil.setX(slot, WarehouseScrollLayout.playerSlotX(inventoryIndex));
            SlotFieldUtil.setY(slot,
                WarehouseScrollLayout.playerSlotY(inventoryIndex, mainTop));
        }
    }

    private void renderVanillaPlayerPanel(GuiGraphics graphics) {
        int panelRight = warehouseLeft() - 4;
        graphics.fill(leftPos + 4, topPos + 4, panelRight,
            topPos + imageHeight - 4, 0xCC10181B);
        graphics.renderOutline(leftPos + 4, topPos + 4,
            panelRight - leftPos - 4, imageHeight - 8, 0xFF465754);
        graphics.drawString(font, playerInventoryTitle, leftPos + 10, topPos + 8,
            0xFFE7ECEA, false);
        int mainTop = topPos + vanillaInventoryTop();
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                renderVanillaSlotBackground(graphics,
                    leftPos + 10 + column * ROW_HEIGHT,
                    mainTop + row * ROW_HEIGHT);
            }
        }
        int hotbarY = mainTop + ROW_HEIGHT * 3 + 8;
        for (int column = 0; column < 9; column++) {
            renderVanillaSlotBackground(graphics,
                leftPos + 10 + column * ROW_HEIGHT, hotbarY);
        }
    }

    private static void renderVanillaSlotBackground(GuiGraphics graphics, int x, int y) {
        graphics.fill(x - 1, y - 1, x + 17, y + 17, 0xDD10191B);
        graphics.renderOutline(x - 1, y - 1, 18, 18, 0xFF4F615E);
    }

    private void rememberScrollState() {
        CATEGORY_SCROLLS.put(menu.category(),
            new ScrollState(scrollPixels, targetScrollPixels));
    }

    private record ScrollState(double current, double target) {
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static boolean inside(double x, double y,
                                  int bx, int by, int width, int height) {
        return x >= bx && x < bx + width && y >= by && y < by + height;
    }
}

