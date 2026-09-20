package com.xtdpotato.xero_delta.grid;

import com.xtdpotato.xero_delta.Config;
import com.xtdpotato.xero_delta.ModDataComponents;
import com.xtdpotato.xero_delta.data.ItemSize;
import com.xtdpotato.xero_delta.data.ModDataStorage;
import com.xtdpotato.xero_delta.data.TaczCompatibilityRules;
import com.xtdpotato.xero_delta.item.DeltaPackItem;
import com.xtdpotato.xero_delta.tag.ModTags;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

public class GridBackingStore {
    public enum PlacementStatus {
        BLOCKED,
        CAN_PLACE,
        CAN_STACK,
        CAN_SWAP
    }

    public record PlacementResult(int x, int y, boolean rotated, PlacementStatus status, Set<Integer> blockers) {
        public boolean isAccepted() {
            return status != PlacementStatus.BLOCKED;
        }
    }

    private ItemStack boxStack;
    private final int width;
    private final int height;
    private final int containerIndex;
    private final List<ItemStack> items;
    private final Predicate<ItemStack> blockedPredicate;

    public GridBackingStore(ItemStack boxStack, int width, int height) {
        this(boxStack, width, height, 0, GridBackingStore::isBlocked);
    }

    public GridBackingStore(ItemStack boxStack, int width, int height, int containerIndex) {
        this(boxStack, width, height, containerIndex, GridBackingStore::isBlocked);
    }

    public GridBackingStore(ItemStack boxStack, int width, int height, int containerIndex,
                            Predicate<ItemStack> blockedPredicate) {
        this.boxStack = boxStack;
        this.width = width;
        this.height = height;
        this.containerIndex = Math.max(0, containerIndex);
        this.blockedPredicate = blockedPredicate == null
            ? GridBackingStore::isBlocked : blockedPredicate;
        this.items = new ArrayList<>();
        for (int i = 0; i < width * height; i++) items.add(ItemStack.EMPTY);
        load();
    }

    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public int getSize() { return width * height; }
    public int getContainerIndex() { return containerIndex; }
    public ItemStack getBoxStack() { return boxStack; }

    public void reload(ItemStack newBoxStack, int newWidth, int newHeight) {
        if (newBoxStack != this.boxStack) {
            this.boxStack = newBoxStack;
            load();
        }
    }

    public void load() {
        List<ItemStack> stored;
        if (containerIndex == 0) {
            stored = boxStack.getOrDefault(ModDataComponents.GRID_CONTENTS.get(), List.of());
        } else {
            List<List<ItemStack>> containers = boxStack.getOrDefault(ModDataComponents.GRID_CONTAINERS.get(), List.of());
            int storageIndex = containerIndex - 1;
            stored = storageIndex < containers.size() ? containers.get(storageIndex) : List.of();
        }
        items.clear();
        for (int i = 0; i < width * height; i++) {
            if (i < stored.size()) items.add(stored.get(i).copy());
            else items.add(ItemStack.EMPTY);
        }
    }

    public void save() {
        List<ItemStack> copy = new ArrayList<>();
        for (ItemStack s : items) copy.add(s.copy());
        if (containerIndex == 0) {
            boxStack.set(ModDataComponents.GRID_CONTENTS.get(), copy);
            return;
        }
        List<List<ItemStack>> containers = new ArrayList<>();
        @SuppressWarnings("unchecked")
        List<List<ItemStack>> storedContainers = (List<List<ItemStack>>) (List<?>)
            boxStack.getOrDefault(ModDataComponents.GRID_CONTAINERS.get(), List.of());
        for (List<ItemStack> old : storedContainers) {
            List<ItemStack> row = new ArrayList<>();
            for (ItemStack stack : old) row.add(stack.copy());
            containers.add(row);
        }
        int storageIndex = containerIndex - 1;
        while (containers.size() <= storageIndex) containers.add(new ArrayList<>());
        containers.set(storageIndex, copy);
        boxStack.set(ModDataComponents.GRID_CONTAINERS.get(), containers);
    }

