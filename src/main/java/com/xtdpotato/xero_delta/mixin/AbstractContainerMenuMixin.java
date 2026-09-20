package com.xtdpotato.xero_delta.mixin;

import com.xtdpotato.xero_delta.Config;
import com.xtdpotato.xero_delta.api.GridMenuAccessor;
import com.xtdpotato.xero_delta.data.ContainerGridRules;
import com.xtdpotato.xero_delta.data.ItemSize;
import com.xtdpotato.xero_delta.data.ModDataStorage;
import com.xtdpotato.xero_delta.data.ServerGridCarryState;
import com.xtdpotato.xero_delta.data.ServerGridRotationState;
import com.xtdpotato.xero_delta.data.PlayerLayoutSlotRules;
import com.xtdpotato.xero_delta.data.PlayerLayoutInventoryPolicy;
import com.xtdpotato.xero_delta.data.LootSearchManager;
import com.xtdpotato.xero_delta.data.KnifeSkinRules;
import com.xtdpotato.xero_delta.grid.ContainerGridHelper;
import com.xtdpotato.xero_delta.grid.DeltaPackTransferService;
import com.xtdpotato.xero_delta.grid.GridBackingStore;
import com.xtdpotato.xero_delta.grid.GridPackingPlan;
import com.xtdpotato.xero_delta.network.ModNetwork;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Mixin(AbstractContainerMenu.class)
public class AbstractContainerMenuMixin implements GridMenuAccessor {

    @Unique
    private GridBackingStore deltaSafetyBox$gridStore;
    @Unique
    private boolean xero$redirectingGridClick;

    @Override
    public GridBackingStore deltaSafetyBox$getGridStore() { return deltaSafetyBox$gridStore; }

    @Override
    public void deltaSafetyBox$setGridStore(GridBackingStore store) { this.deltaSafetyBox$gridStore = store; }

