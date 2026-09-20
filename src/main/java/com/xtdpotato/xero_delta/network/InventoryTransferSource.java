package com.xtdpotato.xero_delta.network;

import com.xtdpotato.xero_delta.data.ModDataStorage;
import com.xtdpotato.xero_delta.grid.ContainerGridHelper;
import com.xtdpotato.xero_delta.grid.GridBackingStore;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import com.xtdpotato.xero_delta.trading.TradingInventorySource;
import com.xtdpotato.xero_delta.trading.TradingInventorySources;
import com.xtdpotato.xero_delta.entity.CorpseEntity;
import com.xtdpotato.xero_delta.menu.CorpseMenu;

/** Resolves a source item and keeps extraction/restoration transactional. */
public final class InventoryTransferSource {
    public interface Handle {
        default String sourceId() { return ""; }
        ItemStack peek();
        ItemStack extract(int amount);
        boolean restore(ItemStack stack);
    }

    private InventoryTransferSource() {}

    public static Handle find(ServerPlayer player, String sourceId) {
        Handle source = resolve(player, sourceId);
        if (source == null || com.xtdpotato.xero_delta.data.KnifeSkinRules.locked(source.peek())) return null;
        if (source == null) return null;
        return new Handle() {
            @Override public String sourceId() { return sourceId; }
            @Override public ItemStack peek() { return source.peek(); }
            @Override public ItemStack extract(int amount) { return source.extract(amount); }
            @Override public boolean restore(ItemStack stack) { return source.restore(stack); }
        };
    }

