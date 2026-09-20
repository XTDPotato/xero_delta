package com.xtdpotato.xero_delta.compat;

import com.xtdpotato.xero_delta.ModDataComponents;
import com.xtdpotato.xero_delta.data.PlayerLayoutSlotRules;
import com.xtdpotato.xero_delta.data.SafetyBoxAccessData;
import com.xtdpotato.xero_delta.grid.DeltaPackTransferService;
import com.xtdpotato.xero_delta.grid.GridBackingStore;
import com.xtdpotato.xero_delta.item.DeltaPackItem;
import com.xtdpotato.xero_delta.item.SafetyBoxItem;
import com.xtdpotato.xero_delta.item.TimedUseItem;
import com.xtdpotato.xero_delta.network.PlayerEquipmentSync;
import com.xtdpotato.xero_delta.trading.TradingOptionalBackpackIntegrations;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import top.theillusivec4.curios.api.CuriosApi;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiPredicate;

/**
 * Server-authoritative automatic equip/upgrade for Delta chest rigs and backpacks.
 * Every upgrade is validated in a temporary grid before the equipped slot changes.
 */
public final class DeltaPackAutoEquipService {
    public enum PickupPreparation {
        NO_CONTENTS,
        READY,
        BLOCKED
    }

    public static final class PickupTransaction {
        private final PickupPreparation preparation;
        private final ItemStack source;
        private final List<ItemStack> sourceContents;
        private final List<List<ItemStack>> sourceContainers;
        private final PickupEvacuationPlan plan;
        private boolean finished;

        private PickupTransaction(PickupPreparation preparation, ItemStack source,
                                  List<ItemStack> sourceContents,
                                  List<List<ItemStack>> sourceContainers,
                                  PickupEvacuationPlan plan) {
            this.preparation = preparation;
            this.source = source;
            this.sourceContents = sourceContents;
            this.sourceContainers = sourceContainers;
            this.plan = plan;
        }

        public PickupPreparation preparation() {
            return preparation;
        }

        public void commit() {
            finished = true;
        }

        public void rollback() {
            if (finished || preparation != PickupPreparation.READY) return;
            finished = true;
            if (plan != null) plan.restoreOriginals();
            if (source != null && !source.isEmpty()) {
                source.set(ModDataComponents.GRID_CONTENTS.get(), copyStacks(sourceContents));
                source.set(ModDataComponents.GRID_CONTAINERS.get(), copyContainers(sourceContainers));
            }
            if (plan != null) sync(plan.player);
        }
    }

    private DeltaPackAutoEquipService() {
    }

    /**
     * Empties a picked-up carrier only when every nested item has a legal
     * destination. All writes are staged in copied stacks and committed as one
     * operation, so a failed pickup leaves the ground item and player storage
     * unchanged.
     */
    public static PickupTransaction prepareCarrierPickup(ServerPlayer player,
                                                         ItemStack source) {
        DeltaPackTransferService.Payload payload = DeltaPackTransferService.payload(source);
        if (payload == null) {
            return new PickupTransaction(PickupPreparation.NO_CONTENTS,
                source, List.of(), List.of(), null);
        }
        DeltaPackItem sourcePack = (DeltaPackItem) source.getItem();
        PickupEvacuationPlan plan = PickupEvacuationPlan.create(
            player, sourcePack);
        if (plan == null || !plan.insertAll(payload.contents())) {
            return new PickupTransaction(PickupPreparation.BLOCKED,
                source, List.of(), List.of(), null);
        }
        List<ItemStack> originalContents = copyStacks(source.getOrDefault(
            ModDataComponents.GRID_CONTENTS.get(), List.of()));
        List<List<ItemStack>> originalContainers = copyContainers(source.getOrDefault(
            ModDataComponents.GRID_CONTAINERS.get(), List.of()));
        plan.commit();
        source.set(ModDataComponents.GRID_CONTENTS.get(), List.of());
        source.set(ModDataComponents.GRID_CONTAINERS.get(), List.of());
        sync(player);
        return new PickupTransaction(PickupPreparation.READY, source,
            originalContents, originalContainers, plan);
    }

