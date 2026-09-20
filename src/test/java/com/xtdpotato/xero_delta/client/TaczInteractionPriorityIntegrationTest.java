package com.xtdpotato.xero_delta.client;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TaczInteractionPriorityIntegrationTest {
    private static final Path MAIN_ROOT = Path.of(
        "src/main/java/com/xtdpotato/xero_delta");

    @Test
    void optionalProbeMirrorsTaczPromptRequirements() throws IOException {
        String source = Files.readString(MAIN_ROOT.resolve(
            "compat/TaczInteractionPriority.java"));

        assertTrue(source.contains("INTERACT_KEY"));
        assertTrue(source.contains("interactKey.same(xeroKey)"));
        assertTrue(source.contains("DISABLE_INTERACT_HUD_TEXT"));
        assertTrue(source.contains("mainHandHoldGun"));
        assertTrue(source.contains("canInteractBlock"));
        assertTrue(source.contains("canInteractEntity"));
    }

    @Test
    void promptAndActionUseTheSamePriorityProbe() throws IOException {
        String hud = Files.readString(MAIN_ROOT.resolve(
            "client/ContextInteractionHudRenderer.java"));
        String client = Files.readString(MAIN_ROOT.resolve(
            "client/XeroDeltaClient.java"));

        assertTrue(hud.contains("TaczInteractionPriority.ownsInteraction"));
        assertTrue(hud.contains("taczOwnsInteraction && prompt != null"));
        assertTrue(hud.contains("carryTargetOnly"));
        assertTrue(client.contains("BetterLootingClientPriority.hasPickupTarget() || "
            + "taczOwnsInteraction"));
        assertTrue(client.contains("RESCUE_KEY.isDown() && !taczOwnsInteraction"));
    }
}
