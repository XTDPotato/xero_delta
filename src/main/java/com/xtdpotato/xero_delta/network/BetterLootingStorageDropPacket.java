package com.xtdpotato.xero_delta.network;

import com.xtdpotato.xero_delta.XeroDelta;
import com.xtdpotato.xero_delta.compat.DeltaPackAutoEquipService;
import com.xtdpotato.xero_delta.data.PlayerLayoutSlotRules;
import com.xtdpotato.xero_delta.data.SafetyBoxAccessData;
import com.xtdpotato.xero_delta.grid.GridBackingStore;
import com.xtdpotato.xero_delta.item.DeltaPackItem;
import com.xtdpotato.xero_delta.item.SafetyBoxItem;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import top.theillusivec4.curios.api.CuriosApi;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Server-authoritative bridge for dragging a Better Looting ground entry into
 * an equipped Delta storage grid.
 */
public record BetterLootingStorageDropPacket(List<Integer> entityIds,
                                             String identifier,
                                             int cell,
                                             boolean rotated)
    implements CustomPacketPayload {
    private static final Set<String> STORAGE_SLOTS = Set.of(
        "chest_rig", "backpack", "card_holder", "safety_box", "pockets", "helmet", "chest");
    private static final int MAX_ENTITY_IDS = 256;
    private static final double MAX_DISTANCE_SQUARED = 100.0D;

    public static final Type<BetterLootingStorageDropPacket> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(XeroDelta.MOD_ID,
            "better_looting_storage_drop"));
    public static final StreamCodec<FriendlyByteBuf, BetterLootingStorageDropPacket> STREAM_CODEC =
        StreamCodec.of((buffer, packet) -> {
            buffer.writeVarInt(packet.entityIds.size());
            for (int entityId : packet.entityIds) buffer.writeVarInt(entityId);
            buffer.writeUtf(packet.identifier, 32);
            buffer.writeVarInt(packet.cell);
            buffer.writeBoolean(packet.rotated);
        }, buffer -> {
            int size = buffer.readVarInt();
            if (size < 0 || size > MAX_ENTITY_IDS) {
                throw new IllegalArgumentException(
                    "Invalid Better Looting entity count: " + size);
            }
            List<Integer> entityIds = new ArrayList<>(size);
            for (int index = 0; index < size; index++) {
                entityIds.add(buffer.readVarInt());
            }
            return new BetterLootingStorageDropPacket(
                entityIds, buffer.readUtf(32), buffer.readVarInt(), buffer.readBoolean());
        });

    public BetterLootingStorageDropPacket {
        entityIds = List.copyOf(entityIds);
    }

    @Override
    public Type<BetterLootingStorageDropPacket> type() {
        return TYPE;
    }

    public static void handle(BetterLootingStorageDropPacket packet,
                              IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                || !player.isAlive()
                || !PlayerLayoutSlotRules.enabled(player)
                || !STORAGE_SLOTS.contains(packet.identifier)
                || packet.entityIds.isEmpty()
                || packet.entityIds.size() > MAX_ENTITY_IDS
                || packet.cell < -1) {
                return;
            }
            if ("pockets".equals(packet.identifier)) {
                insertIntoPocket(player, packet);
                return;
            }
            if (packet.cell == -1) {
                if ("helmet".equals(packet.identifier)
                    || "chest".equals(packet.identifier)) {
                    equipArmorFromEntities(player, packet.entityIds,
                        "helmet".equals(packet.identifier)
                            ? net.minecraft.world.entity.EquipmentSlot.HEAD
                            : net.minecraft.world.entity.EquipmentSlot.CHEST);
                } else if ("chest_rig".equals(packet.identifier)
                    || "backpack".equals(packet.identifier)) {
                    DeltaPackAutoEquipService.equipFromEntities(player,
                        packet.entityIds, packet.identifier);
                }
                return;
            }
            insertIntoEquippedStorage(player, packet);
        });
    }

    private static void equipArmorFromEntities(ServerPlayer player,
                                               List<Integer> entityIds,
                                               net.minecraft.world.entity.EquipmentSlot slot) {
        final boolean[] equipped = {false};
        int moved = processEntities(player, entityIds, incoming -> {
            if (equipped[0] || incoming.isEmpty()
                || !PlayerEquipmentSync.canEquip(player, incoming, slot)) return incoming;
            ItemStack current = player.getItemBySlot(slot);
            if (!current.isEmpty()) {
                ItemStack displaced = current.copy();
                DeltaPackAutoEquipService.tryStoreInEquippedBackpack(player, displaced);
                if (!displaced.isEmpty()) return incoming;
            }
            player.setItemSlot(slot, incoming.copyWithCount(1));
            ItemStack remainder = incoming.copy();
            remainder.shrink(1);
            equipped[0] = true;
            return remainder;
        });
        if (moved <= 0) return;
        PlayerEquipmentSync.sync(player, slot);
        player.playNotifySound(
            SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.2F, 2.0F);
    }
    private static void insertIntoEquippedStorage(
        ServerPlayer player, BetterLootingStorageDropPacket packet) {
        var curiosOptional = CuriosApi.getCuriosInventory(player);
        if (curiosOptional.isEmpty()) return;
        var curios = curiosOptional.get();
        var slotHandler = curios.getStacksHandler(packet.identifier).orElse(null);
        if (slotHandler == null || slotHandler.getSlots() <= 0
            || !curios.isSlotActive(packet.identifier, 0)) {
            return;
        }
        ItemStack carrier = slotHandler.getStacks().getStackInSlot(0);
        if (carrier.isEmpty()) return;

        boolean safetyBox = "safety_box".equals(packet.identifier);
        GridBackingStore gridStore = null;
        IItemHandler capabilityStore = null;
        int width = 0;
        int height = 0;

        if (safetyBox) {
            if (!(carrier.getItem() instanceof SafetyBoxItem box)) return;
            String itemId = carrier.getItemHolder().getKey().location().toString();
            if (!SafetyBoxAccessData.get(player.server).isUnlocked(
                player.getUUID(), itemId, System.currentTimeMillis())) {
                ModNetwork.sendTranslatedNotice(
                    player, "safety_box.xero_delta.expired_read_only");
                return;
            }
            width = box.getGridWidth();
            height = box.getGridHeight();
            gridStore = new GridBackingStore(carrier, width, height);
        } else if (carrier.getItem() instanceof DeltaPackItem pack) {
            if (!pack.slotIdentifier().equals(packet.identifier)) return;
            width = pack.gridWidth();
            height = pack.gridHeight();
            gridStore = new GridBackingStore(carrier, width, height, 0,
                stack -> GridBackingStore.isBlockedInEquippedStorage(
                    packet.identifier, stack));
        } else {
            capabilityStore = carrier.getCapability(Capabilities.ItemHandler.ITEM);
            if (capabilityStore == null || capabilityStore.getSlots() <= 0) return;
            if (packet.cell >= capabilityStore.getSlots()) return;
        }

        if (gridStore != null && packet.cell >= width * height) return;
        final GridBackingStore resolvedGrid = gridStore;
        final IItemHandler resolvedCapability = capabilityStore;
        final int resolvedWidth = width;
        int moved = processEntities(player, packet.entityIds, incoming -> {
            if (resolvedGrid != null) {
                return insertIntoGrid(
                    resolvedGrid, resolvedWidth, packet.cell, incoming, packet.rotated);
            }
            return resolvedCapability.insertItem(packet.cell, incoming, false);
        });
        if (moved <= 0) return;

        curios.setEquippedCurio(packet.identifier, 0, carrier.copy());
        slotHandler.update();
        player.getInventory().setChanged();
        player.inventoryMenu.broadcastChanges();
        if (player.containerMenu != player.inventoryMenu) {
            player.containerMenu.broadcastChanges();
        }
        if (safetyBox && gridStore != null) {
            ModNetwork.sendToClient(player, new GridSyncPacket(
                gridStore.getAllItems(), width, height,
                player.containerMenu.getCarried().copy(), 0));
        }
        player.playNotifySound(
            SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.2F, 2.0F);
    }

    private static void insertIntoPocket(ServerPlayer player,
                                         BetterLootingStorageDropPacket packet) {
        if (packet.cell < 4 || packet.cell > 8) return;
        int moved = processEntities(player, packet.entityIds, incoming -> {
            if (!PlayerLayoutSlotRules.canPlace(player, packet.cell, incoming)) return incoming;
            ItemStack remainder = incoming.copy();
            ItemStack current = player.getInventory().getItem(packet.cell);
            if (current.isEmpty()) {
                int move = Math.min(remainder.getCount(),
                    Math.min(remainder.getMaxStackSize(), player.getInventory().getMaxStackSize()));
                player.getInventory().setItem(packet.cell, remainder.copyWithCount(move));
                remainder.shrink(move);
            } else if (ItemStack.isSameItemSameComponents(current, remainder)) {
                int move = Math.min(remainder.getCount(),
                    Math.min(current.getMaxStackSize(), player.getInventory().getMaxStackSize())
                        - current.getCount());
                if (move > 0) {
                    current.grow(move);
                    remainder.shrink(move);
                }
            }
            return remainder;
        });
        if (moved <= 0) return;
        player.getInventory().setChanged();
        player.inventoryMenu.broadcastChanges();
        if (player.containerMenu != player.inventoryMenu) {
            player.containerMenu.broadcastChanges();
        }
        player.playNotifySound(
            SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.2F, 2.0F);
    }
    private static ItemStack insertIntoGrid(GridBackingStore store, int width,
                                            int cell, ItemStack incoming,
                                            boolean rotated) {
        ItemStack remainder = incoming.copy();
        int x = cell % width;
        int y = cell / width;
        if (store.canStackAt(x, y, remainder)) {
            store.stackInto(x, y, remainder);
        }
        if (remainder.isEmpty()) return ItemStack.EMPTY;

        if (store.canPlace(x, y, remainder, rotated, Set.of())
            && store.place(x, y, remainder, rotated)) {
            return ItemStack.EMPTY;
        }
        return remainder;
    }

    private static int processEntities(ServerPlayer player, List<Integer> entityIds,
                                       StorageInserter inserter) {
        int movedTotal = 0;
        Set<Integer> visited = new HashSet<>();
        for (int entityId : entityIds) {
            if (!visited.add(entityId)) continue;
            Entity entity = player.level().getEntity(entityId);
            if (!(entity instanceof ItemEntity itemEntity)
                || !itemEntity.isAlive()
                || player.distanceToSqr(itemEntity) >= MAX_DISTANCE_SQUARED) {
                continue;
            }

            ItemStack template = itemEntity.getItem().copy();
            if (template.isEmpty()) continue;
            int remainingTotal = template.getCount() + betterLootingExtraCount(itemEntity);
            int movedFromEntity = 0;
            while (remainingTotal > 0) {
                int offered = Math.min(remainingTotal, template.getMaxStackSize());
                ItemStack remainder = inserter.insert(template.copyWithCount(offered));
                int moved = offered - remainder.getCount();
                if (moved <= 0) break;
                remainingTotal -= moved;
                movedFromEntity += moved;
            }
            if (movedFromEntity <= 0) continue;

            movedTotal += movedFromEntity;
            player.awardStat(
                Stats.ITEM_PICKED_UP.get(template.getItem()), movedFromEntity);
            player.take(itemEntity, movedFromEntity);
            updateGroundEntity(itemEntity, template, remainingTotal);
        }
        return movedTotal;
    }

    private static void updateGroundEntity(ItemEntity entity, ItemStack template,
                                           int remainingTotal) {
        if (remainingTotal <= 0) {
            entity.discard();
            return;
        }
        int visible = Math.min(remainingTotal, template.getMaxStackSize());
        entity.setItem(template.copyWithCount(visible));
        betterLootingSetExtraCount(entity, remainingTotal - visible);
    }

    private static int betterLootingExtraCount(ItemEntity entity) {
        try {
            Method method = entity.getClass().getMethod(
                "betterlooting$getExtraCount");
            Object value = method.invoke(entity);
            return value instanceof Number number
                ? Math.max(0, number.intValue()) : 0;
        } catch (ReflectiveOperationException ignored) {
            return 0;
        }
    }

    private static void betterLootingSetExtraCount(ItemEntity entity, int count) {
        try {
            Method method = entity.getClass().getMethod(
                "betterlooting$setExtraCount", int.class);
            method.invoke(entity, Math.max(0, count));
        } catch (ReflectiveOperationException ignored) {
        }
    }

    @FunctionalInterface
    private interface StorageInserter {
        ItemStack insert(ItemStack incoming);
    }
}
