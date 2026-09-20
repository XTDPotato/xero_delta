package com.xtdpotato.xero_delta.grid;

import com.xtdpotato.xero_delta.Config;
import com.xtdpotato.xero_delta.tag.ModTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class SafetyBoxSlot extends Slot {
    public final int gridX, gridY;

    public SafetyBoxSlot(SafetyBoxContainer container, int gx, int gy, int px, int py) {
        super(container, gy * container.getGridWidth() + gx, px, py);
        this.gridX = gx; this.gridY = gy;
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (stack.is(ModTags.SAFETY_BOX)) return false;
        if (Config.INSTANCE.isBlacklisted(stack)) return false;
        return true;
    }

    @Override
    public boolean mayPickup(Player player) {
        return hasItem();
    }

    @Override public int getMaxStackSize() { return 64; }
}