    @Inject(method = "clicked", at = @At("HEAD"), cancellable = true)
    private void xero$enforceContainerGrid(int slotId, int button, ClickType clickType, Player player, CallbackInfo ci) {
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        AbstractContainerMenu self = (AbstractContainerMenu) (Object) this;

        if (KnifeSkinRules.blocksPlayerInventoryAction(player, self.getCarried())
            || (slotId >= 0 && slotId < self.slots.size()
                && KnifeSkinRules.blocksPlayerInventoryAction(player, self.slots.get(slotId).getItem()))
            || (clickType == ClickType.SWAP && button >= 0 && button < player.getInventory().getContainerSize()
                && KnifeSkinRules.blocksPlayerInventoryAction(player, player.getInventory().getItem(button)))) {
            self.sendAllDataToRemote();
            ci.cancel();
            return;
        }

        if (LootSearchManager.blocksClick(serverPlayer, self, slotId, clickType)) {
            self.sendAllDataToRemote();
            ci.cancel();
            return;
        }
        xero$markPlayerPlacedStack(serverPlayer, self, slotId, clickType);
        if (PlayerLayoutSlotRules.enabled(serverPlayer)) {
            if (self instanceof InventoryMenu && slotId >= 0 && slotId <= 4) {
                self.sendAllDataToRemote();
                ci.cancel();
                return;
            }
            if (slotId >= 0 && slotId < self.slots.size()) {
                Slot layoutSlot = self.slots.get(slotId);
                ItemStack carriedForLayout = self.getCarried();
                boolean sourceIsPlayerInventory =
                    layoutSlot.container == serverPlayer.getInventory();
                if (layoutSlot.container == serverPlayer.getInventory()
                    && !carriedForLayout.isEmpty()
                    && !PlayerLayoutSlotRules.canPlace(serverPlayer,
                        layoutSlot.getContainerSlot(), carriedForLayout)) {
                    self.sendAllDataToRemote();
                    ci.cancel();
                    return;
                }
                if (clickType == ClickType.QUICK_MOVE
                    && PlayerLayoutInventoryPolicy.blocksQuickMove(
                        sourceIsPlayerInventory, self instanceof InventoryMenu)) {
                    self.sendAllDataToRemote();
                    ci.cancel();
                    return;
                }
                if (clickType == ClickType.SWAP && sourceIsPlayerInventory
                    && PlayerLayoutInventoryPolicy.isDisabledMainSlot(
                        layoutSlot.getContainerSlot())) {
                    self.sendAllDataToRemote();
                    ci.cancel();
                    return;
                }
                if (clickType == ClickType.SWAP && !sourceIsPlayerInventory
                    && button >= 0 && button < PlayerLayoutInventoryPolicy.HOTBAR_SIZE
                    && !PlayerLayoutSlotRules.canPlace(
                        serverPlayer, button, layoutSlot.getItem())) {
                    self.sendAllDataToRemote();
                    ci.cancel();
                    return;
                }
            }
        }
        if (!Config.INSTANCE.itemGridEnabled.get()) return;
        if (!ContainerGridRules.isScreenEnabled(self.getClass().getName())) return;
        ContainerGridHelper.refresh(self, ModDataStorage::getCachedSizeFor);
        if (slotId < 0 || slotId >= self.slots.size()) {
            // An outside click can drop the carried stack. Do not let the
            // origin from that old pickup affect a later swap in this menu.
            ServerGridCarryState.clearAll(serverPlayer);
            return;
        }

        Slot slot = self.slots.get(slotId);
        if (!slot.isActive()) return;
        if (!ContainerGridHelper.isGridSlotEnabled(self, slot)) return;

        Slot occupiedBy = ContainerGridHelper.footprintAnchorFor(self, slot, ModDataStorage::getCachedSizeFor);
        ItemStack carried = self.getCarried();
        if (carried.isEmpty()) {
            ServerGridRotationState.setManualPriority(serverPlayer, false);
            if (clickType == ClickType.PICKUP && button == 0) {
                Slot origin = occupiedBy != null ? occupiedBy : slot;
                ItemStack originStack = origin.getItem();
                if (!originStack.isEmpty()) {
                    ServerGridCarryState.rememberContainer(serverPlayer, self, origin.index,
                        ContainerGridHelper.orientedSize(originStack, ModDataStorage::getCachedSizeFor),
                        GridBackingStore.isRotated(originStack));
                } else {
                    ServerGridCarryState.clearAll(serverPlayer);
                }
            }
        }
        if (occupiedBy != null && occupiedBy != slot && !xero$redirectingGridClick) {
            xero$redirectGridClick(self, occupiedBy, button, clickType, player);
            ci.cancel();
            return;
        }

        if (clickType == ClickType.PICKUP && button == 1 && !carried.isEmpty()
            && ContainerGridHelper.supportsCarriedContainerInteraction(carried)) {
            if (occupiedBy != null && occupiedBy != slot) {
                xero$redirectGridClick(self, occupiedBy, button, clickType, player);
                ci.cancel();
            }
            return;
        }

        if (clickType == ClickType.PICKUP && !carried.isEmpty()) {
            var carriedSize = ModDataStorage.getCachedSizeFor(carried);
            boolean largeCarried = carriedSize.width() > 1 || carriedSize.height() > 1;
            if (button == 0 && xero$handleCarriedGridClick(self, slot, serverPlayer)) {
                ci.cancel();
                return;
            }
            if (button == 1 && largeCarried
                && xero$handleCarriedGridRightClick(self, slot, serverPlayer)) {
                ci.cancel();
                return;
            }
            if (button != 0 && largeCarried) {
                xero$cancelAndResync(self, ci);
                return;
            }
        }

        if (occupiedBy != null && occupiedBy != slot) {
            xero$cancelAndResync(self, ci);
            return;
        }

        if (!carried.isEmpty() && clickType == ClickType.QUICK_CRAFT) {
            var size = ModDataStorage.getCachedSizeFor(carried);
            if (size.width() > 1 || size.height() > 1) {
                xero$cancelAndResync(self, ci);
                return;
            }
        }
    }

