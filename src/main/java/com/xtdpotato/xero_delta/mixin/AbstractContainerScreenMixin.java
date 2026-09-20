package com.xtdpotato.xero_delta.mixin;

import com.xtdpotato.xero_delta.api.GridWidgetAccessor;
import com.xtdpotato.xero_delta.client.ClientDataCache;
import com.xtdpotato.xero_delta.client.ClientGridRotation;
import com.xtdpotato.xero_delta.client.ContainerGridClickHandler;
import com.xtdpotato.xero_delta.client.ContainerGridRenderBridge;
import com.xtdpotato.xero_delta.client.DeltaContainerLayoutController;
import com.xtdpotato.xero_delta.client.GridItemRenderer;
import com.xtdpotato.xero_delta.client.LootSearchClientState;
import com.xtdpotato.xero_delta.client.LootSearchOverlay;
import com.xtdpotato.xero_delta.client.QualityItemBackground;
import com.xtdpotato.xero_delta.data.ItemSize;
import com.xtdpotato.xero_delta.grid.ContainerGridHelper;
import com.xtdpotato.xero_delta.grid.GridBackingStore;
import com.xtdpotato.xero_delta.grid.GridWidget;
import com.xtdpotato.xero_delta.screen.CorpseScreen;
import com.xtdpotato.xero_delta.menu.CorpseMenu;
import com.xtdpotato.xero_delta.screen.GroundPackScreen;
import com.xtdpotato.xero_delta.screen.PersonalWarehouseScreen;
import com.xtdpotato.xero_delta.screen.PlayerStatusScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.lwjgl.glfw.GLFW;

import java.util.Set;

