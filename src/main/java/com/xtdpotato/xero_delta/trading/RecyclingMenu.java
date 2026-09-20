package com.xtdpotato.xero_delta.trading;

import com.xtdpotato.xero_delta.ModMenus;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

public final class RecyclingMenu extends AbstractContainerMenu {
    public RecyclingMenu(int containerId, Inventory inventory) {
        super(ModMenus.RECYCLING_STATION.get(), containerId);
    }

    @Override
    public boolean stillValid(Player player) {
        return player.isAlive();
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        return ItemStack.EMPTY;
    }
}
