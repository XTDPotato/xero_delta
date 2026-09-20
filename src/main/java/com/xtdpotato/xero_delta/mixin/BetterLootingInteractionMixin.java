package com.xtdpotato.xero_delta.mixin;

import com.xtdpotato.xero_delta.network.BetterLootingStorageDropPacket;
import com.xtdpotato.xero_delta.network.ModNetwork;
import com.xtdpotato.xero_delta.screen.PlayerStatusScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Extends Better Looting's drag release hit-test to Delta's virtual storage
 * cells. Reflection keeps Better Looting an optional runtime dependency.
 */
@Pseudo
@Mixin(targets = "com.mohuia.better_looting.client.inventory.LootListInteraction",
    remap = false)
public abstract class BetterLootingInteractionMixin {
    @Shadow(remap = false) private int dragIndex;
    @Shadow(remap = false) private double dragCurrentX;
    @Shadow(remap = false) private double dragCurrentY;
    @Shadow(remap = false) private boolean dragModeActive;
    @Shadow(remap = false) private boolean isDraggingItem;

    @Inject(method = "onItemRelease", at = @At("HEAD"),
        cancellable = true, require = 0, remap = false)
    private void xero$releaseIntoDeltaStorage(
        InventoryScreen screen, CallbackInfo callback) {
        if (!isDraggingItem || !dragModeActive
            || !(screen instanceof PlayerStatusScreen statusScreen)) {
            return;
        }
        List<Integer> entityIds = xero$draggedEntityIds(dragIndex);
        if (entityIds.isEmpty()) return;
        ItemStack dragged = xero$draggedStack(entityIds);
        PlayerStatusScreen.BetterLootingDropTarget target =
            statusScreen.betterLootingDropTargetAt(
                dragCurrentX, dragCurrentY, dragged);
        if (target == null) return;

        ModNetwork.sendToServer(new BetterLootingStorageDropPacket(
            entityIds, target.identifier(), target.cell(), target.rotated()));

        isDraggingItem = false;
        dragIndex = -1;
        dragModeActive = false;
        callback.cancel();
    }

    @Unique
    private static ItemStack xero$draggedStack(List<Integer> entityIds) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return ItemStack.EMPTY;
        for (int entityId : entityIds) {
            if (minecraft.level.getEntity(entityId) instanceof ItemEntity item
                && item.isAlive() && !item.getItem().isEmpty()) {
                return item.getItem().copy();
            }
        }
        return ItemStack.EMPTY;
    }
    @Unique
    private static List<Integer> xero$draggedEntityIds(int index) {
        if (index < 0) return List.of();
        try {
            Class<?> listType = Class.forName(
                "com.mohuia.better_looting.client.inventory.InventoryLootList");
            Object list = listType.getField("INSTANCE").get(null);
            Field nearbyItemsField = listType.getDeclaredField("nearbyItems");
            nearbyItemsField.setAccessible(true);
            Object value = nearbyItemsField.get(list);
            if (!(value instanceof List<?> entries) || index >= entries.size()) {
                return List.of();
            }
            Object entry = entries.get(index);
            Method sourceEntities = entry.getClass().getMethod(
                "getSourceEntities");
            Object sources = sourceEntities.invoke(entry);
            if (!(sources instanceof List<?> entities)) return List.of();

            Set<Integer> ids = new LinkedHashSet<>();
            for (Object source : entities) {
                if (source instanceof ItemEntity item && item.isAlive()) {
                    ids.add(item.getId());
                    if (ids.size() >= 256) break;
                }
            }
            return new ArrayList<>(ids);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return List.of();
        }
    }
}
