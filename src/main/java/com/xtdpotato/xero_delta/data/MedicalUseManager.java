package com.xtdpotato.xero_delta.data;

import com.xtdpotato.xero_delta.grid.GridBackingStore;
import com.xtdpotato.xero_delta.item.DeltaPackItem;
import com.xtdpotato.xero_delta.item.BattlefieldMedicalKitRules;
import com.xtdpotato.xero_delta.item.OutdoorMedicalKitRules;
import com.xtdpotato.xero_delta.item.BasicHealthMedicalRules;
import com.xtdpotato.xero_delta.item.MedicalItem;
import com.xtdpotato.xero_delta.item.ConsumableProfile;
import com.xtdpotato.xero_delta.item.MedicalHealthRules;
import com.xtdpotato.xero_delta.item.MedicalTreatment;
import com.xtdpotato.xero_delta.item.MedicalUseRules;
import com.xtdpotato.xero_delta.item.SafetyBoxItem;
import com.xtdpotato.xero_delta.item.RepairKitItem;
import com.xtdpotato.xero_delta.item.TimedUseItem;
import com.xtdpotato.xero_delta.network.MedicalUseStatePacket;
import com.xtdpotato.xero_delta.network.InventoryTransferSource;
import com.xtdpotato.xero_delta.ModEffects;
import com.xtdpotato.xero_delta.trading.TradingInventorySource;
import com.xtdpotato.xero_delta.trading.TradingInventorySources;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import top.theillusivec4.curios.api.CuriosApi;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.UUID;

/** Server-authoritative timed use of medical items stored outside the hotbar. */
public final class MedicalUseManager {
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private static final Map<UUID, ResourceLocation> PREFERRED_ITEMS = new HashMap<>();

    private MedicalUseManager() {
    }

    /** Temporarily reserved storage items still belong to this player. */
    public static ItemStack reservedFor(ServerPlayer player) {
        Session session = SESSIONS.get(player.getUUID());
        return session == null ? ItemStack.EMPTY : session.reserved();
    }

    public static void startAuto(ServerPlayer player) {
        if (player == null || SESSIONS.containsKey(player.getUUID())
            || player.isDeadOrDying() || player.isSpectator()
            || DownedManager.interactionLocked(player)) return;
        Candidate candidate = findCandidate(player);
        if (candidate == null) {
            if (hasSurgeryItem(player)) {
                player.displayClientMessage(Component.translatable(
                    "medical.xero_delta.not_injured"), true);
            }
            return;
        }
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(candidate.matcher().getItem());
        InventoryTransferSource.Handle source = itemId == null
            ? null : findStoredSource(player, itemId);
        if (source != null) {
            startFromSource(player, source);
            return;
        }
    }

    /**
     * Starts a one-press timed use for the item currently held in {@code hand}.
     * The session is server authoritative, so releasing the use button does not
     * cancel it; the dedicated cancel action (left click) does.
     */
    public static void startStored(ServerPlayer player, ResourceLocation itemId) {
        if (player == null || itemId == null || SESSIONS.containsKey(player.getUUID())
            || player.isDeadOrDying() || player.isSpectator()
            || DownedManager.interactionLocked(player)) return;
        InventoryTransferSource.Handle source = findStoredSource(player, itemId);
        if (source != null) startFromSource(player, source);
    }

    /** Starts a wheel selection and remembers it for later short presses. */
    public static void startFromWheel(ServerPlayer player, ResourceLocation itemId) {
        if (player == null || itemId == null || SESSIONS.containsKey(player.getUUID())
            || player.isDeadOrDying() || player.isSpectator()
            || DownedManager.interactionLocked(player)) return;
        InventoryTransferSource.Handle source = findStoredSource(player, itemId);
        if (source != null && startFromSource(player, source)) {
            PREFERRED_ITEMS.put(player.getUUID(), itemId);
        } else if (hasShortcutItem(player, itemId, true)) {
            player.displayClientMessage(Component.translatable(
                "medical.xero_delta.not_injured"), true);
        }
    }