    @Inject(method = "broadcastChanges", at = @At("HEAD"))
    private void xero$invalidateGridOnBroadcast(CallbackInfo ci) {
        ContainerGridHelper.invalidate((AbstractContainerMenu) (Object) this);
    }

    @Inject(method = "removed", at = @At("HEAD"))
    private void xero$clearGridIndex(Player player, CallbackInfo ci) {
        ContainerGridHelper.invalidate((AbstractContainerMenu) (Object) this);
    }

    @Unique
    private static void xero$markPlayerPlacedStack(ServerPlayer player, AbstractContainerMenu menu,
                                                    int slotId, ClickType clickType) {
        if (slotId < 0 || slotId >= menu.slots.size()) return;
        Slot slot = menu.slots.get(slotId);
        ItemStack candidate = menu.getCarried();
        if (candidate.isEmpty() && clickType == ClickType.QUICK_MOVE
            && slot.container == player.getInventory()) {
            candidate = slot.getItem();
        }
        if (candidate.isEmpty()) return;
        if (slot.container instanceof net.minecraft.world.entity.player.Inventory) return;
        candidate.set(com.xtdpotato.xero_delta.ModDataComponents.LOOT_SEARCHED.get(),
            player.getGameProfile().getName());
    }

    private void xero$redirectGridClick(AbstractContainerMenu menu, Slot target, int button, ClickType clickType, Player player) {
        if (xero$redirectingGridClick) return;
        xero$redirectingGridClick = true;
        try {
            menu.clicked(target.index, button, clickType, player);
        } finally {
            xero$redirectingGridClick = false;
        }
        menu.sendAllDataToRemote();
    }

    @Unique
    private void xero$cancelAndResync(AbstractContainerMenu menu, CallbackInfo ci) {
        xero$resync(menu);
        ci.cancel();
    }

    @Unique
    private static void xero$resync(AbstractContainerMenu menu) {
        if (ContainerGridHelper.usesStorageAdapter(menu)) {
            ContainerGridHelper.synchronizeStorageMenu(menu);
        } else {
            menu.sendAllDataToRemote();
        }
    }

    @Unique
    private boolean xero$handleCarriedGridClick(AbstractContainerMenu menu, Slot clicked, ServerPlayer player) {
        ItemStack carried = menu.getCarried();
        var size = ModDataStorage.getCachedSizeFor(carried);
        boolean large = size.width() > 1 || size.height() > 1;
        Slot occupiedBy = ContainerGridHelper.footprintAnchorFor(menu, clicked, ModDataStorage::getCachedSizeFor);
        if (!large && occupiedBy == null) return false;

        Slot interactionSlot = occupiedBy != null ? occupiedBy : clicked;
        if (!large) {
            return xero$stackOrSwapAtAnchor(menu, interactionSlot, player);
        }

        // A click on any covered cell of an item is an explicit request to swap
        // with that item. Resolve its real anchor before considering neighbours.
        if (occupiedBy != null) {
            var exact = ContainerGridHelper.resolveExactPlacement(menu, occupiedBy, carried,
                GridBackingStore.isRotated(carried), false,
                ModDataStorage::getCachedSizeFor);
            if (exact.status() == GridBackingStore.PlacementStatus.CAN_SWAP) {
                // This click explicitly targets the occupied footprint. If
                // the atomic swap cannot be completed, keep the carried stack
                // in hand instead of searching another (often top-left) cell.
                if (!xero$completeSwap(menu, exact, carried, player)) xero$resync(menu);
                return true;
            }
            if (exact.status() == GridBackingStore.PlacementStatus.CAN_STACK
                || exact.status() == GridBackingStore.PlacementStatus.CAN_PLACE) {
                return xero$applyPlacement(menu, exact, carried, player);
            }
            xero$resync(menu);
            return true;
        }

        // The client has already converted the mouse position to an explicit
        // anchor before sending the click. Respect that anchor first; a broad
        // candidate search here would shift the item a second time toward the
        // top-left of the grid.
        var placement = ContainerGridHelper.resolveExactPlacement(menu, clicked, carried,
            GridBackingStore.isRotated(carried), false,
            ModDataStorage::getCachedSizeFor);
        if (!placement.isAccepted() || placement.anchor() == null) {
            xero$resync(menu);
            return true;
        }

        if (placement.status() == GridBackingStore.PlacementStatus.CAN_STACK) {
            return xero$applyPlacement(menu, placement, carried, player);
        }

        if (placement.status() == GridBackingStore.PlacementStatus.CAN_PLACE) {
            return xero$applyPlacement(menu, placement, carried, player);
        }

        if (placement.status() == GridBackingStore.PlacementStatus.CAN_SWAP) {
            if (!xero$completeSwap(menu, placement, carried, player)) xero$resync(menu);
            return true;
        }

        return true;
    }

