package com.xtdpotato.xero_delta.trading;

import com.xtdpotato.xero_delta.ModDataComponents;
import com.xtdpotato.xero_delta.data.MedicalUseManager;
import com.xtdpotato.xero_delta.data.PersonalWarehouseData;
import com.xtdpotato.xero_delta.data.WarehouseCategory;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import top.theillusivec4.curios.api.CuriosApi;
import java.util.*;

/** Counts ownership across storage locations, so moving an item does not create supply. */
final class RecipeOwnedInventory {
    final Map<String, Long> counts = new LinkedHashMap<>();
    final Map<String, ItemStack> samples = new HashMap<>();
    private final Set<ItemStack> visited = Collections.newSetFromMap(new IdentityHashMap<>());
    private int scanned;

    void add(ItemStack stack) { add(stack, 0); }

    static RecipeOwnedInventory capture(ServerPlayer player) {
        RecipeOwnedInventory snapshot = new RecipeOwnedInventory();
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++)
            snapshot.add(player.getInventory().getItem(slot), 0);
        CuriosApi.getCuriosInventory(player).ifPresent(curios -> {
            for (var slot : curios.findCurios(stack -> !stack.isEmpty())) snapshot.add(slot.stack(), 0);
        });
        for (ItemStack carrier : TradingOptionalBackpackIntegrations.accessoryStacks(player))
            snapshot.add(carrier, 0);
        snapshot.add(TradingOptionalBackpackIntegrations.travelersAttachmentStack(player), 0);
        snapshot.add(player.containerMenu.getCarried(), 0);
        // Crafting inputs still belong to the player. The result slot is only a preview.
        for (var slot : player.containerMenu.slots)
            if (slot.container instanceof CraftingContainer) snapshot.add(slot.getItem(), 0);
        if (player.containerMenu != player.inventoryMenu)
            for (var slot : player.inventoryMenu.slots)
                if (slot.container instanceof CraftingContainer) snapshot.add(slot.getItem(), 0);
        var warehouse = PersonalWarehouseData.get(player.server).warehouse(player.getUUID());
        for (WarehouseCategory category : WarehouseCategory.values())
            for (ItemStack stack : warehouse.bin(category).items()) snapshot.add(stack, 0);
        snapshot.add(MedicalUseManager.reservedFor(player), 0);
        for (ItemStack escrow : TradingMarketData.get(player.server).escrowFor(player.getUUID()))
            snapshot.add(escrow, 0);
        return snapshot;
    }

    private void add(ItemStack stack, int depth) {
        if (stack == null || stack.isEmpty() || !visited.add(stack)) return;
        if (depth > 32 || ++scanned > 200_000)
            throw new IllegalStateException("Inventory graph exceeds safe traversal bounds");
        boolean grid = stack.has(ModDataComponents.GRID_CONTENTS.get())
            || stack.has(ModDataComponents.GRID_CONTAINERS.get());
        var handler = grid ? null : stack.getCapability(Capabilities.ItemHandler.ITEM);
        ItemStack sample = grid || handler != null || stack.has(DataComponents.CONTAINER)
            ? stack.getItem().getDefaultInstance() : stack.copyWithCount(1);
        String key = RecipeSupplyIndex.key(sample);
        counts.merge(key, (long) stack.getCount(), RecipeWorldMarketData::saturatedAdd);
        samples.putIfAbsent(key, sample);
        if (grid) {
            for (ItemStack child : stack.getOrDefault(ModDataComponents.GRID_CONTENTS.get(), List.<ItemStack>of()))
                add(child, depth + 1);
            for (var container : stack.getOrDefault(ModDataComponents.GRID_CONTAINERS.get(), List.<List<ItemStack>>of()))
                for (ItemStack child : container) add(child, depth + 1);
        } else if (stack.has(DataComponents.CONTAINER)) {
            stack.get(DataComponents.CONTAINER).stream().forEach(child -> add(child, depth + 1));
        } else if (handler != null) {
            if (handler.getSlots() > 200_000) throw new IllegalStateException("Invalid item inventory size");
            for (int slot = 0; slot < handler.getSlots(); slot++) add(handler.getStackInSlot(slot), depth + 1);
        }
        if (stack.has(DataComponents.BUNDLE_CONTENTS))
            for (ItemStack child : stack.get(DataComponents.BUNDLE_CONTENTS).items()) add(child, depth + 1);
    }
}