    /** Starts the exact wheel entry selected by the client. */
    public static void startFromWheel(ServerPlayer player, ResourceLocation itemId,
                                      String sourceId) {
        if (player == null || itemId == null || sourceId == null
            || SESSIONS.containsKey(player.getUUID()) || player.isDeadOrDying()
            || player.isSpectator() || DownedManager.interactionLocked(player)) return;
        InventoryTransferSource.Handle source = InventoryTransferSource.find(player, sourceId);
        ItemStack stack = source == null ? ItemStack.EMPTY : source.peek();
        ResourceLocation actualId = stack.isEmpty() ? null
            : BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (!itemId.equals(actualId)) return;
        if (startFromSource(player, source)) {
            PREFERRED_ITEMS.put(player.getUUID(), itemId);
        } else if (stack.getItem() instanceof MedicalItem) {
            player.displayClientMessage(Component.translatable(
                "medical.xero_delta.not_injured"), true);
        }
    }

    /** Reserves and later consumes exactly the source selected by the detail card. */
    public static boolean startFromSource(ServerPlayer player,
                                          InventoryTransferSource.Handle source) {
        if (player == null || source == null || SESSIONS.containsKey(player.getUUID())
            || player.isDeadOrDying() || player.isSpectator()
            || DownedManager.interactionLocked(player)) return false;
        ItemStack candidate = source.peek();
        if (!(candidate.getItem() instanceof TimedUseItem) || !canUseStored(player, candidate)) {
            return false;
        }
        ItemStack reserved = source.extract(1);
        if (reserved.isEmpty()) return false;
        int duration = Math.max(1, reserved.getUseDuration(player));
        long now = player.serverLevel().getGameTime();
        MedicalTreatment treatment = reserved.getItem() instanceof MedicalItem medical
            ? medical.treatment() : null;
        Session session = new Session(reserved.copyWithCount(1), treatment,
            null, now, duration, source, reserved);
        session.prepareIncrementalHealth(player);
        SESSIONS.put(player.getUUID(), session);
        PacketDistributor.sendToPlayer(player, MedicalUseStatePacket.active(
            reserved, duration, duration, source.sourceId()));
        player.inventoryMenu.broadcastChanges();
        if (player.containerMenu != player.inventoryMenu) player.containerMenu.broadcastChanges();
        return true;
    }

    private static boolean canUseStored(ServerPlayer player, ItemStack stack) {
        if (stack.getItem() instanceof RepairKitItem repairKit) {
            return repairKit.canUse(player);
        }
        return MedicalUseRules.canUseWheelItem(player, stack);
    }

    private static ItemStack findStoredItem(ServerPlayer player, ResourceLocation itemId) {
        for (String identifier : List.of("chest_rig", "backpack", "safety_box")) {
            ItemStack found = findStoredInCarrier(player, identifier, itemId);
            if (!found.isEmpty()) return found;
        }
        for (int slot = 4; slot <= 8; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (!stack.isEmpty() && itemId.equals(id)) return stack.copyWithCount(1);
        }
        return ItemStack.EMPTY;
    }

    private static InventoryTransferSource.Handle findStoredSource(ServerPlayer player,
                                                                    ResourceLocation itemId) {
        TradingInventorySource selected = null;
        int selectedRank = Integer.MAX_VALUE;
        for (TradingInventorySource source : TradingInventorySources.list(player)) {
            ItemStack stack = source.peek();
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (stack.isEmpty() || !itemId.equals(id) || !canUseStored(player, stack)) continue;
            int rank = storedSourceRank(source.id());
            if (rank < selectedRank) {
                selected = source;
                selectedRank = rank;
            }
        }
        return selected == null ? null : InventoryTransferSource.find(player, selected.id());
    }

    private static int storedSourceRank(String sourceId) {
        if (sourceId == null) return Integer.MAX_VALUE;
        if (sourceId.contains("|curio|chest_rig|")) return 0;
        if (sourceId.startsWith("player|")) {
            try {
                int slot = Integer.parseInt(sourceId.substring("player|".length()));
                return slot >= 4 && slot <= 8 ? 1 : Integer.MAX_VALUE;
            } catch (NumberFormatException ignored) {
                return Integer.MAX_VALUE;
            }
        }
        return Integer.MAX_VALUE;
    }

