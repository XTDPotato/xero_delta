package com.xtdpotato.xero_delta.data;

import com.xtdpotato.xero_delta.ModDataComponents;

import com.xtdpotato.xero_delta.grid.ContainerGridHelper;
import com.xtdpotato.xero_delta.grid.SafetyBoxMenu;
import com.xtdpotato.xero_delta.menu.CorpseMenu;
import com.xtdpotato.xero_delta.network.LootSearchStatePacket;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AbstractFurnaceMenu;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.BeaconMenu;
import net.minecraft.world.inventory.BrewingStandMenu;
import net.minecraft.world.inventory.CartographyTableMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.CrafterMenu;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.inventory.GrindstoneMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.LoomMenu;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.inventory.StonecutterMenu;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/** Server-authoritative sequential searching for external storage contents. */
public final class LootSearchManager {
    private static final Map<UUID, Session> ACTIVE = new HashMap<>();
    private static final Map<Container, Map<UUID, Map<Integer, ItemStack>>> SEARCHED =
        new WeakHashMap<>();
    private static final Map<Container, Map<Integer, SearchLock>> SEARCH_LOCKS =
        new WeakHashMap<>();
    private static final Map<Container, String> CONTAINER_SCOPES = new WeakHashMap<>();
    private static final Map<String, Map<UUID, Map<Integer, ItemStack>>> SCOPED_SEARCHED =
        new HashMap<>();
    private static final Map<String, Map<Integer, SearchLock>> SCOPED_LOCKS =
        new HashMap<>();
    private static final Map<UUID, RecentTarget> RECENT_TARGETS = new HashMap<>();
    private static final Set<String> PENDING_BLOCK_RESETS = new HashSet<>();
    private static final Set<UUID> PENDING_ENTITY_RESETS = new HashSet<>();
    private static final String ENTITY_OVERRIDE_SET = "xero_delta_search_override_set";
    private static final String ENTITY_OVERRIDE = "xero_delta_search_override";

    private LootSearchManager() {
    }

    public static void open(ServerPlayer player, AbstractContainerMenu menu) {
        Session previous = ACTIVE.remove(player.getUUID());
        if (previous != null) previous.releaseLock(player);
        if (!isSearchableMenu(menu)) {
            sendEmpty(player, menu.containerId);
            return;
        }
        bindRecentScope(player, menu);
        Boolean override = recentOverride(player);
        if (Boolean.FALSE.equals(override)) {
            sendEmpty(player, menu.containerId);
            return;
        }
        if (Boolean.TRUE.equals(override)) consumePendingReset(player, menu);

        ContainerGridHelper.refresh(menu, ModDataStorage::getCachedSizeFor);
        List<Target> targets = collectTargets(player, menu);
        if (targets.isEmpty()) {
            sendEmpty(player, menu.containerId);
            return;
        }

        Session session = new Session(menu, targets);
        ACTIVE.put(player.getUUID(), session);
        session.startNext(player);
    }

    public static void tick(ServerPlayer player) {
        Session session = ACTIVE.get(player.getUUID());
        if (session == null) return;
        if (player.containerMenu != session.menu) {
            session.releaseLock(player);
            ACTIVE.remove(player.getUUID());
            sendEmpty(player, session.menu.containerId);
            return;
        }
        session.tick(player);
        if (session.finished()) ACTIVE.remove(player.getUUID());
    }

    public static void close(ServerPlayer player, AbstractContainerMenu menu) {
        Session session = ACTIVE.get(player.getUUID());
        if (session == null || session.menu != menu) return;
        session.releaseLock(player);
        ACTIVE.remove(player.getUUID());
        sendEmpty(player, menu.containerId);
    }

    public static void logout(ServerPlayer player) {
        Session session = ACTIVE.remove(player.getUUID());
        if (session != null) session.releaseLock(player);
        RECENT_TARGETS.remove(player.getUUID());
    }

    public static void noteBlockInteraction(ServerPlayer player, BlockPos pos) {
        RECENT_TARGETS.put(player.getUUID(), RecentTarget.block(
            player.serverLevel(), pos, player.serverLevel().getGameTime()));
    }