    public ItemStack getItemAt(int x, int y) {
        int idx = y * width + x;
        if (idx < 0 || idx >= items.size()) return ItemStack.EMPTY;
        return items.get(idx).copy();
    }

    public ItemStack getItemRaw(int x, int y) {
        int idx = y * width + x;
        if (idx < 0 || idx >= items.size()) return ItemStack.EMPTY;
        return items.get(idx);
    }

    public int getSlotIndex(int x, int y) { return y * width + x; }

    public boolean isCellOccupied(int x, int y) {
        return findAnchorIndexAt(x, y) >= 0;
    }

    public boolean canPlace(int x, int y, ItemStack stack) {
        return canPlace(x, y, stack, isRotated(stack), Set.of());
    }

    public boolean canPlace(int x, int y, ItemStack stack, boolean rotated, Set<Integer> ignoredAnchors) {
        if (blockedPredicate.test(stack)) return false;
        ItemSize size = orientedSize(stack, rotated);
        if (x < 0 || y < 0 || x + size.width() > width || y + size.height() > height) return false;
        if (!fitsPackRegion(x, y, size) || crossesSplitGap(x, size.width())) return false;
        for (int yy = y; yy < y + size.height(); yy++) {
            for (int xx = x; xx < x + size.width(); xx++) {
                int anchor = findAnchorIndexAt(xx, yy);
                if (anchor >= 0 && !ignoredAnchors.contains(anchor)) return false;
            }
        }
        return true;
    }

    public boolean place(int x, int y, ItemStack stack) {
        return place(x, y, stack, isRotated(stack));
    }

    public boolean place(int x, int y, ItemStack stack, boolean rotated) {
        int idx = y * width + x;
        if (idx < 0 || idx >= items.size()) return false;
        if (!canPlace(x, y, stack, rotated, Set.of())) return false;
        ItemStack copy = stack.copy();
        setRotated(copy, rotated);
        items.set(idx, copy);
        save();
        return true;
    }

    /**
     * Splits an anchor stack into the nearest complete footprint. Nothing is
     * mutated until a destination has been found, so a full grid is a no-op.
     */
    public boolean splitStackToNearest(int sourceAnchor, int amount) {
        if (sourceAnchor < 0 || sourceAnchor >= items.size()) return false;
        ItemStack source = items.get(sourceAnchor);
        SplitCounts counts = splitCounts(source.getCount(), amount);
        if (source.isEmpty() || counts == null) return false;
        int sourceX = sourceAnchor % width;
        int sourceY = sourceAnchor / width;
        ItemStack split = source.copyWithCount(counts.split());
        boolean rotated = isRotated(source);
        ItemSize footprint = orientedSize(source, rotated);

        List<Integer> candidates = new ArrayList<>();
        for (int index = 0; index < items.size(); index++) {
            if (index != sourceAnchor) candidates.add(index);
        }
        candidates.sort(Comparator
            .comparingInt((Integer index) -> footprintDistance(sourceX, sourceY,
                footprint, index % width, index / width))
            .thenComparingInt(index -> nearbyDirectionRank(
                sourceX, sourceY, footprint, index % width, index / width))
            .thenComparingInt(Integer::intValue));

        for (int destination : candidates) {
            int x = destination % width;
            int y = destination / width;
            if (!canPlace(x, y, split, rotated, Set.of())) continue;
            source.setCount(counts.remainder());
            items.set(destination, split);
            save();
            return true;
        }
        return false;
    }

    /**
     * Splits an anchor directly to the cursor without removing and re-placing
     * the source footprint. This cannot fail because of grid bounds or a
     * changed placement rule after the source has already been mutated.
     */
    public ItemStack splitStackToCursor(int sourceAnchor, int amount) {
        if (sourceAnchor < 0 || sourceAnchor >= items.size()) return ItemStack.EMPTY;
        ItemStack source = items.get(sourceAnchor);
        SplitCounts counts = splitCounts(source.getCount(), amount);
        if (source.isEmpty() || counts == null) return ItemStack.EMPTY;

        ItemStack split = source.copyWithCount(counts.split());
        source.setCount(counts.remainder());
        save();
        return split;
    }

