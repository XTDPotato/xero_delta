package com.xtdpotato.xero_delta.mixin;

import com.xtdpotato.xero_delta.screen.PlayerStatusScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

/** Moves Better Looting's InventoryScreen pickup list to the right of Xero's character canvas. */
@Pseudo
@Mixin(targets = "com.mohuia.better_looting.client.inventory.InventoryLootList", remap = false)
public abstract class BetterLootingInventoryLootListMixin {
    @Shadow(remap = false) private int cachedTopPos;
    @Shadow(remap = false) private List<?> nearbyItems;

    @ModifyConstant(method = "render", constant = @Constant(intValue = 100),
        require = 0, remap = false)
    private int xero$moveInventoryLootListToRight(int original) {
        if (Minecraft.getInstance().screen instanceof PlayerStatusScreen screen) {
            return screen.betterLootingPanelOffsetConstant();
        }
        return original;
    }

    @ModifyArg(method = "render", at = @At(value = "INVOKE",
        target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(FFF)V", ordinal = 0),
        index = 1, require = 0, remap = false)
    private float xero$alignLootListWithChestRig(float original) {
        if (Minecraft.getInstance().screen instanceof PlayerStatusScreen screen) {
            cachedTopPos = screen.betterLootingPanelTop();
            return cachedTopPos;
        }
        return original;
    }

    @Inject(method = "render", at = @At("TAIL"), require = 0, remap = false)
    private void xero$renderDeltaPlacementPreview(GuiGraphics graphics,
                                                  InventoryScreen inventory,
                                                  int mouseX, int mouseY,
                                                  CallbackInfo ci) {
        if (!(inventory instanceof PlayerStatusScreen screen)) return;
        ItemStack stack = xero$draggedStack();
        if (!stack.isEmpty()) {
            screen.renderBetterLootingPlacementPreview(graphics, stack, mouseX, mouseY);
        }
    }

    private ItemStack xero$draggedStack() {
        try {
            Class<?> interactionType = Class.forName(
                "com.mohuia.better_looting.client.inventory.LootListInteraction");
            Field instanceField = interactionType.getField("INSTANCE");
            Object interaction = instanceField.get(null);
            Method draggingMethod = interactionType.getMethod("isDraggingItem");
            if (!Boolean.TRUE.equals(draggingMethod.invoke(interaction))) return ItemStack.EMPTY;
            Field dragIndexField = interactionType.getDeclaredField("dragIndex");
            dragIndexField.setAccessible(true);
            int index = dragIndexField.getInt(interaction);
            if (index < 0 || index >= nearbyItems.size()) return ItemStack.EMPTY;
            Object entry = nearbyItems.get(index);
            Object value = entry.getClass().getMethod("getItem").invoke(entry);
            return value instanceof ItemStack stack ? stack : ItemStack.EMPTY;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return ItemStack.EMPTY;
        }
    }
}