    public static void noteEntityInteraction(ServerPlayer player, Entity entity) {
        RECENT_TARGETS.put(player.getUUID(), RecentTarget.entity(
            entity, player.serverLevel().getGameTime()));
    }

    public static void setBlockSearchRequired(ServerLevel level, BlockPos pos, boolean required) {
        LootSearchRulesData.get(level.getServer()).setBlockRule(level.dimension(), pos, required);
        String key = blockTargetKey(level.dimension(), pos);
        if (required) PENDING_BLOCK_RESETS.add(key); else PENDING_BLOCK_RESETS.remove(key);
        if (level.getBlockEntity(pos) instanceof Container container) {
            resetContainer(container);
            clearLootSearchedMarkers(container);
        }
    }

    public static void setEntitySearchRequired(Entity entity, boolean required) {
        entity.getPersistentData().putBoolean(ENTITY_OVERRIDE_SET, true);
        entity.getPersistentData().putBoolean(ENTITY_OVERRIDE, required);
        if (required) PENDING_ENTITY_RESETS.add(entity.getUUID());
        else PENDING_ENTITY_RESETS.remove(entity.getUUID());
        if (entity instanceof Container container) {
            resetContainer(container);
            clearLootSearchedMarkers(container);
        }
        if (entity instanceof com.xtdpotato.xero_delta.entity.CorpseEntity corpse) {
            resetContainer(corpse.inventory());
            clearLootSearchedMarkers(corpse.inventory());
        }
    }

    /** Rebuilds the pending footprint queue after a third-party transactional sort. */
    public static void contentsReordered(ServerPlayer player, AbstractContainerMenu menu) {
        Session session = ACTIVE.get(player.getUUID());
        if (session != null && session.menu == menu) session.forceRestart = true;
    }

    private static void resetContainer(Container container) {
        SEARCHED.remove(container);
        SEARCH_LOCKS.remove(container);
        String scope = CONTAINER_SCOPES.get(container);
        if (scope != null) {
            SCOPED_SEARCHED.remove(scope);
            SCOPED_LOCKS.remove(scope);
        }
        for (Session session : ACTIVE.values()) {
            if (session.uses(container)) session.forceRestart = true;
        }
    }

    /** Removes the persistent searched marker when a container rule is changed. */
    private static void clearLootSearchedMarkers(Container container) {
        if (container == null) return;
        boolean changed = false;
        for (int index = 0; index < container.getContainerSize(); index++) {
            ItemStack stack = container.getItem(index);
            if (stack.isEmpty() || !stack.has(ModDataComponents.LOOT_SEARCHED.get())) continue;
            stack.remove(ModDataComponents.LOOT_SEARCHED.get());
            container.setItem(index, stack);
            changed = true;
        }
        if (changed) container.setChanged();
    }

    private static Boolean recentOverride(ServerPlayer player) {
        RecentTarget target = RECENT_TARGETS.get(player.getUUID());
        if (target == null || player.serverLevel().getGameTime() - target.gameTime > 40L) return null;
        if (target.entity != null) {
            var data = target.entity.getPersistentData();
            return data.getBoolean(ENTITY_OVERRIDE_SET) ? data.getBoolean(ENTITY_OVERRIDE) : null;
        }
        return LootSearchRulesData.get(player.server).blockRule(target.dimension, target.pos);
    }

    private static void consumePendingReset(ServerPlayer player, AbstractContainerMenu menu) {
        RecentTarget target = RECENT_TARGETS.get(player.getUUID());
        boolean pending = target != null && (target.entity != null
            ? PENDING_ENTITY_RESETS.remove(target.entity.getUUID())
            : PENDING_BLOCK_RESETS.remove(blockTargetKey(target.dimension, target.pos)));
        if (!pending) return;
        Set<Container> containers = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        for (Slot slot : menu.slots) {
            if (!(slot.container instanceof Inventory)) containers.add(slot.container);
        }
        containers.forEach(container -> {
            resetContainer(container);
            clearLootSearchedMarkers(container);
        });
    }

