package com.xtdpotato.xero_delta.data;

import com.xtdpotato.xero_delta.ModEntities;
import com.xtdpotato.xero_delta.Config;
import com.xtdpotato.xero_delta.entity.CorpseEntity;
import com.xtdpotato.xero_delta.menu.CorpseMenu;
import com.xtdpotato.xero_delta.network.DownedStatePacket;
import com.xtdpotato.xero_delta.network.CombatFeedPacket;
import com.xtdpotato.xero_delta.network.RescueRequestPulsePacket;
import com.xtdpotato.xero_delta.network.RescueHoldPacket;
import com.xtdpotato.xero_delta.tag.ModTags;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import top.theillusivec4.curios.api.CuriosApi;

import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.UUID;

/** Server-authoritative red-down/yellow-down, carrying and rescue runtime. */
public final class DownedManager {
    private static final String ROOT = "xero_delta_downed";
    private static final String STAGE = "stage";
    private static final String REMAINING = "remaining";
    private static final String DURATION = "duration";
    private static final String YELLOW_USED = "yellow_used";
    private static final String YELLOW_RESCUE_HEALTH_PENALTY = "yellow_rescue_health_penalty";
    private static final String CORPSE = "corpse";
    private static final String NEXT_RESCUE_REQUEST = "next_rescue_request";
    private static final String PROTECTED_KNIFE = "xero_delta_protected_knife";
    private static final ResourceLocation RED_CRAWL_SPEED = ResourceLocation.fromNamespaceAndPath(
        "xero_delta", "red_down_crawl_speed");
    private static final ResourceLocation CARRY_SPEED = ResourceLocation.fromNamespaceAndPath(
        "xero_delta", "carry_slow_speed");

    private static final Map<UUID, CarryWindup> CARRY_WINDUPS = new HashMap<>();
    private static final Map<UUID, CarryWindup> CARRY_DROPS = new HashMap<>();
    private static final Map<UUID, UUID> CARRYING = new HashMap<>();
    private static final Map<UUID, UUID> CARRIED_BY = new HashMap<>();
    private static final Map<UUID, RescueSession> RESCUES = new HashMap<>();
    private static final Map<UUID, Integer> ABANDON_HOLDS = new HashMap<>();

    private DownedManager() {}