    /**
     * Tries every dedicated Delta loadout destination before a quick-moved item
     * is routed into carried storage. Each equip operation is transactional.
     */
    public static boolean tryAutoEquipLoadoutPickup(ServerPlayer player, ItemStack source) {
        if (player == null || source == null || source.isEmpty()) return false;
        if (tryEquipFromStack(player, source)) return true;
        if (source.isEmpty()) return true;
        if (tryAutoEquipArmorPickup(player, source)) return true;
        if (source.isEmpty()) return true;
        return tryAutoEquipTaczGunPickup(player, source);
    }

    /** Explicit quick-move gestures replace the matching loadout item even
     * when the incoming item is not an automatic pickup upgrade. */
    public static boolean tryQuickEquipLoadout(ServerPlayer player, ItemStack source) {
        return tryQuickEquipLoadout(player, source,
            (identifier, displaced) -> tryStoreInEquippedCarrier(player, displaced,
                "chest_rig".equals(identifier) ? "backpack" : "chest_rig"));
    }

    /** The sink must consume the entire displaced empty carrier or the swap is cancelled. */
    public static boolean tryQuickEquipLoadout(ServerPlayer player, ItemStack source,
                                               BiPredicate<String, ItemStack> displacedSink) {
        if (player == null || source == null || source.isEmpty()) return false;
        if (tryEquipFromStack(player, source, false, displacedSink)) return true;
        return !source.isEmpty() && tryEquipArmor(player, source, false);
    }

    /** Consumes one pack from {@code source} when it can be equipped or upgraded. */
    public static boolean tryEquipFromStack(ServerPlayer player, ItemStack source) {
        return tryEquipFromStack(player, source, true, null);
    }

    private static boolean tryEquipFromStack(ServerPlayer player, ItemStack source,
                                             boolean requireUpgrade,
                                             BiPredicate<String, ItemStack> displacedSink) {
        if (player == null || source == null || source.isEmpty()
            || !(source.getItem() instanceof DeltaPackItem incoming)
            || !("chest_rig".equals(incoming.slotIdentifier())
                || "backpack".equals(incoming.slotIdentifier()))) {
            return false;
        }

        var curiosOptional = CuriosApi.getCuriosInventory(player);
        if (curiosOptional.isEmpty()) return false;
        var curios = curiosOptional.get();
        String identifier = incoming.slotIdentifier();
        var slotHandler = curios.getStacksHandler(identifier).orElse(null);
        if (slotHandler == null || slotHandler.getSlots() <= 0
            || !curios.isSlotActive(identifier, 0)) return false;

        ItemStack current = slotHandler.getStacks().getStackInSlot(0);
        ItemStack replacement = source.copyWithCount(1);
        if (current.isEmpty()) {
            curios.setEquippedCurio(identifier, 0, replacement);
            slotHandler.update();
            source.shrink(1);
            sync(player);
            return true;
        }

        if (!(current.getItem() instanceof DeltaPackItem oldPack)
            || !identifier.equals(oldPack.slotIdentifier())
            || (requireUpgrade && incoming.capacity() <= oldPack.capacity())) return false;

        GridBackingStore replacementStore = new GridBackingStore(
            replacement, incoming.gridWidth(), incoming.gridHeight(), 0,
            stack -> GridBackingStore.isBlockedInEquippedStorage(identifier, stack));
        if (!isEmpty(replacementStore.getAllItems())) return false;

        GridBackingStore oldStore = new GridBackingStore(
            current, oldPack.gridWidth(), oldPack.gridHeight());
        List<ItemStack> oldItems = oldStore.getAllItems();
        for (ItemStack oldItem : oldItems) {
            if (oldItem.isEmpty()) continue;
            GridBackingStore.PlacementResult placement = replacementStore.findFreePlacement(oldItem);
            if (placement.status() != GridBackingStore.PlacementStatus.CAN_PLACE
                || !replacementStore.place(placement.x(), placement.y(), oldItem,
                    placement.rotated())) {
                return false;
            }
        }

        DeltaPackTransferService.Payload displacedPayload =
            DeltaPackTransferService.payload(current);
        ItemStack displaced = displacedPayload == null
            ? current.copyWithCount(1) : displacedPayload.emptyCarrier().copyWithCount(1);
        source.shrink(1);

        if (displacedSink != null) {
            boolean stored = displacedSink.test(identifier, displaced);
            if (!stored || !displaced.isEmpty()) {
                source.grow(1);
                return false;
            }
        }

        curios.setEquippedCurio(identifier, 0, replacement);
        slotHandler.update();
        if (displacedSink == null) {
            player.getInventory().add(displaced);
            if (!displaced.isEmpty()) player.drop(displaced, false);
        }
        sync(player);
        return true;
    }

