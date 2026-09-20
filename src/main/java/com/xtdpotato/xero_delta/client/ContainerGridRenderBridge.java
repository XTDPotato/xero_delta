package com.xtdpotato.xero_delta.client;

import com.xtdpotato.xero_delta.data.ItemSize;
import com.xtdpotato.xero_delta.grid.ContainerGridHelper;
import com.xtdpotato.xero_delta.grid.GridBackingStore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.Set;

public final class ContainerGridRenderBridge {
    private ContainerGridRenderBridge() {
    }

    /**
     * Renders the carried-stack placement state and forwards covered-cell
     * tooltips to the footprint anchor. Third-party screens that replace
     * AbstractContainerScreen#render can call this from their own render tail.
     */
    public static void renderInteractionOverlay(AbstractContainerScreen<?> screen, GuiGraphics graphics,
                                                int mouseX, int mouseY) {
        if (screen == null) return;
        AbstractContainerMenu menu = screen.getMenu();
        if (!ClientDataCache.INSTANCE.isItemGridEnabled(menu.getClass().getName())) return;
        ContainerGridHelper.prepare(menu, ClientDataCache.INSTANCE::getSize,
            ClientDataCache.INSTANCE.revision());

        ClientGridRotation.update(Minecraft.getInstance());
        ItemStack carried = menu.getCarried();
        Set<Slot> ignoredAnchors = Set.of();
        if (carried.isEmpty()) {
            carried = DeltaContainerLayoutController.virtualCarriedStack(screen);
            Slot sourceAnchor = DeltaContainerLayoutController
                .virtualExternalSourceAnchor(screen);
            if (sourceAnchor != null) ignoredAnchors = Set.of(sourceAnchor);
        }
        Slot hovered = slotAt(screen, mouseX, mouseY);
        if (carried.isEmpty()) {
            if (!DeltaContainerLayoutController.isActive(screen)) {
                renderCoveredCellTooltip(menu, graphics, hovered, mouseX, mouseY);
            }
            return;
        }
        if (hovered == null || !ContainerGridHelper.isGridSlotEnabled(menu, hovered)) return;

        double fractionX = slotFractionX(screen, hovered, mouseX);
        double fractionY = slotFractionY(screen, hovered, mouseY);
        var placement = ContainerGridHelper.resolveCursorPlacement(menu, hovered, carried,
            fractionX, fractionY, ClientGridRotation.allowAutoRotate(), ignoredAnchors,
            ClientDataCache.INSTANCE::getSize);
        if (placement == null) return;

        ItemSize size = placement.status() == GridBackingStore.PlacementStatus.CAN_STACK
            && placement.anchor() != null
            ? ContainerGridHelper.orientedSize(placement.anchor().getItem(), ClientDataCache.INSTANCE::getSize)
            : ContainerGridHelper.orientedSize(carried, placement.rotated(), ClientDataCache.INSTANCE::getSize);
        Slot anchor = placement.anchor() != null ? placement.anchor() : hovered;
        int color = switch (placement.status()) {
            case CAN_PLACE, CAN_STACK -> 0x9000CC00;
            case CAN_SWAP -> 0x90FFAA00;
            default -> 0x90CC2222;
        };

        Set<Slot> cells = ContainerGridHelper.footprintCells(menu, anchor, size);
        if (cells.isEmpty()) cells = ContainerGridHelper.clippedFootprintCells(menu, anchor, size);
        if (cells.isEmpty() && ContainerGridHelper.isGridSlotEnabled(menu, anchor)) cells = Set.of(anchor);
        if (cells.isEmpty()) return;
        int maxCellX = cells.stream().mapToInt(cell -> cell.x).max().orElse(anchor.x);
        int maxCellY = cells.stream().mapToInt(cell -> cell.y).max().orElse(anchor.y);
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(screen.getGuiLeft(), screen.getGuiTop(), 300);
        try {
            for (Slot cell : cells) {
                int cellRight = cell.x + (cell.x == maxCellX
                    ? ContainerGridHelper.SLOT_FACE_SIZE : ContainerGridHelper.SLOT_STEP);
                int cellBottom = cell.y + (cell.y == maxCellY
                    ? ContainerGridHelper.SLOT_FACE_SIZE : ContainerGridHelper.SLOT_STEP);
                graphics.fill(cell.x, cell.y, cellRight, cellBottom, color);
            }
        } finally {
            pose.popPose();
        }
    }