    private static ItemStack findStoredInCarrier(ServerPlayer player, String identifier,
                                                 ResourceLocation itemId) {
        final ItemStack[] found = {ItemStack.EMPTY};
        CuriosApi.getCuriosInventory(player).ifPresent(curios -> {
            var handler = curios.getStacksHandler(identifier).orElse(null);
            if (handler == null || handler.getSlots() <= 0
                || !curios.isSlotActive(identifier, 0)) return;
            ItemStack carrier = handler.getStacks().getStackInSlot(0);
            GridBackingStore store;
            if (carrier.getItem() instanceof DeltaPackItem pack
                && identifier.equals(pack.slotIdentifier())) {
                store = new GridBackingStore(carrier, pack.gridWidth(), pack.gridHeight(), 0,
                    stack -> GridBackingStore.isBlockedInEquippedStorage(identifier, stack));
            } else if ("safety_box".equals(identifier)
                && carrier.getItem() instanceof SafetyBoxItem box) {
                store = new GridBackingStore(carrier, box.getGridWidth(), box.getGridHeight());
            } else {
                return;
            }
            for (int index = 0; index < store.getSize(); index++) {
                ItemStack stack = store.getItemRaw(index % store.getWidth(), index / store.getWidth());
                ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
                if (id != null && id.equals(itemId) && !stack.isEmpty()) {
                    found[0] = stack.copyWithCount(1);
                    return;
                }
            }
        });
        return found[0];
    }
    public static boolean startHeld(ServerPlayer player, InteractionHand hand) {
        if (player == null || hand == null || SESSIONS.containsKey(player.getUUID())
            || player.isDeadOrDying() || player.isSpectator()
            || DownedManager.interactionLocked(player)) return false;
        ItemStack stack = player.getItemInHand(hand);
        if (!(stack.getItem() instanceof TimedUseItem)) return false;
        if (stack.getItem() instanceof RepairKitItem repair && !repair.canUse(player)) return false;
        if (stack.getItem() instanceof MedicalItem
            && !MedicalUseRules.canUseWheelItem(player, stack)) return false;
        int duration = Math.max(1, stack.getUseDuration(player));
        long now = player.serverLevel().getGameTime();
        MedicalTreatment treatment = stack.getItem() instanceof MedicalItem medical
            ? medical.treatment() : null;
        Session session = new Session(stack.copyWithCount(1),
            treatment, hand, now, duration, null, ItemStack.EMPTY);
        session.prepareIncrementalHealth(player);
        SESSIONS.put(player.getUUID(), session);
        PacketDistributor.sendToPlayer(player, MedicalUseStatePacket.active(
            stack, duration, duration));
        return true;
    }

    public static void cancel(ServerPlayer player) {
        if (player == null) return;
        Session session = SESSIONS.remove(player.getUUID());
        if (session == null) return;
        flushHealingDurability(session.healingStack, session);
        syncDurabilityMedicalKit(player);
        if (session.incrementalHealthItem() && session.healingApplied) {
            consumeIncrementalHealthItem(player, session);
            syncDurabilityMedicalKit(player);
        } else {
            restoreReserved(player, session);
        }
        PacketDistributor.sendToPlayer(player, MedicalUseStatePacket.inactive());
    }

    public static boolean cancelForJump(ServerPlayer player) {
        return cancelForProhibitedAction(player);
    }

    public static boolean cancelForProhibitedAction(ServerPlayer player) {
        if (player == null) return false;
        Session session = SESSIONS.get(player.getUUID());
        if (session == null || !(session.matcher().getItem() instanceof MedicalItem)) return false;
        cancel(player);
        return true;
    }

    public static boolean isMedicalActive(ServerPlayer player) {
        if (player == null) return false;
        Session session = SESSIONS.get(player.getUUID());
        return session != null && session.matcher().getItem() instanceof MedicalItem;
    }