    @Unique
    private boolean xero$handleCarriedGridRightClick(AbstractContainerMenu menu, Slot clicked,
                                                      ServerPlayer player) {
        ItemStack carried = menu.getCarried();
        if (carried.isEmpty()) return false;
        Slot occupiedBy = ContainerGridHelper.footprintAnchorFor(menu, clicked, ModDataStorage::getCachedSizeFor);
        if (occupiedBy != null) {
            // Right-click only adds one to a matching footprint; it never
            // swaps a different item out from under the cursor.
            ItemStack target = occupiedBy.getItem();
            if (!ContainerGridHelper.canStackInto(occupiedBy, target, carried)) {
                xero$resync(menu);
                return true;
            }
            ItemStack insertion = carried.copyWithCount(1);
            GridBackingStore.setRotated(insertion, GridBackingStore.isRotated(target));
            int inserted = xero$safeInsert(occupiedBy, insertion, 1);
            if (inserted > 0) {
                carried.shrink(inserted);
                menu.setCarried(carried.isEmpty() ? ItemStack.EMPTY : carried);
                menu.broadcastChanges();
                ContainerGridHelper.synchronizeStorageMenu(menu);
            } else {
                xero$resync(menu);
            }
            return true;
        }

        var placement = ContainerGridHelper.resolveExactPlacement(menu, clicked, carried,
            GridBackingStore.isRotated(carried), false,
            ModDataStorage::getCachedSizeFor);
        if (placement.status() != GridBackingStore.PlacementStatus.CAN_PLACE
            || placement.anchor() == null) {
            xero$resync(menu);
            return true;
        }

        ItemStack placed = carried.copyWithCount(1);
        GridBackingStore.setRotated(placed, placement.rotated());
        int inserted = xero$safeInsert(placement.anchor(), placed, 1);
        if (inserted > 0) {
            carried.shrink(inserted);
            menu.setCarried(carried.isEmpty() ? ItemStack.EMPTY : carried);
            if (carried.isEmpty()) ServerGridCarryState.clearAll(player);
            menu.broadcastChanges();
            ContainerGridHelper.synchronizeStorageMenu(menu);
        } else {
            xero$resync(menu);
        }
        return true;
    }