    private static void renderCoveredCellTooltip(AbstractContainerMenu menu, GuiGraphics graphics,
                                                  Slot hovered, int mouseX, int mouseY) {
        if (hovered == null || !ContainerGridHelper.isGridSlotEnabled(menu, hovered)) return;
        Slot anchor = ContainerGridHelper.footprintAnchorFor(menu, hovered, ClientDataCache.INSTANCE::getSize);
        if (anchor != null && anchor != hovered && !anchor.getItem().isEmpty()) {
            graphics.renderTooltip(Minecraft.getInstance().font, anchor.getItem(), mouseX, mouseY);
        }
    }

    private static double slotFractionX(AbstractContainerScreen<?> screen, Slot slot, double mouseX) {
        double local = mouseX - screen.getGuiLeft() - slot.x;
        return Math.max(0.0, Math.min(0.999, local / ContainerGridHelper.SLOT_STEP));
    }

    private static double slotFractionY(AbstractContainerScreen<?> screen, Slot slot, double mouseY) {
        double local = mouseY - screen.getGuiTop() - slot.y;
        return Math.max(0.0, Math.min(0.999, local / ContainerGridHelper.SLOT_STEP));
    }

    private static Slot slotAt(AbstractContainerScreen<?> screen, double mouseX, double mouseY) {
        AbstractContainerMenu menu = screen.getMenu();
        double localX = mouseX - screen.getGuiLeft();
        double localY = mouseY - screen.getGuiTop();
        Slot hit = ContainerGridHelper.hitSlot(menu, localX, localY);
        if (hit != null) return hit;
        return null;
    }

    public static boolean renderSizedSlotItem(GuiGraphics graphics, ItemStack stack, int renderX, int renderY) {
        SlotRenderContext context = findContext(stack, renderX, renderY);
        if (context == null) return false;

        AbstractContainerMenu menu = context.screen().getMenu();
        Slot slot = context.slot();
        // A legacy/native storage transfer can leave a 1x1 stack in a cell
        // covered by a larger anchor. The anchor owns that visual footprint;
        // suppress the covered stack instead of drawing it over the anchor.
        Slot coveringAnchor = ContainerGridHelper.footprintAnchorFor(menu, slot, ClientDataCache.INSTANCE::getSize);
        if (coveringAnchor != null && coveringAnchor != slot) return true;
        ItemSize size = ContainerGridHelper.orientedSize(stack, GridBackingStore.isRotated(stack),
            ClientDataCache.INSTANCE::getSize);
        if (size.width() <= 1 && size.height() <= 1) return false;
        if (!ContainerGridHelper.isAnchorSlot(menu, slot, ClientDataCache.INSTANCE::getSize)) return false;
        Set<Slot> cells = ContainerGridHelper.footprintCells(menu, slot, size);
        if (cells.size() != size.width() * size.height()) return false;

        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxCellX = Integer.MIN_VALUE;
        int maxCellY = Integer.MIN_VALUE;
        for (Slot cell : cells) {
            minX = Math.min(minX, cell.x + context.offsetX());
            minY = Math.min(minY, cell.y + context.offsetY());
            maxCellX = Math.max(maxCellX, cell.x + context.offsetX());
            maxCellY = Math.max(maxCellY, cell.y + context.offsetY());
        }
        for (Slot cell : cells) {
            int cellX = cell.x + context.offsetX();
            int cellY = cell.y + context.offsetY();
            // Cells remain seamless inside a footprint, but its final row/column ends at the
            // visible slot face instead of spilling into the next slot's two-pixel gutter.
            int cellRight = cellX + (cellX == maxCellX
                ? ContainerGridHelper.SLOT_FACE_SIZE : ContainerGridHelper.SLOT_STEP);
            int cellBottom = cellY + (cellY == maxCellY
                ? ContainerGridHelper.SLOT_FACE_SIZE : ContainerGridHelper.SLOT_STEP);
            graphics.fill(cellX, cellY, cellRight, cellBottom, QualityItemBackground.color(stack));
        }
        GridItemRenderer.renderSizedItem(graphics, Minecraft.getInstance().font, stack, minX, minY,
            maxCellX + ContainerGridHelper.SLOT_FACE_SIZE - minX,
            maxCellY + ContainerGridHelper.SLOT_FACE_SIZE - minY,
            GridBackingStore.isRotated(stack), ClientDataCache.INSTANCE.shouldRotateTexture(stack),
            ClientDataCache.INSTANCE.shouldStretchTexture(stack),
            ClientDataCache.INSTANCE.proportionalTextureScale(stack));
        return true;
    }