    private static String blockTargetKey(ResourceKey<Level> dimension, BlockPos pos) {
        return dimension.location() + "|" + pos.asLong();
    }

    private static void bindRecentScope(ServerPlayer player, AbstractContainerMenu menu) {
        RecentTarget target = RECENT_TARGETS.get(player.getUUID());
        if (target == null || player.serverLevel().getGameTime() - target.gameTime > 40L) return;
        String scope = target.entity != null
            ? "entity|" + target.entity.getUUID()
            : "block|" + blockTargetKey(target.dimension, target.pos);
        for (Slot slot : menu.slots) {
            if (!(slot.container instanceof Inventory)) CONTAINER_SCOPES.put(slot.container, scope);
        }
    }

    public static boolean blocksClick(ServerPlayer player, AbstractContainerMenu menu,
                                      int slotId, ClickType clickType) {
        Session session = ACTIVE.get(player.getUUID());
        if (session == null || session.menu != menu || session.hiddenAnchors.isEmpty()) return false;
        if (slotId >= 0 && session.hiddenCells.contains(slotId)) return true;
        if (clickType == ClickType.PICKUP_ALL) return true;
        return clickType == ClickType.QUICK_MOVE && slotId >= 0 && slotId < menu.slots.size()
            && menu.slots.get(slotId).container instanceof Inventory;
    }

    public static void prioritizeNext(ServerPlayer player, int containerId, int slotId) {
        Session session = ACTIVE.get(player.getUUID());
        if (session == null || session.menu != player.containerMenu
            || session.menu.containerId != containerId
            || !session.hiddenAnchors.contains(slotId)) return;
        if (session.current != null && session.current.slotId == slotId) return;
        for (int index = 0; index < session.queued.size(); index++) {
            Target target = session.queued.get(index);
            if (target.slotId != slotId) continue;
            session.queued.remove(index);
            session.queued.add(0, target);
            return;
        }
    }

    private static List<Target> collectTargets(ServerPlayer player, AbstractContainerMenu menu) {
        Map<Integer, Target> bySlotId = new LinkedHashMap<>();
        for (Slot anchor : menu.slots) {
            if (!isSearchableSlot(menu, anchor) || anchor.getItem().isEmpty()) continue;
            ItemStack fingerprint = anchor.getItem().copy();
            if (fingerprint.isEmpty() || wasSearched(player, anchor, fingerprint)) continue;
            Set<Integer> cellIds = new HashSet<>();
            var size = ContainerGridHelper.orientedSize(
                fingerprint, ModDataStorage::getCachedSizeFor);
            Set<Slot> footprint = ContainerGridHelper.footprintCells(menu, anchor, size);
            if (footprint.size() == size.width() * size.height()) {
                for (Slot cell : footprint) cellIds.add(cell.index);
            }
            cellIds.add(anchor.index);
            bySlotId.put(anchor.index, new Target(anchor.index, anchor,
                fingerprint, LootSearchRules.durationTicks(
                    ModDataStorage.getCachedQualityFor(fingerprint)), Set.copyOf(cellIds)));
        }

        List<Integer> order = LootSearchRules.visualOrder(bySlotId.values().stream()
            .map(target -> new LootSearchRules.Cell(
                target.slotId, target.slot.x, target.slot.y))
            .toList());
        List<Target> result = new ArrayList<>(order.size());
        for (int slotId : order) {
            Target target = bySlotId.get(slotId);
            if (target != null) result.add(target);
        }
        return result;
    }

    private static boolean isSearchableMenu(AbstractContainerMenu menu) {
        if (menu == null || menu instanceof InventoryMenu || menu instanceof CraftingMenu
            || menu instanceof AbstractFurnaceMenu || menu instanceof EnchantmentMenu
            || menu instanceof AnvilMenu || menu instanceof SmithingMenu
            || menu instanceof GrindstoneMenu || menu instanceof StonecutterMenu
            || menu instanceof LoomMenu || menu instanceof CartographyTableMenu
            || menu instanceof BrewingStandMenu || menu instanceof BeaconMenu
            || menu instanceof MerchantMenu || menu instanceof CrafterMenu
            || menu instanceof SafetyBoxMenu) return false;
        if (menu instanceof CorpseMenu) return true;

        String name = menu.getClass().getName().toLowerCase(Locale.ROOT);
        if (name.startsWith("com.xtdpotato.xero_delta.")) return false;
        return !containsAny(name, "craft", "furnace", "smelt", "anvil", "smith",
            "merchant", "trade", "mail", "recycl", "recipe", "enchant", "loom",
            "grind", "stonecutter", "beacon", "brewing", "crafter", "upgrade",
            "filter", "setting");
    }

