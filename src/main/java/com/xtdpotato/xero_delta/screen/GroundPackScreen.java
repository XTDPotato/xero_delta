package com.xtdpotato.xero_delta.screen;

import com.xtdpotato.xero_delta.screen.material.Material3Theme;

import com.xtdpotato.xero_delta.client.DeltaGridCellRenderer;
import com.xtdpotato.xero_delta.client.ClientDataCache;
import com.xtdpotato.xero_delta.client.GridItemRenderer;
import com.xtdpotato.xero_delta.client.QualityItemBackground;
import com.xtdpotato.xero_delta.data.ItemSize;
import com.xtdpotato.xero_delta.grid.ContainerGridHelper;
import com.xtdpotato.xero_delta.grid.GridBackingStore;
import com.xtdpotato.xero_delta.menu.GroundPackCarrierSlot;
import com.xtdpotato.xero_delta.menu.GroundPackMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.Set;

public final class GroundPackScreen extends AbstractContainerScreen<GroundPackMenu> {
    private final PackRegionLayout layout;

    public GroundPackScreen(GroundPackMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.layout = GroundPackMenu.layoutFor(
            menu.identifier, menu.gridWidth, menu.gridHeight);
        int gridPixelWidth = layout.width();
        int gridPixelHeight = layout.height();
        this.imageWidth = Math.max(176, GroundPackMenu.GRID_X + gridPixelWidth + 10);
        this.imageHeight = Math.max(148, GroundPackMenu.GRID_Y + gridPixelHeight + 12);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick,
                            int mouseX, int mouseY) {
        int left = leftPos;
        int top = topPos;
        graphics.fill(left, top, left + imageWidth, top + imageHeight, Material3Theme.SURFACE_CONTAINER);
        graphics.renderOutline(left, top, imageWidth, imageHeight, Material3Theme.OUTLINE_VARIANT);
        graphics.fill(left + 1, top + 30, left + imageWidth - 1, top + 31,
            Material3Theme.OUTLINE_VARIANT);

        for (int row = 0; row < menu.gridHeight; row++) {
            for (int column = 0; column < menu.gridWidth; column++) {
                PackRegionLayout.Rect cell = layout.cellBounds(column, row);
                if (cell == null) continue;
                DeltaGridCellRenderer.render(graphics,
                    left + GroundPackMenu.GRID_X + cell.x(),
                    top + GroundPackMenu.GRID_Y + cell.y(), GroundPackMenu.CELL);
            }
        }
    }

    /** Draws ground-pack contents with a seamless, full-cell footprint. */
    public boolean renderGroundPackSlot(GuiGraphics graphics, Slot slot) {
        if (slot instanceof GroundPackCarrierSlot) {
            ItemStack carrier = slot.getItem();
            if (carrier.isEmpty()) return true;
            int x = slot.x - 2;
            int y = slot.y - 2;
            int size = 36;
            graphics.fill(x, y, x + size, y + size, QualityItemBackground.color(carrier));
            GridItemRenderer.renderSizedItem(graphics, font, carrier, x, y, size, size,
                false, ClientDataCache.INSTANCE.shouldRotateTexture(carrier),
                ClientDataCache.INSTANCE.shouldStretchTexture(carrier),
                ClientDataCache.INSTANCE.proportionalTextureScale(carrier));
            return true;
        }
        if (!menu.isPackSlot(slot.index)) return false;
        ItemStack stack = slot.getItem();
        if (stack.isEmpty()) return true;
        ContainerGridHelper.prepare(menu, ClientDataCache.INSTANCE::getSize,
            ClientDataCache.INSTANCE.revision());
        Slot anchor = ContainerGridHelper.footprintAnchorFor(
            menu, slot, ClientDataCache.INSTANCE::getSize);
        if (anchor != null && anchor != slot) return true;
        ItemSize size = ContainerGridHelper.orientedSize(stack,
            GridBackingStore.isRotated(stack), ClientDataCache.INSTANCE::getSize);
        Set<Slot> cells = ContainerGridHelper.footprintCells(menu, slot, size);
        if (cells.size() != size.width() * size.height()) return false;
        int minX = cells.stream().mapToInt(cell -> cell.x).min().orElse(slot.x);
        int minY = cells.stream().mapToInt(cell -> cell.y).min().orElse(slot.y);
        int maxX = cells.stream().mapToInt(cell -> cell.x).max().orElse(slot.x)
            + GroundPackMenu.CELL;
        int maxY = cells.stream().mapToInt(cell -> cell.y).max().orElse(slot.y)
            + GroundPackMenu.CELL;
        graphics.fill(minX, minY, maxX, maxY, QualityItemBackground.color(stack));
        GridItemRenderer.renderSizedItem(graphics, font, stack, minX, minY,
            maxX - minX, maxY - minY, GridBackingStore.isRotated(stack),
            ClientDataCache.INSTANCE.shouldRotateTexture(stack),
            ClientDataCache.INSTANCE.shouldStretchTexture(stack),
            ClientDataCache.INSTANCE.proportionalTextureScale(stack));
        return true;
    }