    private static Handle resolve(ServerPlayer player, String sourceId) {
        if (player == null || sourceId == null || sourceId.length() > 256) return null;
        if ("cursor".equals(sourceId)) return new CursorHandle(player.containerMenu);
        if (sourceId.startsWith("corpse_storage|")) {
            String[] parts = sourceId.split("\\|", -1);
            if (parts.length != 4 || !(player.containerMenu instanceof CorpseMenu menu)) {
                return null;
            }
            try {
                int entityId = Integer.parseInt(parts[1]);
                int carrierSlot = Integer.parseInt(parts[2]);
                int cell = Integer.parseInt(parts[3]);
                CorpseEntity corpse = menu.corpseEntity();
                if (corpse == null || menu.corpseEntityId() != entityId
                    || corpse.getId() != entityId || player.distanceToSqr(corpse) > 64.0D) {
                    return null;
                }
                GridBackingStore store = corpse.carrierStore(carrierSlot);
                if (store == null || cell < 0 || cell >= store.getSize()) return null;
                int anchor = store.findAnchorIndexAt(cell % store.getWidth(), cell / store.getWidth());
                return anchor < 0 ? null
                    : new CorpseStorageHandle(corpse, carrierSlot, anchor);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        if (sourceId.startsWith("container|")) {
            try {
                int slot = Integer.parseInt(sourceId.substring("container|".length()));
                if (slot < 0 || slot >= player.containerMenu.slots.size()) return null;
                Slot target = player.containerMenu.slots.get(slot);
                if (target == null || !target.isActive() || !target.mayPickup(player)) {
                    return null;
                }
                return new MenuHandle(player.containerMenu, target);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        TradingInventorySource source = TradingInventorySources.find(player, sourceId);
        return source == null ? null : new TradingHandle(source);
    }

    private static final class CursorHandle implements Handle {
        private final AbstractContainerMenu menu;
        private CursorHandle(AbstractContainerMenu menu) { this.menu = menu; }
        @Override public ItemStack peek() { return menu.getCarried().copy(); }
        @Override public ItemStack extract(int amount) {
            ItemStack current = menu.getCarried();
            if (current.isEmpty() || amount <= 0) return ItemStack.EMPTY;
            int take = Math.min(amount, current.getCount());
            ItemStack result = current.copyWithCount(take);
            current.shrink(take);
            menu.setCarried(current.isEmpty() ? ItemStack.EMPTY : current);
            return result;
        }
        @Override public boolean restore(ItemStack stack) {
            if (stack == null || stack.isEmpty()) return true;
            ItemStack current = menu.getCarried();
            if (current.isEmpty()) {
                menu.setCarried(stack.copy());
                return true;
            }
            if (!ItemStack.isSameItemSameComponents(current, stack)
                || current.getCount() + stack.getCount() > current.getMaxStackSize()) return false;
            current.grow(stack.getCount());
            menu.setCarried(current);
            return true;
        }
    }

    private static final class CorpseStorageHandle implements Handle {
        private final CorpseEntity corpse;
        private final int carrierSlot;
        private final int anchor;

        private CorpseStorageHandle(CorpseEntity corpse, int carrierSlot, int anchor) {
            this.corpse = corpse;
            this.carrierSlot = carrierSlot;
            this.anchor = anchor;
        }

        @Override public ItemStack peek() {
            GridBackingStore store = corpse.carrierStore(carrierSlot);
            if (store == null || anchor >= store.getSize()) return ItemStack.EMPTY;
            return store.getItemRaw(anchor % store.getWidth(), anchor / store.getWidth()).copy();
        }

        @Override public ItemStack extract(int amount) {
            GridBackingStore store = corpse.carrierStore(carrierSlot);
            if (store == null || anchor >= store.getSize() || amount <= 0) return ItemStack.EMPTY;
            ItemStack current = store.getItemRaw(anchor % store.getWidth(), anchor / store.getWidth());
            if (current.isEmpty()) return ItemStack.EMPTY;
            int take = Math.min(amount, current.getCount());
            ItemStack result = current.copyWithCount(take);
            ItemStack removed = store.removeAnchor(anchor);
            removed.shrink(take);
            if (!removed.isEmpty()) {
                store.placeDirect(anchor % store.getWidth(), anchor / store.getWidth(), removed);
                store.save();
            }
            corpse.carrierContentsChanged(carrierSlot);
            return result;
        }

        @Override public boolean restore(ItemStack stack) {
            if (stack == null || stack.isEmpty()) return true;
            GridBackingStore store = corpse.carrierStore(carrierSlot);
            if (store == null || anchor >= store.getSize()) return false;
            boolean restored = store.place(anchor % store.getWidth(), anchor / store.getWidth(),
                stack, GridBackingStore.isRotated(stack));
            if (restored) corpse.carrierContentsChanged(carrierSlot);
            return restored;
        }
    }

    private static final class TradingHandle implements Handle {
        private final TradingInventorySource source;
        private TradingHandle(TradingInventorySource source) { this.source = source; }
        @Override public ItemStack peek() { return source.peek().copy(); }
        @Override public ItemStack extract(int amount) { return source.extract(amount, false); }
        @Override public boolean restore(ItemStack stack) { return source.restore(stack); }
    }

    private static final class MenuHandle implements Handle {
        private final AbstractContainerMenu menu;
        private final Slot slot;
        private MenuHandle(AbstractContainerMenu menu, Slot slot) {
            this.menu = menu; this.slot = slot;
        }
        @Override public ItemStack peek() { return slot.getItem().copy(); }
        @Override public ItemStack extract(int amount) {
            ItemStack current = slot.getItem();
            if (current.isEmpty() || amount <= 0) return ItemStack.EMPTY;
            int take = Math.min(amount, current.getCount());
            ItemStack result = current.copyWithCount(take);
            if (take >= current.getCount()) slot.set(ItemStack.EMPTY);
            else slot.set(current.copyWithCount(current.getCount() - take));
            slot.setChanged();
            ContainerGridHelper.invalidate(menu);
            return result;
        }
        @Override public boolean restore(ItemStack stack) {
            if (stack == null || stack.isEmpty()) return true;
            ItemStack current = slot.getItem();
            if (!current.isEmpty()) return false;
            if (!slot.mayPlace(stack)) return false;
            if (ContainerGridHelper.isGridSlotEnabled(menu, slot)) {
                ContainerGridHelper.refresh(menu, ModDataStorage::getCachedSizeFor);
                var placement = ContainerGridHelper.resolveExactPlacement(
                    menu, slot, stack, GridBackingStore.isRotated(stack), false,
                    ModDataStorage::getCachedSizeFor);
                if (placement == null || !placement.isAccepted() || placement.anchor() != slot) return false;
            }
            slot.set(stack.copy());
            slot.setChanged();
            ContainerGridHelper.invalidate(menu);
            return true;
        }
    }
}