    public static void tick(ServerPlayer player) {
        Session session = SESSIONS.get(player.getUUID());
        if (session == null) return;
        if (player.isDeadOrDying() || player.isSpectator()
            || DownedManager.interactionLocked(player)) {
            cancel(player);
            return;
        }
        if (session.matcher().getItem() instanceof MedicalItem
            && (player.isShiftKeyDown() || player.isVisuallyCrawling())) {
            player.setShiftKeyDown(false);
            cancel(player);
            return;
        }
        if (session.hand() != null
            && !matchesHeld(player.getItemInHand(session.hand()), session.matcher())) {
            cancel(player);
            return;
        }
        player.setSprinting(false);
        if (session.durabilityMedicalKit()) {
            tickDurabilityMedicalKit(player, session);
            return;
        }
        if (session.incrementalHealthItem()) {
            tickIncrementalHealthItem(player, session);
            return;
        }
        if (player.serverLevel().getGameTime() < session.finishTick()) return;

        SESSIONS.remove(player.getUUID());
        if (session.hand() != null) {
            finishHeld(player, session);
        } else if (session.source() != null) {
            if (canUseStored(player, session.matcher())) {
                session.reserved().finishUsingItem(player.serverLevel(), player);
            }
            restoreReserved(player, session);
        } else if (canUseStored(player, session.matcher())
            && consumeMatching(player, session.matcher(), session.treatment())) {
            if (session.matcher().getItem() instanceof RepairKitItem) {
                session.matcher().finishUsingItem(player.serverLevel(), player);
            } else {
                MedicalUseRules.applyItem(player, session.matcher());
            }
        }
        PacketDistributor.sendToPlayer(player, MedicalUseStatePacket.inactive());
    }

    public static void clear(ServerPlayer player) {
        if (player == null) return;
        Session session = SESSIONS.remove(player.getUUID());
        if (session != null) flushHealingDurability(session.healingStack, session);
        if (session != null && session.incrementalHealthItem() && session.healingApplied) {
            consumeIncrementalHealthItem(player, session);
        } else {
            restoreReserved(player, session);
        }
        PREFERRED_ITEMS.remove(player.getUUID());
    }

    private static void restoreReserved(ServerPlayer player, Session session) {
        if (session == null || session.source() == null || session.reserved().isEmpty()) return;
        if (!session.source().restore(session.reserved().copy())) {
            player.drop(session.reserved().copy(), false);
        }
        player.inventoryMenu.broadcastChanges();
        if (player.containerMenu != player.inventoryMenu) player.containerMenu.broadcastChanges();
    }

    private static void tickIncrementalHealthItem(ServerPlayer player, Session session) {
        long now = player.serverLevel().getGameTime();
        while (session.nextHealingTick <= now && session.nextHealingTick <= session.finishTick()
            && session.remainingHealing > 0.0001F
            && MedicalHealthRules.hasMissingHealth(player.getHealth(), player.getMaxHealth())) {
            float before = player.getHealth();
            float amount = Math.min(session.healPerPulse(), session.remainingHealing);
            player.heal(Math.min(amount, player.getMaxHealth() - before));
            float healed = Math.max(0.0F, player.getHealth() - before);
            session.remainingHealing = Math.max(0.0F, session.remainingHealing - healed);
            session.nextHealingTick += BasicHealthMedicalRules.PULSE_INTERVAL_TICKS;
            if (healed <= 0.0F) break;
            session.healingApplied = true;
        }
        if (now >= session.finishTick() || session.remainingHealing <= 0.0001F
            || !MedicalHealthRules.hasMissingHealth(player.getHealth(), player.getMaxHealth())) {
            SESSIONS.remove(player.getUUID());
            if (session.healingApplied) consumeIncrementalHealthItem(player, session);
            else restoreReserved(player, session);
            player.getInventory().setChanged();
            player.inventoryMenu.broadcastChanges();
            if (player.containerMenu != player.inventoryMenu) {
                player.containerMenu.broadcastChanges();
            }
            PacketDistributor.sendToPlayer(player, MedicalUseStatePacket.inactive());
        }
    }

    private static void consumeIncrementalHealthItem(ServerPlayer player, Session session) {
        if (session.source() != null) return;
        if (session.hand() != null) {
            ItemStack current = player.getItemInHand(session.hand());
            if (matchesHeld(current, session.matcher())) {
                current.shrink(1);
                player.setItemInHand(session.hand(), current);
            }
            return;
        }
        consumeMatching(player, session.matcher(), session.treatment());
    }