    @Unique
    private boolean xero$applyPlacement(AbstractContainerMenu menu, ContainerGridHelper.PlacementResult placement,
                                        ItemStack carried, ServerPlayer player) {
        Slot anchor = placement.anchor();
        if (anchor == null) {
            xero$resync(menu);
            return true;
        }
        if (placement.status() == GridBackingStore.PlacementStatus.CAN_STACK) {
            ItemStack target = anchor.getItem();
            int limit = Math.min(anchor.getMaxStackSize(target), target.getMaxStackSize());
            int move = Math.min(carried.getCount(), limit - target.getCount());
            if (move <= 0) {
                xero$resync(menu);
                return true;
            }
            ItemStack insertion = carried.copyWithCount(move);
            GridBackingStore.setRotated(insertion, GridBackingStore.isRotated(target));
            int inserted = xero$safeInsert(anchor, insertion, move);
            if (inserted <= 0) {
                xero$resync(menu);
                return true;
            }
            carried.shrink(inserted);
        } else if (placement.status() == GridBackingStore.PlacementStatus.CAN_PLACE) {
            DeltaPackTransferService.Payload payload =
                DeltaPackTransferService.payload(carried);
            if (payload != null) {
                if (!xero$placeWithUnpackedContents(menu, placement, payload, player)) {
                    xero$resync(menu);
                    return true;
                }
                carried.setCount(0);
                menu.setCarried(ItemStack.EMPTY);
                ServerGridCarryState.clearAll(player);
                menu.broadcastChanges();
                ContainerGridHelper.synchronizeStorageMenu(menu);
                return true;
            }
            int limit = Math.min(anchor.getMaxStackSize(carried), carried.getMaxStackSize());
            int move = Math.min(carried.getCount(), limit);
            if (move <= 0) {
                xero$resync(menu);
                return true;
            }
            ItemStack placed = carried.copyWithCount(move);
            GridBackingStore.setRotated(placed, placement.rotated());
            int inserted = xero$safeInsert(anchor, placed, move);
            if (inserted <= 0) {
                xero$resync(menu);
                return true;
            }
            carried.shrink(inserted);
        } else {
            return false;
        }
        menu.setCarried(carried.isEmpty() ? ItemStack.EMPTY : carried);
        menu.broadcastChanges();
        ContainerGridHelper.synchronizeStorageMenu(menu);
        if (carried.isEmpty()) ServerGridCarryState.clearAll(player);
        return true;
    }

    @Unique
    private boolean xero$placeWithUnpackedContents(AbstractContainerMenu menu,
                                                   ContainerGridHelper.PlacementResult placement,
                                                   DeltaPackTransferService.Payload payload,
                                                   ServerPlayer player) {
        Slot anchor = placement.anchor();
        if (anchor == null) return false;
        List<ItemStack> snapshot = new ArrayList<>(menu.slots.size());
        for (Slot slot : menu.slots) snapshot.add(slot.getItem().copy());
        Set<Slot> destination = DeltaPackTransferService.destinationCells(menu, anchor);
        if (destination.isEmpty()) {
            xero$notifyInternalContentsNoSpace(player, payload);
            return false;
        }
        try {
            ItemStack placed = payload.emptyCarrier().copy();
            GridBackingStore.setRotated(placed, placement.rotated());
            anchor.set(placed);
            anchor.setChanged();
            ContainerGridHelper.invalidate(menu);
            ContainerGridHelper.prepare(menu, ModDataStorage::getCachedSizeFor);
            if (!DeltaPackTransferService.insertContents(menu, destination, payload.contents())) {
                xero$restoreSlots(menu, snapshot);
                xero$notifyInternalContentsNoSpace(player, payload);
                return false;
            }
            return true;
        } catch (RuntimeException failure) {
            xero$restoreSlots(menu, snapshot);
            return false;
        }
    }

