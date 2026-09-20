package com.xtdpotato.xero_delta.data;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

public final class ServerGridCarryState {
    public record SafetyBoxOrigin(UUID boxId, int containerIndex, int x, int y,
                                  ItemSize footprint, boolean rotated) {
    }

    public record ContainerOrigin(AbstractContainerMenu menu, int slotIndex, ItemSize footprint, boolean rotated) {
    }

    public record EquippedStorageOrigin(String identifier, int x, int y,
                                        ItemSize footprint, boolean rotated) {
    }

    private static final Map<ServerPlayer, SafetyBoxOrigin> SAFETY_BOX_ORIGINS =
        Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<ServerPlayer, ContainerOrigin> CONTAINER_ORIGINS =
        Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<ServerPlayer, EquippedStorageOrigin> EQUIPPED_STORAGE_ORIGINS =
        Collections.synchronizedMap(new WeakHashMap<>());

    private ServerGridCarryState() {
    }

    public static void rememberSafetyBox(ServerPlayer player, UUID boxId, int containerIndex,
                                         int x, int y, ItemSize footprint, boolean rotated) {
        CONTAINER_ORIGINS.remove(player);
        EQUIPPED_STORAGE_ORIGINS.remove(player);
        SAFETY_BOX_ORIGINS.put(player,
            new SafetyBoxOrigin(boxId, containerIndex, x, y, footprint, rotated));
    }

    public static SafetyBoxOrigin safetyBoxOrigin(ServerPlayer player) {
        return SAFETY_BOX_ORIGINS.get(player);
    }

    public static void clearSafetyBox(ServerPlayer player) {
        SAFETY_BOX_ORIGINS.remove(player);
    }

    public static void rememberContainer(ServerPlayer player, AbstractContainerMenu menu, int slotIndex,
                                         ItemSize footprint, boolean rotated) {
        SAFETY_BOX_ORIGINS.remove(player);
        EQUIPPED_STORAGE_ORIGINS.remove(player);
        CONTAINER_ORIGINS.put(player, new ContainerOrigin(menu, slotIndex, footprint, rotated));
    }

    public static ContainerOrigin containerOrigin(ServerPlayer player) {
        return CONTAINER_ORIGINS.get(player);
    }

    public static void clearContainer(ServerPlayer player) {
        CONTAINER_ORIGINS.remove(player);
    }

    public static void rememberEquippedStorage(ServerPlayer player, String identifier,
                                               int x, int y, ItemSize footprint,
                                               boolean rotated) {
        SAFETY_BOX_ORIGINS.remove(player);
        CONTAINER_ORIGINS.remove(player);
        EQUIPPED_STORAGE_ORIGINS.put(player,
            new EquippedStorageOrigin(identifier, x, y, footprint, rotated));
    }

    public static EquippedStorageOrigin equippedStorageOrigin(ServerPlayer player) {
        return EQUIPPED_STORAGE_ORIGINS.get(player);
    }

    public static void clearEquippedStorage(ServerPlayer player) {
        EQUIPPED_STORAGE_ORIGINS.remove(player);
    }

    public static void clearAll(ServerPlayer player) {
        SAFETY_BOX_ORIGINS.remove(player);
        CONTAINER_ORIGINS.remove(player);
        EQUIPPED_STORAGE_ORIGINS.remove(player);
    }
}
