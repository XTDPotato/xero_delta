package com.xtdpotato.xero_delta;

import com.xtdpotato.xero_delta.data.ModDataStorage;
import com.xtdpotato.xero_delta.data.DownedManager;
import com.xtdpotato.xero_delta.data.ContainerGridRules;
import com.xtdpotato.xero_delta.data.PlayerWeightCalculator;
import com.xtdpotato.xero_delta.data.PlayerInjuryManager;
import com.xtdpotato.xero_delta.data.MedicalUseManager;
import com.xtdpotato.xero_delta.data.BallisticArmorManager;
import com.xtdpotato.xero_delta.data.SafetyBoxAccessData;
import com.xtdpotato.xero_delta.data.KnifeAccessData;
import com.xtdpotato.xero_delta.data.PlayerFeatureAccessData;
import com.xtdpotato.xero_delta.data.PlayerLayoutSlotRules;
import com.xtdpotato.xero_delta.data.PlayerLayoutRulesData;
import com.xtdpotato.xero_delta.data.HealthSystemRulesData;
import com.xtdpotato.xero_delta.data.TeamStatusSync;
import com.xtdpotato.xero_delta.data.PlayerStaminaManager;
import com.xtdpotato.xero_delta.data.FirstJoinGuidePolicy;
import com.xtdpotato.xero_delta.data.ServerItemRules;
import com.xtdpotato.xero_delta.data.LootSearchManager;
import com.xtdpotato.xero_delta.data.CorpseRulesData;
import com.xtdpotato.xero_delta.entity.CorpseEntity;
import com.xtdpotato.xero_delta.grid.ContainerGridNormalizer;
import com.xtdpotato.xero_delta.grid.ContainerGridHelper;
import com.xtdpotato.xero_delta.grid.GridBackingStore;
import com.xtdpotato.xero_delta.item.SafetyBoxItem;
import com.xtdpotato.xero_delta.item.DeltaPackItem;
import com.xtdpotato.xero_delta.menu.CorpseMenu;
import com.xtdpotato.xero_delta.network.ModNetwork;
import com.xtdpotato.xero_delta.network.InspectCancelPacket;
import com.xtdpotato.xero_delta.network.SyncDataPacket;
import com.xtdpotato.xero_delta.network.MailSyncPacket;
import com.xtdpotato.xero_delta.network.PlayerStatusPacket;
import com.xtdpotato.xero_delta.network.DamageDirectionPacket;
import com.xtdpotato.xero_delta.network.CorpseRulesSyncPacket;
import com.xtdpotato.xero_delta.mail.MailData;
import com.xtdpotato.xero_delta.trading.TradingMarketData;
import com.xtdpotato.xero_delta.tag.ModTags;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.EntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import net.neoforged.neoforge.event.ItemAttributeModifierEvent;
import net.neoforged.neoforge.event.entity.living.LivingEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.minecraft.world.entity.Mob;
import top.theillusivec4.curios.api.CuriosApi;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@EventBusSubscriber(modid = XeroDelta.MOD_ID)
public class ServerEvents {
    private static final String MOB_CORPSE_CREATED = "xero_delta_mob_corpse_created";
    private static final ResourceLocation ENCUMBRANCE_MODIFIER =
        ResourceLocation.fromNamespaceAndPath(XeroDelta.MOD_ID, "encumbrance");
    private static final Map<UUID, StatusSnapshot> LAST_STATUS = new HashMap<>();

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            DownedManager.preserveKnifeOnDeath(player);
        }
    }

    /**
     * Converts the final, mod-adjusted mob drop list into one loot corpse.
     * Running at LOWEST and clearing the original ItemEntities makes the corpse
     * the only owner of the stacks, preventing duplication with other drop hooks.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onMobDrops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof Mob mob)
            || !(mob.level() instanceof net.minecraft.server.level.ServerLevel level)
            || mob.getPersistentData().getBoolean(MOB_CORPSE_CREATED)) return;
        CorpseRulesData corpseRules = CorpseRulesData.get(level.getServer());
        if (!corpseRules.shouldGenerateFor(mob)) return;
        // The marker guards against mods that re-dispatch or replay drop processing.
        // Players are intentionally excluded so Corail's Tombstone remains the sole
        // owner of its player-death grave transaction.
        mob.getPersistentData().putBoolean(MOB_CORPSE_CREATED, true);
        var corpse = ModEntities.CORPSE.get().create(level);
        if (corpse == null) return;
        corpse.setMobOwner(mob);
        removeCapturedEquipmentDrops(corpse.captureMobEquipment(mob), event.getDrops());
        corpse.moveTo(mob.getX(), mob.getY(), mob.getZ(), mob.getYRot(), 0.0F);
        equipMobCorpseCarriers(corpse, mob, event.getDrops(), corpseRules);
        for (var dropped : java.util.List.copyOf(event.getDrops())) {
            ItemStack stack = dropped.getItem();
            if (!stack.isEmpty()) {
                ItemStack remainder = storeMobDrop(corpse, mob, stack);
                if (!remainder.isEmpty()) {
                    level.addFreshEntity(new net.minecraft.world.entity.item.ItemEntity(
                        level, mob.getX(), mob.getY(), mob.getZ(), remainder));
                }
            }
            dropped.discard();
        }
        event.getDrops().clear();
        level.addFreshEntity(corpse);
    }

    private static void removeCapturedEquipmentDrops(
        java.util.List<ItemStack> captured,
        java.util.Collection<net.minecraft.world.entity.item.ItemEntity> drops) {
        for (ItemStack equipped : captured) {
            if (equipped.isEmpty()) continue;
            for (var dropped : drops) {
                ItemStack stack = dropped.getItem();
                if (!ItemStack.isSameItemSameComponents(stack, equipped)) continue;
                stack.shrink(Math.min(stack.getCount(), equipped.getCount()));
                dropped.setItem(stack);
                if (stack.isEmpty()) dropped.discard();
                break;
            }
        }
    }

    private static void equipMobCorpseCarriers(
        com.xtdpotato.xero_delta.entity.CorpseEntity corpse,
        Mob mob,
        java.util.Collection<net.minecraft.world.entity.item.ItemEntity> drops,
        CorpseRulesData rules) {
        ItemStack originalChestRig = corpse.chestRigItem();
        ItemStack originalBackpack = corpse.backpackItem();
        if (!originalChestRig.isEmpty()) {
            if (corpse.inventory().getItem(CorpseMenu.CHEST_RIG_SLOT).isEmpty()) {
                corpse.tryEquipCarrier(CorpseMenu.CHEST_RIG_SLOT, originalChestRig);
            }
            removeMatchingCarrierDrop(drops, originalChestRig);
        }
        if (!originalBackpack.isEmpty()) {
            if (corpse.inventory().getItem(CorpseMenu.BACKPACK_SLOT).isEmpty()) {
                corpse.tryEquipCarrier(CorpseMenu.BACKPACK_SLOT, originalBackpack);
            }
            removeMatchingCarrierDrop(drops, originalBackpack);
        }

        // Some Curios/death integrations materialize equipped carriers only in
        // LivingDropsEvent. Recover those stacks before assigning random gear.
        if (corpse.inventory().getItem(CorpseMenu.CHEST_RIG_SLOT).isEmpty()) {
            equipCarrierFromDrops(corpse, drops, CorpseMenu.CHEST_RIG_SLOT, "chest_rig");
        }
        if (corpse.inventory().getItem(CorpseMenu.BACKPACK_SLOT).isEmpty()) {
            equipCarrierFromDrops(corpse, drops, CorpseMenu.BACKPACK_SLOT, "backpack");
        }

        // An equipped carrier is authoritative; configured generated carriers
        // are used only when the mob did not already wear either one.
        if (rules.generateChestRig()
            && corpse.inventory().getItem(CorpseMenu.CHEST_RIG_SLOT).isEmpty()) {
            ItemStack stack = rules.generatedCarrier(mob, true);
            if (!stack.isEmpty()) corpse.tryEquipCarrier(CorpseMenu.CHEST_RIG_SLOT, stack);
        }
        if (rules.generateBackpack()
            && corpse.inventory().getItem(CorpseMenu.BACKPACK_SLOT).isEmpty()) {
            ItemStack stack = rules.generatedCarrier(mob, false);
            if (!stack.isEmpty()) corpse.tryEquipCarrier(CorpseMenu.BACKPACK_SLOT, stack);
        }
    }

    private static void equipCarrierFromDrops(
        com.xtdpotato.xero_delta.entity.CorpseEntity corpse,
        java.util.Collection<net.minecraft.world.entity.item.ItemEntity> drops,
        int corpseSlot, String identifier) {
        for (var dropped : drops) {
            ItemStack stack = dropped.getItem();
            if (!corpse.tryEquipCarrier(corpseSlot, stack)) continue;
            stack.shrink(1);
            dropped.setItem(stack);
            if (stack.isEmpty()) dropped.discard();
            return;
        }
    }

    private static void removeMatchingCarrierDrop(
        java.util.Collection<net.minecraft.world.entity.item.ItemEntity> drops,
        ItemStack carrier) {
        if (carrier.isEmpty()) return;
        for (var dropped : drops) {
            ItemStack stack = dropped.getItem();
            if (!ItemStack.isSameItemSameComponents(stack, carrier)) continue;
            // Curios integrations can materialize the same equipped stack more
            // than once. Every exact duplicate belongs to the corpse carrier slot.
            dropped.setItem(ItemStack.EMPTY);
            dropped.discard();
        }
    }

    private static ItemStack storeMobDrop(
        com.xtdpotato.xero_delta.entity.CorpseEntity corpse, Mob mob, ItemStack original) {
        ItemStack remainder = original.copy();
        if (isMobWeaponDrop(mob, remainder)) {
            if (PlayerLayoutSlotRules.isTaczGun(remainder)
                && PlayerLayoutSlotRules.isHandgun(remainder)) {
                return corpse.addStackAt(CorpseMenu.SIDEARM_SLOT, remainder);
            }
            ItemStack afterPrimary = corpse.addStackAt(CorpseMenu.PRIMARY_ONE_SLOT, remainder);
            return afterPrimary.isEmpty() ? ItemStack.EMPTY
                : corpse.addStackAt(CorpseMenu.PRIMARY_TWO_SLOT, afterPrimary);
        }
        remainder = insertIntoCorpsePack(corpse, CorpseMenu.CHEST_RIG_SLOT, remainder);
        remainder = insertIntoCorpsePack(corpse, CorpseMenu.BACKPACK_SLOT, remainder);
        return remainder.isEmpty() ? ItemStack.EMPTY : corpse.addStackToStorage(remainder);
    }

    private static boolean isMobWeaponDrop(Mob mob, ItemStack stack) {
        return PlayerLayoutSlotRules.isTaczGun(stack)
            || ItemStack.isSameItemSameComponents(stack, mob.getMainHandItem())
            || ItemStack.isSameItemSameComponents(stack, mob.getOffhandItem());
    }

    private static ItemStack insertIntoCorpsePack(
        com.xtdpotato.xero_delta.entity.CorpseEntity corpse, int carrierSlot,
        ItemStack source) {
        if (source.isEmpty()) return ItemStack.EMPTY;
        ItemStack carrier = corpse.inventory().getItem(carrierSlot);
        if (!(carrier.getItem() instanceof DeltaPackItem pack)) return source;
        GridBackingStore store = new GridBackingStore(carrier,
            pack.gridWidth(), pack.gridHeight(), 0,
            stack -> GridBackingStore.isBlockedInEquippedStorage(
                pack.slotIdentifier(), stack));
        ItemStack remainder = source.copy();
        for (int index = 0; index < store.getSize() && !remainder.isEmpty(); index++) {
            int x = index % store.getWidth();
            int y = index / store.getWidth();
            if (store.canStackAt(x, y, remainder)) store.stackInto(x, y, remainder);
        }
        while (!remainder.isEmpty()) {
            var placement = store.findFreePlacement(remainder);
            if (placement.status() != GridBackingStore.PlacementStatus.CAN_PLACE) break;
            int moved = Math.min(remainder.getCount(), remainder.getMaxStackSize());
            ItemStack placed = remainder.copyWithCount(moved);
            if (!store.place(placement.x(), placement.y(), placed, placement.rotated())) break;
            remainder.shrink(moved);
        }
        return remainder;
    }

    /** Stores a valuable dropped stack in the equipped safety box when the normal inventory is full. */
    @SubscribeEvent
    public static void onValuableItemPickup(ItemEntityPickupEvent.Pre event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;
        ItemStack dropped = event.getItemEntity().getItem();
        if (PlayerLayoutSlotRules.enabled(player)) {
            // BetterLooting or vanilla remains the pickup initiator. The optional
            // BetterLooting mixin routes only its explicit batch pickup into Delta.
            return;
        }
        if (!Config.INSTANCE.quickMoveEnabled.get()) return;
        if (dropped.isEmpty() || dropped.is(ModTags.SAFETY_BOX) || Config.INSTANCE.isBlacklisted(dropped)) return;
        long value = ModDataStorage.get(player.serverLevel()).getPriceFor(dropped);
        if (value < Config.INSTANCE.quickMoveValueThreshold.get()) return;
        if (ContainerGridHelper.canTransferIntoPlayerInventory(player.containerMenu, dropped, true,
            ModDataStorage::getCachedSizeFor)) return;

        ItemStack boxStack = findSafetyBox(player);
        if (boxStack.isEmpty() || !(boxStack.getItem() instanceof SafetyBoxItem box)) return;
        GridBackingStore store = new GridBackingStore(boxStack, box.getGridWidth(), box.getGridHeight());
        boolean changed = false;
        for (int index = 0; index < store.getSize() && !dropped.isEmpty(); index++) {
            int x = index % store.getWidth();
            int y = index / store.getWidth();
            if (store.canStackAt(x, y, dropped)) changed |= store.stackInto(x, y, dropped);
        }
        if (!dropped.isEmpty()) {
            var placement = store.findFreePlacement(dropped);
            if (placement.status() == GridBackingStore.PlacementStatus.CAN_PLACE) {
                changed = store.place(placement.x(), placement.y(), dropped, placement.rotated()) || changed;
                if (changed) dropped.setCount(0);
            }
        }
        if (changed) {
            event.setCanPickup(TriState.FALSE);
            event.getItemEntity().setPickUpDelay(2);
        }
    }

    private static ItemStack findSafetyBox(ServerPlayer player) {
        try {
            var curios = CuriosApi.getCuriosInventory(player);
            if (curios.isPresent()) {
                var found = curios.get().findFirstCurio(stack -> stack.is(ModTags.SAFETY_BOX)
                    && SafetyBoxAccessData.get(player.server).isUnlocked(player.getUUID(),
                        stack.getItemHolder().getKey().location().toString(), System.currentTimeMillis()));
                if (found.isPresent()) return found.get().stack();
            }
        } catch (RuntimeException ignored) {
        }
        return ItemStack.EMPTY;
    }
    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            PlayerLayoutSlotRules.ejectDisabledMainInventory(sp);
            PlayerStaminaManager.initialize(sp);
            syncRules(sp);
            // The integrated server can post the login event before the client has
            // completed its play-payload setup. Send the same snapshot next tick so
            // the first world load never depends on a later rule edit to resync.
            sp.server.execute(() -> {
                if (sp.isAlive()) {
                    syncRules(sp);
                    sendFirstJoinGuide(sp);
                }
            });
        }
    }

    private static void sendFirstJoinGuide(ServerPlayer player) {
        ModDataStorage data = ModDataStorage.get(player.serverLevel());
        boolean hasPriceRules = !data.getManualPrices().isEmpty()
            || !data.getAutoPriceKeys().isEmpty();
        boolean show = FirstJoinGuidePolicy.shouldShow(
            player.createCommandSourceStack().hasPermission(2),
            ServerItemRules.hasConfiguredRules(),
            hasPriceRules,
            PlayerLayoutRulesData.get(player.server).enabled());
        if (!show) return;

        player.sendSystemMessage(Component.translatable("guide.xero_delta.loaded")
            .append(guideAction("guide.xero_delta.calculate_action", "/xero_all qsq",
                "guide.xero_delta.calculate_hover"))
            .append(Component.translatable("guide.xero_delta.calculate_suffix"))
            .append(guideAction("guide.xero_delta.layout_action", "/xero layout true",
                "guide.xero_delta.layout_hover"))
            .append(Component.translatable("guide.xero_delta.layout_suffix")));
    }

    private static Component guideAction(String textKey, String command, String hoverKey) {
        return Component.translatable(textKey).withStyle(style -> style
            .withColor(ChatFormatting.AQUA)
            .withBold(true)
            .withUnderlined(true)
            .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                Component.translatable(hoverKey))));
    }

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        if (!event.isWasDeath()
            || !(event.getOriginal() instanceof ServerPlayer original)
            || !(event.getEntity() instanceof ServerPlayer replacement)) return;
        boolean retainEffects = HealthSystemRulesData.get(replacement.server)
            .retainEffectsAfterDeath();
        DownedManager.restoreKnifeAfterDeath(original, replacement);
        PlayerInjuryManager.restoreAfterDeath(original, replacement, retainEffects);
        DownedManager.resetAfterDeath(replacement);
        PlayerStaminaManager.copyMaximumAfterDeath(original, replacement);
        replacement.server.execute(() -> syncRules(replacement));
    }

    private static void syncRules(ServerPlayer player) {
        TradingMarketData.get(player.server).claimUnresolvedAccount(
            player.getUUID(), player.getGameProfile().getName());
        MailData.get(player.server).deliverBroadcasts(
            player.getUUID(), player.getGameProfile().getName());
        var data = ModDataStorage.get(player.serverLevel());
        ModNetwork.sendSyncToPlayer(player, SyncDataPacket.from(data, player.server));
        PacketDistributor.sendToPlayer(player, CorpseRulesSyncPacket.from(player));
        PacketDistributor.sendToPlayer(player, MailSyncPacket.snapshot(player, false, null, "", true, 0L));
        SafetyBoxAccessData.get(player.server).sync(player);
        KnifeAccessData.get(player.server).sync(player);
        syncPlayerStatus(player, true);
        DownedManager.sync(player);
        TeamStatusSync.send(player);
        PlayerStaminaManager.sync(player);
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        LootSearchManager.tick(player);
        DownedManager.tick(player);
        PlayerStaminaManager.tick(player);
        PlayerLayoutSlotRules.ejectDisabledMainInventory(player);
        PlayerInjuryManager.tickHotbarSwitch(player);
        PlayerInjuryManager.tickConsequences(player);
        MedicalUseManager.tick(player);
        if (player.tickCount % 20 == 0
            && SafetyBoxAccessData.get(player.server).removeExpired(
                player.getUUID(), System.currentTimeMillis())) {
            ModNetwork.sendTranslatedNotice(player, "safety_box.xero_delta.expired");
            SafetyBoxAccessData.get(player.server).sync(player);
        }
        if (PlayerInjuryManager.expireJumpPenalty(player)) {
            syncPlayerStatus(player, true);
            return;
        }
        if (player.tickCount % 5 == 0) TeamStatusSync.send(player);
        if (player.tickCount % 10 != 0) return;
        syncPlayerStatus(player, false);
    }

    @SubscribeEvent
    public static void onIncomingPlayerDamage(LivingIncomingDamageEvent event) {
        BallisticArmorManager.onIncomingDamage(event);
    }

    @SubscribeEvent
    public static void onPlayerDamageCalculated(LivingDamageEvent.Pre event) {
        DownedManager.onDamage(event);
    }

    @SubscribeEvent
    public static void onPlayerDamaged(LivingDamageEvent.Post event) {
        BallisticArmorManager.onDamageApplied(event);
        PlayerInjuryManager.onDamage(event);
        if (!(event.getEntity() instanceof ServerPlayer player)
            || event.getNewDamage() <= 0.0F) return;
        var sourcePosition = event.getSource().getSourcePosition();
        if (sourcePosition == null && event.getSource().getEntity() != null) {
            sourcePosition = event.getSource().getEntity().position();
        }
        if (sourcePosition == null) return;
        var direction = sourcePosition.subtract(player.position());
        if (direction.x * direction.x + direction.z * direction.z < 1.0E-4D) return;
        PacketDistributor.sendToPlayer(player, new DamageDirectionPacket(
            direction.x, direction.z, event.getNewDamage()));
    }

    @SubscribeEvent
    public static void onItemAttributeModifiers(ItemAttributeModifierEvent event) {
        if (BallisticArmorManager.isBrokenEquipment(event.getItemStack())) {
            event.clearModifiers();
        }
    }

    @SubscribeEvent
    public static void onPlayerFall(LivingFallEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (PlayerInjuryManager.onFall(player, event.getDistance())) syncPlayerStatus(player, true);
    }

    @SubscribeEvent
    public static void onPlayerJump(LivingEvent.LivingJumpEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (MedicalUseManager.cancelForJump(player)) {
            var movement = player.getDeltaMovement();
            player.setDeltaMovement(movement.x, Math.min(0.0D, movement.y), movement.z);
            player.hasImpulse = true;
            return;
        }
        PlayerStaminaManager.consumeJump(player);
        if (PlayerInjuryManager.beginBrokenLegJumpPenalty(player)) syncPlayerStatus(player, true);
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        LootSearchManager.logout(player);
        DownedManager.onLogout(player);
        MedicalUseManager.clear(player);
        LAST_STATUS.remove(player.getUUID());
        PlayerInjuryManager.clear(player);
        var speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) speed.removeModifier(ENCUMBRANCE_MODIFIER);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onDownedPlayerSize(EntityEvent.Size event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
            || !DownedManager.isRed(player)) return;
        event.setNewSize(EntityDimensions.scalable(event.getNewSize().width(), 1.0F)
            .withEyeHeight(0.4F));
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onDownedEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (MedicalUseManager.cancelForProhibitedAction(player)) {
            event.setCanceled(true);
            return;
        }
        if (isCarriedInteractionTarget(event.getTarget())) {
            event.setCanceled(true);
            return;
        }
        LootSearchManager.noteEntityInteraction(player, event.getTarget());
        if (event.getTarget() instanceof ServerPlayer target && DownedManager.isRed(target)) {
            event.setCanceled(true);
            return;
        }
        if (DownedManager.interactionLocked(player)) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onDownedOrRescuingKnockback(LivingKnockBackEvent event) {
        if (event.getEntity() instanceof ServerPlayer player
            && (DownedManager.isRed(player)
                || DownedManager.isBeingRescued(player)
                || DownedManager.isRescuing(player))) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onDownedEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            if (MedicalUseManager.cancelForProhibitedAction(player)) {
                event.setCanceled(true);
                return;
            }
            if (isCarriedInteractionTarget(event.getTarget())) {
                event.setCanceled(true);
                return;
            }
            LootSearchManager.noteEntityInteraction(player, event.getTarget());
            if (DownedManager.interactionLocked(player)) event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onDownedRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            if (MedicalUseManager.cancelForProhibitedAction(player)) {
                event.setCanceled(true);
                return;
            }
            LootSearchManager.noteBlockInteraction(player, event.getPos());
            if (DownedManager.interactionLocked(player)) event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onDownedRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (event.getEntity() instanceof ServerPlayer player
            && (MedicalUseManager.cancelForProhibitedAction(player)
                || DownedManager.interactionLocked(player))) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onDownedLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getEntity() instanceof ServerPlayer player
            && (MedicalUseManager.cancelForProhibitedAction(player)
                || DownedManager.interactionLocked(player))) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onDownedAttack(AttackEntityEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (isCarriedInteractionTarget(event.getTarget())
            || MedicalUseManager.cancelForProhibitedAction(player)
            || DownedManager.interactionLocked(player)) event.setCanceled(true);
    }

    private static boolean isCarriedInteractionTarget(Entity target) {
        if (target instanceof ServerPlayer player) return DownedManager.isCarried(player);
        return target instanceof CorpseEntity corpse && corpse.carryingPlayer() != null;
    }

    @SubscribeEvent
    public static void onDownedContainerOpen(PlayerContainerEvent.Open event) {
        if (event.getEntity() instanceof ServerPlayer player
            && DownedManager.interactionLocked(player)) player.closeContainer();
    }
    public static void syncPlayerStatus(ServerPlayer player, boolean force) {
        PlayerWeightCalculator.Snapshot weight = PlayerWeightCalculator.calculate(player);
        PlayerInjuryManager.Snapshot injury = PlayerInjuryManager.snapshot(player);
        double weightProgress = Math.max(0.0D, Math.min(1.0D,
            (weight.kilograms() - 50.0D) / 38.0D));
        double speedPenalty = Math.min(0.80D,
            weightProgress * 0.30D + weight.carrierSpeedPenalty()
                + PlayerInjuryManager.movementPenalty(injury,
                    PlayerInjuryManager.jumpSlowActive(player),
                    player.hasEffect(ModEffects.PAIN_RELIEF)));
        // Movement penalties are applied to client walking input so they do not zoom the FOV.
        applyEncumbrance(player, 0.0D);

        long balance = TradingMarketData.get(player.server).balance(player.getUUID());
        double healthPenalty = PlayerInjuryManager.healthPenalty(player, injury);
        PlayerFeatureAccessData features = PlayerFeatureAccessData.get(player.server);
        boolean layoutEnabled = PlayerLayoutSlotRules.enabled(player);
        boolean allowChangeBc = features.allowChangeBc(player.getUUID());
        boolean layoutClick = features.layoutClick(player.getUUID());
        StatusSnapshot current = new StatusSnapshot(balance, weight.kilograms(), speedPenalty,
            healthPenalty, injury, layoutEnabled, allowChangeBc, layoutClick);
        StatusSnapshot previous = LAST_STATUS.put(player.getUUID(), current);
        if (!force && current.nearlyEquals(previous)) return;
        PacketDistributor.sendToPlayer(player, new PlayerStatusPacket(
            balance, weight.kilograms(), speedPenalty, healthPenalty,
            layoutEnabled, allowChangeBc, layoutClick,
            injury.head(), injury.chest(), injury.abdomen(), injury.leftArm(), injury.rightArm(),
            injury.leftLeg(), injury.rightLeg(), injury.wholeBody()));
    }

    private static void applyEncumbrance(ServerPlayer player, double penalty) {
        var speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) return;
        speed.removeModifier(ENCUMBRANCE_MODIFIER);
        if (penalty <= 0.0001D) return;
        speed.addTransientModifier(new AttributeModifier(ENCUMBRANCE_MODIFIER, -penalty,
            AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }

    private record StatusSnapshot(long balance, double weightKg, double speedPenalty,
                                  double healthPenalty,
                                  PlayerInjuryManager.Snapshot injury,
                                  boolean layoutEnabled, boolean allowChangeBc, boolean layoutClick) {
        private boolean nearlyEquals(StatusSnapshot other) {
            return other != null && balance == other.balance
                && Math.abs(weightKg - other.weightKg) < 0.01D
                && Math.abs(speedPenalty - other.speedPenalty) < 0.0001D
                && Math.abs(healthPenalty - other.healthPenalty) < 0.0001D
                && injury.equals(other.injury)
                && layoutEnabled == other.layoutEnabled
                && allowChangeBc == other.allowChangeBc
                && layoutClick == other.layoutClick;
        }
    }

    @SubscribeEvent
    public static void onContainerOpen(PlayerContainerEvent.Open event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        var menu = event.getContainer();
        player.server.execute(() -> {
            if (player.containerMenu != menu) return;
            if (Config.INSTANCE.itemGridEnabled.get()
                && ContainerGridRules.isScreenEnabled(menu.getClass().getName())) {
                ContainerGridNormalizer.normalize(player, menu);
            }
            LootSearchManager.open(player, menu);
        });
    }

    @SubscribeEvent
    public static void onContainerClose(PlayerContainerEvent.Close event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            LootSearchManager.close(player, event.getContainer());
        }
    }

    /**
     * Better Looting places a picked-up item into the selected Slot directly
     * and then posts NeoForge's normal pickup event.  That bypasses vanilla
     * clicked/quick-move packets, so a multi-cell item can temporarily land
     * on an edge cell with no complete footprint.  Normalize on the next
     * server tick, after the third-party packet has finished all of its slot
     * and carried-stack writes.
     */
    @SubscribeEvent
    public static void onItemEntityPickup(ItemEntityPickupEvent.Post event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;
        if (!Config.INSTANCE.itemGridEnabled.get()) return;
        var menu = player.containerMenu;
        if (!ContainerGridRules.isScreenEnabled(menu.getClass().getName())) return;
        player.server.execute(() -> {
            if (player.isAlive() && player.containerMenu == menu) {
                ContainerGridNormalizer.normalize(player, menu);
            }
        });
    }
}