    public static void onDamage(LivingDamageEvent.Pre event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
            || player.isCreative() || player.isSpectator()) return;
        float damage = event.getNewDamage();
        if (damage <= 0.0F) return;
        int stage = stage(player);
        if (stage == 1) {
            if (event.getSource().is(DamageTypeTags.IS_EXPLOSION)) {
                Vec3 explosionOrigin = event.getSource().getSourcePosition();
                if (explosionOrigin == null && event.getSource().getEntity() != null) {
                    explosionOrigin = event.getSource().getEntity().position();
                }
                if (explosionOrigin != null
                    && DownedRules.isCloseExplosion(
                        explosionOrigin.distanceToSqr(player.position()))) {
                    // A point-blank blast consumes the complete red-down window.
                    // The normal tick transition then moves the player into yellow
                    // downed state without bypassing corpse/rescue bookkeeping.
                    setRemaining(player, 0);
                    player.setHealth(1.0F);
                    event.setNewDamage(0.0F);
                    sync(player);
                    return;
                }
                damage = DownedRules.redDownExplosionDamage(damage);
            }
            setRemaining(player, DownedRules.subtractDamageTime(
                remaining(player), duration(player), damage));
            player.setHealth(1.0F);
            event.setNewDamage(0.0F);
            sync(player);
            return;
        }
        if (stage == 2) {
            event.setNewDamage(0.0F);
            return;
        }
        if (damage < player.getHealth()) return;
        if (yellowUsed(player)) return;
        DamageSource source = event.getSource();
        boolean headshot = PlayerInjuryManager.isHeadshot(player, source);
        enterRed(player, isBoltSniper(source.getEntity()));
        broadcastDownedFeed(player, source, headshot);
        event.setNewDamage(0.0F);
    }

    public static void tick(ServerPlayer player) {
        tickCarry(player);
        tickRescue(player);
        int stage = stage(player);
        if (stage == 1) {
            if (tickAbandonHold(player)) return;
            player.setHealth(1.0F);
            player.setSprinting(false);
            player.setForcedPose(Pose.SWIMMING);
            applyRedCrawlSpeed(player, true);
            applyRedWaterBuoyancy(player);
            if (!isCarried(player) && !isBeingRescued(player)) setRemaining(player, remaining(player) - 1);
            if (remaining(player) <= 0) enterYellow(player);
        } else if (stage == 2) {
            setRemaining(player, remaining(player) - 1);
            if (remaining(player) <= 0) finalDeath(player);
        }
        if (player.tickCount % 5 == 0) sync(player);
    }

    public static boolean isRed(ServerPlayer player) { return stage(player) == 1; }
    public static boolean isYellow(ServerPlayer player) { return stage(player) == 2; }
    public static boolean isDowned(ServerPlayer player) { return stage(player) != 0; }
    public static boolean hasYellowRescueHealthPenalty(ServerPlayer player) {
        return data(player).getBoolean(YELLOW_RESCUE_HEALTH_PENALTY);
    }
    public static boolean isCarried(ServerPlayer player) { return CARRIED_BY.containsKey(player.getUUID()); }
    public static boolean isCarrying(ServerPlayer player) {
        return CARRYING.containsKey(player.getUUID()) || CARRY_WINDUPS.containsKey(player.getUUID());
    }
    public static boolean isBeingRescued(ServerPlayer player) {
        return incomingRescue(player.getUUID()) != null;
    }
    public static boolean isRescuing(ServerPlayer player) {
        return RESCUES.containsKey(player.getUUID());
    }
    public static boolean interactionLocked(ServerPlayer player) {
        return isDowned(player) || isCarrying(player) || RESCUES.containsKey(player.getUUID());
    }

    public static boolean requestRescue(ServerPlayer player) {
        int currentStage = stage(player);
        if (currentStage != 1 && currentStage != 2) return false;
        long now = player.serverLevel().getGameTime();
        CompoundTag state = data(player);
        if (state.contains(NEXT_RESCUE_REQUEST)
            && !DownedRules.canRequestRescue(now, state.getLong(NEXT_RESCUE_REQUEST))) {
            return false;
        }
        state.putLong(NEXT_RESCUE_REQUEST, DownedRules.nextRescueRequestTime(now));
        save(player, state);

        var message = net.minecraft.network.chat.Component.translatable(
            "downed.xero_delta.rescue_request_chat", player.getDisplayName());
        LinkedHashSet<ServerPlayer> recipients = new LinkedHashSet<>();
        recipients.add(player);
        recipients.addAll(FtbTeamIntegration.onlineTeammates(player));
        RescueRequestPulsePacket pulse = new RescueRequestPulsePacket(
            player.getUUID(), (byte) currentStage, Config.INSTANCE.rescueRequestSound.get());
        for (ServerPlayer recipient : recipients) {
            recipient.sendSystemMessage(message);
            PacketDistributor.sendToPlayer(recipient, pulse);
        }
        return true;
    }

    public static void startAbandonHold(ServerPlayer player) {
        if (isRed(player)) ABANDON_HOLDS.putIfAbsent(player.getUUID(), 0);
    }

    public static void cancelAbandonHold(ServerPlayer player) {
        ABANDON_HOLDS.remove(player.getUUID());
    }

    public static void abandonRescue(ServerPlayer player) {
        if (isYellow(player)) abandonYellow(player);
    }

    /** Immediately turns a failed evacuation into a lootable corpse and final death. */
    public static boolean failEvacuation(ServerPlayer player, long raidEarnings) {
        if (player == null || player.isRemoved() || player.isDeadOrDying()) return false;
        dropCarrierOf(player);
        dropCarried(player);
        cancelRescuesFor(player.getUUID());
        RESCUES.remove(player.getUUID());
        CorpseEntity corpse = ModEntities.CORPSE.get().create(player.serverLevel());
        if (corpse == null) return false;
        corpse.moveTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), 0.0F);
        corpse.setOwner(player);
        corpse.setRaidEarnings(raidEarnings);
        moveInventoryToCorpse(player, corpse);
        corpse.convertToLootBox();
        player.serverLevel().addFreshEntity(corpse);
        player.setCamera(player);
        player.setGameMode(GameType.SURVIVAL);
        clearStage(player, true);
        sync(player);
        player.kill();
        return true;
    }

    public static TeamSnapshot teamSnapshot(ServerPlayer player) {
        RescueSession ownRescue = RESCUES.get(player.getUUID());
        Map.Entry<UUID, RescueSession> incoming = incomingRescue(player.getUUID());
        RescueSession progress = incoming != null ? incoming.getValue() : ownRescue;
        return new TeamSnapshot((byte) stage(player), Math.max(0, remaining(player)),
            Math.max(0, duration(player)), progress == null ? 0 : progress.progress(),
            progress == null ? 0 : progress.required(), isCarried(player),
            isCarrying(player), ownRescue != null, incoming != null,
            carryPhase(player), carryTicks(player));
    }

    public static void toggleCarry(ServerPlayer carrier, int requestedEntityId) {
        UUID carrierId = carrier.getUUID();
        if (CARRYING.containsKey(carrierId)) {
            if (!CARRY_DROPS.containsKey(carrierId)) {
                UUID target = CARRYING.get(carrierId);
                CARRY_DROPS.put(carrierId, new CarryWindup(target,
                    DownedRules.CARRY_DROP_TICKS,
                    CarryPositionResolver.dropPosition(carrier, 1.0F)));
                CARRY_WINDUPS.remove(carrierId);
                sync(carrier);
                syncCarriedPlayer(carrier, target);
            }
            return;
        }
        if (isDowned(carrier) || RESCUES.containsKey(carrierId)) return;
        Entity target = requestedEntityId < 0 ? null : carrier.serverLevel().getEntity(requestedEntityId);
        if (!canCarryTarget(carrier, target)) target = nearestCarryTarget(carrier);
        if (target == null) {
            carrier.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                "downed.xero_delta.no_carry_target"), true);
            return;
        }
        if (target instanceof ServerPlayer targetPlayer) cancelRescuesFor(targetPlayer.getUUID());
        CARRYING.put(carrierId, target.getUUID());
        CARRIED_BY.put(target.getUUID(), carrierId);
        syncCarriedTarget(carrier, target);
        CARRY_WINDUPS.put(carrierId, new CarryWindup(target.getUUID(),
            DownedRules.CARRY_WINDUP_TICKS, null));
        sync(carrier);
        if (target instanceof ServerPlayer targetPlayer) sync(targetPlayer);
    }

    public static void handleRescueHold(ServerPlayer rescuer, byte action, int targetEntityId) {
        if (action == RescueHoldPacket.CANCEL) {
            cancelOwnRescue(rescuer);
            return;
        }
        long now = rescuer.serverLevel().getGameTime();
        RescueSession existing = RESCUES.get(rescuer.getUUID());
        if (action == RescueHoldPacket.HEARTBEAT) {
            if (existing != null && existing.entityId() == targetEntityId) {
                RESCUES.put(rescuer.getUUID(), existing.withHeartbeat(now));
            }
            return;
        }
        if (action != RescueHoldPacket.START || targetEntityId < 0) return;
        if (existing != null) {
            if (existing.entityId() == targetEntityId) {
                RESCUES.put(rescuer.getUUID(), existing.withHeartbeat(now));
                return;
            }
            cancelOwnRescue(rescuer);
        }
        Entity targetEntity = rescuer.serverLevel().getEntity(targetEntityId);
        if (targetEntity instanceof ServerPlayer target) {
            beginRedRescue(rescuer, target, now);
        } else if (targetEntity instanceof CorpseEntity corpse) {
            beginYellowRescue(rescuer, corpse, now);
        }
    }

    private static void beginRedRescue(ServerPlayer rescuer, ServerPlayer target, long now) {
        if (rescuer == target || !isRed(target) || interactionLocked(rescuer)
            || isCarried(target) || rescuer.distanceToSqr(target) > 16.0D
            || !isTeammate(rescuer, target.getUUID())
            || incomingRescue(target.getUUID()) != null) return;
        RESCUES.put(rescuer.getUUID(), RescueSession.red(
            target, rescuer, now, DownedRules.RED_RESCUE_TICKS));
        sync(rescuer);
        sync(target);
    }

    private static void beginYellowRescue(ServerPlayer rescuer, CorpseEntity corpse, long now) {
        UUID ownerId = corpse.ownerId();
        ServerPlayer owner = ownerId == null ? null : rescuer.server.getPlayerList().getPlayer(ownerId);
        if (owner == null || !isYellow(owner) || interactionLocked(rescuer)
            || rescuer.distanceToSqr(corpse) > 16.0D
            || !isTeammate(rescuer, ownerId)
            || incomingRescue(ownerId) != null) return;
        RESCUES.put(rescuer.getUUID(), RescueSession.yellow(
            owner, corpse, rescuer, now, DownedRules.YELLOW_RESCUE_TICKS));
        sync(rescuer);
        sync(owner);
    }

    public static void onLogout(ServerPlayer player) {
        dropCarried(player);
        UUID carrierId = CARRIED_BY.remove(player.getUUID());
        if (carrierId != null) CARRYING.remove(carrierId);
        CARRY_WINDUPS.remove(player.getUUID());
        CARRY_DROPS.remove(player.getUUID());
        cancelOwnRescue(player);
        ABANDON_HOLDS.remove(player.getUUID());
        cancelRescuesFor(player.getUUID());
    }

    public static void resetAfterDeath(ServerPlayer replacement) {
        cancelOwnRescue(replacement);
        cancelRescuesFor(replacement.getUUID());
        ABANDON_HOLDS.remove(replacement.getUUID());
        replacement.getPersistentData().remove(ROOT);
        sync(replacement);
    }

    public static void preserveKnifeOnDeath(ServerPlayer player) {
        if (player == null || !PlayerLayoutSlotRules.enabled(player)) return;
        int slot = PlayerLayoutInventoryPolicy.KNIFE_SLOT;
        ItemStack knife = player.getInventory().getItem(slot);
        if (knife.isEmpty()) return;
        player.getPersistentData().put(PROTECTED_KNIFE,
            knife.copy().save(player.registryAccess()));
        player.getInventory().setItem(slot, ItemStack.EMPTY);
        player.getInventory().setChanged();
    }

    public static void restoreKnifeAfterDeath(ServerPlayer original,
                                              ServerPlayer replacement) {
        if (original == null || replacement == null) return;
        CompoundTag encoded = original.getPersistentData().getCompound(PROTECTED_KNIFE);
        original.getPersistentData().remove(PROTECTED_KNIFE);
        replacement.getPersistentData().remove(PROTECTED_KNIFE);
        if (encoded.isEmpty()) return;
        ItemStack knife = ItemStack.parseOptional(replacement.registryAccess(), encoded);
        if (knife.isEmpty()) return;
        int slot = PlayerLayoutInventoryPolicy.KNIFE_SLOT;
        if (replacement.getInventory().getItem(slot).isEmpty()) {
            replacement.getInventory().setItem(slot, knife);
        } else {
            replacement.getInventory().placeItemBackInInventory(knife);
        }
        replacement.getInventory().setChanged();
    }

    public static void sync(ServerPlayer player) {
        RescueSession ownRescue = RESCUES.get(player.getUUID());
        Map.Entry<UUID, RescueSession> incoming = incomingRescue(player.getUUID());
        RescueSession displayed = incoming != null ? incoming.getValue() : ownRescue;
        String rescuerName = "";
        if (incoming != null) {
            ServerPlayer rescuer = player.server.getPlayerList().getPlayer(incoming.getKey());
            if (rescuer != null) rescuerName = rescuer.getDisplayName().getString();
        } else if (ownRescue != null) {
            ServerPlayer target = player.server.getPlayerList().getPlayer(ownRescue.target());
            if (target != null) rescuerName = target.getDisplayName().getString();
        }
        PacketDistributor.sendToPlayer(player, new DownedStatePacket((byte) stage(player),
            Math.max(0, remaining(player)), Math.max(0, duration(player)),
            displayed == null ? 0 : displayed.progress(),
            displayed == null ? 0 : displayed.required(),
            isCarried(player), isCarrying(player), ownRescue != null,
            incoming != null, rescuerName, carryPhase(player), carryTicks(player)));
    }

    private static void enterRed(ServerPlayer player, boolean boltSniper) {
        ABANDON_HOLDS.remove(player.getUUID());
        int duration = DownedRules.redDuration(boltSniper);
        writeState(player, 1, duration, duration);
        player.setHealth(1.0F);
        player.setSprinting(false);
        player.setForcedPose(Pose.SWIMMING);
        player.refreshDimensions();
        player.closeContainer();
        sync(player);
    }

    private static void enterYellow(ServerPlayer player) {
        ABANDON_HOLDS.remove(player.getUUID());
        player.setForcedPose(null);
        player.refreshDimensions();
        applyRedCrawlSpeed(player, false);
        dropCarrierOf(player);
        cancelRescuesFor(player.getUUID());
        CorpseEntity corpse = ModEntities.CORPSE.get().create(player.serverLevel());
        if (corpse != null) {
            corpse.moveTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), 0.0F);
            corpse.setOwner(player);
            moveInventoryToCorpse(player, corpse);
            player.serverLevel().addFreshEntity(corpse);
            CompoundTag data = data(player);
            data.putUUID(CORPSE, corpse.getUUID());
            data.putBoolean(YELLOW_USED, true);
            data.putInt(STAGE, 2);
            data.putInt(REMAINING, DownedRules.YELLOW_WINDOW_TICKS);
            data.putInt(DURATION, DownedRules.YELLOW_WINDOW_TICKS);
            save(player, data);
        } else {
            writeState(player, 2, DownedRules.YELLOW_WINDOW_TICKS,
                DownedRules.YELLOW_WINDOW_TICKS);
            setYellowUsed(player, true);
        }
        player.setHealth(1.0F);
        player.setGameMode(GameType.SPECTATOR);
        ServerPlayer teammate = spectatorCandidates(player).stream().findFirst().orElse(null);
        player.setCamera(teammate == null ? player : teammate);
        sync(player);
    }

    public static void switchSpectatorTarget(ServerPlayer player) {
        if (!isYellow(player) || !player.isSpectator()) return;
        var candidates = spectatorCandidates(player);
        if (candidates.isEmpty()) {
            player.setCamera(player);
            return;
        }
        Entity current = player.getCamera();
        int currentIndex = -1;
        if (current != null) for (int index = 0; index < candidates.size(); index++) {
            if (candidates.get(index).getUUID().equals(current.getUUID())) {
                currentIndex = index;
                break;
            }
        }
        player.setCamera(candidates.get((currentIndex + 1) % candidates.size()));
    }

    private static void reviveRed(ServerPlayer player) {
        dropCarrierOf(player);
        clearStage(player, false);
        player.setHealth(Math.max(1.0F, player.getMaxHealth() * 0.20F));
        sync(player);
    }

    private static void reviveYellow(ServerPlayer player, CorpseEntity corpse) {
        player.setCamera(player);
        player.setGameMode(GameType.SURVIVAL);
        if (corpse != null && !corpse.isRemoved()) {
            player.teleportTo(corpse.getX(), corpse.getY() + 0.2D, corpse.getZ());
            corpse.restoreRemaining(player);
            corpse.discard();
        }
        setYellowRescueHealthPenalty(player, true);
        clearStage(player, true);
        player.setHealth(Math.max(1.0F, player.getMaxHealth() * 0.20F));
        sync(player);
    }

    private static void finalDeath(ServerPlayer player) {
        CompoundTag state = data(player);
        CorpseEntity corpse = state.hasUUID(CORPSE)
            ? findCorpse(player.serverLevel(), state.getUUID(CORPSE)) : null;
        // The corpse created at yellow-down is the authoritative inventory. Keep
        // that same entity and switch only its presentation state so a final
        // death cannot duplicate or drop the stored equipment.
        if (corpse != null && !corpse.isRemoved()) {
            corpse.convertToLootBox();
        }
        player.setCamera(player);
        player.setGameMode(GameType.SURVIVAL);
        clearStage(player, true);
        player.kill();
    }

    private static void abandonYellow(ServerPlayer player) {
        dropCarrierOf(player);
        RESCUES.remove(player.getUUID());
        cancelRescuesFor(player.getUUID());
        CompoundTag state = data(player);
        CorpseEntity corpse = state.hasUUID(CORPSE)
            ? findCorpse(player.serverLevel(), state.getUUID(CORPSE)) : null;
        player.setCamera(player);
        player.setGameMode(GameType.SURVIVAL);
        if (corpse != null && !corpse.isRemoved()) {
            player.teleportTo(corpse.getX(), corpse.getY() + 0.2D, corpse.getZ());
            corpse.restoreRemaining(player);
            player.getInventory().setChanged();
            player.inventoryMenu.broadcastChanges();
            if (player.containerMenu != player.inventoryMenu) {
                player.containerMenu.broadcastChanges();
            }
            corpse.discard();
        }
        clearStage(player, true);
        sync(player);
        player.kill();
    }

    private static boolean tickAbandonHold(ServerPlayer player) {
        Integer held = ABANDON_HOLDS.get(player.getUUID());
        if (held == null) return false;
        int next = held + 1;
        if (next < DownedRules.ABANDON_HOLD_TICKS) {
            ABANDON_HOLDS.put(player.getUUID(), next);
            return false;
        }
        ABANDON_HOLDS.remove(player.getUUID());
        enterYellow(player);
        return true;
    }

    private static void tickCarry(ServerPlayer carrier) {
        UUID carrierId = carrier.getUUID();
        UUID targetId = CARRYING.get(carrierId);
        if (targetId == null) {
            applyCarrySpeed(carrier, false);
            CARRY_WINDUPS.remove(carrierId);
            CARRY_DROPS.remove(carrierId);
            return;
        }
        Entity target = carrier.serverLevel().getEntity(targetId);
        if (target == null || target.isRemoved() || carrier.isRemoved() || carrier.isDeadOrDying()) {
            dropCarried(carrier);
            return;
        }
        CarryWindup windup = CARRY_WINDUPS.get(carrierId);
        if (windup != null) {
            freezeCarryWindup(carrier, windup);
            syncCarriedTarget(carrier, target);
            applyCarrySpeed(carrier, false);
            if (windup.remaining() <= 1) {
                CARRY_WINDUPS.remove(carrierId);
                sync(carrier);
                if (target instanceof ServerPlayer targetPlayer) sync(targetPlayer);
            } else {
                CARRY_WINDUPS.put(carrierId, windup.withRemaining(windup.remaining() - 1));
            }
            return;
        }
        CarryWindup drop = CARRY_DROPS.get(carrierId);
        if (drop != null) {
            freezeCarryWindup(carrier, drop);
            syncCarriedTarget(carrier, target);
            applyCarrySpeed(carrier, false);
            if (drop.remaining() <= 1) dropCarried(carrier, drop.destination());
            else CARRY_DROPS.put(carrierId, drop.withRemaining(drop.remaining() - 1));
            return;
        }
        applyCarrySpeed(carrier, true);
        // CorpseEntity is a custom non-living entity and some loader/mod
        // combinations do not update a passenger's absolute position until
        // the next vehicle rebuild. Keep the authoritative target attached to
        // the carrier every server tick instead of leaving it at its pickup
        // position. The riding relationship is still maintained for vanilla
        // and third-party passenger handling.
        syncCarriedTarget(carrier, target);
    }

    private static void syncCarriedTarget(ServerPlayer carrier, Entity target) {
        double x = carrier.getX();
        double y = carrier.getY() + 0.5D;
        double z = carrier.getZ();
        if (target instanceof CorpseEntity corpse) {
            corpse.setCarrierEntityId(carrier.getId());
            // The client follows the carrier id and renders its own smooth path.
            // Avoid broadcasting a hard teleport packet for the corpse every tick.
            corpse.setPos(x, y, z);
        } else {
            target.teleportTo(x, y, z);
        }
        target.setYRot(carrier.getYRot());
        target.setXRot(0.0F);
        target.setDeltaMovement(0.0D, 0.0D, 0.0D);
        if (!target.isPassenger() && !target.startRiding(carrier, true)) {
            // Keep position synchronization functional even when another mod
            // rejects the passenger relationship for this custom entity.
            target.setPos(x, y, z);
        }
    }

    private static void freezeCarryWindup(ServerPlayer carrier, CarryWindup windup) {
        carrier.setSprinting(false);
    }

    private static void syncCarriedPlayer(ServerPlayer carrier, UUID targetId) {
        Entity target = carrier.serverLevel().getEntity(targetId);
        if (target instanceof ServerPlayer targetPlayer) sync(targetPlayer);
    }

    private static void tickRescue(ServerPlayer rescuer) {
        RescueSession session = RESCUES.get(rescuer.getUUID());
        if (session == null) return;
        ServerPlayer target = rescuer.server.getPlayerList().getPlayer(session.target());
        CorpseEntity corpse = session.corpse() == null ? null : findCorpse(rescuer.serverLevel(), session.corpse());
        double distance = session.yellow() && corpse != null
            ? rescuer.distanceToSqr(corpse) : target == null ? Double.MAX_VALUE : rescuer.distanceToSqr(target);
        boolean targetFell = !session.yellow() && target != null
            && (target.getY() < session.targetY() - 0.35D || target.getDeltaMovement().y < -0.12D);
        boolean valid = target != null && distance <= 16.0D && !isDowned(rescuer)
            && !rescuer.isRemoved() && !rescuer.isDeadOrDying()
            && DownedRules.rescueHeartbeatFresh(rescuer.serverLevel().getGameTime(), session.lastHeartbeat())
            && !targetFell
            && (session.yellow() ? isYellow(target) && corpse != null : isRed(target) && !isCarried(target));
        if (!valid) {
            cancelOwnRescue(rescuer);
            return;
        }

        rescuer.teleportTo(session.rescuerX(), session.rescuerY(), session.rescuerZ());
        rescuer.setDeltaMovement(0.0D, 0.0D, 0.0D);
        rescuer.setSprinting(false);
        if (!session.yellow()) {
            target.teleportTo(session.targetX(), target.getY(), session.targetZ());
            target.setDeltaMovement(0.0D, target.getDeltaMovement().y, 0.0D);
            target.lookAt(EntityAnchorArgument.Anchor.EYES, rescuer.getEyePosition());
        }
        int next = session.progress() + 1;
        if (next < session.required()) {
            RESCUES.put(rescuer.getUUID(), session.withProgress(next));
            if ((next & 1) == 0) {
                sync(rescuer);
                sync(target);
            }
            return;
        }
        RESCUES.remove(rescuer.getUUID());
        if (session.yellow()) reviveYellow(target, corpse); else reviveRed(target);
        broadcastRescueFeed(rescuer, target);
        sync(rescuer);
    }

    private static void broadcastDownedFeed(ServerPlayer target, DamageSource source,
                                            boolean headshot) {
        Entity attacker = source.getEntity();
        if (attacker == null && source.getDirectEntity() instanceof Projectile projectile) {
            attacker = projectile.getOwner();
        }
        if (attacker == null) attacker = source.getDirectEntity();
        String actorName = attacker == null
            ? net.minecraft.network.chat.Component.translatable(
                "combat_feed.xero_delta.environment").getString()
            : attacker.getDisplayName().getString();
        ItemStack weapon = attacker instanceof LivingEntity living
            ? living.getMainHandItem().copyWithCount(1) : ItemStack.EMPTY;
        UUID attackerId = attacker instanceof ServerPlayer serverPlayer
            ? serverPlayer.getUUID() : null;
        for (ServerPlayer viewer : target.server.getPlayerList().getPlayers()) {
            PacketDistributor.sendToPlayer(viewer, new CombatFeedPacket(
                CombatFeedPacket.DOWNED, actorName, target.getDisplayName().getString(),
                attackerId != null && isTeammate(viewer, attackerId),
                isTeammate(viewer, target.getUUID()), headshot, weapon));
        }
    }

    private static void broadcastRescueFeed(ServerPlayer rescuer, ServerPlayer target) {
        if (target == null) return;
        for (ServerPlayer viewer : rescuer.server.getPlayerList().getPlayers()) {
            PacketDistributor.sendToPlayer(viewer, new CombatFeedPacket(
                CombatFeedPacket.RESCUED, rescuer.getDisplayName().getString(),
                target.getDisplayName().getString(),
                isTeammate(viewer, rescuer.getUUID()),
                isTeammate(viewer, target.getUUID()), false, ItemStack.EMPTY));
        }
    }

    private static void dropCarried(ServerPlayer carrier) {
        dropCarried(carrier, null);
    }

    private static void dropCarried(ServerPlayer carrier, Vec3 requestedDestination) {
        CARRY_WINDUPS.remove(carrier.getUUID());
        CARRY_DROPS.remove(carrier.getUUID());
        applyCarrySpeed(carrier, false);
        UUID targetId = CARRYING.remove(carrier.getUUID());
        if (targetId == null) {
            sync(carrier);
            return;
        }
        CARRIED_BY.remove(targetId);
        Entity target = carrier.serverLevel().getEntity(targetId);
        if (target != null) {
            Vec3 destination = requestedDestination == null
                ? CarryPositionResolver.dropPosition(carrier, 1.0F) : requestedDestination;
            if (target instanceof CorpseEntity corpse) corpse.setCarrierEntityId(-1);
            target.stopRiding();
            target.teleportTo(destination.x, destination.y, destination.z);
            target.setDeltaMovement(0.0D, 0.0D, 0.0D);
            if (target instanceof ServerPlayer targetPlayer) sync(targetPlayer);
        }
        sync(carrier);
    }

    private static void dropCarrierOf(ServerPlayer target) {
        UUID carrierId = CARRIED_BY.get(target.getUUID());
        if (carrierId == null) return;
        ServerPlayer carrier = target.server.getPlayerList().getPlayer(carrierId);
        if (carrier != null) dropCarried(carrier);
    }

    private static Map.Entry<UUID, RescueSession> incomingRescue(UUID targetId) {
        return RESCUES.entrySet().stream()
            .filter(entry -> entry.getValue().target().equals(targetId))
            .findFirst().orElse(null);
    }

    private static void cancelOwnRescue(ServerPlayer rescuer) {
        RescueSession removed = RESCUES.remove(rescuer.getUUID());
        if (removed == null) return;
        ServerPlayer target = rescuer.server.getPlayerList().getPlayer(removed.target());
        sync(rescuer);
        if (target != null) sync(target);
    }

    private static void cancelRescuesFor(UUID targetId) {
        Map<UUID, RescueSession> removed = new HashMap<>();
        RESCUES.entrySet().removeIf(entry -> {
            boolean matches = entry.getValue().target().equals(targetId);
            if (matches) removed.put(entry.getKey(), entry.getValue());
            return matches;
        });
        var server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        for (UUID rescuerId : removed.keySet()) {
            ServerPlayer rescuer = server.getPlayerList().getPlayer(rescuerId);
            if (rescuer != null) sync(rescuer);
        }
    }

    private static void moveInventoryToCorpse(ServerPlayer player, CorpseEntity corpse) {
        // Stable presentation slots: primaries 0/1, helmet/chest 2/3,
        // pistol 4, pockets 5-9, chest rig/backpack 10-11.
        moveStackToCorpseSlot(player, corpse, player.getInventory().items, 0, 0);
        moveStackToCorpseSlot(player, corpse, player.getInventory().items, 1, 1);
        moveStackToCorpseSlot(player, corpse, player.getInventory().items, 2, 4);
        for (int index = 4; index <= 8; index++) {
            moveStackToCorpseSlot(player, corpse, player.getInventory().items,
                index, index + 1);
        }

        // Move the worn carriers first. Loose inventory can then be inserted into
        // their real internal grids instead of being sent to an extra corpse area.
        CuriosApi.getCuriosInventory(player).ifPresent(curios -> {
            curios.getCurios().forEach((identifier, handler) -> {
                if ("safety_box".equals(identifier) || "card_holder".equals(identifier)) return;
                var stacks = handler.getStacks();
                for (int slot = 0; slot < stacks.getSlots(); slot++) {
                    ItemStack stack = stacks.getStackInSlot(slot);
                    if (stack.isEmpty() || stack.is(ModTags.SAFETY_BOX)) continue;
                    int presentationSlot = slot == 0 ? switch (identifier) {
                        case "chest_rig" -> CorpseMenu.CHEST_RIG_SLOT;
                        case "backpack" -> CorpseMenu.BACKPACK_SLOT;
                        default -> -1;
                    } : -1;
                    ItemStack snapshot = presentationSlot >= 0
                        ? corpse.inventory().getItem(presentationSlot) : ItemStack.EMPTY;
                    boolean alreadyEquipped = presentationSlot >= 0
                        && !snapshot.isEmpty()
                        && ItemStack.isSameItemSameComponents(snapshot, stack);
                    ItemStack remainder = alreadyEquipped ? ItemStack.EMPTY
                        : presentationSlot >= 0
                            ? corpse.addStackAt(presentationSlot, stack)
                            : corpse.addStackToStorage(stack);
                    stacks.setStackInSlot(slot, ItemStack.EMPTY);
                    if (!remainder.isEmpty()) player.drop(remainder, false);
                }
                handler.update();
            });
        });

        for (int index = 9; index < player.getInventory().items.size(); index++) {
            moveGenericStack(player, corpse, player.getInventory().items, index);
        }

        // Inventory armor order is feet, legs, chest, head.
        moveStackToCorpseSlot(player, corpse, player.getInventory().armor, 3, 2);
        moveStackToCorpseSlot(player, corpse, player.getInventory().armor, 2, 3);
        moveGenericStack(player, corpse, player.getInventory().armor, 0);
        moveGenericStack(player, corpse, player.getInventory().armor, 1);
        for (int index = 0; index < player.getInventory().offhand.size(); index++) {
            moveGenericStack(player, corpse, player.getInventory().offhand, index);
        }

        ItemStack cursor = player.containerMenu.getCarried();
        if (!cursor.isEmpty() && !cursor.is(ModTags.SAFETY_BOX)) {
            ItemStack remainder = corpse.addStackToStorage(cursor);
            player.containerMenu.setCarried(ItemStack.EMPTY);
            if (!remainder.isEmpty()) player.drop(remainder, false);
        }
        player.getInventory().setChanged();
        player.inventoryMenu.broadcastChanges();
    }
    private static void moveStackToCorpseSlot(ServerPlayer player, CorpseEntity corpse,
                                               net.minecraft.core.NonNullList<ItemStack> inventory,
                                               int index, int corpseSlot) {
        ItemStack stack = inventory.get(index);
        if (stack.isEmpty() || stack.is(ModTags.SAFETY_BOX)) return;
        ItemStack remainder = corpse.addStackAt(corpseSlot, stack);
        inventory.set(index, ItemStack.EMPTY);
        if (!remainder.isEmpty()) player.drop(remainder, false);
    }

    private static void moveGenericStack(ServerPlayer player, CorpseEntity corpse,
                                         net.minecraft.core.NonNullList<ItemStack> inventory,
                                         int index) {
        ItemStack stack = inventory.get(index);
        if (stack.isEmpty() || stack.is(ModTags.SAFETY_BOX)) return;
        ItemStack remainder = corpse.addStackToStorage(stack);
        inventory.set(index, ItemStack.EMPTY);
        if (!remainder.isEmpty()) player.drop(remainder, false);
    }

    private static CorpseEntity findCorpse(ServerLevel level, UUID id) {
        Entity entity = level.getEntity(id);
        return entity instanceof CorpseEntity corpse ? corpse : null;
    }

    private static java.util.List<ServerPlayer> spectatorCandidates(ServerPlayer player) {
        return FtbTeamIntegration.onlineTeammates(player).stream()
            .filter(candidate -> candidate != player && !isDowned(candidate)
                && !candidate.isSpectator() && !candidate.isDeadOrDying())
            .toList();
    }

    private static boolean isBoltSniper(Entity attacker) {
        if (!(attacker instanceof LivingEntity living)) return false;
        ItemStack weapon = living.getMainHandItem();
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(weapon.getItem());
        String text = ((id == null ? "" : id.toString()) + " " + weapon.getHoverName().getString()
            + " " + weapon).toLowerCase(java.util.Locale.ROOT);
        return text.contains("bolt") || text.contains("sniper") || text.contains("m700")
            || text.contains("m95") || text.contains("m82") || text.contains("m107")
            || text.contains("\u6813\u72d9") || text.contains("\u72d9\u51fb");
    }

    private static void applyRedCrawlSpeed(ServerPlayer player, boolean active) {
        var speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) return;
        if (active && !speed.hasModifier(RED_CRAWL_SPEED)) {
            speed.addTransientModifier(new AttributeModifier(RED_CRAWL_SPEED, -0.75D,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        } else if (!active && speed.hasModifier(RED_CRAWL_SPEED)) {
            speed.removeModifier(RED_CRAWL_SPEED);
        }
    }

    private static void applyRedWaterBuoyancy(ServerPlayer player) {
        if (isCarried(player) || !player.isInWater() || !player.isUnderWater()) return;
        var movement = player.getDeltaMovement();
        player.setDeltaMovement(movement.x, Math.min(0.16D, movement.y + 0.055D), movement.z);
        player.hurtMarked = true;
    }

    private static int stage(ServerPlayer player) { return data(player).getInt(STAGE); }
    private static int remaining(ServerPlayer player) { return data(player).getInt(REMAINING); }
    private static int duration(ServerPlayer player) { return data(player).getInt(DURATION); }
    private static boolean yellowUsed(ServerPlayer player) { return data(player).getBoolean(YELLOW_USED); }

    private static void setRemaining(ServerPlayer player, int value) {
        CompoundTag data = data(player);
        data.putInt(REMAINING, Math.max(0, value));
        save(player, data);
    }

    private static void applyCarrySpeed(ServerPlayer player, boolean active) {
        var speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) return;
        if (active && !speed.hasModifier(CARRY_SPEED)) {
            speed.addTransientModifier(new AttributeModifier(CARRY_SPEED, -0.45D,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        } else if (!active && speed.hasModifier(CARRY_SPEED)) {
            speed.removeModifier(CARRY_SPEED);
        }
    }

    private static byte carryPhase(ServerPlayer player) {
        UUID carrierId = player.getUUID();
        UUID carriedBy = CARRIED_BY.get(carrierId);
        if (carriedBy != null) carrierId = carriedBy;
        if (CARRY_WINDUPS.containsKey(carrierId)) return 1;
        if (CARRY_DROPS.containsKey(carrierId)) return 3;
        return CARRYING.containsKey(carrierId) ? (byte) 2 : 0;
    }

    private static int carryTicks(ServerPlayer player) {
        UUID carrierId = player.getUUID();
        UUID carriedBy = CARRIED_BY.get(carrierId);
        if (carriedBy != null) carrierId = carriedBy;
        CarryWindup state = CARRY_WINDUPS.get(carrierId);
        if (state == null) state = CARRY_DROPS.get(carrierId);
        return state == null ? 0 : Math.max(0, state.remaining());
    }

    private static boolean canCarryTarget(ServerPlayer carrier, Entity target) {
        if (target == null || target == carrier || target.isRemoved()
            || target.distanceToSqr(carrier) > 16.0D || CARRIED_BY.containsKey(target.getUUID())) return false;
        if (target instanceof ServerPlayer player) {
            return isRed(player) && isTeammate(carrier, player.getUUID());
        }
        if (target instanceof CorpseEntity corpse) {
            // Bodies and their final loot boxes use the same carry interaction.
            // The server still validates distance and one-carrier-at-a-time above.
            return true;
        }
        return false;
    }

    private static Entity nearestCarryTarget(ServerPlayer carrier) {
        return carrier.serverLevel().getEntities(carrier,
                carrier.getBoundingBox().inflate(3.5D), entity -> canCarryTarget(carrier, entity))
            .stream().min(Comparator.comparingDouble(carrier::distanceToSqr)).orElse(null);
    }

    public static boolean isTeammate(ServerPlayer viewer, UUID playerId) {
        return playerId != null && FtbTeamIntegration.memberIdsIncludingSelf(viewer).contains(playerId);
    }

    private static void setYellowRescueHealthPenalty(ServerPlayer player, boolean value) {
        CompoundTag state = data(player);
        state.putBoolean(YELLOW_RESCUE_HEALTH_PENALTY, value);
        save(player, state);
    }

    private static void setYellowUsed(ServerPlayer player, boolean value) {
        CompoundTag data = data(player);
        data.putBoolean(YELLOW_USED, value);
        save(player, data);
    }

    private static void writeState(ServerPlayer player, int stage, int remaining, int duration) {
        CompoundTag data = data(player);
        data.putInt(STAGE, stage);
        data.putInt(REMAINING, remaining);
        data.putInt(DURATION, duration);
        save(player, data);
    }

    private static void clearStage(ServerPlayer player, boolean keepYellowUsed) {
        ABANDON_HOLDS.remove(player.getUUID());
        player.setForcedPose(null);
        player.refreshDimensions();
        applyRedCrawlSpeed(player, false);
        boolean used = keepYellowUsed && yellowUsed(player);
        boolean rescuedFromYellow = hasYellowRescueHealthPenalty(player);
        CompoundTag data = new CompoundTag();
        if (used) data.putBoolean(YELLOW_USED, true);
        if (rescuedFromYellow) data.putBoolean(YELLOW_RESCUE_HEALTH_PENALTY, true);
        player.getPersistentData().put(ROOT, data);
    }

    private static CompoundTag data(ServerPlayer player) {
        return player.getPersistentData().getCompound(ROOT);
    }

    private static void save(ServerPlayer player, CompoundTag data) {
        player.getPersistentData().put(ROOT, data);
    }

    public record TeamSnapshot(byte stage, int remainingTicks, int durationTicks,
                               int rescueTicks, int rescueDurationTicks,
                               boolean carried, boolean carrying,
                               boolean rescuing, boolean beingRescued,
                               byte carryPhase, int carryTicks) {
    }
    private record CarryWindup(UUID target, int remaining, Vec3 destination) {
        CarryWindup withRemaining(int value) { return new CarryWindup(target, value, destination); }
    }
    private record RescueSession(UUID target, UUID corpse, int entityId,
                                 int progress, int required, boolean yellow,
                                 double rescuerX, double rescuerY, double rescuerZ,
                                 double targetX, double targetY, double targetZ,
                                 long lastHeartbeat) {
        static RescueSession red(ServerPlayer target, ServerPlayer rescuer,
                                 long heartbeat, int required) {
            return new RescueSession(target.getUUID(), null, target.getId(), 0, required, false,
                rescuer.getX(), rescuer.getY(), rescuer.getZ(),
                target.getX(), target.getY(), target.getZ(), heartbeat);
        }

        static RescueSession yellow(ServerPlayer target, CorpseEntity corpse, ServerPlayer rescuer,
                                    long heartbeat, int required) {
            return new RescueSession(target.getUUID(), corpse.getUUID(), corpse.getId(), 0, required, true,
                rescuer.getX(), rescuer.getY(), rescuer.getZ(),
                corpse.getX(), corpse.getY(), corpse.getZ(), heartbeat);
        }

        RescueSession withProgress(int value) {
            return new RescueSession(target, corpse, entityId, value, required, yellow,
                rescuerX, rescuerY, rescuerZ, targetX, targetY, targetZ, lastHeartbeat);
        }

        RescueSession withHeartbeat(long value) {
            return new RescueSession(target, corpse, entityId, progress, required, yellow,
                rescuerX, rescuerY, rescuerZ, targetX, targetY, targetZ, value);
        }
    }
}