    static SplitCounts splitCounts(int sourceCount, int requestedAmount) {
        if (sourceCount <= 1 || requestedAmount <= 0) return null;
        int split = Math.min(sourceCount - 1, requestedAmount);
        return new SplitCounts(sourceCount - split, split);
    }

    record SplitCounts(int remainder, int split) {
    }

    static int footprintDistance(int sourceX, int sourceY, ItemSize footprint,
                                 int destinationX, int destinationY) {
        int dx = axisDistance(sourceX, footprint.width(), destinationX,
            footprint.width());
        int dy = axisDistance(sourceY, footprint.height(), destinationY,
            footprint.height());
        return dx + dy;
    }

    private static int axisDistance(int firstStart, int firstSize,
                                    int secondStart, int secondSize) {
        if (secondStart >= firstStart + firstSize) {
            return secondStart - (firstStart + firstSize) + 1;
        }
        if (firstStart >= secondStart + secondSize) {
            return firstStart - (secondStart + secondSize) + 1;
        }
        return 0;
    }

    private static int nearbyDirectionRank(int sourceX, int sourceY, ItemSize footprint,
                                           int destinationX, int destinationY) {
        if (destinationX >= sourceX + footprint.width()) return 0;
        if (destinationX + footprint.width() <= sourceX) return 1;
        if (destinationY >= sourceY + footprint.height()) return 2;
        if (destinationY + footprint.height() <= sourceY) return 3;
        return 4;
    }

    public ItemStack remove(int x, int y) {
        int idx = findAnchorIndexAt(x, y);
        if (idx < 0 || idx >= items.size()) return ItemStack.EMPTY;
        ItemStack result = items.get(idx).copy();
        items.set(idx, ItemStack.EMPTY);
        save();
        return result;
    }

    public int[] findFreeSlot() {
        return findFreeSlot(ItemStack.EMPTY);
    }

    public int[] findFreeSlot(ItemStack stack) {
        for (int i = 0; i < items.size(); i++) {
            int x = i % width;
            int y = i / width;
            if (stack.isEmpty() ? items.get(i).isEmpty() : canPlace(x, y, stack)) {
                return new int[]{x, y};
            }
        }
        return null;
    }

    public PlacementResult findFreePlacement(ItemStack stack) {
        if (stack.isEmpty()) return new PlacementResult(0, 0, false, PlacementStatus.BLOCKED, Set.of());
        for (int i = 0; i < items.size(); i++) {
            PlacementResult resolved = resolvePlacement(i % width, i / width, stack, isRotated(stack), true, Set.of());
            if (resolved.status() == PlacementStatus.CAN_PLACE) return resolved;
        }
        return new PlacementResult(0, 0, isRotated(stack), PlacementStatus.BLOCKED, Set.of());
    }

    public void clear() {
        for (int i = 0; i < items.size(); i++) items.set(i, ItemStack.EMPTY);
    }

    public void placeDirect(int x, int y, ItemStack stack) {
        int idx = y * width + x;
        if (idx >= 0 && idx < items.size()) items.set(idx, stack.copy());
    }

    public List<ItemStack> getAllItems() {
        List<ItemStack> copy = new ArrayList<>();
        for (ItemStack s : items) copy.add(s.copy());
        return copy;
    }

    public void restoreAll(List<ItemStack> snapshot) {
        items.clear();
        for (int i = 0; i < width * height; i++) {
            items.add(i < snapshot.size() ? snapshot.get(i).copy() : ItemStack.EMPTY);
        }
        save();
    }