    private static void tickDurabilityMedicalKit(ServerPlayer player, Session session) {
        long now = player.serverLevel().getGameTime();
        if (now < session.startupEndTick()) return;
        ItemStack kit = session.activeStack(player);
        if (kit.isEmpty()) {
            finishDurabilityMedicalSession(player, session);
            return;
        }
        boolean stackChanged = false;
        if (!session.startupApplied) {
            session.startupApplied = true;
            stackChanged = applyDurabilityMedicalStartup(player, kit);
            if (kit.isEmpty()) {
                if (stackChanged) syncDurabilityMedicalKit(player);
                finishDurabilityMedicalSession(player, session);
                return;
            }
            session.recalculateDurabilityMedicalDuration(player, kit, now);
            PacketDistributor.sendToPlayer(player, MedicalUseStatePacket.active(
                kit, session.remainingTicks(now), session.duration(),
                session.source() == null ? "" : session.source().sourceId()));
        }

        // Heal every server tick after activation. This keeps the visible health
        // change continuous at the item's advertised per-second rate.
        while (!kit.isEmpty() && session.nextHealingTick <= now
            && session.nextHealingTick <= session.finishTick()
            && MedicalHealthRules.hasMissingHealth(
                player.getHealth(), player.getMaxHealth())) {
            float healed = healWithDurabilityMedicalKit(player, kit, session);
            session.nextHealingTick++;
            if (healed <= 0.0F) break;
            stackChanged = true;
        }
        if (stackChanged) {
            syncDurabilityMedicalKit(player);
        }
        if (kit.isEmpty() || now >= session.finishTick()
            || !MedicalHealthRules.hasMissingHealth(
                player.getHealth(), player.getMaxHealth())) {
            flushHealingDurability(kit, session);
            finishDurabilityMedicalSession(player, session);
        }
    }

    private static boolean applyDurabilityMedicalStartup(ServerPlayer player, ItemStack kit) {
        if (!MedicalUseRules.hasWoundTreatment(kit)) return false;
        boolean healthMissing = MedicalHealthRules.hasMissingHealth(
            player.getHealth(), player.getMaxHealth());
        if (MedicalUseRules.isOutdoorKit(kit)) {
            return applyOutdoorMedicalStartup(player, kit, healthMissing);
        }
        return applyBattlefieldMedicalStartup(player, kit, healthMissing);
    }

    private static boolean applyBattlefieldMedicalStartup(ServerPlayer player, ItemStack kit,
                                                           boolean healthMissing) {
        boolean changed = tryTreatOneWound(player, kit,
            BattlefieldMedicalKitRules.WOUND_DURABILITY);
        if (healthMissing) {
            changed |= tryGrantPainRelief(player, kit,
                BattlefieldMedicalKitRules.PAIN_RELIEF_DURABILITY,
                BattlefieldMedicalKitRules.PAIN_RELIEF_TICKS);
        }
        return changed;
    }

    private static boolean applyOutdoorMedicalStartup(ServerPlayer player, ItemStack kit,
                                                       boolean healthMissing) {
        if (!healthMissing) return false;
        // Outdoor kits prioritize their health-triggered pain relief, then spend
        // a separate 25 durability to treat one wound when enough remains.
        boolean changed = tryGrantPainRelief(player, kit,
            OutdoorMedicalKitRules.PAIN_RELIEF_DURABILITY,
            OutdoorMedicalKitRules.PAIN_RELIEF_TICKS);
        changed |= tryTreatOneWound(player, kit, OutdoorMedicalKitRules.WOUND_DURABILITY);
        return changed;
    }

    private static boolean tryTreatOneWound(ServerPlayer player, ItemStack kit, int cost) {
        if (kit.isEmpty() || remainingDurability(kit) < cost
            || !PlayerInjuryManager.canApplyTreatment(player, MedicalTreatment.TRAUMA_MINOR)
            || !PlayerInjuryManager.applyTreatment(player, MedicalTreatment.TRAUMA_MINOR)) {
            return false;
        }
        consumeDurability(kit, cost);
        return true;
    }

    private static boolean tryGrantPainRelief(ServerPlayer player, ItemStack kit,
                                              int cost, int durationTicks) {
        if (kit.isEmpty() || player.hasEffect(ModEffects.PAIN_RELIEF)
            || remainingDurability(kit) < cost) return false;
        consumeDurability(kit, cost);
        player.addEffect(new MobEffectInstance(ModEffects.PAIN_RELIEF,
            durationTicks, 0, false, false, true));
        return true;
    }

