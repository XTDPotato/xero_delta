package com.xtdpotato.xero_delta.menu;

import com.xtdpotato.xero_delta.ModMenus;
import com.xtdpotato.xero_delta.grid.DeltaQuickMoveService;
import com.xtdpotato.xero_delta.compat.DeltaPackAutoEquipService;
import com.xtdpotato.xero_delta.item.DeltaPackItem;
import com.xtdpotato.xero_delta.screen.PackRegionLayout;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public final class GroundPackMenu extends AbstractContainerMenu {
    public static final int CELL = 18;
    public static final int REGION_GAP = 2;
    public static final int GRID_X = 52;
    public static final int GRID_Y = 42;

    public final GroundPackContainer container;
    public final int gridWidth;
    public final int gridHeight;
    public final String identifier;
    private final int entityId;
    private final int packSlots;
    private final int carrierSlotIndex;

    public GroundPackMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        this(id, inventory, readOpenData(inventory, buffer));
    }

    public GroundPackMenu(int id, Inventory inventory, ItemEntity entity,
                          DeltaPackItem pack) {
        this(id, inventory, new OpenData(entity, entity.getId(), entity.getItem().copy(),
            pack.slotIdentifier(), pack.gridWidth(), pack.gridHeight()));
    }

    private GroundPackMenu(int id, Inventory inventory, OpenData data) {
        super(ModMenus.GROUND_PACK.get(), id);
        this.entityId = data.entityId;
        this.identifier = data.identifier;
        this.gridWidth = data.width;
        this.gridHeight = data.height;
        this.container = new GroundPackContainer(
            data.entity, data.carrier, data.width, data.height);
        this.container.startOpen(inventory.player);

        PackRegionLayout layout = layoutFor(data.identifier, data.width, data.height);
        for (int row = 0; row < gridHeight; row++) {
            for (int column = 0; column < gridWidth; column++) {
                PackRegionLayout.Rect cell = layout == null
                    ? new PackRegionLayout.Rect(column * CELL, row * CELL, CELL, CELL)
                    : layout.cellBounds(column, row);
                int x = GRID_X + (cell == null ? column * CELL : cell.x());
                int y = GRID_Y + (cell == null ? row * CELL : cell.y());
                addSlot(new GroundPackSlot(container, identifier,
                    column, row, x, y));
            }
        }
        this.packSlots = gridWidth * gridHeight;
        this.carrierSlotIndex = slots.size();
        addSlot(new GroundPackCarrierSlot(container, identifier, 10, 45));

        int inventoryY = GRID_Y + Math.max(gridHeight * CELL,
            layout == null ? 0 : layout.height()) + 16;
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, column + row * 9 + 9,
                    8 + column * CELL, inventoryY + row * CELL));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column,
                8 + column * CELL, inventoryY + 58));
        }
    }

    public int entityId() { return entityId; }
    public ItemStack carrierStack() { return container.carrier(); }
    public boolean isPackSlot(int index) { return index >= 0 && index < packSlots; }
    public boolean isCarrierSlot(int index) { return index == carrierSlotIndex; }

    public static PackRegionLayout layoutFor(String identifier, int width, int height) {
        if ("chest_rig".equals(identifier) && width == 4 && height == 6) {
            return new PackRegionLayout(List.of(
                new PackRegionLayout.LogicalRegion(0, 0, 2, 1),
                new PackRegionLayout.LogicalRegion(2, 0, 2, 1),
                new PackRegionLayout.LogicalRegion(0, 1, 2, 2),
                new PackRegionLayout.LogicalRegion(2, 1, 2, 2),
                new PackRegionLayout.LogicalRegion(0, 3, 1, 3),
                new PackRegionLayout.LogicalRegion(1, 3, 1, 3),
                new PackRegionLayout.LogicalRegion(2, 3, 2, 3)), CELL, REGION_GAP);
        }
        return new PackRegionLayout(List.of(
            new PackRegionLayout.LogicalRegion(0, 0, width, height)),
            CELL, REGION_GAP);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack original = slot.getItem();
        ItemStack result = original.copy();
        if (index < packSlots) {
            if (!(player instanceof ServerPlayer serverPlayer)) return ItemStack.EMPTY;
            ItemStack moving = original.copy();
            if (!DeltaQuickMoveService.moveIntoPlayerDelta(serverPlayer, moving)) {
                return ItemStack.EMPTY;
            }
            slot.set(moving);
        } else if (index == carrierSlotIndex) {
            if (!(player instanceof ServerPlayer serverPlayer)) return ItemStack.EMPTY;
            ItemStack moving = original.copy();
            if (!DeltaPackAutoEquipService.tryQuickEquipLoadout(serverPlayer, moving)
                || !moving.isEmpty()) return ItemStack.EMPTY;
            slot.set(ItemStack.EMPTY);
        } else if (!moveItemStackTo(original, 0, packSlots, false)) {
            return ItemStack.EMPTY;
        }
        if (original.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
        return result;
    }

    @Override public boolean stillValid(Player player) { return container.stillValid(player); }

    @Override
    public void removed(Player player) {
        super.removed(player);
        container.stopOpen(player);
        if (player instanceof ServerPlayer && container.entity() != null
            && container.entity().isAlive()) {
            container.entity().setDefaultPickUpDelay();
        }
    }

    private static OpenData readOpenData(Inventory inventory,
                                         RegistryFriendlyByteBuf buffer) {
        int entityId = buffer.readVarInt();
        String identifier = buffer.readUtf(32);
        int width = Math.max(1, Math.min(16, buffer.readVarInt()));
        int height = Math.max(1, Math.min(16, buffer.readVarInt()));
        Entity resolved = inventory.player.level().getEntity(entityId);
        ItemEntity entity = resolved instanceof ItemEntity item ? item : null;
        ItemStack carrier = entity == null ? ItemStack.EMPTY : entity.getItem().copy();
        return new OpenData(entity, entityId, carrier, identifier, width, height);
    }

    private record OpenData(ItemEntity entity, int entityId, ItemStack carrier,
                            String identifier, int width, int height) {
    }
}