    public int findAnchorIndexAt(int x, int y) {
        if (x < 0 || y < 0 || x >= width || y >= height) return -1;
        for (int i = 0; i < items.size(); i++) {
            ItemStack stack = items.get(i);
            if (stack.isEmpty()) continue;
            int ax = i % width;
            int ay = i / width;
            ItemSize size = sizeOfStored(stack);
            if (x >= ax && x < ax + size.width() && y >= ay && y < ay + size.height()) return i;
        }
        return -1;
    }

    public Set<Integer> blockersFor(int x, int y, ItemStack stack, boolean rotated) {
        Set<Integer> blockers = new HashSet<>();
        if (blockedPredicate.test(stack)) return blockers;
        ItemSize size = orientedSize(stack, rotated);
        if (!fitsPackRegion(x, y, size) || crossesSplitGap(x, size.width())) return blockers;
        for (int yy = y; yy < y + size.height(); yy++) {
            for (int xx = x; xx < x + size.width(); xx++) {
                int anchor = findAnchorIndexAt(xx, yy);
                if (anchor >= 0) blockers.add(anchor);
            }
        }
        return blockers;
    }

    public PlacementResult resolvePlacement(int hoverX, int hoverY, ItemStack stack, boolean preferredRotated, boolean allowRotate, Set<Integer> ignoredAnchors) {
        return resolvePlacement(hoverX, hoverY, stack, preferredRotated,
            allowRotate, ignoredAnchors, 0.5D, 0.5D);
    }

    private PlacementResult resolvePlacement(int hoverX, int hoverY, ItemStack stack,
                                             boolean preferredRotated, boolean allowRotate,
                                             Set<Integer> ignoredAnchors,
                                             double fractionX, double fractionY) {
        if (blockedPredicate.test(stack)) {
            return blocked(hoverX, hoverY, preferredRotated, Set.of());
        }

        int stackAnchor = findAnchorIndexAt(hoverX, hoverY);
        if (stackAnchor >= 0 && !ignoredAnchors.contains(stackAnchor)) {
            ItemStack target = items.get(stackAnchor);
            if (isSameItemIgnoringRotation(target, stack) && target.getCount() < target.getMaxStackSize()) {
                return new PlacementResult(stackAnchor % width, stackAnchor / width, isRotated(target), PlacementStatus.CAN_STACK, Set.of(stackAnchor));
            }
        }

        List<Boolean> rotations = new ArrayList<>();
        rotations.add(preferredRotated);
        if (allowRotate) {
            ItemSize preferred = orientedSize(stack, preferredRotated);
            ItemSize flipped = orientedSize(stack, !preferredRotated);
            if (!preferred.equals(flipped)) rotations.add(!preferredRotated);
        }

        PlacementResult swapCandidate = null;
        for (boolean rotated : rotations) {
            ItemSize size = orientedSize(stack, rotated);
            for (int[] candidate : candidateAnchors(
                hoverX, hoverY, size, fractionX, fractionY)) {
                int x = candidate[0];
                int y = candidate[1];
                if (canPlace(x, y, stack, rotated, ignoredAnchors)) {
                    return new PlacementResult(x, y, rotated, PlacementStatus.CAN_PLACE, Set.of());
                }
                Set<Integer> blockers = blockersFor(x, y, stack, rotated);
                blockers.removeAll(ignoredAnchors);
                // A swap has one cursor slot for the displaced item. Treating
                // several covered anchors as a swap based on their total area
                // loses their footprint geometry and can leave overlapping data.
                if (blockers.size() == 1
                    && fitsInBounds(x, y, size) && fitsPackRegion(x, y, size)
                    && !crossesSplitGap(x, size.width()) && swapCandidate == null) {
                    swapCandidate = new PlacementResult(x, y, rotated, PlacementStatus.CAN_SWAP, Set.copyOf(blockers));
                }
            }
        }

        return swapCandidate != null ? swapCandidate : blocked(hoverX, hoverY, preferredRotated, Set.of());
    }

