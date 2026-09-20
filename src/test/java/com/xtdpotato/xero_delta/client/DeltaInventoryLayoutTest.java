package com.xtdpotato.xero_delta.client;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class DeltaInventoryLayoutTest {
    private static final Path ROOT = Path.of("src/main/java/com/xtdpotato/xero_delta");

    @Test
    void sharedPanelFitsDifferentHostViewports() {
        for (int width : new int[]{160, 320, 640, 1280}) {
            for (int height : new int[]{120, 240, 480, 720}) {
                for (double factor : new double[]{1, 1.5, 2}) {
                    float scale = DeltaInventoryLayout.fitScale(width, height, factor);
                    assertTrue(scale > 0 && scale <= DeltaInventoryLayout.MAX_SCALE);
                    assertTrue(DeltaInventoryLayout.WIDTH * factor * scale <= width + 0.001);
                    assertTrue(DeltaInventoryLayout.HEIGHT * scale <= height + 0.001);
                }
            }
        }
    }

    @Test
    void playerScreenDelegatesDeltaLayoutToTheContainerController() throws Exception {
        String player = Files.readString(ROOT.resolve("screen/PlayerStatusScreen.java"));
        String controller = Files.readString(ROOT.resolve("client/DeltaContainerLayoutController.java"));
        assertTrue(player.contains("DeltaContainerLayoutController.prepare(this, leftPos, topPos)"));
        assertTrue(player.contains("if (sharedDeltaLayout) return;"));
        assertFalse(player.contains("restrictedLayout = PlayerStatusClientState.INSTANCE.layoutEnabled()"));
        assertFalse(player.contains("restrictedLayout = true"));
        assertTrue(controller.contains("return status.usesSharedDeltaLayout()"));
        assertFalse(controller.contains("EMBEDDED_MAX_SCALE"));
    }

    @Test
    void sharedSafetyBoxIsNotRenderedAgainByTheForegroundOverlay() throws Exception {
        String source = Files.readString(ROOT.resolve("client/XeroDeltaClient.java"));
        assertTrue(source.contains("boolean embeddedStatusGrid = cs instanceof PlayerStatusScreen\n"
            + "            && !DeltaContainerLayoutController.isEmbedded(cs)"));
        assertTrue(source.contains("if (embeddedDeltaGrid && !embeddedStatusGrid)"));
    }

    @Test
    void standaloneIntegrationPreservesLootTargetsAndDropEdges() throws Exception {
        String player = Files.readString(ROOT.resolve("screen/PlayerStatusScreen.java"));
        String controller = Files.readString(ROOT.resolve("client/DeltaContainerLayoutController.java"));
        assertTrue(player.contains("extends InventoryScreen"));
        assertTrue(player.contains("DeltaContainerLayoutController.betterLootingDropTargetAt("));
        assertTrue(player.contains("DeltaContainerLayoutController.renderCarriedDropPreview(this"));
        assertTrue(controller.contains("Math.min(30, Math.max(1, screen.width / 2))"));
        assertTrue(controller.contains("SlotFieldUtil.setX(slot, -1000)"));
    }
}