@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin implements GridWidgetAccessor {

    @Unique public GridWidget deltaSafetyBox$gridWidget;
    @Shadow protected int leftPos;
    @Shadow protected int topPos;
    @Shadow protected int inventoryLabelY;
    @Unique private int xero$originalInventoryLabelY;
    @Unique private boolean xero$inventoryLabelCaptured;
    @Shadow protected abstract void slotClicked(Slot slot, int slotId, int mouseButton, ClickType type);

    @Override public GridWidget deltaSafetyBox$getGridWidget() { return deltaSafetyBox$gridWidget; }

    @Inject(method = "init", at = @At("TAIL"))
    private void xero$positionNativeContainer(CallbackInfo ci) {
        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) (Object) this;
        int[] position = DeltaContainerLayoutController.position(screen, leftPos, topPos);
        leftPos = position[0];
        topPos = position[1];
        xero$updateInventoryLabel(screen);
    }

    @Inject(method = "removed", at = @At("HEAD"))
    private void onRemoved(CallbackInfo ci) {
        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) (Object) this;
        DeltaContainerLayoutController.restore(screen);
        ContainerGridHelper.invalidate(screen.getMenu());
        deltaSafetyBox$gridWidget = null;
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void xero$prepareContainerGrid(GuiGraphics graphics, int mouseX, int mouseY,
                                           float partialTick, CallbackInfo ci) {
        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) (Object) this;
        int[] position = DeltaContainerLayoutController.position(screen, leftPos, topPos);
        leftPos = position[0];
        topPos = position[1];
        DeltaContainerLayoutController.positionRecipeBookButton(screen);
        DeltaContainerLayoutController.prepare(screen, leftPos, topPos);
        xero$updateInventoryLabel(screen);
        DeltaContainerLayoutController.beginRender(screen, graphics);
        AbstractContainerMenu menu = screen.getMenu();
        if (ClientDataCache.INSTANCE.isItemGridEnabled(menu.getClass().getName())
            || LootSearchClientState.INSTANCE.isActiveFor(menu.containerId)) {
            ContainerGridHelper.prepare(menu, ClientDataCache.INSTANCE::getSize,
                ClientDataCache.INSTANCE.revision());
        }
    }

    @ModifyVariable(method = "render", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private int xero$scaledRenderMouseX(int mouseX) {
        return (int) Math.round(DeltaContainerLayoutController.nativeMouseX(
            (AbstractContainerScreen<?>) (Object) this, mouseX));
    }

    @ModifyVariable(method = "render", at = @At("HEAD"), argsOnly = true, ordinal = 1)
    private int xero$scaledRenderMouseY(int mouseY) {
        return (int) Math.round(DeltaContainerLayoutController.nativeMouseY(
            (AbstractContainerScreen<?>) (Object) this, mouseY));
    }

    @Inject(method = "renderBackground", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;renderBg(Lnet/minecraft/client/gui/GuiGraphics;FII)V"))
    private void xero$beginNativeContainerLayer(GuiGraphics graphics, int mouseX, int mouseY,
                                                float partialTick, CallbackInfo ci) {
        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) (Object) this;
        DeltaContainerLayoutController.renderPanels(screen, graphics, mouseX, mouseY);
        DeltaContainerLayoutController.beginNativeContainerLayer(screen, graphics);
    }

    @Inject(method = "renderBackground", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;renderBg(Lnet/minecraft/client/gui/GuiGraphics;FII)V",
        shift = At.Shift.AFTER))
    private void xero$endNativeContainerLayer(GuiGraphics graphics, int mouseX, int mouseY,
                                              float partialTick, CallbackInfo ci) {
        DeltaContainerLayoutController.endNativeContainerLayer(
            (AbstractContainerScreen<?>) (Object) this, graphics);
    }

    @Inject(method = "renderSlot", at = @At("HEAD"), cancellable = true)
    private void xero$renderSlotSizeFootprint(GuiGraphics graphics, Slot slot, CallbackInfo ci) {
        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) (Object) this;
        AbstractContainerMenu menu = screen.getMenu();
        if (screen instanceof CorpseScreen corpse
            && corpse.renderCorpsePresentationSlot(graphics, slot)) {
            ci.cancel();
            return;
        }
        if (LootSearchOverlay.renderHiddenSlot(screen, graphics, slot)) {
            ci.cancel();
            return;
        }
        if (DeltaContainerLayoutController.renderPlayerSlot(screen, graphics, slot)) {
            ci.cancel();
            return;
        }
        if (screen instanceof GroundPackScreen ground
            && ground.renderGroundPackSlot(graphics, slot)) {
            ci.cancel();
            return;
        }
        if (!ClientDataCache.INSTANCE.isItemGridEnabled(menu.getClass().getName())) return;
        if (!ContainerGridHelper.isGridSlotEnabled(menu, slot)) return;
        Slot coveringAnchor = ContainerGridHelper.footprintAnchorFor(menu, slot, ClientDataCache.INSTANCE::getSize);
        if (coveringAnchor != null && coveringAnchor != slot) {
            // A stale/third-party storage snapshot can contain a stack in a
            // covered cell. The anchor owns that footprint; do not draw the
            // covered stack on top of it while the server normalizes it.
            ci.cancel();
            return;
        }
        ItemStack stack = slot.getItem();
        if (stack.isEmpty()) return;
        boolean rotated = GridBackingStore.isRotated(stack);
        ItemSize size = ContainerGridHelper.orientedSize(stack, rotated, ClientDataCache.INSTANCE::getSize);
        if (size.width() <= 1 && size.height() <= 1) return;
        if (!ContainerGridHelper.isAnchorSlot(menu, slot, ClientDataCache.INSTANCE::getSize)) return;
        if (screen instanceof PersonalWarehouseScreen warehouse
            && warehouse.renderSizedWarehouseItem(graphics, slot, stack, size, rotated)) {
            ci.cancel();
            return;
        }
        Set<Slot> cells = ContainerGridHelper.footprintCells(menu, slot, size);
        if (cells.size() < size.width() * size.height()) return;
        int x1 = Integer.MAX_VALUE;
        int y1 = Integer.MAX_VALUE;
        int maxCellX = Integer.MIN_VALUE;
        int maxCellY = Integer.MIN_VALUE;
        for (Slot cell : cells) {
            x1 = Math.min(x1, cell.x);
            y1 = Math.min(y1, cell.y);
            maxCellX = Math.max(maxCellX, cell.x);
            maxCellY = Math.max(maxCellY, cell.y);
        }
        for (Slot cell : cells) {
            int cellRight = cell.x + (cell.x == maxCellX
                ? ContainerGridHelper.SLOT_FACE_SIZE : ContainerGridHelper.SLOT_STEP);
            int cellBottom = cell.y + (cell.y == maxCellY
                ? ContainerGridHelper.SLOT_FACE_SIZE : ContainerGridHelper.SLOT_STEP);
            graphics.fill(cell.x, cell.y, cellRight, cellBottom, QualityItemBackground.color(stack));
        }
        GridItemRenderer.renderSizedItem(graphics, Minecraft.getInstance().font, stack, x1, y1,
            maxCellX + ContainerGridHelper.SLOT_FACE_SIZE - x1,
            maxCellY + ContainerGridHelper.SLOT_FACE_SIZE - y1,
            rotated, ClientDataCache.INSTANCE.shouldRotateTexture(stack),
            ClientDataCache.INSTANCE.shouldStretchTexture(stack),
            ClientDataCache.INSTANCE.proportionalTextureScale(stack));
        xero$renderExternalWeightBadge(screen, graphics, stack, x1, y1,
            maxCellX + ContainerGridHelper.SLOT_FACE_SIZE - x1,
            maxCellY + ContainerGridHelper.SLOT_FACE_SIZE - y1);
        ci.cancel();
    }

    @Inject(method = "renderSlotHighlight(Lnet/minecraft/client/gui/GuiGraphics;III)V",
        at = @At("HEAD"), cancellable = true)
    private static void xero$suppressDeltaPlayerSlotHighlight(GuiGraphics graphics,
                                                               int x, int y, int z,
                                                               CallbackInfo ci) {
        if (xero$shouldSuppressSlotHighlight(x, y)) ci.cancel();
    }

    @Inject(method = "renderSlotHighlight(Lnet/minecraft/client/gui/GuiGraphics;IIII)V",
        at = @At("HEAD"), cancellable = true)
    private static void xero$suppressColoredDeltaPlayerSlotHighlight(GuiGraphics graphics,
                                                                      int x, int y, int z,
                                                                      int color,
                                                                      CallbackInfo ci) {
        if (xero$shouldSuppressSlotHighlight(x, y)) ci.cancel();
    }

    private static boolean xero$shouldSuppressSlotHighlight(int x, int y) {
        if (Minecraft.getInstance().screen instanceof AbstractContainerScreen<?> screen
            && (screen instanceof PlayerStatusScreen status
                && status.suppressesVanillaSlotHighlight(x, y)
                || DeltaContainerLayoutController.suppressesVanillaSlotHighlight(screen, x, y))) {
            return true;
        }
        return false;
    }

    @Inject(method = "renderSlot", at = @At("RETURN"))
    private void xero$renderExternalSlotWeight(GuiGraphics graphics, Slot slot,
                                                CallbackInfo ci) {
        if (slot == null || slot.container instanceof net.minecraft.world.entity.player.Inventory
            || slot.x < -500 || slot.y < -500) return;
        ItemStack stack = slot.getItem();
        if (stack.isEmpty()) return;
        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) (Object) this;
        if (screen instanceof CorpseScreen && slot.index <= CorpseMenu.BACKPACK_SLOT) return;
        AbstractContainerMenu menu = screen.getMenu();
        if (ClientDataCache.INSTANCE.isItemGridEnabled(menu.getClass().getName())
            && ContainerGridHelper.isGridSlotEnabled(menu, slot)) {
            Slot anchor = ContainerGridHelper.footprintAnchorFor(
                menu, slot, ClientDataCache.INSTANCE::getSize);
            if (anchor != null && anchor != slot) return;
            ItemSize size = ContainerGridHelper.orientedSize(
                stack, GridBackingStore.isRotated(stack), ClientDataCache.INSTANCE::getSize);
            if (size.width() > 1 || size.height() > 1) return;
        }
        if (screen instanceof PersonalWarehouseScreen warehouse) {
            warehouse.renderWarehouseItemWeightBadge(graphics, slot, stack, 18, 18);
        } else {
            xero$renderExternalWeightBadge(screen, graphics, stack,
                slot.x - 1, slot.y - 1, 18, 18);
        }
    }

    @Unique
    private static void xero$renderExternalWeightBadge(AbstractContainerScreen<?> screen,
                                                        GuiGraphics graphics, ItemStack stack,
                                                        int x, int y, int width, int height) {
        if (screen instanceof PlayerStatusScreen statusScreen) {
            statusScreen.renderExternalItemWeightBadge(graphics, stack, x, y, width, height);
        } else {
            DeltaContainerLayoutController.renderExternalItemWeightBadge(
                screen, graphics, stack, x, y, width, height);
        }
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void xero$renderContainerPlacementPreview(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) (Object) this;
        Minecraft minecraft = Minecraft.getInstance();
        double screenMouseX = minecraft.mouseHandler.xpos()
            * minecraft.getWindow().getGuiScaledWidth()
            / (double) minecraft.getWindow().getScreenWidth();
        double screenMouseY = minecraft.mouseHandler.ypos()
            * minecraft.getWindow().getGuiScaledHeight()
            / (double) minecraft.getWindow().getScreenHeight();
        int nativeMouseX = (int) Math.round(
            DeltaContainerLayoutController.nativeMouseX(screen, screenMouseX));
        int nativeMouseY = (int) Math.round(
            DeltaContainerLayoutController.nativeMouseY(screen, screenMouseY));
        LootSearchOverlay.handleHover(screen, nativeMouseX, nativeMouseY);
        if (LootSearchOverlay.hiddenAnchorAt(screen, nativeMouseX, nativeMouseY) == null) {
            ContainerGridRenderBridge.renderInteractionOverlay(
                screen, graphics, nativeMouseX, nativeMouseY);
        }
        DeltaContainerLayoutController.renderInteractionOverlay(
            screen, graphics, (int) Math.round(screenMouseX),
            (int) Math.round(screenMouseY));
        if (screen instanceof CorpseScreen corpse) {
            corpse.renderPresentationOverlay(graphics, nativeMouseX, nativeMouseY);
        }
        DeltaContainerLayoutController.endRender(screen, graphics);
    }

    /**
     * Mouse Tweaks and similar gesture mods query AbstractContainerScreen#findSlot
     * before deciding whether to emit a click. Covered cells are physically empty,
     * so remap them to the sized item's real anchor at the hit-test boundary.
     */
    @Inject(method = "findSlot", at = @At("RETURN"), cancellable = true)
    private void xero$mapGestureSlotToGridAnchor(double mouseX, double mouseY,
                                                 CallbackInfoReturnable<Slot> cir) {
        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) (Object) this;
        AbstractContainerMenu menu = screen.getMenu();
        if (screen instanceof CorpseScreen corpse) {
            Slot corpseHit = corpse.corpsePresentationSlotAt(
                mouseX - leftPos, mouseY - topPos);
            if (corpseHit != null) cir.setReturnValue(corpseHit);
        }
        Slot deltaPlayerHit = DeltaContainerLayoutController.playerSlotAt(
            screen, mouseX - leftPos, mouseY - topPos);
        if (deltaPlayerHit != null) cir.setReturnValue(deltaPlayerHit);
        boolean gridEnabled = ClientDataCache.INSTANCE.isItemGridEnabled(menu.getClass().getName());
        if (!gridEnabled && !LootSearchClientState.INSTANCE.isActiveFor(menu.containerId)) return;

        ContainerGridHelper.prepare(menu, ClientDataCache.INSTANCE::getSize,
            ClientDataCache.INSTANCE.revision());
        Slot hit = cir.getReturnValue();
        if (gridEnabled && hit != null && ContainerGridHelper.isGridSlotEnabled(menu, hit)) {
            Slot anchor = ContainerGridHelper.footprintAnchorFor(
                menu, hit, ClientDataCache.INSTANCE::getSize);
            if (anchor != null) cir.setReturnValue(anchor);
        }

        // Sized item rendering also owns the two-pixel gutters between vanilla slot faces.
        if (gridEnabled && cir.getReturnValue() == null) {
            Slot gutterAnchor = xero$footprintAnchorAt(menu, mouseX - leftPos, mouseY - topPos);
            if (gutterAnchor != null) cir.setReturnValue(gutterAnchor);
        }
        Slot resolved = cir.getReturnValue();
        if (resolved != null) {
            Slot anchor = ContainerGridHelper.footprintAnchorFor(
                menu, resolved, ClientDataCache.INSTANCE::getSize);
            if (anchor == null) anchor = resolved;
            if (LootSearchClientState.INSTANCE.isHidden(menu.containerId, anchor.index)) {
                cir.setReturnValue(null);
            }
        }
    }

    @Inject(method = "hasClickedOutside", at = @At("HEAD"), cancellable = true)
    private void xero$keepDeltaPanelInside(double mouseX, double mouseY,
                                           int left, int top, int button,
                                           CallbackInfoReturnable<Boolean> cir) {
        if (DeltaContainerLayoutController.isInsidePanel(
            (AbstractContainerScreen<?>) (Object) this, mouseX, mouseY)) {
            cir.setReturnValue(false);
        }
    }
    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void xero$blockOccupiedFootprintClicks(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) (Object) this;
        if (DeltaContainerLayoutController.mouseClicked(
            screen, mouseX, mouseY, button)) {
            cir.setReturnValue(true);
            return;
        }
        if ((button == 0 || button == 1)
            && DeltaContainerLayoutController.blocksOutsideDrop(
                screen, mouseX, mouseY)) {
            cir.setReturnValue(true);
            return;
        }
        AbstractContainerMenu menu = screen.getMenu();
        if (LootSearchOverlay.hiddenAnchorAt(screen, mouseX, mouseY) != null) {
            cir.setReturnValue(true);
            return;
        }
        if (!ClientDataCache.INSTANCE.isItemGridEnabled(menu.getClass().getName())) return;
        ContainerGridHelper.prepare(menu, ClientDataCache.INSTANCE::getSize,
            ClientDataCache.INSTANCE.revision());
        Slot clicked = xero$slotAt(mouseX, mouseY, menu);
        if (clicked == null) return;
        if (!ContainerGridHelper.isGridSlotEnabled(menu, clicked)) return;
        Slot occupiedBy = ContainerGridHelper.footprintAnchorFor(menu, clicked, ClientDataCache.INSTANCE::getSize);
        ItemStack carried = menu.getCarried();
        if (carried.isEmpty() && occupiedBy != null && occupiedBy != clicked) {
            if (button != 0 && button != 1) {
                cir.setReturnValue(true);
                return;
            }
            ClickType clickType = Screen.hasShiftDown() ? ClickType.QUICK_MOVE : ClickType.PICKUP;
            ContainerGridClickHandler.sendGridClick(menu, occupiedBy.index, button, clickType);
            cir.setReturnValue(true);
            return;
        }
        if (!carried.isEmpty()) {
            if (button == 1 && ContainerGridHelper.supportsCarriedContainerInteraction(carried)) {
                return;
            }
            ItemSize size = ClientDataCache.INSTANCE.getSize(carried);
            if (size.width() <= 1 && size.height() <= 1 && occupiedBy != null) {
                if (button != 0 && button != 1) {
                    cir.setReturnValue(true);
                    return;
                }
                ContainerGridClickHandler.sendGridClick(menu, occupiedBy.index, button, ClickType.PICKUP);
                cir.setReturnValue(true);
                return;
            }
            if (size.width() <= 1 && size.height() <= 1
                && !xero$isVanillaSlotFace(clicked, mouseX, mouseY)) {
                if (button != 0 && button != 1) {
                    cir.setReturnValue(true);
                    return;
                }
                ContainerGridClickHandler.sendGridClick(menu, clicked.index, button, ClickType.PICKUP);
                cir.setReturnValue(true);
                return;
            }
            if (size.width() > 1 || size.height() > 1) {
                if (button != 0 && button != 1) {
                    cir.setReturnValue(true);
                    return;
                }
                double fractionX = xero$slotFractionX(clicked, mouseX);
                double fractionY = xero$slotFractionY(clicked, mouseY);
                var placement = ContainerGridHelper.resolveCursorPlacement(menu, clicked, carried,
                    fractionX, fractionY, ClientGridRotation.allowAutoRotate(), ClientDataCache.INSTANCE::getSize);
                if (placement != null && placement.isAccepted() && placement.anchor() != null) {
                    ContainerGridClickHandler.commitPlacement(menu, placement, button);
                    cir.setReturnValue(true);
                } else if (placement == null || !placement.isAccepted()) {
                    // Prevent vanilla from mutating a single physical slot
                    // when the requested complete footprint is blocked.
                    cir.setReturnValue(true);
                }
            }
        }
    }

    @ModifyVariable(method = "mouseClicked", at = @At("HEAD"),
        argsOnly = true, ordinal = 0)
    private double xero$scaledClickMouseX(double mouseX) {
        return DeltaContainerLayoutController.nativeMouseX(
            (AbstractContainerScreen<?>) (Object) this, mouseX);
    }

    @ModifyVariable(method = "mouseClicked", at = @At("HEAD"),
        argsOnly = true, ordinal = 1)
    private double xero$scaledClickMouseY(double mouseY) {
        return DeltaContainerLayoutController.nativeMouseY(
            (AbstractContainerScreen<?>) (Object) this, mouseY);
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void xero$dropCoveredGridItem(int keyCode, int scanCode, int modifiers,
                                          CallbackInfoReturnable<Boolean> cir) {
        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) (Object) this;
        AbstractContainerMenu menu = screen.getMenu();
        Minecraft minecraft = Minecraft.getInstance();
        if (DeltaContainerLayoutController.editorKeyPressed(
            screen, keyCode, modifiers)) {
            cir.setReturnValue(true);
            return;
        }
        if (keyCode == GLFW.GLFW_KEY_L
            && DeltaContainerLayoutController.togglePositionEditor(screen)) {
            cir.setReturnValue(true);
            return;
        }
        double mouseX = minecraft.mouseHandler.xpos() * minecraft.getWindow().getGuiScaledWidth()
            / (double) minecraft.getWindow().getScreenWidth();
        double mouseY = minecraft.mouseHandler.ypos() * minecraft.getWindow().getGuiScaledHeight()
            / (double) minecraft.getWindow().getScreenHeight();
        if (DeltaContainerLayoutController.keyPressed(
            screen, keyCode, scanCode, modifiers, mouseX, mouseY)) {
            cir.setReturnValue(true);
            return;
        }
        if (LootSearchOverlay.hiddenAnchorAt(screen, mouseX, mouseY) != null
            && xero$isLootItemActionKey(minecraft, keyCode, scanCode)) {
            cir.setReturnValue(true);
            return;
        }
        if (!ClientDataCache.INSTANCE.isItemGridEnabled(menu.getClass().getName())
            || !minecraft.options.keyDrop.matches(keyCode, scanCode)) return;
        ContainerGridHelper.prepare(menu, ClientDataCache.INSTANCE::getSize,
            ClientDataCache.INSTANCE.revision());
        Slot hovered = xero$slotAt(mouseX, mouseY, menu);
        if (hovered == null || !ContainerGridHelper.isGridSlotEnabled(menu, hovered)) return;
        Slot anchor = ContainerGridHelper.footprintAnchorFor(menu, hovered, ClientDataCache.INSTANCE::getSize);
        if (anchor == null || anchor == hovered) return;

        // Vanilla uses button 1 for Ctrl+Q (drop the whole stack), otherwise 0.
        slotClicked(anchor, anchor.index, Screen.hasControlDown() ? 1 : 0, ClickType.THROW);
        cir.setReturnValue(true);
    }

    @Inject(method = "renderTooltip", at = @At("HEAD"), cancellable = true)
    private void xero$hideUnsearchedTooltip(GuiGraphics graphics, int mouseX, int mouseY,
                                             CallbackInfo ci) {
        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) (Object) this;
        if (screen instanceof PlayerStatusScreen status
            && status.suppressesHoveredItemTooltip()
            || DeltaContainerLayoutController.isActive(screen)
            || !screen.getMenu().getCarried().isEmpty()
            || LootSearchOverlay.hiddenAnchorAt(screen, mouseX, mouseY) != null) {
            ci.cancel();
        }
    }
    @Unique
    private boolean xero$isLootItemActionKey(Minecraft minecraft, int keyCode, int scanCode) {
        if (minecraft.options.keyDrop.matches(keyCode, scanCode)
            || minecraft.options.keySwapOffhand.matches(keyCode, scanCode)
            || minecraft.options.keyPickItem.matches(keyCode, scanCode)) return true;
        for (var hotbarKey : minecraft.options.keyHotbarSlots) {
            if (hotbarKey.matches(keyCode, scanCode)) return true;
        }
        return false;
    }

    @Unique
    private double xero$slotFractionX(Slot slot, double mouseX) {
        double local = mouseX - leftPos - slot.x;
        return Math.max(0.0, Math.min(0.999, local / ContainerGridHelper.SLOT_STEP));
    }

    @Unique
    private double xero$slotFractionY(Slot slot, double mouseY) {
        double local = mouseY - topPos - slot.y;
        return Math.max(0.0, Math.min(0.999, local / ContainerGridHelper.SLOT_STEP));
    }

    @Unique
    private boolean xero$isVanillaSlotFace(Slot slot, double mouseX, double mouseY) {
        double localX = mouseX - leftPos;
        double localY = mouseY - topPos;
        return localX >= slot.x && localX < slot.x + ContainerGridHelper.SLOT_FACE_SIZE
            && localY >= slot.y && localY < slot.y + ContainerGridHelper.SLOT_FACE_SIZE;
    }

    @Unique
    private Slot xero$slotAt(double mouseX, double mouseY, AbstractContainerMenu menu) {
        double localX = mouseX - leftPos;
        double localY = mouseY - topPos;
        Slot hit = ContainerGridHelper.hitSlot(menu, localX, localY);
        if (hit != null) return hit;
        Slot footprintAnchor = xero$footprintAnchorAt(menu, localX, localY);
        if (footprintAnchor != null) return footprintAnchor;
        // Do not reuse AbstractContainerScreen's previous-frame hovered slot.
        // Sophisticated moves/filters storage slots between frames, so that
        // fallback can turn a click in an empty gutter into a pickup of the
        // item that was just placed.
        return null;
    }

    /**
     * Sized items visually occupy the two-pixel gutters between vanilla slot
     * faces. Treat those gutters as part of the footprint so a normal click
     * cannot fall through to Minecraft's outside-slot handling.
     */
    @Unique
    private Slot xero$footprintAnchorAt(AbstractContainerMenu menu, double localX, double localY) {
        for (Slot anchor : menu.slots) {
            if (!anchor.isActive() || !ContainerGridHelper.isGridSlotEnabled(menu, anchor)) continue;
            ItemStack stack = anchor.getItem();
            if (stack.isEmpty()) continue;
            ItemSize size = ContainerGridHelper.orientedSize(stack, ClientDataCache.INSTANCE::getSize);
            if (size.width() <= 1 && size.height() <= 1) continue;
            Set<Slot> cells = ContainerGridHelper.footprintCells(menu, anchor, size);
            if (cells.size() != size.width() * size.height()) continue;
            int maxX = cells.stream().mapToInt(slot -> slot.x).max().orElse(anchor.x);
            int maxY = cells.stream().mapToInt(slot -> slot.y).max().orElse(anchor.y);
            for (Slot cell : cells) {
                int right = cell.x + (cell.x == maxX
                    ? ContainerGridHelper.SLOT_FACE_SIZE : ContainerGridHelper.SLOT_STEP);
                int bottom = cell.y + (cell.y == maxY
                    ? ContainerGridHelper.SLOT_FACE_SIZE : ContainerGridHelper.SLOT_STEP);
                if (localX >= cell.x && localX < right && localY >= cell.y && localY < bottom) {
                    return anchor;
                }
            }
        }
        return null;
    }

    @Unique
    private void xero$updateInventoryLabel(AbstractContainerScreen<?> screen) {
        if (!xero$inventoryLabelCaptured) {
            xero$originalInventoryLabelY = inventoryLabelY;
            xero$inventoryLabelCaptured = true;
        }
        inventoryLabelY = DeltaContainerLayoutController.isActive(screen)
            ? -10_000 : xero$originalInventoryLabelY;
    }
}