    /**
     * Validates one anchor already resolved by the client placement preview.
     * Packet handlers must use this path so a multi-cell anchor is not treated
     * as a cursor cell and centered a second time on the server.
     */
    public PlacementResult resolveExactPlacement(int anchorX, int anchorY, ItemStack stack,
                                                 boolean rotated, Set<Integer> ignoredAnchors) {
        if (stack.isEmpty() || blockedPredicate.test(stack)) {
            return blocked(anchorX, anchorY, rotated, Set.of());
        }

        int stackAnchor = findAnchorIndexAt(anchorX, anchorY);
        if (stackAnchor >= 0 && !ignoredAnchors.contains(stackAnchor)) {
            ItemStack target = items.get(stackAnchor);
            if (isSameItemIgnoringRotation(target, stack)
                && target.getCount() < target.getMaxStackSize()) {
                return new PlacementResult(stackAnchor % width, stackAnchor / width,
                    isRotated(target), PlacementStatus.CAN_STACK, Set.of(stackAnchor));
            }
        }

        ItemSize size = orientedSize(stack, rotated);
        if (canPlace(anchorX, anchorY, stack, rotated, ignoredAnchors)) {
            return new PlacementResult(anchorX, anchorY, rotated,
                PlacementStatus.CAN_PLACE, Set.of());
        }
        Set<Integer> blockers = blockersFor(anchorX, anchorY, stack, rotated);
        blockers.removeAll(ignoredAnchors);
        if (blockers.size() == 1
            && fitsInBounds(anchorX, anchorY, size)
            && fitsPackRegion(anchorX, anchorY, size)
            && !crossesSplitGap(anchorX, size.width())) {
            return new PlacementResult(anchorX, anchorY, rotated,
                PlacementStatus.CAN_SWAP, Set.copyOf(blockers));
        }
        return blocked(anchorX, anchorY, rotated, Set.of());
    }

    /**
     * Uses the same boundary-driven automatic rotation rule as vanilla slot grids.
     * The carried orientation is retained until the cursor touches the matching
     * grid/region edge or an occupied footprint; the rotated result then expands
     * away from that boundary.
     */
    public PlacementResult resolveCursorPlacement(int hoverX, int hoverY, ItemStack stack,
                                                  double fractionX, double fractionY,
                                                  boolean allowRotate,
                                                  Set<Integer> ignoredAnchors) {
        boolean preferredRotated = isRotated(stack);
        if (stack.isEmpty()) {
            return blocked(hoverX, hoverY, preferredRotated, Set.of());
        }

        int occupied = findAnchorIndexAt(hoverX, hoverY);
        if (occupied >= 0 && !ignoredAnchors.contains(occupied)) {
            ItemStack target = items.get(occupied);
            if (isSameItemIgnoringRotation(target, stack)
                && target.getCount() < target.getMaxStackSize()) {
                return new PlacementResult(occupied % width, occupied / width,
                    isRotated(target), PlacementStatus.CAN_STACK, Set.of(occupied));
            }
        }

        PlacementResult preferred = resolveCursorCandidate(hoverX, hoverY, stack,
            preferredRotated, fractionX, fractionY, ignoredAnchors);
        if (!allowRotate) return preferred;
        ItemSize preferredSize = orientedSize(stack, preferredRotated);
        if (preferredSize.width() == preferredSize.height()) return preferred;

        boolean boundary;
        if (preferredSize.width() > preferredSize.height()) {
            boundary = isBoundaryContact(hoverX + (fractionX < 0.5D ? -1 : 1), hoverY);
        } else {
            boundary = isBoundaryContact(hoverX, hoverY + (fractionY < 0.5D ? -1 : 1));
        }
        if (!boundary) return preferred;
        PlacementResult rotated = resolveCursorCandidate(hoverX, hoverY, stack,
            !preferredRotated, fractionX, fractionY, ignoredAnchors);
        return rotated.isAccepted() ? rotated : preferred;
    }