    @Unique
    private boolean xero$completeSwap(AbstractContainerMenu menu, ContainerGridHelper.PlacementResult placement,
                                      ItemStack carried, ServerPlayer player) {
        ServerGridCarryState.ContainerOrigin origin = ServerGridCarryState.containerOrigin(player);
        if (origin != null && origin.menu() == menu
            && xero$swapIntoContainerOrigin(menu, placement, carried, player, origin)) {
            return true;
        }
        if (placement.blockers().size() != 1 || placement.anchor() == null) return false;
        Slot blocker = placement.blockers().iterator().next();
        if (!blocker.mayPickup(player)) return false;
        int limit = Math.min(placement.anchor().getMaxStackSize(carried), carried.getMaxStackSize());
        if (carried.getCount() > limit) return false;
        List<ItemStack> snapshot = new ArrayList<>(menu.slots.size());
        for (Slot slot : menu.slots) snapshot.add(slot.getItem().copy());
        ItemStack returned = blocker.getItem().copy();
        if (returned.isEmpty()) return false;
        DeltaPackTransferService.Payload payload =
            DeltaPackTransferService.payload(carried);
        Set<Slot> destination = payload == null ? Set.of()
            : DeltaPackTransferService.destinationCells(menu, placement.anchor());
        try {
            blocker.set(ItemStack.EMPTY);
            ItemStack placed = payload == null
                ? carried.copy() : payload.emptyCarrier().copy();
            GridBackingStore.setRotated(placed, placement.rotated());
            placement.anchor().set(placed);
            blocker.setChanged();
            placement.anchor().setChanged();
            ContainerGridHelper.invalidate(menu);
            ContainerGridHelper.prepare(menu, ModDataStorage::getCachedSizeFor);
            if (payload != null && (destination.isEmpty()
                || !DeltaPackTransferService.insertContents(
                    menu, destination, payload.contents()))) {
                xero$restoreSlots(menu, snapshot);
                menu.setCarried(carried);
                xero$notifyInternalContentsNoSpace(player, payload);
                return false;
            }
            menu.setCarried(returned);
        } catch (RuntimeException failure) {
            xero$restoreSlots(menu, snapshot);
            menu.setCarried(carried);
            return false;
        }
        ServerGridCarryState.clearAll(player);
        menu.broadcastChanges();
        ContainerGridHelper.synchronizeStorageMenu(menu);
        return true;
    }

    @Unique
    private boolean xero$swapIntoContainerOrigin(AbstractContainerMenu menu,
                                                  ContainerGridHelper.PlacementResult placement,
                                                  ItemStack carried, ServerPlayer player,
                                                  ServerGridCarryState.ContainerOrigin origin) {
        if (placement.anchor() == null || origin.slotIndex() < 0 || origin.slotIndex() >= menu.slots.size()) return false;
        for (Slot blocker : placement.blockers()) {
            if (!blocker.mayPickup(player)) return false;
        }
        int limit = Math.min(placement.anchor().getMaxStackSize(carried), carried.getMaxStackSize());
        if (carried.getCount() > limit) return false;

        Slot originAnchor = menu.slots.get(origin.slotIndex());
        Set<Slot> originCells = ContainerGridHelper.footprintCells(menu, originAnchor, origin.footprint());
        int originArea = origin.footprint().width() * origin.footprint().height();
        if (originCells.size() != originArea) return false;
        int displacedArea = 0;
        for (Slot blocker : placement.blockers()) {
            ItemSize blockerSize = ModDataStorage.getCachedSizeFor(blocker.getItem());
            displacedArea += blockerSize.width() * blockerSize.height();
            if (!ContainerGridHelper.canRefillOrigin(origin.footprint(), displacedArea)) return false;
        }

        List<ItemStack> snapshot = new ArrayList<>(menu.slots.size());
        for (Slot slot : menu.slots) snapshot.add(slot.getItem().copy());
        DeltaPackTransferService.Payload payload =
            DeltaPackTransferService.payload(carried);
        Set<Slot> destination = payload == null ? Set.of()
            : DeltaPackTransferService.destinationCells(menu, placement.anchor());
        try {
            List<ItemStack> displaced = new ArrayList<>();
            for (Slot blocker : placement.blockers()) {
                ItemStack stack = blocker.getItem().copy();
                if (!stack.isEmpty()) displaced.add(stack);
                blocker.set(ItemStack.EMPTY);
            }

            ItemStack placed = payload == null
                ? carried.copy() : payload.emptyCarrier().copy();
            GridBackingStore.setRotated(placed, placement.rotated());
            placement.anchor().set(placed);

            // Relocate against the new occupancy after the blockers are clear.
            ContainerGridHelper.invalidate(menu);
            ContainerGridHelper.prepare(menu, ModDataStorage::getCachedSizeFor);

            displaced.sort(Comparator.comparingInt(AbstractContainerMenuMixin::xero$stackArea).reversed());
            if (!xero$repackDisplaced(menu, originCells, displaced)) {
                xero$restoreSlots(menu, snapshot);
                return false;
            }
            if (payload != null && (destination.isEmpty()
                || !DeltaPackTransferService.insertContents(
                    menu, destination, payload.contents()))) {
                xero$restoreSlots(menu, snapshot);
                xero$notifyInternalContentsNoSpace(player, payload);
                return false;
            }
        } catch (RuntimeException failure) {
            xero$restoreSlots(menu, snapshot);
            return false;
        }

        for (Slot slot : menu.slots) slot.setChanged();
        menu.setCarried(ItemStack.EMPTY);
        ServerGridCarryState.clearAll(player);
        menu.broadcastChanges();
        ContainerGridHelper.synchronizeStorageMenu(menu);
        return true;
    }