    private static float healWithDurabilityMedicalKit(ServerPlayer player, ItemStack kit,
                                                       Session session) {
        int available = remainingDurability(kit);
        if (available <= 0) return 0.0F;
        float before = player.getHealth();
        float baseline = PlayerInjuryManager.baselineMaximumHealth(player);
        float displayHealing = Math.min(session.healPerTick(),
            Math.max(0.0F, available - session.pendingHealingDurability));
        float requested = Math.min(player.getMaxHealth() - before,
            MedicalHealthRules.toEntityHealth(displayHealing, baseline));
        if (requested <= 0.0F) return 0.0F;
        player.heal(requested);
        float healed = Math.max(0.0F, player.getHealth() - before);
        session.healingStack = kit;
        session.pendingHealingDurability += MedicalHealthRules.toDisplayHealth(healed, baseline);
        int wholeDurability = (int) Math.floor(session.pendingHealingDurability + 0.0001F);
        if (wholeDurability > 0) {
            consumeDurability(kit, wholeDurability);
            session.pendingHealingDurability -= wholeDurability;
        }
        return healed;
    }

    private static void flushHealingDurability(ItemStack kit, Session session) {
        if (kit.isEmpty() || session.pendingHealingDurability <= 0.0001F) return;
        consumeDurability(kit, 1);
        session.pendingHealingDurability = 0.0F;
    }

    private static int remainingDurability(ItemStack stack) {
        return stack.isEmpty() ? 0 : Math.max(0, stack.getMaxDamage() - stack.getDamageValue());
    }

    private static void consumeDurability(ItemStack stack, int amount) {
        if (stack.isEmpty() || amount <= 0) return;
        int next = stack.getDamageValue() + Math.min(amount, remainingDurability(stack));
        if (next >= stack.getMaxDamage()) stack.shrink(1);
        else stack.setDamageValue(next);
    }

    private static void syncDurabilityMedicalKit(ServerPlayer player) {
        player.getInventory().setChanged();
        player.inventoryMenu.broadcastChanges();
        if (player.containerMenu != player.inventoryMenu) {
            player.containerMenu.broadcastChanges();
        }
    }

    private static void finishDurabilityMedicalSession(ServerPlayer player, Session session) {
        SESSIONS.remove(player.getUUID());
        if (session.source() != null && !session.reserved().isEmpty()) {
            if (!session.source().restore(session.reserved().copy())) {
                player.drop(session.reserved().copy(), false);
            }
        }
        player.getInventory().setChanged();
        player.inventoryMenu.broadcastChanges();
        if (player.containerMenu != player.inventoryMenu) player.containerMenu.broadcastChanges();
        PacketDistributor.sendToPlayer(player, MedicalUseStatePacket.inactive());
    }

    private static Candidate findCandidate(ServerPlayer player) {
        List<ItemStack> items = shortcutItems(player);
        boolean healthNeeded = MedicalHealthRules.hasMissingHealth(
            player.getHealth(), player.getMaxHealth());
        boolean injuryNeeded = items.stream().anyMatch(stack ->
            stack.getItem() instanceof MedicalItem medical
                && MedicalUseRules.isInjuryTreatment(medical.treatment())
                && MedicalUseRules.canTreatInjury(player, stack));
        ResourceLocation preferred = PREFERRED_ITEMS.get(player.getUUID());
        Candidate best = null;
        int bestScore = Integer.MAX_VALUE;
        for (ItemStack stack : items) {
            if (!(stack.getItem() instanceof MedicalItem medical)) continue;
            boolean applicableInjury = (!MedicalUseRules.isHealthItem(stack) || healthNeeded)
                && MedicalUseRules.isInjuryTreatment(medical.treatment())
                && MedicalUseRules.canTreatInjury(player, stack);
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
            int score = MedicalShortcutRules.candidateScore(
                injuryNeeded, healthNeeded, id != null && id.equals(preferred),
                MedicalUseRules.isSurgeryItem(stack), applicableInjury,
                MedicalUseRules.isHealthItem(stack), MedicalUseRules.priority(stack));
            if (score < bestScore) {
                best = new Candidate(stack.copyWithCount(1), medical.treatment(), score);
                bestScore = score;
            }
        }
        return best;
    }