    private static boolean isSearchableSlot(AbstractContainerMenu menu, Slot slot) {
        if (slot == null || !slot.isActive() || slot.container instanceof Inventory) return false;
        if (menu instanceof CorpseMenu) {
            return slot.index >= CorpseMenu.POCKET_START
                && slot.index <= CorpseMenu.POCKET_END
                && !LootSearchRules.isImmediatelyVisibleCorpseSlot(slot.index);
        }
        if (ContainerGridHelper.usesStorageAdapter(menu)) {
            return ContainerGridHelper.isStorageInventorySlot(menu, slot);
        }
        // Searching is a storage rule, not an item-grid rendering rule. This keeps
        // vanilla chests/barrels and third-party storage such as Lootr searchable
        // even when that menu is not registered for Delta grid rendering.
        return true;
    }

    private static boolean containsAny(String value, String... terms) {
        for (String term : terms) if (value.contains(term)) return true;
        return false;
    }

    private static boolean wasSearched(ServerPlayer player, Slot slot, ItemStack current) {
        if (current.getOrDefault(com.xtdpotato.xero_delta.ModDataComponents.LOOT_SEARCHED.get(), "").equals(player.getGameProfile().getName())) return true;
        Map<UUID, Map<Integer, ItemStack>> byPlayer = searchedByPlayer(slot.container);
        for (UUID member : FtbTeamIntegration.memberIdsIncludingSelf(player)) {
            Map<Integer, ItemStack> slots = byPlayer.get(member);
            if (slots != null && sameStack(slots.get(slot.getContainerSlot()), current)) return true;
        }
        return false;
    }

    private static void remember(ServerPlayer player, Target target, ItemStack current) {
        if (!current.isEmpty()) {
            current.set(com.xtdpotato.xero_delta.ModDataComponents.LOOT_SEARCHED.get(), player.getGameProfile().getName());
            ItemStack live = target.slot.getItem();
            if (!live.isEmpty()) live.set(com.xtdpotato.xero_delta.ModDataComponents.LOOT_SEARCHED.get(), player.getGameProfile().getName());
        }
        Map<Integer, ItemStack> slots = searchedByPlayer(target.slot.container)
            .computeIfAbsent(player.getUUID(), ignored -> new HashMap<>());
        if (current.isEmpty()) slots.remove(target.slot.getContainerSlot());
        else slots.put(target.slot.getContainerSlot(), current.copy());
    }

    private static boolean sameStack(ItemStack first, ItemStack second) {
        return first != null && second != null
            && ItemStack.isSameItemSameComponents(first, second);
    }

    private static void sendEmpty(ServerPlayer player, int containerId) {
        PacketDistributor.sendToPlayer(player,
            new LootSearchStatePacket(containerId, List.of(), -1, 0, 0, List.of()));
    }

    private static boolean tryLock(ServerPlayer player, Target target) {
        Map<Integer, SearchLock> locks = locksFor(target.slot.container, true);
        int containerSlot = target.slot.getContainerSlot();
        SearchLock existing = locks.get(containerSlot);
        if (existing != null && !existing.searcher.equals(player.getUUID())) {
            Session owner = ACTIVE.get(existing.searcher);
            if (owner == null || owner.current == null
                || !sameSearchContainer(owner.current.slot.container, target.slot.container)
                || owner.current.slot.getContainerSlot() != containerSlot
                || !sameStack(owner.current.fingerprint, existing.fingerprint)) {
                locks.remove(containerSlot);
                existing = null;
            }
        }
        if (existing != null && !existing.searcher.equals(player.getUUID())) return false;
        locks.put(containerSlot, new SearchLock(player.getUUID(), target.fingerprint.copy()));
        return true;
    }