    private PlacementResult resolveCursorCandidate(int hoverX, int hoverY,
                                                     ItemStack stack,
                                                     boolean rotated,
                                                     double fractionX,
                                                     double fractionY,
                                                     Set<Integer> ignoredAnchors) {
        ItemSize size = orientedSize(stack, rotated);
        PlacementResult swapCandidate = null;
        for (int[] candidate : candidateAnchors(hoverX, hoverY, size, fractionX, fractionY)) {
            PlacementResult placement = resolveExactPlacement(candidate[0], candidate[1],
                stack, rotated, ignoredAnchors);
            if (placement.status() == PlacementStatus.CAN_PLACE
                || placement.status() == PlacementStatus.CAN_STACK) {
                return placement;
            }
            if (placement.status() == PlacementStatus.CAN_SWAP && swapCandidate == null) {
                swapCandidate = placement;
            }
        }
        return swapCandidate != null
            ? swapCandidate
            : blocked(hoverX, hoverY, rotated, Set.of());
    }

    private boolean isBoundaryContact(int x, int y) {
        if (x < 0 || y < 0 || x >= width || y >= height) return true;
        ItemSize oneCell = new ItemSize(1, 1);
        if (!fitsPackRegion(x, y, oneCell) || crossesSplitGap(x, 1)) return true;
        return findAnchorIndexAt(x, y) >= 0;
    }
    private PlacementResult blocked(int x, int y, boolean rotated, Set<Integer> blockers) {
        return new PlacementResult(x, y, rotated, PlacementStatus.BLOCKED, blockers);
    }

    private List<int[]> candidateAnchors(int hoverX, int hoverY, ItemSize size,
                                         double fractionX, double fractionY) {
        List<int[]> candidates = new ArrayList<>();
        int preferredX = hoverX
            - ContainerGridHelper.anchorOffset(size.width(), fractionX);
        int preferredY = hoverY
            - ContainerGridHelper.anchorOffset(size.height(), fractionY);
        candidates.add(new int[]{preferredX, preferredY});
        for (int y = hoverY; y >= hoverY - size.height() + 1; y--) {
            for (int x = hoverX; x >= hoverX - size.width() + 1; x--) {
                if (x != preferredX || y != preferredY) {
                    candidates.add(new int[]{x, y});
                }
            }
        }
        return candidates;
    }

    private boolean fitsInBounds(int x, int y, ItemSize size) {
        return x >= 0 && y >= 0 && x + size.width() <= width && y + size.height() <= height;
    }

    public PlacementResult findPlacementWithin(ItemStack stack, boolean preferredRotated,
                                               int minX, int minY, int maxX, int maxY) {
        if (stack.isEmpty()) return blocked(minX, minY, preferredRotated, Set.of());
        boolean[] rotations = preferredRotated
            ? new boolean[]{true, false}
            : new boolean[]{false, true};
        for (boolean rotated : rotations) {
            ItemSize size = orientedSize(stack, rotated);
            if (rotated != preferredRotated && size.equals(orientedSize(stack, preferredRotated))) continue;
            for (int y = minY; y + size.height() <= maxY; y++) {
                for (int x = minX; x + size.width() <= maxX; x++) {
                    if (canPlace(x, y, stack, rotated, Set.of())) {
                        return new PlacementResult(x, y, rotated, PlacementStatus.CAN_PLACE, Set.of());
                    }
                }
            }
        }
        return blocked(minX, minY, preferredRotated, Set.of());
    }

    public ItemStack getStackAtIncludingFootprint(int x, int y) {
        int idx = findAnchorIndexAt(x, y);
        return idx >= 0 ? items.get(idx) : ItemStack.EMPTY;
    }

    public boolean canStackAt(int x, int y, ItemStack carried) {
        if (carried.isEmpty()) return false;
        int idx = findAnchorIndexAt(x, y);
        if (idx < 0) return false;
        ItemStack target = items.get(idx);
        return isSameItemIgnoringRotation(target, carried)
            && target.getCount() < target.getMaxStackSize();
    }