    public int[] groundPackSlotBounds(Slot slot) {
        if (slot instanceof GroundPackCarrierSlot) {
            return new int[]{slot.x - 2, slot.y - 2, 36, 36};
        }
        if (!menu.isPackSlot(slot.index)) return null;
        ContainerGridHelper.prepare(menu, ClientDataCache.INSTANCE::getSize,
            ClientDataCache.INSTANCE.revision());
        Slot anchor = ContainerGridHelper.footprintAnchorFor(
            menu, slot, ClientDataCache.INSTANCE::getSize);
        if (anchor == null) anchor = slot;
        ItemStack stack = anchor.getItem();
        ItemSize size = stack.isEmpty() ? ItemSize.ONE
            : ContainerGridHelper.orientedSize(stack,
                GridBackingStore.isRotated(stack), ClientDataCache.INSTANCE::getSize);
        Set<Slot> cells = ContainerGridHelper.footprintCells(menu, anchor, size);
        if (cells.isEmpty()) return new int[]{anchor.x, anchor.y,
            GroundPackMenu.CELL, GroundPackMenu.CELL};
        int minX = cells.stream().mapToInt(cell -> cell.x).min().orElse(anchor.x);
        int minY = cells.stream().mapToInt(cell -> cell.y).min().orElse(anchor.y);
        int maxX = cells.stream().mapToInt(cell -> cell.x).max().orElse(anchor.x)
            + GroundPackMenu.CELL;
        int maxY = cells.stream().mapToInt(cell -> cell.y).max().orElse(anchor.y)
            + GroundPackMenu.CELL;
        return new int[]{minX, minY, maxX - minX, maxY - minY};
    }

    public Slot groundCarrierSlotAt(double localX, double localY) {
        for (Slot slot : menu.slots) {
            if (!(slot instanceof GroundPackCarrierSlot)) continue;
            int[] bounds = groundPackSlotBounds(slot);
            if (localX >= bounds[0] && localX < bounds[0] + bounds[2]
                && localY >= bounds[1] && localY < bounds[1] + bounds[3]) return slot;
        }
        return null;
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        int used = 0;
        for (int index = 0; index < menu.gridWidth * menu.gridHeight; index++) {
            ItemStack stack = menu.slots.get(index).getItem();
            if (stack.isEmpty()) continue;
            var size = GridBackingStore.sizeOfStored(stack);
            used += size.width() * size.height();
        }
        Component capacity = Component.literal(" " + Math.min(used,
            menu.gridWidth * menu.gridHeight) + "/" + menu.gridWidth * menu.gridHeight);
        graphics.drawString(font, title.copy().append(capacity),
            10, 11, 0xFFF1F5F3, false);
    }
}