    private static void unlock(ServerPlayer player, Target target) {
        Map<Integer, SearchLock> locks = locksFor(target.slot.container, false);
        if (locks == null) return;
        int containerSlot = target.slot.getContainerSlot();
        SearchLock lock = locks.get(containerSlot);
        if (lock != null && lock.searcher.equals(player.getUUID())) locks.remove(containerSlot);
        if (locks.isEmpty()) removeLocks(target.slot.container);
    }

    private static List<Integer> teammateLockedSlots(ServerPlayer player, Session viewer) {
        Set<UUID> teammates = new HashSet<>(FtbTeamIntegration.memberIdsIncludingSelf(player));
        teammates.remove(player.getUUID());
        if (teammates.isEmpty()) return List.of();
        List<Integer> result = new ArrayList<>();
        for (Target target : viewer.allHiddenTargets()) {
            Map<Integer, SearchLock> locks = locksFor(target.slot.container, false);
            if (locks == null) continue;
            SearchLock lock = locks.get(target.slot.getContainerSlot());
            if (lock != null && teammates.contains(lock.searcher)) result.add(target.slotId);
        }
        return result.stream().distinct().sorted().toList();
    }

    private static Map<UUID, Map<Integer, ItemStack>> searchedByPlayer(Container container) {
        String scope = CONTAINER_SCOPES.get(container);
        return scope == null
            ? SEARCHED.computeIfAbsent(container, ignored -> new HashMap<>())
            : SCOPED_SEARCHED.computeIfAbsent(scope, ignored -> new HashMap<>());
    }

    private static Map<Integer, SearchLock> locksFor(Container container, boolean create) {
        String scope = CONTAINER_SCOPES.get(container);
        if (scope == null) {
            return create ? SEARCH_LOCKS.computeIfAbsent(container, ignored -> new HashMap<>())
                : SEARCH_LOCKS.get(container);
        }
        return create ? SCOPED_LOCKS.computeIfAbsent(scope, ignored -> new HashMap<>())
            : SCOPED_LOCKS.get(scope);
    }

    private static void removeLocks(Container container) {
        String scope = CONTAINER_SCOPES.get(container);
        if (scope == null) SEARCH_LOCKS.remove(container); else SCOPED_LOCKS.remove(scope);
    }

    private static boolean sameSearchContainer(Container first, Container second) {
        if (first == second) return true;
        String firstScope = CONTAINER_SCOPES.get(first);
        return firstScope != null && firstScope.equals(CONTAINER_SCOPES.get(second));
    }

    private static final class Session {
        private final AbstractContainerMenu menu;
        private final List<Target> queued;
        private final Set<Integer> hiddenAnchors = new HashSet<>();
        private final Set<Integer> hiddenCells = new HashSet<>();
        private Target current;
        private int elapsedTicks;
        private boolean forceRestart;

        private Session(AbstractContainerMenu menu, List<Target> targets) {
            this.menu = menu;
            this.queued = new ArrayList<>(targets);
            for (Target target : targets) {
                hiddenAnchors.add(target.slotId);
                hiddenCells.addAll(target.cellIds);
            }
        }

        private void tick(ServerPlayer player) {
            if (forceRestart) {
                releaseLock(player);
                open(player, menu);
                return;
            }
            pruneTeamCompleted(player);
            if (current == null) {
                startNext(player);
                if (current == null) {
                    if (finished()) sendEmpty(player, menu.containerId); else sync(player);
                    return;
                }
            }
            ItemStack live = current.slot.getItem();
            if (!sameStack(live, current.fingerprint)) {
                if (live.isEmpty()) {
                    remember(player, current, ItemStack.EMPTY);
                    unlockCurrent(player);
                    return;
                }
                unlock(player, current);
                current = current.withStack(live.copy());
                elapsedTicks = 0;
                if (!tryLock(player, current)) {
                    queued.add(0, current);
                    current = null;
                    startNext(player);
                }
                sync(player);
                return;
            }
            elapsedTicks++;
            if (elapsedTicks >= current.durationTicks) {
                remember(player, current, live);
                unlockCurrent(player);
            } else if (player.tickCount % 5 == 0) {
                sync(player);
            }
        }