    public static boolean suppressVanillaDecorations(ItemStack stack, int renderX, int renderY) {
        SlotRenderContext context = findContext(stack, renderX, renderY);
        if (context == null) return false;
        ItemSize size = ClientDataCache.INSTANCE.getSize(stack);
        Slot coveringAnchor = ContainerGridHelper.footprintAnchorFor(context.screen().getMenu(), context.slot(),
            ClientDataCache.INSTANCE::getSize);
        if (coveringAnchor != null && coveringAnchor != context.slot()) return true;
        return size.width() > 1 || size.height() > 1;
    }

    private static SlotRenderContext findContext(ItemStack stack, int renderX, int renderY) {
        if (stack.isEmpty() || QualityItemBackground.isSuppressed()) return null;
        if (!(Minecraft.getInstance().screen instanceof AbstractContainerScreen<?> screen)) return null;
        AbstractContainerMenu menu = screen.getMenu();
        if (!ClientDataCache.INSTANCE.isItemGridEnabled(menu.getClass().getName())) return null;

        Slot localSlot = ContainerGridHelper.slotAt(menu, renderX, renderY);
        if (localSlot != null && !localSlot.getItem().isEmpty()
            && ItemStack.isSameItemSameComponents(localSlot.getItem(), stack)) {
            return new SlotRenderContext(screen, localSlot, 0, 0);
        }
        Slot guiSlot = ContainerGridHelper.slotAt(menu,
            renderX - screen.getGuiLeft(), renderY - screen.getGuiTop());
        if (guiSlot != null && !guiSlot.getItem().isEmpty()
            && ItemStack.isSameItemSameComponents(guiSlot.getItem(), stack)) {
            return new SlotRenderContext(screen, guiSlot, screen.getGuiLeft(), screen.getGuiTop());
        }

        // A third-party screen may move slots outside its normal render pass.
        // Keep the old scan as a correctness fallback, but it is no longer the
        // hot path for Sophisticated's regular per-slot rendering.
        for (Slot slot : menu.slots) {
            if (!slot.isActive() || slot.getItem().isEmpty()) continue;
            if (!ContainerGridHelper.isGridSlotEnabled(menu, slot)) continue;
            if (!ItemStack.isSameItemSameComponents(slot.getItem(), stack)) continue;
            if (near(slot.x, renderX) && near(slot.y, renderY)) {
                return new SlotRenderContext(screen, slot, 0, 0);
            }
            if (near(screen.getGuiLeft() + slot.x, renderX)
                && near(screen.getGuiTop() + slot.y, renderY)) {
                return new SlotRenderContext(screen, slot, screen.getGuiLeft(), screen.getGuiTop());
            }
        }
        return null;
    }

    private static boolean near(int expected, int actual) {
        return Math.abs(expected - actual) <= 1;
    }

    private record SlotRenderContext(AbstractContainerScreen<?> screen, Slot slot, int offsetX, int offsetY) {
    }

}
