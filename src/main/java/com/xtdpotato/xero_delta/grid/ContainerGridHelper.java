package com.xtdpotato.xero_delta.grid;

import com.xtdpotato.xero_delta.data.ItemSize;
import com.xtdpotato.xero_delta.menu.CorpseMenu;
import com.xtdpotato.xero_delta.menu.PersonalWarehouseMenu;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AbstractFurnaceMenu;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.BeaconMenu;
import net.minecraft.world.inventory.BrewingStandMenu;
import net.minecraft.world.inventory.CartographyTableMenu;
import net.minecraft.world.inventory.CrafterMenu;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.inventory.GrindstoneMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.LoomMenu;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.StonecutterMenu;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public final class ContainerGridHelper {
    public static final int SLOT_STEP = 18;
    /** The usable slot face; the remaining two pixels of SLOT_STEP are the inter-slot gutter. */
    public static final int SLOT_FACE_SIZE = 16;
    /**
     * Per-menu coordinate and occupancy index.  Grid queries are made from
     * renderSlot, hit testing, click handling and quick-move in the same tick;
     * rebuilding the layout in each query turns a large storage menu into an
     * accidental quadratic/cubic workload.
     */
    private static final Map<AbstractContainerMenu, GridIndex> GRID_INDEXES =
        java.util.Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<AbstractContainerMenu, Boolean> CLIENT_MENU_FLAGS =
        java.util.Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Class<?>, Optional<StorageMenuAdapter>> STORAGE_ADAPTERS = new ConcurrentHashMap<>();
    private static final Set<String> NON_STORAGE_SLOT_MARKERS = Set.of(
        "upgrade", "filter", "ghost", "setting", "result", "output", "crafting",
        "recipe", "display", "dummy", "infinite", "preview", "payment", "curio",
        "groundpackcarrier"
    );

    public record PlacementResult(Slot anchor, boolean rotated, GridBackingStore.PlacementStatus status, Set<Slot> blockers) {
        public boolean isAccepted() {
            return status != GridBackingStore.PlacementStatus.BLOCKED;
        }
    }

    public record GridSlotInfo(Slot slot, String group, int column, int row, boolean adapted) {
    }

    private ContainerGridHelper() {
    }

    /** Build a client index at most once until the menu layout/content is invalidated. */
    public static void prepare(AbstractContainerMenu menu, Function<ItemStack, ItemSize> sizeGetter) {
        prepare(menu, sizeGetter, 0L);
    }

    /**
     * Reuses immutable coordinate data while the visible slot layout is unchanged and
     * only rebuilds occupancy when item contents or synced rules change.
     */
    public static void prepare(AbstractContainerMenu menu, Function<ItemStack, ItemSize> sizeGetter,
                               long rulesRevision) {
        if (menu == null) return;
        MenuSignatures signatures = signatures(menu, rulesRevision);
        GridIndex cached = GRID_INDEXES.get(menu);
        if (cached == null || cached.layoutSignature() != signatures.layout()) {
            rebuild(menu, sizeGetter, signatures);
        } else if (cached.contentSignature() != signatures.content()) {
            cached.rebuildOccupancy(sizeGetter, signatures.content());
        } else {
            cached.ensureOccupancy(menu, sizeGetter);
        }
    }

    /** Force one fresh index build. Used once per client render after scrolling/filtering. */
    public static void refresh(AbstractContainerMenu menu, Function<ItemStack, ItemSize> sizeGetter) {
        if (menu != null) rebuild(menu, sizeGetter, signatures(menu, 0L));
    }

    /** Mark a menu dirty after a slot mutation or a third-party layout update. */
    public static void invalidate(AbstractContainerMenu menu) {
        if (menu == null) return;
        GRID_INDEXES.remove(menu);
    }

    /** Finds a visible slot by its local GUI pixel coordinate in O(1). */
    public static Slot slotAt(AbstractContainerMenu menu, int x, int y) {
        if (menu == null) return null;
        GridIndex index = gridIndex(menu, null);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                Slot slot = index.renderSlots().get(new PositionKey(x + dx, y + dy));
                if (slot != null) return slot;
            }
        }
        return null;
    }

    /**
     * Resolves GUI input against the complete internal 18x18 slot step.
     * Vanilla's item face is only 16x16, but the two pixels leading to an
     * existing right/bottom neighbour must not create dead preview strips.
     * The outside edge remains 16x16 so clicks beyond the grid stay outside.
     */
    public static Slot hitSlot(AbstractContainerMenu menu, double localX, double localY) {
        if (menu == null) return null;
        GridIndex index = gridIndex(menu, null);
        int pixelX = (int) Math.floor(localX);
        int pixelY = (int) Math.floor(localY);
        for (int offsetX = 0; offsetX < SLOT_STEP; offsetX++) {
            for (int offsetY = 0; offsetY < SLOT_STEP; offsetY++) {
                Slot slot = index.renderSlots().get(new PositionKey(pixelX - offsetX, pixelY - offsetY));
                if (slot == null) continue;
            if (localX < slot.x || localX >= slot.x + SLOT_STEP
                || localY < slot.y || localY >= slot.y + SLOT_STEP) continue;
            boolean inFaceX = localX < slot.x + SLOT_FACE_SIZE;
            boolean inFaceY = localY < slot.y + SLOT_FACE_SIZE;
            if (inFaceX && inFaceY) return slot;
            boolean hasRightNeighbor = inFaceX || offsetSlot(menu, slot, 1, 0) != null;
            boolean hasBelowNeighbor = inFaceY || offsetSlot(menu, slot, 0, 1) != null;
            if (isWithinSlotHitbox(localX, localY, slot.x, slot.y,
                hasRightNeighbor, hasBelowNeighbor)) return slot;
            }
        }
        return null;
    }

    public static boolean isWithinSlotHitbox(double localX, double localY, int slotX, int slotY,
                                             boolean hasRightNeighbor, boolean hasBelowNeighbor) {
        double width = hasRightNeighbor ? SLOT_STEP : SLOT_FACE_SIZE;
        double height = hasBelowNeighbor ? SLOT_STEP : SLOT_FACE_SIZE;
        return localX >= slotX && localX < slotX + width
            && localY >= slotY && localY < slotY + height;
    }

    public static Slot footprintAnchorFor(AbstractContainerMenu menu, Slot cell, Function<ItemStack, ItemSize> sizeGetter) {
        if (!isGridSlotEnabled(menu, cell)) return null;
        return gridIndex(menu, sizeGetter).occupiedBy().get(cell);
    }

    public static boolean canPlaceAt(AbstractContainerMenu menu, Slot anchor, ItemStack stack,
                                     Function<ItemStack, ItemSize> sizeGetter) {
        if (stack.isEmpty() || anchor == null || !anchor.isActive() || !anchor.mayPlace(stack)) return false;
        if (!isGridSlotEnabled(menu, anchor)) {
            ItemStack existing = anchor.getItem();
            return existing.isEmpty() || isSameGridItem(existing, stack);
        }
        Slot coveringAnchor = footprintAnchorFor(menu, anchor, sizeGetter);
        if (coveringAnchor != null && coveringAnchor != anchor) return false;
        ItemSize size = orientedSize(stack, sizeGetter);
        if (size.width() <= 1 && size.height() <= 1) {
            return anchor.getItem().isEmpty() || isSameGridItem(anchor.getItem(), stack);
        }
        Set<Slot> cells = footprintCells(menu, anchor, size);
        if (cells.size() < size.width() * size.height()) return false;
        for (Slot cell : cells) {
            if (!cell.mayPlace(stack)) return false;
            Slot occupiedBy = footprintAnchorFor(menu, cell, sizeGetter);
            if (occupiedBy != null && occupiedBy != anchor) return false;
            if (cell == anchor) continue;
            ItemStack existing = cell.getItem();
            if (!existing.isEmpty()) return false;
        }
        ItemStack existingAnchor = anchor.getItem();
        return existingAnchor.isEmpty() || isSameGridItem(existingAnchor, stack);
    }

    public static PlacementResult resolvePlacement(AbstractContainerMenu menu, Slot hover, ItemStack stack,
                                                   boolean preferredRotated, boolean allowRotate,
                                                   Function<ItemStack, ItemSize> sizeGetter) {
        return resolvePlacement(menu, hover, stack, preferredRotated, allowRotate, Set.of(), sizeGetter);
    }

    public static PlacementResult resolvePlacement(AbstractContainerMenu menu, Slot hover, ItemStack stack,
                                                   boolean preferredRotated, boolean allowRotate, Set<Slot> ignoredAnchors,
                                                   Function<ItemStack, ItemSize> sizeGetter) {
        if (stack.isEmpty() || hover == null || !hover.isActive() || !isGridSlotEnabled(menu, hover)) {
            return blocked(hover, preferredRotated, Set.of());
        }

        Slot occupied = footprintAnchorFor(menu, hover, sizeGetter);
        if (occupied != null && !ignoredAnchors.contains(occupied)) {
            ItemStack target = occupied.getItem();
            if (canStackInto(occupied, target, stack)) {
                return new PlacementResult(occupied, GridBackingStore.isRotated(target),
                    GridBackingStore.PlacementStatus.CAN_STACK, Set.of(occupied));
            }
        }

        List<Boolean> rotations = rotationOrder(stack, preferredRotated, allowRotate, sizeGetter);
        PlacementResult swapCandidate = null;
        for (boolean rotated : rotations) {
            ItemSize size = orientedSize(stack, rotated, sizeGetter);
            for (Slot candidate : candidateAnchors(menu, hover, size)) {
                if (!candidate.isActive() || !isGridSlotEnabled(menu, candidate) || !candidate.mayPlace(stack)) continue;
                if (canPlaceAt(menu, candidate, stack, rotated, ignoredAnchors, sizeGetter)) {
                    return new PlacementResult(candidate, rotated, GridBackingStore.PlacementStatus.CAN_PLACE, Set.of());
                }
                Set<Slot> blockers = blockersFor(menu, candidate, stack, rotated, sizeGetter);
                blockers.removeAll(ignoredAnchors);
                if (blockers.size() == 1
                    && footprintCells(menu, candidate, size).size() == size.width() * size.height()) {
                    PlacementResult swap = new PlacementResult(candidate, rotated,
                        GridBackingStore.PlacementStatus.CAN_SWAP, Set.copyOf(blockers));
                    if (isBetterPlacement(swap, swapCandidate, sizeGetter)) swapCandidate = swap;
                }
            }
        }

        return swapCandidate != null ? swapCandidate : blocked(hover, preferredRotated, Set.of());
    }

    /**
     * Resolves a placement at one explicit anchor. This is used for item swaps:
     * clicking anywhere inside an occupied large item must target that item's
     * actual anchor, rather than searching a neighbouring anchor first.
     */
    public static PlacementResult resolveExactPlacement(AbstractContainerMenu menu, Slot anchor, ItemStack stack,
                                                         boolean preferredRotated, boolean allowRotate,
                                                         Function<ItemStack, ItemSize> sizeGetter) {
        return resolveExactPlacement(menu, anchor, stack, preferredRotated, allowRotate,
            Set.of(), sizeGetter);
    }

    private static PlacementResult resolveExactPlacement(AbstractContainerMenu menu, Slot anchor,
                                                          ItemStack stack,
                                                          boolean preferredRotated,
                                                          boolean allowRotate,
                                                          Set<Slot> ignoredAnchors,
                                                          Function<ItemStack, ItemSize> sizeGetter) {
        if (stack.isEmpty() || anchor == null || !anchor.isActive() || !isGridSlotEnabled(menu, anchor)
            || !anchor.mayPlace(stack)) {
            return blocked(anchor, preferredRotated, Set.of());
        }
        PlacementResult swapCandidate = null;
        for (boolean rotated : rotationOrder(stack, preferredRotated, allowRotate, sizeGetter)) {
            ItemSize size = orientedSize(stack, rotated, sizeGetter);
            Set<Slot> cells = footprintCells(menu, anchor, size);
            if (cells.size() != size.width() * size.height()) continue;
            boolean acceptsEveryCell = true;
            for (Slot cell : cells) {
                if (!cell.mayPlace(stack)) {
                    acceptsEveryCell = false;
                    break;
                }
            }
            if (!acceptsEveryCell) continue;
            ItemStack target = anchor.getItem();
            if (!ignoredAnchors.contains(anchor) && canStackInto(anchor, target, stack)) {
                return new PlacementResult(anchor, GridBackingStore.isRotated(target),
                    GridBackingStore.PlacementStatus.CAN_STACK, Set.of(anchor));
            }
            if (canPlaceAt(menu, anchor, stack, rotated, ignoredAnchors, sizeGetter)) {
                return new PlacementResult(anchor, rotated, GridBackingStore.PlacementStatus.CAN_PLACE, Set.of());
            }
            Set<Slot> blockers = blockersFor(menu, anchor, stack, rotated, sizeGetter);
            blockers.removeAll(ignoredAnchors);
            if (blockers.size() == 1) {
                PlacementResult swap = new PlacementResult(anchor, rotated,
                    GridBackingStore.PlacementStatus.CAN_SWAP, Set.copyOf(blockers));
                if (isBetterPlacement(swap, swapCandidate, sizeGetter)) swapCandidate = swap;
            }
        }
        return swapCandidate != null ? swapCandidate : blocked(anchor, preferredRotated, Set.of());
    }

    /**
     * Keeps the picked-up orientation until the cursor presses against the
     * matching side of the grid or another footprint. The rotated footprint
     * expands away from that boundary and returns to the picked-up orientation
     * as soon as the cursor leaves it.
     */
    public static PlacementResult resolveCursorPlacement(AbstractContainerMenu menu, Slot hover, ItemStack stack,
                                                           double fractionX, double fractionY, boolean allowRotate,
                                                           Function<ItemStack, ItemSize> sizeGetter) {
        return resolveCursorPlacement(menu, hover, stack, fractionX, fractionY,
            allowRotate, Set.of(), sizeGetter);
    }

    public static PlacementResult resolveCursorPlacement(AbstractContainerMenu menu, Slot hover, ItemStack stack,
                                                           double fractionX, double fractionY, boolean allowRotate,
                                                           Set<Slot> ignoredAnchors,
                                                           Function<ItemStack, ItemSize> sizeGetter) {
        boolean preferredRotated = GridBackingStore.isRotated(stack);
        if (stack.isEmpty() || hover == null || !hover.isActive() || !isGridSlotEnabled(menu, hover)) {
            return blocked(hover, preferredRotated, Set.of());
        }

        Slot occupied = footprintAnchorFor(menu, hover, sizeGetter);
        if (occupied != null && !ignoredAnchors.contains(occupied)) {
            if (canStackInto(occupied, occupied.getItem(), stack)) {
                return new PlacementResult(occupied, GridBackingStore.isRotated(occupied.getItem()),
                    GridBackingStore.PlacementStatus.CAN_STACK, Set.of(occupied));
            }
            // A cursor may be over the edge of another large item while the carried
            // footprint still fits in neighbouring cells. Search those anchors before
            // rendering the drop as a swap or a blocked placement.
            return resolvePlacement(menu, hover, stack, preferredRotated, allowRotate,
                ignoredAnchors, sizeGetter);
        }

        ItemSize preferredSize = orientedSize(stack, preferredRotated, sizeGetter);
        Slot preferredAnchor = cursorAnchor(menu, hover, preferredSize, fractionX, fractionY);
        PlacementResult preferredPlacement = resolveExactPlacement(menu, preferredAnchor, stack,
            preferredRotated, false, ignoredAnchors, sizeGetter);
        if (!allowRotate || preferredSize.width() == preferredSize.height()) {
            return preferredPlacement;
        }

        BoundaryContact contact;
        Slot rotatedAnchor;
        ItemSize rotatedSize = preferredSize.rotated();
        if (preferredSize.width() > preferredSize.height()) {
            boolean leftSide = fractionX < 0.5;
            contact = boundaryContact(menu, hover, leftSide ? -1 : 1, 0, sizeGetter);
            if (contact == null) {
                return rotateOutOfBounds(menu, hover, stack, preferredRotated, preferredSize,
                    preferredAnchor, preferredPlacement, fractionX, fractionY,
                    ignoredAnchors, sizeGetter);
            }
            boolean upward = contactPrefersStart(menu, contact, true, fractionY, sizeGetter);
            int anchorX = leftSide ? 0 : -(rotatedSize.width() - 1);
            int anchorY = upward ? -(rotatedSize.height() - 1) : 0;
            rotatedAnchor = offsetSlot(menu, hover, anchorX, anchorY);
        } else {
            boolean topSide = fractionY < 0.5;
            contact = boundaryContact(menu, hover, 0, topSide ? -1 : 1, sizeGetter);
            if (contact == null) {
                return rotateOutOfBounds(menu, hover, stack, preferredRotated, preferredSize,
                    preferredAnchor, preferredPlacement, fractionX, fractionY,
                    ignoredAnchors, sizeGetter);
            }
            boolean leftward = contactPrefersStart(menu, contact, false, fractionX, sizeGetter);
            int anchorX = leftward ? -(rotatedSize.width() - 1) : 0;
            int anchorY = topSide ? 0 : -(rotatedSize.height() - 1);
            rotatedAnchor = offsetSlot(menu, hover, anchorX, anchorY);
        }
        return resolveRotatedPlacement(menu, stack, !preferredRotated, rotatedAnchor,
            ignoredAnchors, sizeGetter);
    }

    private static PlacementResult rotateOutOfBounds(AbstractContainerMenu menu, Slot hover, ItemStack stack,
                                                      boolean preferredRotated, ItemSize preferredSize,
                                                      Slot preferredAnchor, PlacementResult preferredPlacement,
                                                      double fractionX, double fractionY,
                                                      Set<Slot> ignoredAnchors,
                                                      Function<ItemStack, ItemSize> sizeGetter) {
        int coveredCells = footprintCells(menu, preferredAnchor, preferredSize).size();
        if (isCompleteFootprint(coveredCells, preferredSize)) return preferredPlacement;

        ItemSize rotatedSize = preferredSize.rotated();
        Slot rotatedAnchor = cursorAnchor(menu, hover, rotatedSize, fractionX, fractionY);
        PlacementResult rotated = resolveRotatedPlacement(menu, stack, !preferredRotated,
            rotatedAnchor, ignoredAnchors, sizeGetter);
        return rotated.isAccepted() ? rotated : preferredPlacement;
    }

    private static PlacementResult resolveRotatedPlacement(AbstractContainerMenu menu,
                                                            ItemStack stack,
                                                            boolean rotated,
                                                            Slot preferredAnchor,
                                                            Set<Slot> ignoredAnchors,
                                                            Function<ItemStack, ItemSize> sizeGetter) {
        return resolveExactPlacement(menu, preferredAnchor, stack, rotated, false,
            ignoredAnchors, sizeGetter);
    }

    private static BoundaryContact boundaryContact(AbstractContainerMenu menu, Slot hover, int deltaX, int deltaY,
                                                     Function<ItemStack, ItemSize> sizeGetter) {
        Slot adjacent = offsetSlot(menu, hover, deltaX, deltaY);
        if (adjacent == null || !isGridSlotEnabled(menu, adjacent)) return new BoundaryContact(null, null);
        Slot blocker = footprintAnchorFor(menu, adjacent, sizeGetter);
        if (blocker != null) return new BoundaryContact(adjacent, blocker);
        if (!adjacent.getItem().isEmpty()) return new BoundaryContact(adjacent, adjacent);
        return null;
    }

    private static boolean contactPrefersStart(AbstractContainerMenu menu, BoundaryContact contact,
                                               boolean verticalAxis, double fallbackFraction,
                                               Function<ItemStack, ItemSize> sizeGetter) {
        if (contact.adjacent() == null || contact.blocker() == null) return fallbackFraction < 0.5;
        ItemStack blockerStack = contact.blocker().getItem();
        if (blockerStack.isEmpty()) return fallbackFraction < 0.5;
        ItemSize blockerSize = orientedSize(blockerStack, sizeGetter);
        for (int row = 0; row < blockerSize.height(); row++) {
            for (int column = 0; column < blockerSize.width(); column++) {
                if (offsetSlot(menu, contact.blocker(), column, row) != contact.adjacent()) continue;
                int index = verticalAxis ? row : column;
                int span = verticalAxis ? blockerSize.height() : blockerSize.width();
                return boundaryPrefersStart(index, span);
            }
        }
        return fallbackFraction < 0.5;
    }

    static boolean boundaryPrefersStart(int index, int span) {
        if (span <= 1) return true;
        return Math.max(0, Math.min(span - 1, index)) < (span + 1) / 2;
    }

    static boolean isCompleteFootprint(int coveredCells, ItemSize size) {
        return coveredCells == size.width() * size.height();
    }

    public static boolean canRefillOrigin(ItemSize origin, int displacedArea) {
        return displacedArea >= 0 && displacedArea <= origin.width() * origin.height();
    }

    private record BoundaryContact(Slot adjacent, Slot blocker) {
    }

    public static Set<Slot> footprintCells(AbstractContainerMenu menu, Slot anchor, ItemSize size) {
        if (!isGridSlotEnabled(menu, anchor)) return Set.of();
        return gridIndex(menu, null).footprintSet(anchor, size, false);
    }

    /** Returns the in-grid portion of an otherwise out-of-bounds footprint. */
    public static Set<Slot> clippedFootprintCells(AbstractContainerMenu menu, Slot anchor, ItemSize size) {
        if (!isGridSlotEnabled(menu, anchor)) return Set.of();
        return gridIndex(menu, null).footprintSet(anchor, size, true);
    }

    public static Slot cursorAnchor(AbstractContainerMenu menu, Slot hover, ItemSize size,
                                    double fractionX, double fractionY) {
        if (hover == null) return null;
        int offsetX = anchorOffset(size.width(), fractionX);
        int offsetY = anchorOffset(size.height(), fractionY);
        Slot anchor = offsetSlot(menu, hover, -offsetX, -offsetY);
        return anchor != null ? anchor : hover;
    }

    public static ItemSize orientedSize(ItemStack stack, Function<ItemStack, ItemSize> sizeGetter) {
        return orientedSize(stack, GridBackingStore.isRotated(stack), sizeGetter);
    }

    public static ItemSize orientedSize(ItemStack stack, boolean rotated, Function<ItemStack, ItemSize> sizeGetter) {
        ItemSize size = sizeGetter.apply(stack);
        return rotated ? size.rotated() : size;
    }

    public static boolean canStackInto(Slot slot, ItemStack target, ItemStack stack) {
        if (slot == null || target.isEmpty() || stack.isEmpty()) return false;
        if (!isSameGridItem(target, stack)) return false;
        int limit = Math.min(slot.getMaxStackSize(target), target.getMaxStackSize());
        return target.getCount() < limit;
    }

    public static boolean isSameGridItem(ItemStack first, ItemStack second) {
        return GridBackingStore.isSameItemIgnoringRotation(first, second);
    }

    public static boolean supportsCarriedContainerInteraction(ItemStack stack) {
        if (stack.isEmpty()) return false;
        String className = stack.getItem().getClass().getName().toLowerCase(Locale.ROOT);
        return className.contains("sophisticatedbackpacks")
            || className.contains("backpackitem")
            || className.contains("backpack_item");
    }

    public static boolean isAnchorSlot(AbstractContainerMenu menu, Slot slot, Function<ItemStack, ItemSize> sizeGetter) {
        if (!isGridSlotEnabled(menu, slot)) return true;
        Slot anchor = footprintAnchorFor(menu, slot, sizeGetter);
        return anchor == null || anchor == slot;
    }

    public static boolean isHotbarSlot(Slot slot) {
        return slot != null && slot.container instanceof Inventory
            && slot.getContainerSlot() >= 0 && slot.getContainerSlot() < Inventory.getSelectionSize();
    }

    public static boolean isPlayerMainInventorySlot(Slot slot) {
        return slot != null && slot.container instanceof Inventory
            && slot.getContainerSlot() >= Inventory.getSelectionSize()
            && slot.getContainerSlot() < Inventory.INVENTORY_SIZE;
    }

    public static boolean isGridSlotEnabled(AbstractContainerMenu menu, Slot slot) {
        GridIndex cached = menu == null ? null : GRID_INDEXES.get(menu);
        if (cached != null) return cached.enabled().contains(slot);
        return isGridSlotEnabledUncached(menu, slot);
    }

    private static boolean isGridSlotEnabledUncached(AbstractContainerMenu menu, Slot slot) {
        if (slot == null || !slot.isActive()) return false;
        if (isCreativeMenu(menu)) return false;
        if (isPlayerMainInventorySlot(slot)) return true;
        if (slot.container instanceof Inventory) return false;
        // Corpse equipment/pocket/carrier slots are semantic presentation
        // slots, not one physical rectangular grid. Treating their scattered
        // coordinates as a normal container grid makes open-time normalization
        // repack or drop the worn chest rig/backpack.
        if (menu instanceof CorpseMenu) return false;
        if (isFunctionalMenu(menu)) return false;

        StorageMenuAdapter adapter = storageAdapter(menu);
        if (adapter != null) {
            if (menu instanceof PersonalWarehouseMenu) {
                return adapter.isStorageSlot(menu, slot.index);
            }
            // StorageScreenBase moves filtered-out storage slots to x=-2000 on
            // the client. They remain active in the menu, but must not be a
            // hit target or part of a footprint while the filter is active.
            return adapter.isStorageSlot(menu, slot.index)
                && adapter.isVisible(menu, slot);
        }
        if (isKnownNonStorageSlot(slot)) return false;
        return hasUniquePhysicalCoordinate(menu, slot);
    }

    public static boolean usesStorageAdapter(AbstractContainerMenu menu) {
        return storageAdapter(menu) != null;
    }

    /** True only for the third-party storage inventory, never player slots. */
    public static boolean isStorageInventorySlot(AbstractContainerMenu menu, Slot slot) {
        StorageMenuAdapter adapter = storageAdapter(menu);
        return adapter != null && slot != null && !(slot.container instanceof Inventory)
            && adapter.isStorageSlot(menu, slot.index) && adapter.isVisible(menu, slot);
    }

    /**
     * Sophisticated Core keeps a second slot snapshot for its storage menu.
     * Push that snapshot after an out-of-band grid mutation so the next click
     * cannot be evaluated against the pre-placement contents.
     */
    public static void synchronizeStorageMenu(AbstractContainerMenu menu) {
        if (!usesStorageAdapter(menu)) return;
        Method fullState = findMethod(menu.getClass(), "broadcastFullState");
        if (fullState != null) {
            try {
                fullState.invoke(menu);
                return;
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // Fall back to the vanilla snapshot path below.
            }
        }
        menu.sendAllDataToRemote();
    }

    public static boolean isSophisticatedBackpackMenu(AbstractContainerMenu menu) {
        if (menu == null) return false;
        String name = menu.getClass().getName().toLowerCase(Locale.ROOT);
        return name.contains("sophisticatedbackpacks") && name.contains("backpack");
    }

    public static boolean isSafeNonGridTransferTarget(AbstractContainerMenu menu, Slot slot) {
        return slot != null && (!usesStorageAdapter(menu) || slot.container instanceof Inventory);
    }

    public static GridSlotInfo gridSlotInfo(AbstractContainerMenu menu, Slot slot) {
        LogicalSlot logical = logicalSlot(menu, slot);
        if (logical != null) {
            return new GridSlotInfo(slot, logical.group(), logical.column(), logical.row(), true);
        }
        if (slot == null) return null;
        String group = menu.getClass().getName() + "#physical@" + System.identityHashCode(slot.container);
        return new GridSlotInfo(slot, group, slot.x, slot.y, false);
    }

    public static int compareGridOrder(AbstractContainerMenu menu, Slot first, Slot second) {
        LogicalSlot firstLogical = logicalSlot(menu, first);
        LogicalSlot secondLogical = logicalSlot(menu, second);
        if (firstLogical != null && secondLogical != null
            && firstLogical.group().equals(secondLogical.group())) {
            int row = Integer.compare(firstLogical.row(), secondLogical.row());
            if (row != 0) return row;
            return Integer.compare(firstLogical.column(), secondLogical.column());
        }
        int y = Integer.compare(first.y, second.y);
        if (y != 0) return y;
        int x = Integer.compare(first.x, second.x);
        return x != 0 ? x : Integer.compare(first.index, second.index);
    }

    private static boolean canPlaceAt(AbstractContainerMenu menu, Slot anchor, ItemStack stack, boolean rotated,
                                      Set<Slot> ignoredAnchors, Function<ItemStack, ItemSize> sizeGetter) {
        if (stack.isEmpty() || anchor == null || !anchor.isActive() || !anchor.mayPlace(stack) || !isGridSlotEnabled(menu, anchor)) return false;
        ItemSize size = orientedSize(stack, rotated, sizeGetter);
        Set<Slot> cells = footprintCells(menu, anchor, size);
        if (cells.size() < size.width() * size.height()) return false;
        for (Slot cell : cells) {
            if (!cell.mayPlace(stack)) return false;
            Slot occupiedBy = footprintAnchorFor(menu, cell, sizeGetter);
            if (occupiedBy != null && !ignoredAnchors.contains(occupiedBy)) return false;
            if (cell == anchor) continue;
            if (!cell.getItem().isEmpty()) return false;
        }
        ItemStack existingAnchor = anchor.getItem();
        return existingAnchor.isEmpty() || isSameGridItem(existingAnchor, stack);
    }

    private static Set<Slot> blockersFor(AbstractContainerMenu menu, Slot anchor, ItemStack stack, boolean rotated,
                                         Function<ItemStack, ItemSize> sizeGetter) {
        ItemSize size = orientedSize(stack, rotated, sizeGetter);
        Set<Slot> blockers = new LinkedHashSet<>();
        for (Slot cell : footprintCells(menu, anchor, size)) {
            Slot occupiedBy = footprintAnchorFor(menu, cell, sizeGetter);
            if (occupiedBy != null) blockers.add(occupiedBy);
            else if (!cell.getItem().isEmpty()) blockers.add(cell);
        }
        return blockers;
    }

    static int placementPriority(GridBackingStore.PlacementStatus status) {
        return switch (status) {
            case CAN_STACK -> 4;
            case CAN_PLACE -> 3;
            case CAN_SWAP -> 2;
            case BLOCKED -> 1;
        };
    }

    private static boolean isBetterPlacement(PlacementResult candidate, PlacementResult current,
                                             Function<ItemStack, ItemSize> sizeGetter) {
        if (candidate == null) return false;
        if (current == null) return true;
        int candidatePriority = placementPriority(candidate.status());
        int currentPriority = placementPriority(current.status());
        if (candidatePriority != currentPriority) return candidatePriority > currentPriority;
        if (candidate.status() != GridBackingStore.PlacementStatus.CAN_SWAP) return false;
        if (candidate.blockers().size() != current.blockers().size()) {
            return candidate.blockers().size() < current.blockers().size();
        }
        return blockerArea(candidate.blockers(), sizeGetter) < blockerArea(current.blockers(), sizeGetter);
    }

    private static int blockerArea(Set<Slot> blockers, Function<ItemStack, ItemSize> sizeGetter) {
        int area = 0;
        for (Slot blocker : blockers) {
            ItemStack stack = blocker.getItem();
            if (stack.isEmpty()) continue;
            ItemSize size = orientedSize(stack, sizeGetter);
            area += size.width() * size.height();
        }
        return area;
    }

    public static PlacementResult findPlacementWithin(AbstractContainerMenu menu, Set<Slot> allowedCells,
                                                      ItemStack stack, boolean preferredRotated,
                                                      Function<ItemStack, ItemSize> sizeGetter) {
        if (stack.isEmpty() || allowedCells.isEmpty()) return blocked(null, preferredRotated, Set.of());
        for (boolean rotated : rotationOrder(stack, preferredRotated, true, sizeGetter)) {
            ItemSize size = orientedSize(stack, rotated, sizeGetter);
            List<Slot> candidates = new ArrayList<>(allowedCells);
            candidates.sort((first, second) -> compareGridOrder(menu, first, second));
            for (Slot candidate : candidates) {
                Set<Slot> footprint = footprintCells(menu, candidate, size);
                if (footprint.size() != size.width() * size.height() || !allowedCells.containsAll(footprint)) continue;
                if (canPlaceAt(menu, candidate, stack, rotated, Set.of(), sizeGetter)) {
                    return new PlacementResult(candidate, rotated, GridBackingStore.PlacementStatus.CAN_PLACE, Set.of());
                }
            }
        }
        return blocked(null, preferredRotated, Set.of());
    }

    public static PlacementResult findQuickMovePlacement(AbstractContainerMenu menu, Set<Slot> allowedCells,
                                                         ItemStack stack, boolean preferredRotated, boolean reversed,
                                                         Function<ItemStack, ItemSize> sizeGetter) {
        if (stack.isEmpty() || allowedCells.isEmpty()) return blocked(null, preferredRotated, Set.of());
        List<Slot> candidates = new ArrayList<>();
        for (Slot slot : allowedCells) {
            if (slot.isActive() && isGridSlotEnabled(menu, slot) && slot.getItem().isEmpty()) candidates.add(slot);
        }
        if (candidates.isEmpty()) return blocked(null, preferredRotated, Set.of());
        candidates.sort((first, second) -> compareGridOrder(menu, first, second));
        if (reversed) Collections.reverse(candidates);

        GridIndex layout = gridIndex(menu, sizeGetter);
        List<Boolean> rotations = rotationOrder(stack, preferredRotated, true, sizeGetter);
        for (Slot candidate : candidates) {
            if (!candidate.mayPlace(stack)) continue;
            for (boolean rotated : rotations) {
                ItemSize size = orientedSize(stack, rotated, sizeGetter);
                List<Slot> footprint = layout.footprint(candidate, size);
                if (footprint.size() != size.width() * size.height()) continue;
                boolean valid = true;
                for (Slot cell : footprint) {
                    if (!allowedCells.contains(cell) || !cell.mayPlace(stack)
                        || layout.occupiedBy().containsKey(cell) || !cell.getItem().isEmpty()) {
                        valid = false;
                        break;
                    }
                }
                if (valid) {
                    return new PlacementResult(candidate, rotated,
                        GridBackingStore.PlacementStatus.CAN_PLACE, Set.of());
                }
            }
        }
        return blocked(null, preferredRotated, Set.of());
    }

    /**
     * Moves a carried stack into a grid target, first merging into an existing
     * matching anchor and then filling free footprints. This is shared by
     * vanilla quick-move handling and optional storage-menu adapters.
     */
    public static boolean transferIntoGrid(AbstractContainerMenu menu, ItemStack stack,
                                           Set<Slot> allowedCells, boolean reversed,
                                           Function<ItemStack, ItemSize> sizeGetter) {
        if (stack.isEmpty() || allowedCells.isEmpty()) return false;
        boolean moved = false;

        List<Slot> anchors = new ArrayList<>();
        for (Slot slot : allowedCells) {
            if (!slot.isActive() || !isGridSlotEnabled(menu, slot) || !isAnchorSlot(menu, slot, sizeGetter)) continue;
            if (!slot.getItem().isEmpty() && slot.mayPlace(stack)) anchors.add(slot);
        }
        anchors.sort((first, second) -> compareGridOrder(menu, first, second));
        if (reversed) Collections.reverse(anchors);
        for (Slot anchor : anchors) {
            if (stack.isEmpty()) break;
            ItemStack target = anchor.getItem();
            if (!canStackInto(anchor, target, stack)) continue;
            int limit = Math.min(anchor.getMaxStackSize(target), target.getMaxStackSize());
            int move = Math.min(stack.getCount(), limit - target.getCount());
            if (move <= 0) continue;
            ItemStack insertion = stack.copyWithCount(move);
            GridBackingStore.setRotated(insertion, GridBackingStore.isRotated(target));
            int inserted = safeInsert(anchor, insertion, move);
            if (inserted > 0) {
                stack.shrink(inserted);
                moved = true;
            }
        }

        while (!stack.isEmpty()) {
            PlacementResult placement = findQuickMovePlacement(menu, allowedCells, stack,
                GridBackingStore.isRotated(stack), reversed, sizeGetter);
            Slot anchor = placement.anchor();
            if (placement.status() != GridBackingStore.PlacementStatus.CAN_PLACE || anchor == null) break;
            int move = Math.min(stack.getCount(), Math.min(anchor.getMaxStackSize(stack), stack.getMaxStackSize()));
            if (move <= 0) break;
            ItemStack placed = stack.copyWithCount(move);
            GridBackingStore.setRotated(placed, placement.rotated());
            int inserted = safeInsert(anchor, placed, move);
            if (inserted <= 0) break;
            stack.shrink(inserted);
            moved = true;
            invalidate(menu);
            prepare(menu, sizeGetter);
        }
        return moved;
    }

    /**
     * Preserves vanilla player-inventory quick-move order while respecting the
     * logical footprints in the three-row main inventory. The hotbar remains a
     * normal 1x1 region and is therefore handled as a non-grid target.
     */
    public static boolean transferIntoPlayerInventory(AbstractContainerMenu menu, ItemStack stack,
                                                      boolean reversed,
                                                      Function<ItemStack, ItemSize> sizeGetter) {
        if (menu == null || stack.isEmpty()) return false;
        List<Slot> ordered = new ArrayList<>();
        Set<Slot> mainInventory = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Slot slot : menu.slots) {
            if (!slot.isActive() || !(slot.container instanceof Inventory)) continue;
            int inventorySlot = slot.getContainerSlot();
            if (inventorySlot < 0 || inventorySlot >= Inventory.INVENTORY_SIZE) continue;
            ordered.add(slot);
            if (isPlayerMainInventorySlot(slot) && isGridSlotEnabled(menu, slot)) mainInventory.add(slot);
        }
        if (ordered.isEmpty()) return false;
        ordered.sort(Comparator.comparingInt(slot -> slot.index));
        if (reversed) Collections.reverse(ordered);

        boolean moved = mergeIntoPlayerTargets(menu, stack, ordered, sizeGetter);
        // Sophisticated Core adds main inventory before hotbar. Its reversed
        // quick move therefore checks hotbar empty slots before main inventory.
        if (reversed) moved |= placeIntoPlayerNonGrid(stack, ordered);
        if (!stack.isEmpty() && !mainInventory.isEmpty()) {
            moved |= transferIntoGrid(menu, stack, mainInventory, reversed, sizeGetter);
        }
        if (!reversed) moved |= placeIntoPlayerNonGrid(stack, ordered);
        return moved;
    }

    /** Returns whether a normal container-to-player quick move has any real capacity. */
    public static boolean canTransferIntoPlayerInventory(AbstractContainerMenu menu, ItemStack stack,
                                                         boolean reversed,
                                                         Function<ItemStack, ItemSize> sizeGetter) {
        if (menu == null || stack.isEmpty()) return false;
        List<Slot> ordered = new ArrayList<>();
        Set<Slot> mainInventory = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Slot slot : menu.slots) {
            if (!slot.isActive() || !(slot.container instanceof Inventory)) continue;
            int inventorySlot = slot.getContainerSlot();
            if (inventorySlot < 0 || inventorySlot >= Inventory.INVENTORY_SIZE) continue;
            ordered.add(slot);
            if (isPlayerMainInventorySlot(slot) && isGridSlotEnabled(menu, slot)) mainInventory.add(slot);
        }
        ordered.sort(Comparator.comparingInt(slot -> slot.index));
        if (reversed) Collections.reverse(ordered);

        for (Slot slot : ordered) {
            if (isGridSlotEnabled(menu, slot) && !isAnchorSlot(menu, slot, sizeGetter)) continue;
            ItemStack target = slot.getItem();
            if (canStackInto(slot, target, stack)) return true;
        }
        for (Slot slot : ordered) {
            if (!slot.getItem().isEmpty() || !slot.mayPlace(stack)) continue;
            if (isHotbarSlot(slot) || !isGridSlotEnabled(menu, slot)) return true;
        }
        if (mainInventory.isEmpty()) return false;
        PlacementResult placement = findQuickMovePlacement(menu, mainInventory, stack,
            GridBackingStore.isRotated(stack), reversed, sizeGetter);
        return placement.status() == GridBackingStore.PlacementStatus.CAN_PLACE;
    }

    private static boolean mergeIntoPlayerTargets(AbstractContainerMenu menu, ItemStack stack,
                                                  List<Slot> ordered,
                                                  Function<ItemStack, ItemSize> sizeGetter) {
        boolean moved = false;
        for (Slot slot : ordered) {
            if (stack.isEmpty()) break;
            if (isGridSlotEnabled(menu, slot) && !isAnchorSlot(menu, slot, sizeGetter)) continue;
            ItemStack target = slot.getItem();
            if (!canStackInto(slot, target, stack)) continue;
            int limit = Math.min(slot.getMaxStackSize(target), target.getMaxStackSize());
            int move = Math.min(stack.getCount(), limit - target.getCount());
            if (move <= 0) continue;
            ItemStack insertion = stack.copyWithCount(move);
            GridBackingStore.setRotated(insertion, GridBackingStore.isRotated(target));
            int inserted = safeInsert(slot, insertion, move);
            if (inserted > 0) {
                stack.shrink(inserted);
                moved = true;
            }
        }
        return moved;
    }

    private static boolean placeIntoPlayerNonGrid(ItemStack stack, List<Slot> ordered) {
        boolean moved = false;
        for (Slot slot : ordered) {
            if (stack.isEmpty()) break;
            if (!isHotbarSlot(slot) || !slot.getItem().isEmpty() || !slot.mayPlace(stack)) continue;
            int move = Math.min(stack.getCount(), Math.min(slot.getMaxStackSize(stack), stack.getMaxStackSize()));
            if (move <= 0) continue;
            int inserted = safeInsert(slot, stack.copyWithCount(move), move);
            if (inserted > 0) {
                stack.shrink(inserted);
                moved = true;
            }
        }
        return moved;
    }

    private static int safeInsert(Slot slot, ItemStack insertion, int limit) {
        int before = insertion.getCount();
        ItemStack remainder = slot.safeInsert(insertion, limit);
        int inserted = before - remainder.getCount();
        if (inserted > 0) slot.setChanged();
        return Math.max(0, inserted);
    }

    private static List<Slot> candidateAnchors(AbstractContainerMenu menu, Slot hover, ItemSize size) {
        List<Slot> result = new ArrayList<>();
        for (int dy = 0; dy < size.height(); dy++) {
            for (int dx = 0; dx < size.width(); dx++) {
                Slot candidate = offsetSlot(menu, hover, -dx, -dy);
                if (candidate != null && !result.contains(candidate)) result.add(candidate);
            }
        }
        return result;
    }

    public static Slot offsetSlot(AbstractContainerMenu menu, Slot origin, int deltaX, int deltaY) {
        GridIndex index = gridIndex(menu, null);
        GridCoordinate originCoordinate = index.coordinates().get(origin);
        if (originCoordinate != null) {
            return index.cells().get(new CellKey(originCoordinate.group(),
                originCoordinate.column() + deltaX * originCoordinate.step(),
                originCoordinate.row() + deltaY * originCoordinate.step()));
        }

        int x = origin.x + deltaX * SLOT_STEP;
        int y = origin.y + deltaY * SLOT_STEP;
        for (Slot slot : menu.slots) {
            if (!slot.isActive()) continue;
            if (!sameSlotGroup(menu, origin, slot)) continue;
            if (slot.x == x && slot.y == y) return slot;
        }
        return null;
    }

    public static int anchorOffset(int span, double fraction) {
        if (span <= 1) return 0;
        if ((span & 1) == 1) return span / 2;
        // For an even footprint, the two cells around the center line form a
        // one-cell-wide stable zone. Only crossing either outer half-cell
        // moves the footprint, preventing horizontal motion from changing Y.
        return fraction < 0.5 ? span / 2 : span / 2 - 1;
    }

    private static List<Boolean> rotationOrder(ItemStack stack, boolean preferredRotated, boolean allowRotate,
                                               Function<ItemStack, ItemSize> sizeGetter) {
        List<Boolean> result = new ArrayList<>();
        result.add(preferredRotated);
        if (allowRotate) {
            ItemSize preferred = orientedSize(stack, preferredRotated, sizeGetter);
            ItemSize flipped = orientedSize(stack, !preferredRotated, sizeGetter);
            if (!preferred.equals(flipped)) result.add(!preferredRotated);
        }
        return result;
    }

    private static PlacementResult blocked(Slot anchor, boolean rotated, Set<Slot> blockers) {
        return new PlacementResult(anchor, rotated, GridBackingStore.PlacementStatus.BLOCKED, blockers);
    }

    private static boolean isFunctionalMenu(AbstractContainerMenu menu) {
        return menu instanceof InventoryMenu
            || menu instanceof CraftingMenu
            || menu instanceof AbstractFurnaceMenu
            || menu instanceof EnchantmentMenu
            || menu instanceof AnvilMenu
            || menu instanceof SmithingMenu
            || menu instanceof GrindstoneMenu
            || menu instanceof StonecutterMenu
            || menu instanceof LoomMenu
            || menu instanceof CartographyTableMenu
            || menu instanceof BrewingStandMenu
            || menu instanceof BeaconMenu
            || menu instanceof MerchantMenu
            || menu instanceof CrafterMenu;
    }

    private static boolean sameSlotGroup(AbstractContainerMenu menu, Slot a, Slot b) {
        GridIndex index = gridIndex(menu, null);
        GridCoordinate first = index.coordinates().get(a);
        GridCoordinate second = index.coordinates().get(b);
        if (first != null || second != null) {
            return first != null && second != null && first.group().equals(second.group());
        }
        return a.container == b.container;
    }

    private record LogicalSlot(int column, int row, String group) {
    }

    private record CellKey(String group, int column, int row) {
    }

    private record PositionKey(int x, int y) {
    }

    private record GridCoordinate(String group, int column, int row, int step) {
    }

    private record FootprintKey(Slot anchor, int width, int height, boolean clipped) {
    }

    private record MenuSignatures(long layout, long content) {
    }

    /**
     * Immutable coordinates plus a mutable occupancy map for one menu. The
     * coordinate maps are rebuilt only when a menu is opened, scrolled or
     * explicitly invalidated; all footprint lookups thereafter are O(area).
     */
    private static final class GridIndex {
        private final Map<Slot, GridCoordinate> coordinates;
        private final Map<CellKey, Slot> cells;
        private final Map<PositionKey, Slot> renderSlots;
        private final Set<Slot> enabled;
        private final Map<Slot, Slot> occupiedBy = new IdentityHashMap<>();
        private final Map<FootprintKey, Set<Slot>> footprints = new HashMap<>();
        private final long layoutSignature;
        private long contentSignature;
        private boolean occupancyReady;

        private GridIndex(Map<Slot, GridCoordinate> coordinates,
                          Map<CellKey, Slot> cells, Map<PositionKey, Slot> renderSlots,
                          Set<Slot> enabled, long layoutSignature, long contentSignature) {
            this.coordinates = coordinates;
            this.cells = cells;
            this.renderSlots = renderSlots;
            this.enabled = enabled;
            this.layoutSignature = layoutSignature;
            this.contentSignature = contentSignature;
        }

        private Map<Slot, GridCoordinate> coordinates() { return coordinates; }
        private Map<CellKey, Slot> cells() { return cells; }
        private Map<PositionKey, Slot> renderSlots() { return renderSlots; }
        private Set<Slot> enabled() { return enabled; }
        private Map<Slot, Slot> occupiedBy() { return occupiedBy; }
        private long layoutSignature() { return layoutSignature; }
        private long contentSignature() { return contentSignature; }

        private void ensureOccupancy(AbstractContainerMenu menu,
                                     Function<ItemStack, ItemSize> sizeGetter) {
            if (occupancyReady || sizeGetter == null) return;
            occupiedBy.clear();
            for (Slot anchor : coordinates.keySet()) {
                ItemStack stored = anchor.getItem();
                if (stored.isEmpty()) continue;
                ItemSize size = orientedSize(stored, sizeGetter);
                if (size.width() <= 1 && size.height() <= 1) continue;
                for (Slot cell : footprint(anchor, size)) occupiedBy.putIfAbsent(cell, anchor);
            }
            occupancyReady = true;
        }

        private void rebuildOccupancy(Function<ItemStack, ItemSize> sizeGetter, long newContentSignature) {
            occupancyReady = false;
            contentSignature = newContentSignature;
            ensureOccupancy(null, sizeGetter);
        }

        private Set<Slot> footprintSet(Slot anchor, ItemSize size, boolean clipped) {
            if (anchor == null || size == null) return Set.of();
            FootprintKey key = new FootprintKey(anchor, size.width(), size.height(), clipped);
            return footprints.computeIfAbsent(key, ignored -> {
                List<Slot> cells = clipped ? clippedFootprint(anchor, size) : footprint(anchor, size);
                return cells.isEmpty() ? Set.of() : Collections.unmodifiableSet(new LinkedHashSet<>(cells));
            });
        }

        private List<Slot> footprint(Slot anchor, ItemSize size) {
            GridCoordinate origin = coordinates.get(anchor);
            if (origin == null) return List.of();
            List<Slot> result = new ArrayList<>(size.width() * size.height());
            for (int dy = 0; dy < size.height(); dy++) {
                for (int dx = 0; dx < size.width(); dx++) {
                    Slot cell = cells.get(new CellKey(origin.group(),
                        origin.column() + dx * origin.step(),
                        origin.row() + dy * origin.step()));
                    if (cell == null) return List.of();
                    result.add(cell);
                }
            }
            return result;
        }

        private List<Slot> clippedFootprint(Slot anchor, ItemSize size) {
            GridCoordinate origin = coordinates.get(anchor);
            if (origin == null) return List.of();
            List<Slot> result = new ArrayList<>(size.width() * size.height());
            for (int dy = 0; dy < size.height(); dy++) {
                for (int dx = 0; dx < size.width(); dx++) {
                    Slot cell = cells.get(new CellKey(origin.group(),
                        origin.column() + dx * origin.step(),
                        origin.row() + dy * origin.step()));
                    if (cell != null) result.add(cell);
                }
            }
            return result;
        }
    }

    private static GridIndex gridIndex(AbstractContainerMenu menu,
                                       Function<ItemStack, ItemSize> sizeGetter) {
        if (menu == null) {
            return new GridIndex(new IdentityHashMap<>(), new HashMap<>(), new HashMap<>(), Set.of(), 0L, 0L);
        }
        GridIndex index = GRID_INDEXES.get(menu);
        if (index == null) index = rebuild(menu, sizeGetter, signatures(menu, 0L));
        else index.ensureOccupancy(menu, sizeGetter);
        return index;
    }

    private static GridIndex rebuild(AbstractContainerMenu menu,
                                     Function<ItemStack, ItemSize> sizeGetter) {
        return rebuild(menu, sizeGetter, signatures(menu, 0L));
    }

    private static GridIndex rebuild(AbstractContainerMenu menu,
                                     Function<ItemStack, ItemSize> sizeGetter,
                                     MenuSignatures signatures) {
        StorageMenuAdapter adapter = storageAdapter(menu);
        LogicalLayout layout = adapter == null
            ? new LogicalLayout(Map.of())
            : buildLogicalLayout(menu, adapter, adapter.columns(menu));
        Map<Slot, GridCoordinate> coordinates = new IdentityHashMap<>();
        Map<CellKey, Slot> cells = new HashMap<>();
        Map<PositionKey, Slot> renderSlots = new HashMap<>();
        Set<Slot> enabled = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        for (Slot slot : menu.slots) {
            if (!slot.isActive() || !isGridSlotEnabledUncached(menu, slot)) continue;
            LogicalSlot logical = layout.slots().get(slot);
            if (logical == null && isPlayerMainInventorySlot(slot)) {
                int ordinal = slot.getContainerSlot() - Inventory.getSelectionSize();
                logical = new LogicalSlot(ordinal % 9, ordinal / 9,
                    "player-main@" + System.identityHashCode(slot.container));
            }
            GridCoordinate coordinate;
            if (logical != null) {
                coordinate = new GridCoordinate(logical.group(), logical.column(), logical.row(), 1);
            } else {
                String group = menu.getClass().getName() + "#physical@"
                    + System.identityHashCode(slot.container);
                coordinate = new GridCoordinate(group, slot.x, slot.y, SLOT_STEP);
            }
            coordinates.put(slot, coordinate);
            cells.putIfAbsent(new CellKey(coordinate.group(), coordinate.column(), coordinate.row()), slot);
            renderSlots.putIfAbsent(new PositionKey(slot.x, slot.y), slot);
            enabled.add(slot);
        }
        GridIndex index = new GridIndex(coordinates, cells, renderSlots, enabled,
            signatures.layout(), signatures.content());
        GRID_INDEXES.put(menu, index);
        index.ensureOccupancy(menu, sizeGetter);
        return index;
    }

    private static MenuSignatures signatures(AbstractContainerMenu menu, long rulesRevision) {
        long layout = 0xCBF29CE484222325L;
        long content = mix(0x9E3779B97F4A7C15L, rulesRevision);
        int ordinal = 0;
        for (Slot slot : menu.slots) {
            layout = mix(layout, ordinal++);
            layout = mix(layout, System.identityHashCode(slot));
            layout = mix(layout, System.identityHashCode(slot.container));
            layout = mix(layout, slot.index);
            layout = mix(layout, slot.getContainerSlot());
            layout = mix(layout, slot.x);
            layout = mix(layout, slot.y);
            layout = mix(layout, slot.isActive() ? 1 : 0);

            ItemStack stack = slot.getItem();
            content = mix(content, System.identityHashCode(stack));
            if (!stack.isEmpty()) {
                content = mix(content, stack.getCount());
                content = mix(content, stack.getDamageValue());
                content = mix(content, stack.getComponents().hashCode());
            }
        }
        return new MenuSignatures(layout, content);
    }

    private static long mix(long hash, long value) {
        hash ^= value;
        return hash * 0x100000001B3L;
    }

    private record LogicalLayout(Map<Slot, LogicalSlot> slots) {
    }

    private static LogicalSlot logicalSlot(AbstractContainerMenu menu, Slot slot) {
        if (menu == null || slot == null) return null;
        if (isPlayerMainInventorySlot(slot)) {
            int ordinal = slot.getContainerSlot() - Inventory.getSelectionSize();
            return new LogicalSlot(ordinal % 9, ordinal / 9,
                "player-main@" + System.identityHashCode(slot.container));
        }
        GridCoordinate coordinate = gridIndex(menu, null).coordinates().get(slot);
        return coordinate == null || coordinate.step() != 1 ? null
            : new LogicalSlot(coordinate.column(), coordinate.row(), coordinate.group());
    }

    private static LogicalLayout buildLogicalLayout(AbstractContainerMenu menu, StorageMenuAdapter adapter, int columns) {
        Map<Slot, LogicalSlot> slots = new IdentityHashMap<>();
        String group = menu.getClass().getName() + "#storage";
        List<Slot> storageSlots = new ArrayList<>();
        boolean stableIndexedClientLayout = menu instanceof PersonalWarehouseMenu;
        for (Slot candidate : menu.slots) {
            if (candidate.isActive() && adapter.isStorageSlot(menu, candidate.index)
                && (stableIndexedClientLayout || adapter.isVisible(menu, candidate))) {
                storageSlots.add(candidate);
            }
        }
        if (storageSlots.isEmpty()) return new LogicalLayout(Map.of());

        if (isClientMenu(menu) && !stableIndexedClientLayout) {
            int minX = storageSlots.stream().mapToInt(slot -> slot.x).min().orElse(0);
            int minY = storageSlots.stream().mapToInt(slot -> slot.y).min().orElse(0);
            for (Slot candidate : storageSlots) {
                // The client positions visible slots after filtering, so their
                // physical coordinates are the authoritative logical layout.
                int column = Math.round((candidate.x - minX) / (float) SLOT_STEP);
                int row = Math.round((candidate.y - minY) / (float) SLOT_STEP);
                slots.put(candidate, new LogicalSlot(column, row, group));
            }
        } else {
            // StorageInventorySlot is constructed with x=0,y=0 on the server.
            // Use its stable menu index instead of those placeholder pixels.
            int storageCount = adapter.storageSlotCount(menu);
            int rows = adapter.rows(menu);
            int stableColumns = stableIndexedClientLayout
                ? Math.max(1, columns)
                : rows > 0 && storageCount > 0
                    ? (storageCount + rows - 1) / rows
                    : columns;
            if (stableColumns <= 0) stableColumns = Math.max(1, storageSlots.size());
            for (Slot candidate : storageSlots) {
                int storageIndex = candidate.index;
                int column = Math.floorMod(storageIndex, stableColumns);
                int row = Math.max(0, storageIndex / stableColumns);
                slots.put(candidate, new LogicalSlot(column, row, group));
            }
        }
        return new LogicalLayout(Map.copyOf(slots));
    }

    private static boolean isClientMenu(AbstractContainerMenu menu) {
        if (menu == null) return false;
        Boolean cached = CLIENT_MENU_FLAGS.get(menu);
        if (cached != null) return cached;
        boolean client = false;
        for (Slot slot : menu.slots) {
            if (slot.container instanceof Inventory inventory) {
                client = inventory.player != null && inventory.player.level().isClientSide;
                break;
            }
        }
        CLIENT_MENU_FLAGS.put(menu, client);
        return client;
    }

    private static StorageMenuAdapter storageAdapter(AbstractContainerMenu menu) {
        if (menu == null) return null;
        return STORAGE_ADAPTERS.computeIfAbsent(menu.getClass(), ContainerGridHelper::findStorageAdapter).orElse(null);
    }

    private static Optional<StorageMenuAdapter> findStorageAdapter(Class<?> menuClass) {
        Method columns = findMethod(menuClass, "getColumnsTaken");
        Method storageSlot = findMethod(menuClass, "isStorageInventorySlot", int.class);
        Method storageCount = findMethod(menuClass, "getNumberOfStorageInventorySlots");
        Method rows = findMethod(menuClass, "getNumberOfRows");
        return columns == null || storageSlot == null
            ? Optional.empty()
            : Optional.of(new StorageMenuAdapter(columns, storageSlot, storageCount, rows));
    }

    private static Method findMethod(Class<?> type, String name, Class<?>... parameters) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                Method method = current.getDeclaredMethod(name, parameters);
                if (method.trySetAccessible()) return method;
            } catch (NoSuchMethodException | LinkageError | RuntimeException ignored) {
                // Some third-party menus contain classes that cannot be transformed by
                // this runtime. They are simply not grid-compatible storage menus.
            }
        }
        return null;
    }

    private static boolean isKnownNonStorageSlot(Slot slot) {
        String name = slot.getClass().getName().toLowerCase(Locale.ROOT);
        for (String marker : NON_STORAGE_SLOT_MARKERS) {
            if (name.contains(marker)) return true;
        }
        return false;
    }

    private static boolean hasUniquePhysicalCoordinate(AbstractContainerMenu menu, Slot slot) {
        for (Slot candidate : menu.slots) {
            if (candidate == slot || !candidate.isActive() || candidate.container != slot.container) continue;
            if (candidate.x == slot.x && candidate.y == slot.y) return false;
        }
        return true;
    }

    private record StorageMenuAdapter(Method columnsMethod, Method storageSlotMethod,
                                      Method storageCountMethod, Method rowsMethod) {
        private int columns(AbstractContainerMenu menu) {
            int storageCount = storageSlotCount(menu);
            int rowCount = rows(menu);
            if (storageCount > 0 && rowCount > 0) {
                return (storageCount + rowCount - 1) / rowCount;
            }
            try {
                Object result = columnsMethod.invoke(menu);
                return result instanceof Number number ? number.intValue() : 0;
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                return 0;
            }
        }

        private boolean isStorageSlot(AbstractContainerMenu menu, int slotIndex) {
            try {
                Object result = storageSlotMethod.invoke(menu, slotIndex);
                return result instanceof Boolean value && value;
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                return false;
            }
        }

        private int storageSlotCount(AbstractContainerMenu menu) {
            return invokeInt(storageCountMethod, menu, 0);
        }

        private int rows(AbstractContainerMenu menu) {
            return invokeInt(rowsMethod, menu, 0);
        }

        private boolean isVisible(AbstractContainerMenu menu, Slot slot) {
            // Sophisticated uses x=-2000 in its regular screen and y=-100 in
            // the scrolling panel for hidden slots. Visible storage slots use
            // non-negative menu coordinates; server placeholders are (0,0).
            return !isClientMenu(menu) || (slot.x >= 0 && slot.y >= 0);
        }

        private static int invokeInt(Method method, AbstractContainerMenu menu, int fallback) {
            if (method == null) return fallback;
            try {
                Object result = method.invoke(menu);
                return result instanceof Number number ? number.intValue() : fallback;
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                return fallback;
            }
        }
    }

    private static boolean isCreativeMenu(AbstractContainerMenu menu) {
        if (menu == null) return false;
        String name = menu.getClass().getName();
        return name.contains("CreativeModeInventoryScreen") || name.endsWith("$ItemPickerMenu");
    }
}
