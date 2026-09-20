package com.xtdpotato.xero_delta.grid;

import com.xtdpotato.xero_delta.ModDataComponents;
import com.xtdpotato.xero_delta.data.ModDataStorage;
import com.xtdpotato.xero_delta.data.ServerGridCarryState;
import com.xtdpotato.xero_delta.item.DeltaPackItem;
import com.xtdpotato.xero_delta.trading.TradingInventorySources;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Transactional evacuation used before an equipped safety box is replaced. */
public final class SafetyBoxTransferService {
    private SafetyBoxTransferService() {}

    public record Result(boolean success, int storedStacks) {}

    public static Result evacuate(ServerPlayer player, ItemStack boxStack) {
        return tryEvacuate(player, boxStack);
    }

    public static Result tryEvacuate(ServerPlayer player, ItemStack boxStack) {
        if (player == null || boxStack == null || boxStack.isEmpty()) {
            return new Result(true, 0);
        }
        List<ItemStack> contents = copyContents(boxStack);
        UUID boxId = boxStack.get(ModDataComponents.BOX_UUID.get());
        ServerGridCarryState.SafetyBoxOrigin origin = ServerGridCarryState.safetyBoxOrigin(player);
        boolean carriedFromBox = origin != null && Objects.equals(origin.boxId(), boxId);
        if (carriedFromBox) {
            ItemStack carried = player.containerMenu.getCarried();
            if (!carried.isEmpty()) contents.add(carried.copy());
        }
        if (contents.isEmpty()) {
            clearContents(boxStack);
            if (carriedFromBox) {
                player.containerMenu.setCarried(ItemStack.EMPTY);
                ServerGridCarryState.clearSafetyBox(player);
            }
            return new Result(true, 0);
        }

        Snapshot snapshot = Snapshot.capture(player);
        int stored = 0;
        for (ItemStack content : contents) {
            if (content == null || content.isEmpty()) continue;
            ItemStack remaining = content.copy();
            storeInPlayerStorage(player, remaining, boxStack);
            if (!remaining.isEmpty()) {
                snapshot.restore(player);
                sync(player);
                return new Result(false, stored);
            }
            stored++;
        }

        clearContents(boxStack);
        if (carriedFromBox) {
            player.containerMenu.setCarried(ItemStack.EMPTY);
            ServerGridCarryState.clearSafetyBox(player);
        } else if (origin != null) {
            ServerGridCarryState.clearSafetyBox(player);
        }
        sync(player);
        return new Result(true, stored);
    }

    private static List<ItemStack> copyContents(ItemStack boxStack) {
        List<ItemStack> result = new ArrayList<>();
        List<ItemStack> primary = boxStack.getOrDefault(
            ModDataComponents.GRID_CONTENTS.get(), List.of());
        for (ItemStack stack : primary) {
            if (stack != null && !stack.isEmpty()) result.add(stack.copy());
        }
        @SuppressWarnings("unchecked")
        List<List<ItemStack>> containers = (List<List<ItemStack>>) (List<?>)
            boxStack.getOrDefault(ModDataComponents.GRID_CONTAINERS.get(), List.of());
        for (List<ItemStack> container : containers) {
            for (ItemStack stack : container) {
                if (stack != null && !stack.isEmpty()) result.add(stack.copy());
            }
        }
        return result;
    }

    private static void clearContents(ItemStack boxStack) {
        boxStack.set(ModDataComponents.GRID_CONTENTS.get(), List.of());
        boxStack.set(ModDataComponents.GRID_CONTAINERS.get(), List.of());
    }

    private static void storeInPlayerStorage(ServerPlayer player, ItemStack remaining,
                                             ItemStack excludedBox) {
        ContainerGridHelper.transferIntoPlayerInventory(player.inventoryMenu, remaining, true,
            ModDataStorage::getCachedSizeFor);
        if (remaining.isEmpty()) return;
        insertIntoDeltaPacks(player, remaining, excludedBox);
        if (remaining.isEmpty()) return;
        TradingInventorySources.insertIntoBackpacks(player, remaining, excludedBox);
    }