        private void unlockCurrent(ServerPlayer player) {
            if (current == null) return;
            Target completed = current;
            unlock(player, completed);
            hiddenAnchors.remove(completed.slotId);
            hiddenCells.removeAll(completed.cellIds);
            current = null;
            elapsedTicks = 0;
            startNext(player);
        }

        private void startNext(ServerPlayer player) {
            if (current != null) return;
            int checked = queued.size();
            while (checked-- > 0 && !queued.isEmpty()) {
                Target next = queued.remove(0);
                ItemStack live = next.slot.getItem();
                if (live.isEmpty()) {
                    hiddenAnchors.remove(next.slotId);
                    hiddenCells.removeAll(next.cellIds);
                    remember(player, next, ItemStack.EMPTY);
                    continue;
                }
                if (wasSearched(player, next.slot, live)) {
                    hiddenAnchors.remove(next.slotId);
                    hiddenCells.removeAll(next.cellIds);
                    continue;
                }
                Target active = sameStack(live, next.fingerprint)
                    ? next : next.withStack(live.copy());
                if (!tryLock(player, active)) {
                    queued.add(active);
                    continue;
                }
                current = active;
                elapsedTicks = 0;
                sync(player);
                return;
            }
            if (finished()) sendEmpty(player, menu.containerId); else sync(player);
        }

        private void pruneTeamCompleted(ServerPlayer player) {
            if (current != null && wasSearched(player, current.slot, current.slot.getItem())) {
                unlock(player, current);
                hiddenAnchors.remove(current.slotId);
                hiddenCells.removeAll(current.cellIds);
                current = null;
                elapsedTicks = 0;
            }
            queued.removeIf(target -> {
                ItemStack live = target.slot.getItem();
                if (!live.isEmpty() && !wasSearched(player, target.slot, live)) return false;
                hiddenAnchors.remove(target.slotId);
                hiddenCells.removeAll(target.cellIds);
                return true;
            });
        }

        private void releaseLock(ServerPlayer player) {
            if (current != null) unlock(player, current);
        }

        private List<Target> allHiddenTargets() {
            List<Target> targets = new ArrayList<>(queued);
            if (current != null) targets.add(current);
            return targets;
        }

        private boolean uses(Container container) {
            if (current != null && sameSearchContainer(current.slot.container, container)) return true;
            return queued.stream().anyMatch(
                target -> sameSearchContainer(target.slot.container, container));
        }

        private void sync(ServerPlayer player) {
            List<Integer> hidden = hiddenAnchors.stream().sorted().toList();
            PacketDistributor.sendToPlayer(player, new LootSearchStatePacket(
                menu.containerId, hidden, current == null ? -1 : current.slotId,
                elapsedTicks, current == null ? 0 : current.durationTicks,
                teammateLockedSlots(player, this)));
        }

        private boolean finished() {
            return current == null && queued.isEmpty();
        }
    }

    private record Target(int slotId, Slot slot, ItemStack fingerprint,
                          int durationTicks, Set<Integer> cellIds) {
        private Target withStack(ItemStack stack) {
            return new Target(slotId, slot, stack,
                LootSearchRules.durationTicks(ModDataStorage.getCachedQualityFor(stack)), cellIds);
        }
    }

    private record SearchLock(UUID searcher, ItemStack fingerprint) {
    }

    private record RecentTarget(ResourceKey<Level> dimension, BlockPos pos,
                                Entity entity, long gameTime) {
        private static RecentTarget block(ServerLevel level, BlockPos pos, long gameTime) {
            return new RecentTarget(level.dimension(), pos.immutable(), null, gameTime);
        }

        private static RecentTarget entity(Entity entity, long gameTime) {
            return new RecentTarget(entity.level().dimension(), entity.blockPosition(), entity, gameTime);
        }
    }
}
