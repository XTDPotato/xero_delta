package com.xtdpotato.xero_delta.menu;

import com.xtdpotato.xero_delta.grid.GridBackingStore;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public final class GroundPackSlot extends Slot {
    private final String identifier;

    public GroundPackSlot(GroundPackContainer container, String identifier,
                          int column, int row, int x, int y) {
        super(container, row * container.width() + column, x, y);
        this.identifier = identifier;
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        return stack != null && !stack.isEmpty()
            && !GridBackingStore.isBlockedInEquippedStorage(identifier, stack);
    }

    @Override public boolean mayPickup(Player player) { return hasItem(); }
}
