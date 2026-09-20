package com.xtdpotato.xero_delta.mixin;

import com.xtdpotato.xero_delta.client.ClientDataCache;
import com.xtdpotato.xero_delta.client.ContainerGridClickHandler;
import com.xtdpotato.xero_delta.client.ContainerGridRenderBridge;
import com.xtdpotato.xero_delta.client.DeltaContainerLayoutController;
import com.xtdpotato.xero_delta.client.LootSearchOverlay;
import com.xtdpotato.xero_delta.client.LootSearchClientState;
import com.xtdpotato.xero_delta.data.ItemSize;
import com.xtdpotato.xero_delta.grid.ContainerGridHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Sophisticated renders through its own renderSuper method instead of
 * AbstractContainerScreen#render, so the generic screen hook cannot refresh
 * the slot index for search/scroll updates.
 */
@Pseudo
@Mixin(targets = "net.p3pp3rf1y.sophisticatedcore.client.gui.StorageScreenBase", remap = false)
public abstract class SophisticatedStorageScreenMixin {
    @Inject(method = "render", at = @At("HEAD"), remap = false)
    private void xero$refreshGridBeforeRender(GuiGraphics graphics, int mouseX, int mouseY,
                                               float partialTick, CallbackInfo ci) {
        AbstractContainerMenu menu = ((AbstractContainerScreen<?>) (Object) this).getMenu();
        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) (Object) this;
        DeltaContainerLayoutController.prepare(screen, screen.getGuiLeft(), screen.getGuiTop());
        DeltaContainerLayoutController.beginRender(screen, graphics);
        if (ClientDataCache.INSTANCE.isItemGridEnabled(menu.getClass().getName())
            || LootSearchClientState.INSTANCE.isActiveFor(menu.containerId)) {
            ContainerGridHelper.prepare(menu, ClientDataCache.INSTANCE::getSize,
                ClientDataCache.INSTANCE.revision());
        }
    }

    @Inject(method = "render", at = @At("TAIL"), remap = false)
    private void xero$renderGridInteractionOverlay(GuiGraphics graphics, int mouseX, int mouseY,
                                                   float partialTick, CallbackInfo ci) {
        LootSearchOverlay.handleHover(
            (AbstractContainerScreen<?>) (Object) this, mouseX, mouseY);
        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) (Object) this;
        int nativeMouseX = (int) Math.round(
            DeltaContainerLayoutController.nativeMouseX(screen, mouseX));
        int nativeMouseY = (int) Math.round(
            DeltaContainerLayoutController.nativeMouseY(screen, mouseY));
        ContainerGridRenderBridge.renderInteractionOverlay(
            screen, graphics, nativeMouseX, nativeMouseY);
        DeltaContainerLayoutController.renderInteractionOverlay(
            screen, graphics, mouseX, mouseY);
        DeltaContainerLayoutController.endRender(
            screen, graphics);
    }

    /** Sophisticated calls renderBg a second time from renderSuper. Apply the
     * same right-side transform used by AbstractContainerScreen#renderBackground. */
    @Inject(method = "renderSuper", at = @At(value = "INVOKE",
        target = "Lnet/p3pp3rf1y/sophisticatedcore/client/gui/StorageScreenBase;renderBg(Lnet/minecraft/client/gui/GuiGraphics;FII)V"),
        remap = false, require = 0)
    private void xero$beginSecondNativeBackground(GuiGraphics graphics, int mouseX,
                                                   int mouseY, float partialTick,
                                                   CallbackInfo ci) {
        DeltaContainerLayoutController.beginNativeContainerLayer(
            (AbstractContainerScreen<?>) (Object) this, graphics);
    }

    @Inject(method = "renderSuper", at = @At(value = "INVOKE",
        target = "Lnet/p3pp3rf1y/sophisticatedcore/client/gui/StorageScreenBase;renderBg(Lnet/minecraft/client/gui/GuiGraphics;FII)V",
        shift = At.Shift.AFTER), remap = false, require = 0)
    private void xero$endSecondNativeBackground(GuiGraphics graphics, int mouseX,
                                                 int mouseY, float partialTick,
                                                 CallbackInfo ci) {
        DeltaContainerLayoutController.endNativeContainerLayer(
            (AbstractContainerScreen<?>) (Object) this, graphics);
    }

    @Inject(method = "renderSlot", at = @At("HEAD"), cancellable = true, remap = false)
    private void xero$renderDeltaPlayerSlot(GuiGraphics graphics, Slot slot, CallbackInfo ci) {
        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) (Object) this;
        if (LootSearchOverlay.renderHiddenSlot(screen, graphics, slot)
            || DeltaContainerLayoutController.renderPlayerSlot(screen, graphics, slot)) {
            ci.cancel();
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), remap = false)
    private void xero$refreshGridBeforeClick(double mouseX, double mouseY, int button,
                                             CallbackInfoReturnable<Boolean> cir) {
        AbstractContainerMenu menu = ((AbstractContainerScreen<?>) (Object) this).getMenu();
        if (ClientDataCache.INSTANCE.isItemGridEnabled(menu.getClass().getName())
            || LootSearchClientState.INSTANCE.isActiveFor(menu.containerId)) {
            ContainerGridHelper.prepare(menu, ClientDataCache.INSTANCE::getSize,
                ClientDataCache.INSTANCE.revision());
        }
    }

    /**
     * StorageScreenBase overrides AbstractContainerScreen#mouseClicked.  The
     * generic screen mixin therefore never sees Sophisticated's clicks; route
     * carried multi-cell stacks through the same explicit-anchor resolver.
     * The bridge sends the authoritative click packet directly so this mixin
     * does not depend on a generated callback or nested interface class.
     */
    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true, remap = false)
    private void xero$handleGridClick(double mouseX, double mouseY, int button,
                                      CallbackInfoReturnable<Boolean> cir) {
        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) (Object) this;
        if (LootSearchOverlay.hiddenAnchorAt(screen, mouseX, mouseY) != null) {
            cir.setReturnValue(true);
            return;
        }
        if (DeltaContainerLayoutController.mouseClicked(screen, mouseX, mouseY, button)) {
            cir.setReturnValue(true);
            return;
        }
        if ((button == 0 || button == 1)
            && DeltaContainerLayoutController.blocksOutsideDrop(screen, mouseX, mouseY)) {
            cir.setReturnValue(true);
            return;
        }
        double nativeMouseX = DeltaContainerLayoutController.nativeMouseX(screen, mouseX);
        double nativeMouseY = DeltaContainerLayoutController.nativeMouseY(screen, mouseY);
        if (ContainerGridClickHandler.handle(
            screen, nativeMouseX, nativeMouseY, button)) {
            cir.setReturnValue(true);
        }
    }

    /**
     * StorageScreenBase normally predicts a click by calling menu.clicked on
     * the client before sending its packet.  The grid menu mixin intentionally
     * applies the cross-cell mutation only on the server, so that prediction
     * would put a multi-cell item into one physical slot and leave a stale
     * client snapshot.  Send the packet with no client-side mutation instead;
     * the authoritative server response then updates the whole footprint.
     */
    @Inject(method = "handleInventoryMouseClick", at = @At("HEAD"), cancellable = true, remap = false)
    private void xero$skipGridPrediction(int slotNumber, int mouseButton, ClickType type, CallbackInfo ci) {
        AbstractContainerMenu menu = ((AbstractContainerScreen<?>) (Object) this).getMenu();
        if ((type != ClickType.PICKUP && type != ClickType.QUICK_MOVE && type != ClickType.THROW)
            || slotNumber < 0 || slotNumber >= menu.slots.size()
            || !ClientDataCache.INSTANCE.isItemGridEnabled(menu.getClass().getName())) return;
        Slot slot = menu.slots.get(slotNumber);
        if (!ContainerGridHelper.isGridSlotEnabled(menu, slot)) return;
        ItemStack carried = menu.getCarried();
        Slot anchor = ContainerGridHelper.footprintAnchorFor(menu, slot, ClientDataCache.INSTANCE::getSize);
        ItemStack target = anchor == null ? slot.getItem() : anchor.getItem();
        ItemStack sizedStack = carried.isEmpty() ? target : carried;
        if (sizedStack.isEmpty()) return;
        ItemSize size = ClientDataCache.INSTANCE.getSize(sizedStack);
        boolean coveredCell = anchor != null && anchor != slot;
        if (!coveredCell && size.width() <= 1 && size.height() <= 1) return;

        xero$sendGridClick(menu, slotNumber, mouseButton, type);
        ci.cancel();
    }

    @Unique
    private static void xero$sendGridClick(AbstractContainerMenu menu, int slotNumber,
                                           int mouseButton, ClickType type) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.player.connection == null) return;
        minecraft.player.connection.send(new ServerboundContainerClickPacket(
            menu.containerId, menu.getStateId(), slotNumber, mouseButton, type,
            menu.getCarried().copy(), new Int2ObjectOpenHashMap<>()));
    }
}