    @Unique
    private boolean xero$repackDisplaced(AbstractContainerMenu menu, Set<Slot> originCells,
                                         List<ItemStack> displaced) {
        Map<GridPackingPlan.Cell, Slot> freeCells = new LinkedHashMap<>();
        String group = null;
        for (Slot cell : originCells) {
            ContainerGridHelper.GridSlotInfo info = ContainerGridHelper.gridSlotInfo(menu, cell);
            if (info == null || !info.adapted()) {
                return xero$repackDisplacedSequentially(menu, originCells, displaced);
            }
            if (group == null) group = info.group();
            else if (!group.equals(info.group())) return false;
            Slot occupying = ContainerGridHelper.footprintAnchorFor(
                menu, cell, ModDataStorage::getCachedSizeFor);
            if (occupying != null) continue;
            GridPackingPlan.Cell logical = new GridPackingPlan.Cell(info.column(), info.row());
            if (freeCells.putIfAbsent(logical, cell) != null) return false;
        }

        List<GridPackingPlan.Entry> entries = new ArrayList<>(displaced.size());
        for (int index = 0; index < displaced.size(); index++) {
            ItemStack stack = displaced.get(index);
            entries.add(new GridPackingPlan.Entry(index, ModDataStorage.getCachedSizeFor(stack),
                GridBackingStore.isRotated(stack)));
        }
        var plan = GridPackingPlan.pack(freeCells.keySet(), entries);
        if (plan.isEmpty()) return false;
        for (GridPackingPlan.Placement placement : plan.orElseThrow()) {
            ItemStack stack = displaced.get(placement.index());
            for (GridPackingPlan.Cell logical : placement.cells()) {
                Slot target = freeCells.get(logical);
                if (target == null || !target.mayPlace(stack)) return false;
            }
        }
        for (GridPackingPlan.Placement placement : plan.orElseThrow()) {
            ItemStack relocated = displaced.get(placement.index()).copy();
            GridBackingStore.setRotated(relocated, placement.rotated());
            Slot anchor = freeCells.get(placement.anchor());
            if (anchor == null) return false;
            anchor.set(relocated);
            anchor.setChanged();
        }
        ContainerGridHelper.invalidate(menu);
        ContainerGridHelper.prepare(menu, ModDataStorage::getCachedSizeFor);
        return true;
    }

