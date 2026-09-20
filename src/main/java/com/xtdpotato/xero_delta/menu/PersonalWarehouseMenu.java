package com.xtdpotato.xero_delta.menu;

import com.xtdpotato.xero_delta.ModMenus;
import com.xtdpotato.xero_delta.block.PersonalWarehouseBlock;
import com.xtdpotato.xero_delta.data.PersonalWarehouseData;
import com.xtdpotato.xero_delta.data.PlayerLayoutSlotRules;
import com.xtdpotato.xero_delta.data.WarehouseCategory;
import com.xtdpotato.xero_delta.grid.ContainerGridNormalizer;
import com.xtdpotato.xero_delta.grid.DeltaQuickMoveService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.Arrays;

public final class PersonalWarehouseMenu extends AbstractContainerMenu {
    public static final int SORT = 0;
    public static final int UPGRADE = 1;

    private final Container warehouse;
    private final int rows;
    private final WarehouseCategory category;
    private final String warehouseName;
    private final int[] usedCells;
    private final int[] totalCells;

    public PersonalWarehouseMenu(int id, Inventory inventory, FriendlyByteBuf buffer) {
        this(id, inventory, readOpenData(buffer));
    }

    private PersonalWarehouseMenu(int id, Inventory inventory, OpenData data) {
        this(id, inventory, new SimpleContainer(PersonalWarehouseData.COLUMNS * data.rows()),
            data.rows(), data.category(), data.name(), data.usedCells(), data.totalCells());
    }

    public PersonalWarehouseMenu(int id, Inventory inventory, Container warehouse,
                                 int rows, WarehouseCategory category, String warehouseName,
                                 int[] usedCells, int[] totalCells) {
        super(ModMenus.PERSONAL_WAREHOUSE.get(), id);
        this.warehouse = warehouse;
        this.category = category == null ? WarehouseCategory.MAIN : category;
        this.rows = Math.max(this.category.defaultRows(),
            Math.min(this.category.maximumRows(), rows));
        this.warehouseName = warehouseName == null ? "" : warehouseName;
        this.usedCells = sizedCopy(usedCells);
        this.totalCells = sizedCopy(totalCells);

        for (int row = 0; row < this.rows; row++) {
            for (int column = 0; column < PersonalWarehouseData.COLUMNS; column++) {
                addSlot(new WarehouseSlot(warehouse,
                    row * PersonalWarehouseData.COLUMNS + column,
                    8 + column * 18, 26 + row * 18, this.category));
            }
        }
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, column + row * 9 + 9,
                    8 + column * 18, 146 + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, 8 + column * 18, 204));
        }
    }

    private static OpenData readOpenData(FriendlyByteBuf buffer) {
        WarehouseCategory category = WarehouseCategory.byId(buffer.readUtf(32));
        int rows = Math.max(category.defaultRows(),
            Math.min(category.maximumRows(), buffer.readVarInt()));
        String name = buffer.readUtf(PersonalWarehouseData.MAX_NAME_LENGTH * 4);
        int[] used = new int[WarehouseCategory.values().length];
        int[] total = new int[WarehouseCategory.values().length];
        for (int index = 0; index < WarehouseCategory.values().length; index++) {
            used[index] = Math.max(0, buffer.readVarInt());
            total[index] = Math.max(1, buffer.readVarInt());
        }
        return new OpenData(category, rows, name, used, total);
    }

    private static int[] sizedCopy(int[] source) {
        return source == null ? new int[WarehouseCategory.values().length]
            : Arrays.copyOf(source, WarehouseCategory.values().length);
    }

    public int rows() { return rows; }
    public WarehouseCategory category() { return category; }
    public String warehouseName() { return warehouseName; }
    public int warehouseSlots() { return rows * PersonalWarehouseData.COLUMNS; }

    public int usedCells(WarehouseCategory requested) {
        return usedCells[requested.ordinal()];
    }

    public int totalCells(WarehouseCategory requested) {
        return Math.max(1, totalCells[requested.ordinal()]);
    }

    // ContainerGridHelper discovers these methods reflectively.
    public int getColumnsTaken() { return PersonalWarehouseData.COLUMNS; }
    public boolean isStorageInventorySlot(int slotIndex) {
        return slotIndex >= 0 && slotIndex < warehouseSlots();
    }
    public int getNumberOfStorageInventorySlots() { return warehouseSlots(); }
    public int getNumberOfRows() { return rows; }

    @Override public boolean stillValid(Player player) { return warehouse.stillValid(player); }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id == SORT) {
            if (player instanceof ServerPlayer serverPlayer) {
                ContainerGridNormalizer.sortAndRepack(serverPlayer, this);
            }
            return true;
        }
        if (id == UPGRADE && category == WarehouseCategory.MAIN
            && player.getServer() != null) {
            boolean upgraded = PersonalWarehouseData.get(player.getServer())
                .upgrade(player.getUUID());
            if (upgraded && player instanceof ServerPlayer serverPlayer) {
                PersonalWarehouseBlock.open(serverPlayer, category);
            }
            return upgraded;
        }
        return false;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        int warehouseEnd = warehouseSlots();

        if (index < warehouseEnd) {
            if (player instanceof ServerPlayer serverPlayer
                && PlayerLayoutSlotRules.enabled(serverPlayer)) {
                if (!DeltaQuickMoveService.moveIntoPlayerDelta(serverPlayer, stack)) {
                    return ItemStack.EMPTY;
                }
            } else if (!moveItemStackTo(stack, warehouseEnd, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!category.accepts(stack)
            || !moveItemStackTo(stack, 0, warehouseEnd, false)) {
            return ItemStack.EMPTY;
        }

        if (stack.isEmpty()) slot.set(ItemStack.EMPTY);
        else slot.setChanged();
        return copy;
    }

    private record OpenData(WarehouseCategory category, int rows, String name,
                            int[] usedCells, int[] totalCells) {}

    private static final class WarehouseSlot extends Slot {
        private final WarehouseCategory category;

        private WarehouseSlot(Container container, int slot, int x, int y,
                              WarehouseCategory category) {
            super(container, slot, x, y);
            this.category = category;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return category.accepts(stack) && super.mayPlace(stack);
        }
    }
}