    private static List<ItemStack> shortcutItems(ServerPlayer player) {
        List<ItemStack> items = new ArrayList<>();
        collectChestRigItems(player, items);
        for (int slot = 4; slot <= 8; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.getItem() instanceof MedicalItem) items.add(stack);
        }
        return items;
    }

    private static void collectChestRigItems(ServerPlayer player, List<ItemStack> items) {
        CuriosApi.getCuriosInventory(player).ifPresent(curios -> {
            var handler = curios.getStacksHandler("chest_rig").orElse(null);
            if (handler == null || handler.getSlots() <= 0
                || !curios.isSlotActive("chest_rig", 0)) return;
            ItemStack carrier = handler.getStacks().getStackInSlot(0);
            if (!(carrier.getItem() instanceof DeltaPackItem pack)
                || !"chest_rig".equals(pack.slotIdentifier())) return;
            GridBackingStore store = new GridBackingStore(carrier,
                pack.gridWidth(), pack.gridHeight(), 0,
                stack -> GridBackingStore.isBlockedInEquippedStorage("chest_rig", stack));
            for (int index = 0; index < store.getSize(); index++) {
                ItemStack stack = store.getItemRaw(index % store.getWidth(),
                    index / store.getWidth());
                if (stack.getItem() instanceof MedicalItem) items.add(stack);
            }
        });
    }

    private static boolean hasSurgeryItem(ServerPlayer player) {
        return shortcutItems(player).stream().anyMatch(MedicalUseRules::isSurgeryItem);
    }

    private static boolean hasShortcutItem(ServerPlayer player, ResourceLocation itemId,
                                           boolean surgeryOnly) {
        return shortcutItems(player).stream().anyMatch(stack -> {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
            return itemId.equals(id) && (!surgeryOnly || MedicalUseRules.isSurgeryItem(stack));
        });
    }

    private static boolean consumeMatching(ServerPlayer player, ItemStack matcher,
                                           MedicalTreatment treatment) {
        if (consumeFromCarrier(player, matcher, treatment, "chest_rig")) return true;
        for (int slot = 4; slot <= 8; slot++) {
            ItemStack current = player.getInventory().getItem(slot);
            if (!matches(current, matcher, treatment)) continue;
            current.shrink(1);
            if (current.isEmpty()) player.getInventory().setItem(slot, ItemStack.EMPTY);
            player.getInventory().setChanged();
            player.inventoryMenu.broadcastChanges();
            return true;
        }
        return false;
    }

    private static boolean consumeFromCarrier(ServerPlayer player, ItemStack matcher,
                                               MedicalTreatment treatment, String identifier) {
        final boolean[] consumed = {false};
        CuriosApi.getCuriosInventory(player).ifPresent(curios -> {
            var handler = curios.getStacksHandler(identifier).orElse(null);
            if (handler == null || handler.getSlots() <= 0
                || !curios.isSlotActive(identifier, 0)) return;
            ItemStack carrier = handler.getStacks().getStackInSlot(0);
            GridBackingStore store;
            if (carrier.getItem() instanceof DeltaPackItem pack
                && identifier.equals(pack.slotIdentifier())) {
                store = new GridBackingStore(carrier, pack.gridWidth(), pack.gridHeight(), 0,
                    stack -> GridBackingStore.isBlockedInEquippedStorage(identifier, stack));
            } else if ("safety_box".equals(identifier)
                && carrier.getItem() instanceof SafetyBoxItem box) {
                store = new GridBackingStore(carrier, box.getGridWidth(), box.getGridHeight());
            } else {
                return;
            }
            for (int index = 0; index < store.getSize(); index++) {
                ItemStack current = store.getItemRaw(index % store.getWidth(),
                    index / store.getWidth());
                if (!matches(current, matcher, treatment)) continue;
                current.shrink(1);
                store.save();
                handler.update();
                consumed[0] = true;
                break;
            }
        });
        return consumed[0];
    }

    private static boolean matches(ItemStack current, ItemStack matcher,
                                   MedicalTreatment treatment) {
        if (!GridBackingStore.isSameItemIgnoringRotation(current, matcher)) return false;
        if (matcher.getItem() instanceof RepairKitItem) {
            return current.getItem() instanceof RepairKitItem;
        }
        return current.getItem() instanceof MedicalItem medical
            && medical.treatment() == treatment;
    }

    private static boolean matchesHeld(ItemStack current, ItemStack matcher) {
        if (MedicalUseRules.isDurabilityMedicalKit(matcher)) {
            return !current.isEmpty() && current.is(matcher.getItem());
        }
        return !current.isEmpty()
            && current.getItem() instanceof TimedUseItem
            && GridBackingStore.isSameItemIgnoringRotation(current, matcher);
    }

    private static void finishHeld(ServerPlayer player, Session session) {
        ItemStack current = player.getItemInHand(session.hand());
        if (!matchesHeld(current, session.matcher())) return;
        ItemStack result = current.finishUsingItem(player.serverLevel(), player);
        player.setItemInHand(session.hand(), result);
        player.getInventory().setChanged();
        player.inventoryMenu.broadcastChanges();
        if (player.containerMenu != player.inventoryMenu) {
            player.containerMenu.broadcastChanges();
        }
    }

    private record Candidate(ItemStack matcher, MedicalTreatment treatment, int priority) {
    }

    private static final class Session {
        private final ItemStack matcher;
        private final MedicalTreatment treatment;
        private final InteractionHand hand;
        private final long startTick;
        private int duration;
        private final InventoryTransferSource.Handle source;
        private final ItemStack reserved;
        private boolean startupApplied;
        private boolean healingApplied;
        private float pendingHealingDurability;
        private ItemStack healingStack = ItemStack.EMPTY;
        private float remainingHealing;
        private float incrementalHealPerPulse;
        private long nextHealingTick;

        private Session(ItemStack matcher, MedicalTreatment treatment, InteractionHand hand,
                        long startTick, int duration, InventoryTransferSource.Handle source,
                        ItemStack reserved) {
            this.matcher = matcher;
            this.treatment = treatment;
            this.hand = hand;
            this.startTick = startTick;
            this.duration = duration;
            this.source = source;
            this.reserved = reserved;
            this.nextHealingTick = startTick + startupTicks();
        }

        private void prepareIncrementalHealth(ServerPlayer player) {
            if (!incrementalHealthItem()) return;
            remainingHealing = BasicHealthMedicalRules.totalHealing(player.getMaxHealth());
            incrementalHealPerPulse = BasicHealthMedicalRules.healingPerSecond(
                player.getMaxHealth());
            nextHealingTick = startTick + BasicHealthMedicalRules.PULSE_INTERVAL_TICKS;
        }

        private ItemStack matcher() { return matcher; }
        private MedicalTreatment treatment() { return treatment; }
        private InteractionHand hand() { return hand; }
        private long finishTick() { return startTick + duration; }
        private int duration() { return duration; }
        private int remainingTicks(long now) {
            return (int) Math.max(0L, finishTick() - now);
        }
        private InventoryTransferSource.Handle source() { return source; }
        private ItemStack reserved() { return reserved; }
        private boolean durabilityMedicalKit() {
            return MedicalUseRules.isDurabilityMedicalKit(matcher);
        }
        private boolean incrementalHealthItem() {
            return MedicalUseRules.isHealthItem(matcher) && !durabilityMedicalKit();
        }
        private int startupTicks() {
            ConsumableProfile profile = ConsumableProfile.get(matcher);
            return profile == null ? 0 : profile.startupTicks();
        }
        private float healPerPulse() {
            if (incrementalHealthItem()) {
                return incrementalHealPerPulse;
            }
            return MedicalUseRules.isOutdoorKit(matcher)
                ? OutdoorMedicalKitRules.HEAL_PER_SECOND
                : BattlefieldMedicalKitRules.HEAL_PER_SECOND;
        }

        private float healPerTick() {
            return ConsumableProfile.get(matcher).healPerSecond() / 20.0F;
        }
        private long startupEndTick() { return startTick + startupTicks(); }
        private void recalculateDurabilityMedicalDuration(ServerPlayer player, ItemStack kit,
                                                           long now) {
            int elapsed = (int) Math.max(0L, now - startTick);
            float missing = Math.max(0.0F, player.getMaxHealth() - player.getHealth());
            int healingTicks = MedicalHealthRules.healingTicks(missing,
                PlayerInjuryManager.baselineMaximumHealth(player), remainingDurability(kit),
                healPerTick());
            duration = Math.max(elapsed, elapsed + healingTicks);
            // The activation phase has ended; begin the continuous treatment on
            // the next server tick instead of waiting for another whole second.
            nextHealingTick = now + 1L;
        }
        private ItemStack activeStack(ServerPlayer player) {
            return hand == null ? reserved : player.getItemInHand(hand);
        }
    }
}