    @Unique
    private boolean xero$repackDisplacedSequentially(AbstractContainerMenu menu, Set<Slot> originCells,
                                                     List<ItemStack> displaced) {
        for (ItemStack stack : displaced) {
            ContainerGridHelper.PlacementResult relocated = ContainerGridHelper.findPlacementWithin(
                menu, originCells, stack, GridBackingStore.isRotated(stack), ModDataStorage::getCachedSizeFor);
            if (relocated.status() != GridBackingStore.PlacementStatus.CAN_PLACE
                || relocated.anchor() == null) return false;
            ItemStack relocatedStack = stack.copy();
            GridBackingStore.setRotated(relocatedStack, relocated.rotated());
            relocated.anchor().set(relocatedStack);
            relocated.anchor().setChanged();
            ContainerGridHelper.invalidate(menu);
            ContainerGridHelper.prepare(menu, ModDataStorage::getCachedSizeFor);
        }
        return true;
    }

    @Unique
    private static void xero$restoreSlots(AbstractContainerMenu menu, List<ItemStack> snapshot) {
        for (int i = 0; i < menu.slots.size() && i < snapshot.size(); i++) {
            menu.slots.get(i).set(snapshot.get(i).copy());
            menu.slots.get(i).setChanged();
        }
        ContainerGridHelper.invalidate(menu);
    }

    @Unique
    private static int xero$stackArea(ItemStack stack) {
        var size = ContainerGridHelper.orientedSize(stack, ModDataStorage::getCachedSizeFor);
        return size.width() * size.height();
    }

    @Unique
    private static void xero$notifyInternalContentsNoSpace(
        ServerPlayer player, DeltaPackTransferService.Payload payload) {
        if (payload != null && !payload.contents().isEmpty()) {
            ModNetwork.sendTranslatedNoticePlain(player,
                "storage.xero_delta.internal_contents_no_space");
        }
    }

    @Unique
    private boolean xero$stackOrSwapAtAnchor(AbstractContainerMenu menu, Slot anchor, Player player) {
        ItemStack carried = menu.getCarried();
        if (carried.isEmpty() || anchor == null || !anchor.isActive()) return false;
        ItemStack target = anchor.getItem();
        if (ContainerGridHelper.canStackInto(anchor, target, carried)) {
            int limit = Math.min(anchor.getMaxStackSize(target), target.getMaxStackSize());
            int move = Math.min(carried.getCount(), limit - target.getCount());
            if (move > 0) {
                ItemStack insertion = carried.copyWithCount(move);
                GridBackingStore.setRotated(insertion, GridBackingStore.isRotated(target));
                int inserted = xero$safeInsert(anchor, insertion, move);
                if (inserted > 0) {
                    carried.shrink(inserted);
                    menu.setCarried(carried.isEmpty() ? ItemStack.EMPTY : carried);
                    menu.broadcastChanges();
                    ContainerGridHelper.synchronizeStorageMenu(menu);
                } else {
                    xero$resync(menu);
                }
            } else {
                xero$resync(menu);
            }
            return true;
        }
        if (!target.isEmpty() && anchor.mayPickup(player) && anchor.mayPlace(carried)) {
            int limit = Math.min(anchor.getMaxStackSize(carried), carried.getMaxStackSize());
            if (carried.getCount() > limit) {
                xero$resync(menu);
                return true;
            }
            List<ItemStack> snapshot = new ArrayList<>(menu.slots.size());
            for (Slot slot : menu.slots) snapshot.add(slot.getItem().copy());
            ItemStack old = target.copy();
            try {
                anchor.set(carried.copy());
                menu.setCarried(old);
                anchor.setChanged();
            } catch (RuntimeException failure) {
                xero$restoreSlots(menu, snapshot);
                menu.setCarried(carried);
                xero$resync(menu);
                return true;
            }
            menu.broadcastChanges();
            ContainerGridHelper.synchronizeStorageMenu(menu);
            return true;
        }
        xero$resync(menu);
        return true;
    }

    @Unique
    private static int xero$safeInsert(Slot slot, ItemStack insertion, int limit) {
        int before = insertion.getCount();
        ItemStack remainder = slot.safeInsert(insertion, limit);
        int inserted = before - remainder.getCount();
        if (inserted > 0) slot.setChanged();
        return Math.max(0, inserted);
    }
}
