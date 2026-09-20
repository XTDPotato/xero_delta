package com.xtdpotato.xero_delta.menu;

import com.xtdpotato.xero_delta.ModMenus;
import com.xtdpotato.xero_delta.entity.CorpseEntity;
import com.xtdpotato.xero_delta.data.RaidEarningsData;
import com.xtdpotato.xero_delta.data.PlayerLayoutSlotRules;
import com.xtdpotato.xero_delta.data.ServerItemRules;
import com.xtdpotato.xero_delta.item.DeltaPackItem;
import com.xtdpotato.xero_delta.grid.DeltaQuickMoveService;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public final class CorpseMenu extends AbstractContainerMenu {
    public static final int PRESENTATION_SLOTS = 13;
    public static final int CORPSE_SLOTS = CorpseEntity.SLOT_COUNT;
    public static final int PLAYER_SLOT_START = CORPSE_SLOTS;
    public static final int CLAIM_RAID_EARNINGS = 0;
    public static final int PRIMARY_ONE_SLOT = 0;
    public static final int PRIMARY_TWO_SLOT = 1;
    public static final int HELMET_SLOT = 2;
    public static final int CHEST_SLOT = 3;
    public static final int SIDEARM_SLOT = 4;
    public static final int POCKET_START = 5;
    public static final int POCKET_END = 9;
    public static final int CHEST_RIG_SLOT = 10;
    public static final int BACKPACK_SLOT = 11;
    /**
     * Reserved terminal corpse slot kept out of the visible inventory to
     * preserve the existing corpse data layout after the Archive Bag item was
     * removed.
     */
    public static final int RESERVED_CORPSE_SLOT = 12;
    public static final int OVERFLOW_START = 13;
    private final Container corpseInventory;
    private final CorpseEntity corpse;
    private final int corpseEntityId;
    private final ContainerData raidEarningsData;

    public CorpseMenu(int id, Inventory playerInventory, RegistryFriendlyByteBuf buffer) {
        this(id, playerInventory, new SimpleContainer(CORPSE_SLOTS), null,
            buffer.readVarInt(), new SimpleContainerData(2));
    }

    public CorpseMenu(int id, Inventory playerInventory, Container corpseInventory,
                      CorpseEntity corpse) {
        this(id, playerInventory, corpseInventory, corpse,
            corpse == null ? -1 : corpse.getId(), corpseData(corpse));
    }

    private CorpseMenu(int id, Inventory playerInventory, Container corpseInventory,
                       CorpseEntity corpse, int corpseEntityId,
                       ContainerData raidEarningsData) {
        super(ModMenus.CORPSE.get(), id);
        this.corpseInventory = corpseInventory;
        this.corpse = corpse;
        this.corpseEntityId = corpseEntityId;
        this.raidEarningsData = raidEarningsData;
        checkContainerSize(corpseInventory, CORPSE_SLOTS);
        checkContainerDataCount(raidEarningsData, 2);
        addDataSlots(raidEarningsData);
        corpseInventory.startOpen(playerInventory.player);
        for (int index = 0; index < CORPSE_SLOTS; index++) {
            int corpseSlot = index;
            int[] position = corpseSlotBasePosition(index);
            addSlot(new Slot(corpseInventory, index, position[0], position[1]) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return mayPlaceInCorpseSlot(corpseSlot, stack, playerInventory.player);
                }

                @Override
                public boolean mayPickup(Player player) {
                    // A corpse's worn carriers are part of the corpse layout,
                    // not loose ground loot. Their contents remain editable
                    // through the carrier grid, but the carrier itself must
                    // not be picked up by a normal slot click.
                    return corpseSlot < CHEST_RIG_SLOT;
                }

                @Override
                public void setChanged() {
                    super.setChanged();
                    if (corpse != null) corpse.syncCarrierSlot(corpseSlot);
                }
            });
        }
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(playerInventory, column + row * 9 + 9,
                    42 + column * 18, 220 + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(playerInventory, column,
                42 + column * 18, 282));
        }
    }

    public static int[] corpseSlotBasePosition(int index) {
        if (index == PRIMARY_ONE_SLOT) return new int[]{336, 52};
        if (index == PRIMARY_TWO_SLOT) return new int[]{477, 52};
        if (index == HELMET_SLOT) return new int[]{336, 112};
        if (index == CHEST_SLOT) return new int[]{404, 112};
        if (index == SIDEARM_SLOT) return new int[]{472, 112};
        if (index >= POCKET_START && index <= POCKET_END) {
            return new int[]{326 + (index - POCKET_START) * 36 + 10, 294};
        }
        if (index == CHEST_RIG_SLOT) return new int[]{336, 230};
        if (index == BACKPACK_SLOT) return new int[]{336, 366};
        if (index >= RESERVED_CORPSE_SLOT) return new int[]{-1000, -1000};
        return new int[]{-1000, -1000};
    }

    private static boolean mayPlaceInCorpseSlot(int slot, ItemStack stack, Player player) {
        if (stack == null || stack.isEmpty() || slot >= RESERVED_CORPSE_SLOT) return false;
        return switch (slot) {
            case PRIMARY_ONE_SLOT, PRIMARY_TWO_SLOT -> true;
            case HELMET_SLOT -> stack.canEquip(EquipmentSlot.HEAD, player);
            case CHEST_SLOT -> stack.canEquip(EquipmentSlot.CHEST, player);
            case SIDEARM_SLOT -> PlayerLayoutSlotRules.isTaczGun(stack)
                && PlayerLayoutSlotRules.isHandgun(stack);
            case POCKET_START, POCKET_START + 1, POCKET_START + 2,
                 POCKET_START + 3, POCKET_END -> {
                var size = ServerItemRules.getSizeFor(stack);
                yield size.width() == 1 && size.height() == 1;
            }
            case CHEST_RIG_SLOT, BACKPACK_SLOT ->
                CorpseEntity.isCarrierStackForSlot(slot, stack);
            default -> false;
        };
    }

    /** Returns the corpse presentation slot for a wearable Delta carrier. */
    private static int carrierSlotFor(ItemStack stack) {
        if (!(stack.getItem() instanceof DeltaPackItem pack)) return -1;
        return switch (pack.slotIdentifier()) {
            case "chest_rig" -> CHEST_RIG_SLOT;
            case "backpack" -> BACKPACK_SLOT;
            default -> -1;
        };
    }

    private static ContainerData corpseData(CorpseEntity corpse) {
        if (corpse == null) return new SimpleContainerData(2);
        return new ContainerData() {
            @Override
            public int get(int index) {
                long value = corpse.raidEarnings();
                return index == 0 ? (int) value : (int) (value >>> 32);
            }

            @Override
            public void set(int index, int value) {
            }

            @Override
            public int getCount() {
                return 2;
            }
        };
    }

    public int corpseEntityId() {
        return corpseEntityId;
    }

    public CorpseEntity corpseEntity() {
        return corpse;
    }

    public long raidEarnings() {
        return Integer.toUnsignedLong(raidEarningsData.get(0))
            | (long) raidEarningsData.get(1) << 32;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id != CLAIM_RAID_EARNINGS || corpse == null
            || !(player instanceof ServerPlayer serverPlayer)) return false;
        if (corpse.raidEarnings() <= 1L) return false;
        long claimed = corpse.takeRaidEarnings();
        RaidEarningsData.get(serverPlayer.server).add(serverPlayer.getUUID(), claimed);
        broadcastChanges();
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size() || !stillValid(player)) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        if (index == CHEST_RIG_SLOT || index == BACKPACK_SLOT) {
            if (!(player instanceof ServerPlayer serverPlayer)) return ItemStack.EMPTY;
            ItemStack original = slot.getItem().copy();
            ItemStack moving = original.copy();
            if (!DeltaQuickMoveService.moveIntoPlayerInventory(serverPlayer, moving)) {
                return ItemStack.EMPTY;
            }
            slot.set(moving);
            slot.setChanged();
            return original;
        }
        if (index < CORPSE_SLOTS && index >= RESERVED_CORPSE_SLOT) return ItemStack.EMPTY;
        ItemStack original = slot.getItem();
        ItemStack result = original.copy();
        if (index < CORPSE_SLOTS) {
            if (!(player instanceof ServerPlayer serverPlayer)) return ItemStack.EMPTY;
            ItemStack moving = original.copy();
            if (!DeltaQuickMoveService.moveIntoPlayerInventory(serverPlayer, moving)) {
                return ItemStack.EMPTY;
            }
            slot.set(moving);
        } else if (corpse != null) {
            int carrierSlot = carrierSlotFor(original);
            if (carrierSlot >= 0
                && corpse.tryEquipCarrier(carrierSlot, original)) {
                original.shrink(1);
            } else {
                ItemStack remainder = corpse.addStackToStorage(original);
                if (remainder.getCount() == original.getCount()) return ItemStack.EMPTY;
                slot.set(remainder);
            }
        } else if (!moveItemStackTo(original, 0, RESERVED_CORPSE_SLOT, false)) {
            return ItemStack.EMPTY;
        }
        if (original.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
        return result;
    }

    @Override
    public boolean stillValid(Player player) {
        return corpse == null || (!corpse.isRemoved() && player.distanceToSqr(corpse) <= 64.0D);
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (corpse != null) {
            // Commit the authoritative corpse inventory before the menu is
            // discarded. Custom Delta transfers mutate the backing container
            // outside vanilla's normal click bookkeeping.
            corpse.commitInventoryChanges();
        }
        corpseInventory.stopOpen(player);
    }
}