    /**
     * Equips a picked-up helmet/chest item only when it is not worse in either
     * quality or remaining durability. The old item is preserved in the same
     * backpack-then-chest storage path before the replacement is committed.
     */
    public static boolean tryAutoEquipArmorPickup(ServerPlayer player, ItemStack incoming) {
        return tryEquipArmor(player, incoming, true);
    }

    private static boolean tryEquipArmor(ServerPlayer player, ItemStack incoming,
                                         boolean requireUpgrade) {
        if (player == null || incoming == null || incoming.isEmpty()
            || incoming.getCount() != 1) return false;
        var slot = incoming.canEquip(net.minecraft.world.entity.EquipmentSlot.HEAD, player)
            ? net.minecraft.world.entity.EquipmentSlot.HEAD
            : incoming.canEquip(net.minecraft.world.entity.EquipmentSlot.CHEST, player)
                ? net.minecraft.world.entity.EquipmentSlot.CHEST : null;
        if (slot != net.minecraft.world.entity.EquipmentSlot.HEAD
            && slot != net.minecraft.world.entity.EquipmentSlot.CHEST) return false;
        ItemStack current = player.getItemBySlot(slot);
        if (current.isEmpty()) {
            player.setItemSlot(slot, incoming.copy());
            incoming.setCount(0);
            PlayerEquipmentSync.sync(player, slot);
            return true;
        }
        int incomingQuality = qualityRank(com.xtdpotato.xero_delta.data.ServerItemRules
            .getQualityFor(incoming));
        int currentQuality = qualityRank(com.xtdpotato.xero_delta.data.ServerItemRules
            .getQualityFor(current));
        int incomingDurability = remainingDurability(incoming);
        int currentDurability = remainingDurability(current);
        if (requireUpgrade && (incomingQuality < currentQuality
            || incomingDurability < currentDurability
            || (incomingQuality == currentQuality
                && incomingDurability == currentDurability))) {
            return false;
        }
        ItemStack old = current.copy();
        tryStoreInEquippedBackpack(player, old);
        if (!old.isEmpty()) return false;
        player.setItemSlot(slot, incoming.copy());
        incoming.setCount(0);
        PlayerEquipmentSync.sync(player, slot);
        return true;
    }

    /**
     * Explicit inventory gestures fill an empty legal primary or sidearm slot.
     * Existing loadout weapons are never displaced implicitly, so a failed
     * gesture cannot duplicate or lose an item when carried storage is full.
     */
    public static boolean tryQuickEquipWeaponLoadout(ServerPlayer player, ItemStack incoming) {
        if (player == null || incoming == null || incoming.isEmpty()
            || !PlayerLayoutSlotRules.isTaczGun(incoming)) return false;
        int[] targets = PlayerLayoutSlotRules.isHandgun(incoming)
            ? new int[]{2} : new int[]{0, 1};
        for (int slot : targets) {
            if (!PlayerLayoutSlotRules.canPlace(player, slot, incoming)
                || !player.getInventory().getItem(slot).isEmpty()) continue;
            player.getInventory().setItem(slot, incoming.copyWithCount(1));
            incoming.shrink(1);
            sync(player);
            return true;
        }
        return false;
    }

    /** Places picked-up TACZ weapons in their dedicated Delta loadout slots. */
    public static boolean tryAutoEquipTaczGunPickup(ServerPlayer player, ItemStack incoming) {
        if (player == null || incoming == null || incoming.isEmpty()
            || !PlayerLayoutSlotRules.isTaczGun(incoming)) return false;
        int[] targets = PlayerLayoutSlotRules.isHandgun(incoming)
            ? new int[]{2} : new int[]{0, 1};
        for (int slot : targets) {
            if (!player.getInventory().getItem(slot).isEmpty()
                || !PlayerLayoutSlotRules.canPlace(player, slot, incoming)) continue;
            player.getInventory().setItem(slot, incoming.copyWithCount(1));
            incoming.shrink(1);
            sync(player);
            return true;
        }
        return false;
    }

    private static int remainingDurability(ItemStack stack) {
        return !stack.isDamageableItem() || stack.getMaxDamage() <= 0
            ? Integer.MAX_VALUE : Math.max(0, stack.getMaxDamage() - stack.getDamageValue());
    }

