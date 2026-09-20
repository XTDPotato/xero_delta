package com.xtdpotato.xero_delta.client;

import com.xtdpotato.xero_delta.grid.GridBackingStore;
import com.xtdpotato.xero_delta.network.CarriedRotationPacket;
import com.xtdpotato.xero_delta.network.ModNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;

public final class ClientGridRotation {
    private static boolean manualPriority;
    private static double startMouseX;
    private static double startMouseY;

    private ClientGridRotation() {
    }

    public static void activate(Minecraft minecraft) {
        manualPriority = true;
        startMouseX = scaledMouseX(minecraft);
        startMouseY = scaledMouseY(minecraft);
    }

    public static void update(Minecraft minecraft) {
        if (!manualPriority) return;
        ItemStack carried = minecraft.player == null ? ItemStack.EMPTY : minecraft.player.containerMenu.getCarried();
        boolean moved = Math.abs(scaledMouseX(minecraft) - startMouseX) > 0.5
            || Math.abs(scaledMouseY(minecraft) - startMouseY) > 0.5;
        if (carried.isEmpty() || moved) {
            manualPriority = false;
            if (minecraft.getConnection() != null) {
                ModNetwork.sendToServer(new CarriedRotationPacket(GridBackingStore.isRotated(carried), false));
            }
        }
    }

    public static boolean allowAutoRotate() {
        return !manualPriority;
    }

    public static void clear(Minecraft minecraft) {
        if (!manualPriority) return;
        manualPriority = false;
        ItemStack carried = minecraft.player == null ? ItemStack.EMPTY : minecraft.player.containerMenu.getCarried();
        if (minecraft.getConnection() != null) {
            ModNetwork.sendToServer(new CarriedRotationPacket(GridBackingStore.isRotated(carried), false));
        }
    }

    private static double scaledMouseX(Minecraft minecraft) {
        return minecraft.mouseHandler.xpos() * minecraft.getWindow().getGuiScaledWidth()
            / (double) minecraft.getWindow().getScreenWidth();
    }

    private static double scaledMouseY(Minecraft minecraft) {
        return minecraft.mouseHandler.ypos() * minecraft.getWindow().getGuiScaledHeight()
            / (double) minecraft.getWindow().getScreenHeight();
    }
}