    private static void insertIntoDeltaPacks(ServerPlayer player, ItemStack remaining,
                                             ItemStack excludedBox) {
        Set<ItemStack> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        for (ItemStack carrier : player.getInventory().items) {
            insertIntoDeltaPack(carrier, remaining, excludedBox, visited);
            if (remaining.isEmpty()) return;
        }
        CuriosApi.getCuriosInventory(player).ifPresent(curios -> {
            for (var result : curios.findCurios(stack -> !stack.isEmpty())) {
                if (remaining.isEmpty()) break;
                insertIntoDeltaPack(result.stack(), remaining, excludedBox, visited);
            }
        });
    }

    private static void insertIntoDeltaPack(ItemStack carrier, ItemStack remaining,
                                            ItemStack excludedBox, Set<ItemStack> visited) {
        if (remaining.isEmpty() || carrier == null || carrier.isEmpty()
            || carrier == excludedBox || !visited.add(carrier)
            || !(carrier.getItem() instanceof DeltaPackItem pack)) return;
        GridBackingStore store = new GridBackingStore(
            carrier, pack.gridWidth(), pack.gridHeight(), 0,
            stack -> GridBackingStore.isBlockedInEquippedStorage(
                pack.slotIdentifier(), stack));
        for (int index = 0; index < store.getSize() && !remaining.isEmpty(); index++) {
            int x = index % store.getWidth();
            int y = index / store.getWidth();
            if (store.canStackAt(x, y, remaining)) store.stackInto(x, y, remaining);
        }
        for (DeltaPackItem.GridRegion region : pack.regions()) {
            if (remaining.isEmpty()) break;
            GridBackingStore.PlacementResult placement = store.findPlacementWithin(
                remaining, GridBackingStore.isRotated(remaining),
                region.x(), region.y(), region.x() + region.width(), region.y() + region.height());
            if (placement.status() == GridBackingStore.PlacementStatus.CAN_PLACE
                && store.place(placement.x(), placement.y(), remaining, placement.rotated())) {
                remaining.setCount(0);
            }
        }
    }

    private static void sync(ServerPlayer player) {
        player.getInventory().setChanged();
        player.inventoryMenu.broadcastChanges();
        if (player.containerMenu != player.inventoryMenu) {
            player.containerMenu.broadcastChanges();
        }
    }

    private record CurioSnapshot(String identifier, int index, ItemStack stack) {}

    private record Snapshot(List<ItemStack> inventory, List<CurioSnapshot> curios) {
        static Snapshot capture(ServerPlayer player) {
            List<ItemStack> inventory = new ArrayList<>(
                player.getInventory().getContainerSize());
            for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
                inventory.add(player.getInventory().getItem(slot).copy());
            }
            List<CurioSnapshot> curios = new ArrayList<>();
            CuriosApi.getCuriosInventory(player).ifPresent(handler -> {
                for (var result : handler.findCurios(stack -> !stack.isEmpty())) {
                    curios.add(new CurioSnapshot(result.slotContext().identifier(),
                        result.slotContext().index(), result.stack().copy()));
                }
            });
            return new Snapshot(inventory, curios);
        }

        void restore(ServerPlayer player) {
            for (int slot = 0; slot < inventory.size(); slot++) {
                player.getInventory().setItem(slot, inventory.get(slot).copy());
            }
            CuriosApi.getCuriosInventory(player).ifPresent(handler -> {
                for (CurioSnapshot snapshot : curios) {
                    handler.setEquippedCurio(snapshot.identifier, snapshot.index,
                        snapshot.stack.copy());
                    handler.getStacksHandler(snapshot.identifier)
                        .ifPresent(stacks -> stacks.update());
                }
            });
        }
    }
}