    public boolean stackInto(int x, int y, ItemStack carried) {
        int idx = findAnchorIndexAt(x, y);
        if (idx < 0 || carried.isEmpty()) return false;
        ItemStack target = items.get(idx);
        if (!isSameItemIgnoringRotation(target, carried)) return false;
        int move = Math.min(carried.getCount(), target.getMaxStackSize() - target.getCount());
        if (move <= 0) return false;
        target.grow(move);
        carried.shrink(move);
        save();
        return true;
    }

    public ItemStack removeAnchor(int anchorIdx) {
        if (anchorIdx < 0 || anchorIdx >= items.size()) return ItemStack.EMPTY;
        ItemStack result = items.get(anchorIdx).copy();
        items.set(anchorIdx, ItemStack.EMPTY);
        save();
        return result;
    }

    public static boolean isBlocked(ItemStack stack) {
        if (stack.isEmpty()) return true;
        if (stack.is(ModTags.SAFETY_BOX)) return true;
        return Config.INSTANCE.isBlacklisted(stack);
    }

    /**
     * Equipped chest rigs and backpacks are general-purpose grid storage.
     * Their capacity is governed by footprint placement, not by the safety-box
     * blacklist. Safety boxes remain non-nestable.
     */
    public static boolean isBlockedInEquippedPack(ItemStack stack) {
        return stack.isEmpty() || stack.is(ModTags.SAFETY_BOX);
    }

    /**
     * Shared client/server policy for an equipped carrier. Only chest rigs and
     * backpacks may nest Xero Delta packs; card holders reject every pack.
     */
    public static boolean isBlockedInEquippedStorage(String identifier, ItemStack stack) {
        if ("chest_rig".equals(identifier) || "backpack".equals(identifier)) {
            return isBlockedInEquippedPack(stack);
        }
        return stack.getItem() instanceof DeltaPackItem || isBlocked(stack);
    }

    public static boolean isRotated(ItemStack stack) {
        return stack.getOrDefault(ModDataComponents.GRID_ROTATED.get(), false);
    }

    public static void setRotated(ItemStack stack, boolean rotated) {
        if (rotated) stack.set(ModDataComponents.GRID_ROTATED.get(), true);
        else stack.remove(ModDataComponents.GRID_ROTATED.get());
    }

    public static boolean isSameItemIgnoringRotation(ItemStack first, ItemStack second) {
        if (first.isEmpty() || second.isEmpty() || !first.is(second.getItem())) return false;
        ItemStack normalizedFirst = first.copy();
        ItemStack normalizedSecond = second.copy();
        setRotated(normalizedFirst, false);
        setRotated(normalizedSecond, false);
        normalizedFirst.remove(ModDataComponents.LOOT_SEARCHED.get());
        normalizedSecond.remove(ModDataComponents.LOOT_SEARCHED.get());
        return ItemStack.isSameItemSameComponents(normalizedFirst, normalizedSecond);
    }

    public static ItemSize baseSize(ItemStack stack) {
        return ModDataStorage.getCachedSizeFor(stack);
    }

    public static ItemSize orientedSize(ItemStack stack, boolean rotated) {
        ItemSize size = baseSize(stack);
        return rotated ? size.rotated() : size;
    }

    public static ItemSize sizeOfStored(ItemStack stack) {
        return orientedSize(stack, isRotated(stack));
    }

    private boolean fitsPackRegion(int x, int y, ItemSize size) {
        if (!(boxStack.getItem() instanceof DeltaPackItem pack)) return true;
        return pack.regions().stream().anyMatch(region ->
            region.contains(x, y, size.width(), size.height()));
    }

    public boolean crossesSplitGap(int x, int footprintWidth) {
        return isSplitGrid() && x < 3 && x + footprintWidth > 3;
    }

    public boolean isSplitGrid() {
        return width == 4 && height == 2;
    }
}
