package com.xtdpotato.xero_delta.data;

import com.xtdpotato.xero_delta.ModDataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotResult;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Calculates carried weight without adding the backpack body's automatic weight. */
public final class PlayerWeightCalculator {
    private static final int MAX_NESTING = 6;
    private static final double MAX_WEIGHT = 1_000_000.0D;

    private PlayerWeightCalculator() {
    }

    public static Snapshot calculate(ServerPlayer player) {
        Set<ItemStack> roots = Collections.newSetFromMap(new IdentityHashMap<>());
        double kilograms = 0.0D;
        double carrierPenalty = 0.0D;

        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.isEmpty() || !roots.add(stack)) continue;
            kilograms += stackWeight(stack, 0);
        }

        try {
            var curios = CuriosApi.getCuriosInventory(player);
            if (curios.isPresent()) {
                for (SlotResult result : curios.get().findCurios(stack -> !stack.isEmpty())) {
                    ItemStack stack = result.stack();
                    if (stack.isEmpty() || !roots.add(stack)) continue;
                    kilograms += stackWeight(stack, 0);
                    carrierPenalty += equippedCarrierPenalty(stack);
                }
            }
        } catch (RuntimeException ignored) {
        }

        return new Snapshot(round(Math.min(MAX_WEIGHT, kilograms)),
            Math.min(0.25D, round(carrierPenalty)));
    }

    private static double stackWeight(ItemStack stack, int depth) {
        if (stack == null || stack.isEmpty() || depth > MAX_NESTING) return 0.0D;
        double total = ServerItemWeights.getWeightFor(stack) * Math.max(1, stack.getCount());
        if (depth == MAX_NESTING) return total;

        boolean componentStorage = false;
        List<ItemStack> grid = stack.getOrDefault(ModDataComponents.GRID_CONTENTS.get(), List.of());
        if (!grid.isEmpty()) {
            componentStorage = true;
            for (ItemStack child : grid) total += stackWeight(child, depth + 1);
        }
        List<List<ItemStack>> containers =
            stack.getOrDefault(ModDataComponents.GRID_CONTAINERS.get(), List.of());
        if (!containers.isEmpty()) {
            componentStorage = true;
            for (List<ItemStack> container : containers) {
                for (ItemStack child : container) total += stackWeight(child, depth + 1);
            }
        }
        if (componentStorage) return Math.min(MAX_WEIGHT, total);

        try {
            IItemHandler handler = stack.getCapability(Capabilities.ItemHandler.ITEM);
            if (handler != null) {
                for (int slot = 0; slot < handler.getSlots(); slot++) {
                    total += stackWeight(handler.getStackInSlot(slot), depth + 1);
                    if (total >= MAX_WEIGHT) return MAX_WEIGHT;
                }
            }
        } catch (RuntimeException ignored) {
        }
        return Math.min(MAX_WEIGHT, total);
    }

    private static double equippedCarrierPenalty(ItemStack stack) {
        if (stack.getItem() instanceof com.xtdpotato.xero_delta.item.DeltaPackItem pack) {
            return pack.speedPenalty();
        }
        if (!AutomaticItemWeight.isBackpackLike(stack)) return 0.0D;
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().toLowerCase(Locale.ROOT);
        if (id.contains("diamond") || id.contains("netherite")) return 0.02D;
        if (id.contains("iron") || id.contains("gold")) return 0.01D;
        return switch (ServerItemRules.getQualityFor(stack)) {
            case "blue", "gold" -> 0.01D;
            case "purple", "red" -> 0.02D;
            default -> 0.0D;
        };
    }

    private static double round(double value) {
        return Math.round(value * 1000.0D) / 1000.0D;
    }

    public record Snapshot(double kilograms, double carrierSpeedPenalty) {
    }
}