    private static int qualityRank(String quality) {
        return switch (quality == null ? "gray" : quality.toLowerCase(java.util.Locale.ROOT)) {
            case "red", "mythic" -> 6;
            case "gold", "legendary" -> 5;
            case "purple", "epic" -> 4;
            case "blue", "rare" -> 3;
            case "green", "uncommon" -> 2;
            default -> 1;
        };
    }
    /** Inserts medical/repair items into the chest rig first; other pickups prefer the backpack. */
    public static boolean tryStoreInEquippedBackpack(ServerPlayer player, ItemStack source) {
        boolean chestRigFirst = source != null && source.getItem() instanceof TimedUseItem;
        String first = chestRigFirst ? "chest_rig" : "backpack";
        String second = chestRigFirst ? "backpack" : "chest_rig";
        boolean changed = tryStoreInEquippedCarrier(player, source, first);
        if (!source.isEmpty()) {
            changed |= tryStoreInEquippedCarrier(player, source, second);
        }
        return changed;
    }

    public static boolean tryStoreInEquippedCarrier(ServerPlayer player, ItemStack source,
                                                     String identifier) {
        if (player == null || source == null || source.isEmpty()) return false;
        var curiosOptional = CuriosApi.getCuriosInventory(player);
        boolean changed = false;
        if (curiosOptional.isPresent()) {
            var curios = curiosOptional.get();
            var handler = curios.getStacksHandler(identifier).orElse(null);
            if (handler != null && handler.getSlots() > 0
                && curios.isSlotActive(identifier, 0)) {
                ItemStack backpackStack = handler.getStacks().getStackInSlot(0);
                boolean curioChanged = false;
                if (backpackStack.getItem() instanceof DeltaPackItem pack
                    && identifier.equals(pack.slotIdentifier())) {
                    GridBackingStore store = new GridBackingStore(backpackStack,
                        pack.gridWidth(), pack.gridHeight(), 0,
                        stack -> GridBackingStore.isBlockedInEquippedStorage(identifier, stack));
                    for (int i = 0; i < store.getSize() && !source.isEmpty(); i++) {
                        int x = i % store.getWidth();
                        int y = i / store.getWidth();
                        if (store.canStackAt(x, y, source)) {
                            curioChanged |= store.stackInto(x, y, source);
                        }
                    }
                    if (!source.isEmpty()) {
                        GridBackingStore.PlacementResult placement =
                            store.findFreePlacement(source);
                        if (placement.status() == GridBackingStore.PlacementStatus.CAN_PLACE
                            && store.place(placement.x(), placement.y(), source,
                                placement.rotated())) {
                            source.setCount(0);
                            curioChanged = true;
                        }
                    }
                } else if ("backpack".equals(identifier) && !backpackStack.isEmpty()) {
                    curioChanged = insertIntoCapability(
                        backpackStack.getCapability(Capabilities.ItemHandler.ITEM), source);
                }
                if (curioChanged) handler.update();
                changed |= curioChanged;
            }
        }

        // Traveler's Backpack uses a player attachment instead of the Curios
        // backpack slot. Treat it as the same logical Delta backpack target so
        // warehouse equipment and other quick-moved items can use its capacity.
        if ("backpack".equals(identifier) && !source.isEmpty()) {
            ItemStack travelersBackpack =
                TradingOptionalBackpackIntegrations.travelersAttachmentStack(player);
            IItemHandler travelersInventory = travelersBackpack.isEmpty() ? null
                : travelersBackpack.getCapability(Capabilities.ItemHandler.ITEM);
            boolean travelersChanged = insertIntoCapability(travelersInventory, source);
            if (travelersChanged) {
                TradingOptionalBackpackIntegrations.synchroniseTravelersAttachment(player);
                changed = true;
            }
        }
        if (changed) sync(player);
        return changed;
    }

