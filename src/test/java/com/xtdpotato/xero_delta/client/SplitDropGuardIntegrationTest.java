package com.xtdpotato.xero_delta.client;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

class SplitDropGuardIntegrationTest {
    private static final Path MAIN_ROOT = Path.of(
        "src/main/java/com/xtdpotato/xero_delta");

    @Test
    void detailPreviewDragIsConsumedBeforeItReachesTheInventory() throws IOException {
        String client = Files.readString(MAIN_ROOT.resolve("client/XeroDeltaClient.java"));
        int start = client.indexOf("if (ItemDetailOverlay.mouseDragged");
        int end = client.indexOf("return;", start);
        assertTrue(start >= 0 && end > start);
        assertTrue(client.substring(start, end).contains("event.setCanceled(true)"));
    }

    @Test
    void splitReleaseIsConsumedBeforeVanillaCanDropTheResult() throws IOException {
        String client = Files.readString(MAIN_ROOT.resolve(
            "client/XeroDeltaClient.java"));
        String marker = "if (ItemDetailOverlay.mouseReleased";
        int start = client.indexOf(marker);
        int end = client.indexOf("return;", start);

        assertTrue(start >= 0 && end > start);
        assertTrue(client.substring(start, end).contains("event.setCanceled(true)"));
    }

    @Test
    void safetyBoxOverlayConsumesGuardedClicks() throws IOException {
        String client = Files.readString(MAIN_ROOT.resolve(
            "client/XeroDeltaClient.java"));
        String marker = "if (isInSafePanel(mx, my) || isInOverlayGuard";
        int start = client.indexOf(marker);
        int end = client.indexOf("int[] cell", start);

        assertTrue(start >= 0 && end > start);
        assertTrue(client.substring(start, end).contains("event.setCanceled(true)"));
    }

    @Test
    void splitPacketAndGridSplitNeverUseWorldDropFallbacks() throws IOException {
        String packet = Files.readString(MAIN_ROOT.resolve(
            "network/SplitItemStackPacket.java"));
        String sources = Files.readString(MAIN_ROOT.resolve(
            "trading/TradingInventorySources.java"));
        String grid = Files.readString(MAIN_ROOT.resolve(
            "grid/GridBackingStore.java"));

        assertFalse(packet.contains("player.drop("));
        assertFalse(sources.substring(
            sources.indexOf("public static boolean splitStack"),
            sources.indexOf("static List<Integer> splitDestinationOrder"))
            .contains("player.drop("));
        assertFalse(grid.substring(
            grid.indexOf("public boolean splitStackToNearest"),
            grid.indexOf("static int footprintDistance"))
            .contains("player.drop("));
    }
}
