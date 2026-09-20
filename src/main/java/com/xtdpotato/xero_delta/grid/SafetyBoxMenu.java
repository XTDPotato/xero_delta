package com.xtdpotato.xero_delta.grid;

import com.xtdpotato.xero_delta.ModMenus;
import com.xtdpotato.xero_delta.item.SafetyBoxItem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class SafetyBoxMenu extends AbstractContainerMenu {
    public final SafetyBoxContainer container;
    public final int gridW, gridH;
    private final Inventory playerInv;
    private final int gridStartSlot, gridEndSlot;
    private final int invStartSlot, hotStartSlot, hotEndSlot;

    public SafetyBoxMenu(int windowId, Inventory playerInv) {
        super(ModMenus.SAFETY_BOX.get(), windowId);
        this.playerInv = playerInv;
        ItemStack found = ItemStack.EMPTY;
        int gw = 3, gh = 3;
        for (var hand : net.minecraft.world.InteractionHand.values()) {
            ItemStack s = playerInv.player.getItemInHand(hand);
            if (s.getItem() instanceof SafetyBoxItem sbi) { found = s; gw = sbi.getGridWidth(); gh = sbi.getGridHeight(); break; }
        }
        this.gridW = gw; this.gridH = gh;
        this.container = new SafetyBoxContainer(found, gw, gh);
        this.container.loadFromComponent();
        addAllSlots(8, 18);
        gridStartSlot = 0; gridEndSlot = gw * gh - 1;
        invStartSlot = gw * gh; hotStartSlot = invStartSlot + 27; hotEndSlot = hotStartSlot + 8;
    }

    public SafetyBoxMenu(int windowId, Inventory playerInv, ItemStack boxStack, int gw, int gh) {
        super(ModMenus.SAFETY_BOX.get(), windowId);
        this.playerInv = playerInv;
        this.gridW = gw; this.gridH = gh;
        this.container = new SafetyBoxContainer(boxStack, gw, gh);
        this.container.loadFromComponent();
        addAllSlots(8, 18);
        gridStartSlot = 0; gridEndSlot = gw * gh - 1;
        invStartSlot = gw * gh; hotStartSlot = invStartSlot + 27; hotEndSlot = hotStartSlot + 8;
    }

    private void addAllSlots(int xOff, int yOff) {
        for (int y = 0; y < gridH; y++)
            for (int x = 0; x < gridW; x++)
                this.addSlot(new SafetyBoxSlot(container, x, y, xOff + x * 18, yOff + y * 18));
        int invY = yOff + gridH * 18 + 14;
        for (int row = 0; row < 3; row++)
            for (int col = 0; col < 9; col++)
                this.addSlot(new Slot(playerInv, col + row * 9 + 9, xOff + col * 18, invY + row * 18));
        int hotY = invY + 3 * 18 + 4;
        for (int col = 0; col < 9; col++)
            this.addSlot(new Slot(playerInv, col, xOff + col * 18, hotY));
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIdx) {
        Slot slot = this.slots.get(slotIdx);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack src = slot.getItem().copy();
        ItemStack remaining = src.copy();
        if (slotIdx <= gridEndSlot) {
            if (!moveItemStackTo(src, invStartSlot, hotEndSlot + 1, false)) return ItemStack.EMPTY;
        } else {
            if (!moveItemStackTo(src, gridStartSlot, gridEndSlot + 1, false)) return ItemStack.EMPTY;
        }
        if (src.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
        if (src.getCount() == remaining.getCount()) return ItemStack.EMPTY;
        slot.onTake(player, src);
        return remaining;
    }

    @Override public boolean stillValid(Player player) { return container.stillValid(player); }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (player instanceof ServerPlayer) {
            container.stopOpen(player);
        }
    }
}
