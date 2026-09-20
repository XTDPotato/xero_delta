package com.xtdpotato.xero_delta.item;

import com.xtdpotato.xero_delta.ModDataComponents;
import com.xtdpotato.xero_delta.client.GridPreviewData;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

import java.util.List;
import java.util.Optional;

/** Curios-equipped Delta pack whose contents stay in the item data component. */
public final class DeltaPackItem extends Item implements ICurioItem {
    private final String slotIdentifier;
    private final int gridWidth;
    private final int gridHeight;
    private final double speedPenalty;

    public DeltaPackItem(String slotIdentifier, int gridWidth, int gridHeight,
                         double speedPenalty, Properties properties) {
        super(properties);
        this.slotIdentifier = slotIdentifier;
        this.gridWidth = gridWidth;
        this.gridHeight = gridHeight;
        this.speedPenalty = speedPenalty;
    }

    public int gridWidth() { return gridWidth; }
    public int gridHeight() { return gridHeight; }
    public int capacity() { return gridWidth * gridHeight; }
    public double speedPenalty() { return speedPenalty; }
    public String slotIdentifier() { return slotIdentifier; }

    /** Pouch boundaries used by the DAR rig; full-grid packs return one region. */
    public List<GridRegion> regions() {
        if ("chest_rig".equals(slotIdentifier) && gridWidth == 4 && gridHeight == 6) {
            return List.of(
                new GridRegion(0, 0, 2, 1), new GridRegion(2, 0, 2, 1),
                new GridRegion(0, 1, 2, 2), new GridRegion(2, 1, 2, 2),
                new GridRegion(0, 3, 1, 3), new GridRegion(1, 3, 1, 3),
                new GridRegion(2, 3, 2, 3));
        }
        return List.of(new GridRegion(0, 0, gridWidth, gridHeight));
    }

    public record GridRegion(int x, int y, int width, int height) {
        public boolean contains(int itemX, int itemY, int itemWidth, int itemHeight) {
            return itemX >= x && itemY >= y
                && itemX + itemWidth <= x + width
                && itemY + itemHeight <= y + height;
        }
    }

    @Override
    public boolean canEquip(SlotContext context, ItemStack stack) {
        return slotIdentifier.equals(context.identifier());
    }

    @Override
    public Optional<TooltipComponent> getTooltipImage(ItemStack stack) {
        // Chest rigs and backpacks deliberately keep their contents private in tooltips.
        // SafetyBoxItem retains the secure-container preview.
        return Optional.empty();
    }
}
