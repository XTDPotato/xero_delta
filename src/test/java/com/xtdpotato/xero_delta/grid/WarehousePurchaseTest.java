package com.xtdpotato.xero_delta.grid;

import com.xtdpotato.xero_delta.data.PersonalWarehouseData;
import com.xtdpotato.xero_delta.data.WarehouseCategory;
import com.xtdpotato.xero_delta.menu.PersonalWarehouseMenu;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WarehousePurchaseTest {
    @Test
    void returnsAnUnmodifiedCopyWhenNoWarehouseIsAccessible() {
        var purchased = new ItemStack(Items.PAPER, 12);
        var remainder = WarehouseTransferService.storePurchase((net.minecraft.server.level.ServerPlayer) null, purchased);
        assertEquals(12, remainder.getCount());
        assertNotSame(purchased, remainder);
        assertEquals(12, purchased.getCount());
    }

    @Test
    void storesPurchaseWithoutChangingTheInputStack() {
        var menu = menu();
        var purchased = new ItemStack(Items.PAPER, 100);
        assertTrue(WarehouseTransferService.storePurchase(menu, purchased).isEmpty());
        assertEquals(100, purchased.getCount());
        assertEquals(100, paperCount(menu));
    }

    @Test
    void returnsOnlyOverflowAndDoesNotLoseItemsWhenFull() {
        var menu = menu();
        for (int slot = 0; slot < menu.warehouseSlots(); slot++) {
            menu.slots.get(slot).set(new ItemStack(Items.PAPER, 64));
        }
        int before = paperCount(menu);
        var incoming = new ItemStack(Items.PAPER, 100);
        assertEquals(100, WarehouseTransferService.storePurchase(menu, incoming).getCount());
        assertEquals(before, paperCount(menu));
        menu.slots.get(0).set(ItemStack.EMPTY);
        assertEquals(36, WarehouseTransferService.storePurchase(menu, incoming).getCount());
        assertEquals(before, paperCount(menu));
    }

    private static int paperCount(PersonalWarehouseMenu menu) {
        return menu.slots.subList(0, menu.warehouseSlots()).stream()
            .mapToInt(slot -> slot.getItem().is(Items.PAPER) ? slot.getItem().getCount() : 0).sum();
    }

    private static PersonalWarehouseMenu menu() {
        int rows = WarehouseCategory.MAIN.defaultRows();
        return new PersonalWarehouseMenu(-1, new Inventory(null),
            new SimpleContainer(rows * PersonalWarehouseData.COLUMNS), rows,
            WarehouseCategory.MAIN, "", new int[7], new int[7]);
    }
}