    static boolean insertIntoCapability(IItemHandler handler, ItemStack source) {
        if (handler == null || source == null || source.isEmpty()) return false;
        boolean changed = false;
        try {
            for (int slot = 0; slot < handler.getSlots() && !source.isEmpty(); slot++) {
                int before = source.getCount();
                ItemStack remainder = handler.insertItem(slot, source.copy(), false);
                int after = remainder.isEmpty() ? 0 : Math.min(before, remainder.getCount());
                if (after < before) {
                    source.shrink(before - after);
                    changed = true;
                }
            }
        } catch (RuntimeException ignored) {
            // Optional backpack handlers may become invalid while equipment changes.
        }
        return changed;
    }
    /** Applies a drag-drop result to one or more Better Looting ground entities. */
    public static boolean equipFromEntities(ServerPlayer player, List<Integer> entityIds,
                                            String identifier) {
        if (player == null || entityIds == null
            || (!"chest_rig".equals(identifier) && !"backpack".equals(identifier))) {
            return false;
        }
        for (int entityId : entityIds) {
            if (!(player.level().getEntity(entityId) instanceof ItemEntity itemEntity)
                || !itemEntity.isAlive() || player.distanceToSqr(itemEntity) > 100.0D) {
                continue;
            }
            ItemStack ground = itemEntity.getItem();
            if (!(ground.getItem() instanceof DeltaPackItem pack)
                || !identifier.equals(pack.slotIdentifier())) continue;
            int before = ground.getCount();
            if (!tryEquipFromStack(player, ground)) continue;
            if (ground.isEmpty()) itemEntity.discard();
            else itemEntity.setItem(ground);
            return before != ground.getCount() || itemEntity.isRemoved();
        }
        return false;
    }

    private static boolean isEmpty(List<ItemStack> items) {
        for (ItemStack item : items) if (!item.isEmpty()) return false;
        return true;
    }

    private static final class PickupEvacuationPlan {
        private final ServerPlayer player;
        private final Map<String, ItemStack> carriers = new LinkedHashMap<>();
        private final Map<Integer, ItemStack> pockets = new LinkedHashMap<>();
        private final Map<String, ItemStack> originalCarriers = new LinkedHashMap<>();
        private final Map<Integer, ItemStack> originalPockets = new LinkedHashMap<>();

        private PickupEvacuationPlan(ServerPlayer player) {
            this.player = player;
        }

        static PickupEvacuationPlan create(ServerPlayer player, DeltaPackItem sourcePack) {
            if (player == null) return null;
            PickupEvacuationPlan plan = new PickupEvacuationPlan(player);
            CuriosApi.getCuriosInventory(player).ifPresent(curios -> {
                for (String identifier : List.of("backpack", "chest_rig")) {
                    var handler = curios.getStacksHandler(identifier).orElse(null);
                    if (handler == null || handler.getSlots() <= 0
                        || !curios.isSlotActive(identifier, 0)) continue;
                    ItemStack carrier = handler.getStacks().getStackInSlot(0);
                    if (carrier.getItem() instanceof DeltaPackItem pack
                        && identifier.equals(pack.slotIdentifier())) {
                        // An incoming upgrade will replace this carrier, so its
                        // old grid is not a stable evacuation destination.
                        if (identifier.equals(sourcePack.slotIdentifier())
                            && sourcePack.capacity() > pack.capacity()) continue;
                        plan.carriers.put(identifier, carrier.copy());
                        plan.originalCarriers.put(identifier, carrier.copy());
                    }
                }
                var safetyHandler = curios.getStacksHandler("safety_box").orElse(null);
                if (safetyHandler != null && safetyHandler.getSlots() > 0
                    && curios.isSlotActive("safety_box", 0)) {
                    ItemStack safety = safetyHandler.getStacks().getStackInSlot(0);
                    if (safety.getItem() instanceof SafetyBoxItem
                        && SafetyBoxAccessData.get(player.server).isUnlocked(
                            player.getUUID(),
                            safety.getItemHolder().getKey().location().toString(),
                            System.currentTimeMillis())) {
                        plan.carriers.put("safety_box", safety.copy());
                        plan.originalCarriers.put("safety_box", safety.copy());
                    }
                }
            });
            for (int slot = 4; slot <= 8; slot++) {
                plan.pockets.put(slot, player.getInventory().getItem(slot).copy());
                plan.originalPockets.put(slot, player.getInventory().getItem(slot).copy());
            }
            return plan;
        }

        boolean insertAll(List<ItemStack> contents) {
            for (ItemStack original : contents) {
                ItemStack remainder = original.copy();
                for (Map.Entry<String, ItemStack> entry : carriers.entrySet()) {
                    if (remainder.isEmpty()) break;
                    insertIntoCarrier(entry.getKey(), entry.getValue(), remainder);
                }
                if (!remainder.isEmpty()) insertIntoPockets(remainder);
                if (!remainder.isEmpty()) return false;
            }
            return true;
        }

