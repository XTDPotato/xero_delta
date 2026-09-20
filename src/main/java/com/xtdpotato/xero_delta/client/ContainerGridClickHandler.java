package com.xtdpotato.xero_delta.client;

import com.xtdpotato.xero_delta.data.ItemSize;
import com.xtdpotato.xero_delta.grid.ContainerGridHelper;
import com.xtdpotato.xero_delta.grid.GridBackingStore;
import com.xtdpotato.xero_delta.network.CarriedRotationPacket;
import com.xtdpotato.xero_delta.network.ModNetwork;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.Set;

/**
 * Client-side bridge for containers that replace AbstractContainerScreen's
 * mouseClicked method (notably Sophisticated Core storage screens).
 *
 * The bridge resolves the mouse position to one explicit grid anchor.  A
 * failed explicit placement is deliberately not retried with the broad
 * candidate search: that retry is what makes a rejected drop jump to the
 * first available, usually top-left, cell.
 */
public final class ContainerGridClickHandler {
    private ContainerGridClickHandler() {
    }

    /**
     * Handles a carried multi-cell stack. Returns true when the caller must
     * stop its normal mouse handler, including a blocked placement.
     */
    public static boolean handle(AbstractContainerScreen<?> screen, double mouseX, double mouseY,
                                 int button) {
        if (screen == null) return false;
        AbstractContainerMenu menu = screen.getMenu();
        if (!ClientDataCache.INSTANCE.isItemGridEnabled(menu.getClass().getName())) return false;

        ContainerGridHelper.prepare(menu, ClientDataCache.INSTANCE::getSize,
            ClientDataCache.INSTANCE.revision());
        Slot clicked = slotAt(screen, mouseX, mouseY, menu);
        if (clicked == null || !ContainerGridHelper.isGridSlotEnabled(menu, clicked)) return false;

        ItemStack carried = menu.getCarried();
        if (carried.isEmpty()) {
            Slot occupied = ContainerGridHelper.footprintAnchorFor(
                menu, clicked, ClientDataCache.INSTANCE::getSize);
            if (occupied == null || occupied == clicked) return false;
            if (button != 0 && button != 1) return true;
            ClickType type = button == 0 && Screen.hasShiftDown()
                ? ClickType.QUICK_MOVE : ClickType.PICKUP;
            sendGridClick(menu, occupied.index, button, type);
            return true;
        }
        if (button == 1 && ContainerGridHelper.supportsCarriedContainerInteraction(carried)) return false;

        ItemSize rawSize = ClientDataCache.INSTANCE.getSize(carried);
        if (rawSize.width() <= 1 && rawSize.height() <= 1) {
            Slot occupied = ContainerGridHelper.footprintAnchorFor(
                menu, clicked, ClientDataCache.INSTANCE::getSize);
            if (occupied != null) {
                if (button != 0 && button != 1) return true;
                sendGridClick(menu, occupied.index, button, ClickType.PICKUP);
                return true;
            }
            if (isVanillaFace(screen, clicked, mouseX, mouseY)) return false;
            if (button != 0 && button != 1) return true;
            sendGridClick(menu, clicked.index, button, ClickType.PICKUP);
            return true;
        }
        if (button != 0 && button != 1) return true;

        double fractionX = fractionX(screen, clicked, mouseX);
        double fractionY = fractionY(screen, clicked, mouseY);
        var placement = ContainerGridHelper.resolveCursorPlacement(menu, clicked, carried,
            fractionX, fractionY, ClientGridRotation.allowAutoRotate(), ClientDataCache.INSTANCE::getSize);

        if (placement == null || !placement.isAccepted() || placement.anchor() == null) {
            return true;
        }

        // Always consume the event for a carried grid item, even when the
        // resolved anchor equals the clicked slot.  Letting the third-party
        // handler run a second time is the source of the immediate pickup
        // after a successful placement.
        commitPlacement(menu, placement, button);
        return true;
    }

    public static void commitPlacement(AbstractContainerMenu menu,
                                       ContainerGridHelper.PlacementResult placement, int mouseButton) {
        ItemStack carried = menu.getCarried();
        if (ClientGridRotation.allowAutoRotate()
            && placement.rotated() != GridBackingStore.isRotated(carried)
            && Minecraft.getInstance().getConnection() != null) {
            ModNetwork.sendToServer(new CarriedRotationPacket(placement.rotated(), false));
        }
        sendGridClick(menu, placement.anchor().index, mouseButton, ClickType.PICKUP);
    }

    public static void sendGridClick(AbstractContainerMenu menu, int slotNumber,
                                     int mouseButton, ClickType type) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.player.connection == null) return;
        minecraft.player.connection.send(new ServerboundContainerClickPacket(
            menu.containerId, menu.getStateId(), slotNumber, mouseButton, type,
            menu.getCarried().copy(), new Int2ObjectOpenHashMap<>()));
    }

    private static Slot slotAt(AbstractContainerScreen<?> screen, double mouseX, double mouseY,
                               AbstractContainerMenu menu) {
        double localX = mouseX - screen.getGuiLeft();
        double localY = mouseY - screen.getGuiTop();
        Slot hit = ContainerGridHelper.hitSlot(menu, localX, localY);
        if (hit != null) return hit;

        // Include the two-pixel gutters between slot faces in a large item's
        // footprint, while still returning its real anchor.
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

    private static double fractionX(AbstractContainerScreen<?> screen, Slot slot, double mouseX) {
        double local = mouseX - screen.getGuiLeft() - slot.x;
        return Math.max(0.0, Math.min(0.999, local / ContainerGridHelper.SLOT_STEP));
    }

    private static boolean isVanillaFace(AbstractContainerScreen<?> screen, Slot slot,
                                         double mouseX, double mouseY) {
        double localX = mouseX - screen.getGuiLeft();
        double localY = mouseY - screen.getGuiTop();
        return localX >= slot.x && localX < slot.x + ContainerGridHelper.SLOT_FACE_SIZE
            && localY >= slot.y && localY < slot.y + ContainerGridHelper.SLOT_FACE_SIZE;
    }

    private static double fractionY(AbstractContainerScreen<?> screen, Slot slot, double mouseY) {
        double local = mouseY - screen.getGuiTop() - slot.y;
        return Math.max(0.0, Math.min(0.999, local / ContainerGridHelper.SLOT_STEP));
    }

}