        private void insertIntoCarrier(String identifier, ItemStack carrier,
                                       ItemStack remainder) {
            int width;
            int height;
            java.util.function.Predicate<ItemStack> blocked;
            if (carrier.getItem() instanceof DeltaPackItem pack) {
                width = pack.gridWidth();
                height = pack.gridHeight();
                blocked = stack -> GridBackingStore.isBlockedInEquippedStorage(
                    identifier, stack);
            } else if (carrier.getItem() instanceof SafetyBoxItem box) {
                width = box.getGridWidth();
                height = box.getGridHeight();
                blocked = GridBackingStore::isBlocked;
            } else {
                return;
            }
            GridBackingStore store = new GridBackingStore(
                carrier, width, height, 0, blocked);
            BetterLootingPickupCompat.stackIntoExisting(store, remainder);
            while (!remainder.isEmpty()) {
                GridBackingStore.PlacementResult placement =
                    store.findFreePlacement(remainder);
                if (placement.status() != GridBackingStore.PlacementStatus.CAN_PLACE) break;
                int moved = Math.min(remainder.getCount(), remainder.getMaxStackSize());
                ItemStack placed = remainder.copyWithCount(moved);
                if (!store.place(placement.x(), placement.y(), placed,
                    placement.rotated())) break;
                remainder.shrink(moved);
            }
        }

        private void insertIntoPockets(ItemStack remainder) {
            for (int slot = 4; slot <= 8 && !remainder.isEmpty(); slot++) {
                ItemStack existing = pockets.get(slot);
                if (existing.isEmpty()
                    || !ItemStack.isSameItemSameComponents(existing, remainder)) continue;
                int move = Math.min(remainder.getCount(),
                    Math.min(existing.getMaxStackSize(),
                        player.getInventory().getMaxStackSize()) - existing.getCount());
                if (move > 0) {
                    existing.grow(move);
                    remainder.shrink(move);
                }
            }
            for (int slot = 4; slot <= 8 && !remainder.isEmpty(); slot++) {
                if (!pockets.get(slot).isEmpty()
                    || !PlayerLayoutSlotRules.canPlace(player, slot, remainder)) continue;
                int move = Math.min(remainder.getCount(),
                    Math.min(remainder.getMaxStackSize(),
                        player.getInventory().getMaxStackSize()));
                pockets.put(slot, remainder.copyWithCount(move));
                remainder.shrink(move);
            }
        }

        void commit() {
            CuriosApi.getCuriosInventory(player).ifPresent(curios -> {
                carriers.forEach((identifier, carrier) -> {
                    curios.setEquippedCurio(identifier, 0, carrier.copy());
                    var handler = curios.getStacksHandler(identifier).orElse(null);
                    if (handler != null) handler.update();
                });
            });
            pockets.forEach((slot, stack) ->
                player.getInventory().setItem(slot, stack.copy()));
        }

        void restoreOriginals() {
            CuriosApi.getCuriosInventory(player).ifPresent(curios -> {
                originalCarriers.forEach((identifier, carrier) -> {
                    curios.setEquippedCurio(identifier, 0, carrier.copy());
                    var handler = curios.getStacksHandler(identifier).orElse(null);
                    if (handler != null) handler.update();
                });
            });
            originalPockets.forEach((slot, stack) ->
                player.getInventory().setItem(slot, stack.copy()));
        }
    }

    private static List<ItemStack> copyStacks(List<ItemStack> stacks) {
        if (stacks == null || stacks.isEmpty()) return List.of();
        List<ItemStack> result = new java.util.ArrayList<>(stacks.size());
        for (ItemStack stack : stacks) {
            result.add(stack == null ? ItemStack.EMPTY : stack.copy());
        }
        return List.copyOf(result);
    }

    private static List<List<ItemStack>> copyContainers(List<List<ItemStack>> containers) {
        if (containers == null || containers.isEmpty()) return List.of();
        List<List<ItemStack>> result = new java.util.ArrayList<>(containers.size());
        for (List<ItemStack> container : containers) result.add(copyStacks(container));
        return List.copyOf(result);
    }

    private static void sync(ServerPlayer player) {
        player.getInventory().setChanged();
        player.inventoryMenu.broadcastChanges();
        if (player.containerMenu != player.inventoryMenu) {
            player.containerMenu.broadcastChanges();
        }
    }
}
