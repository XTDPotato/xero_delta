package com.xtdpotato.xero_delta.client;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MedicalWheelLayoutIntegrationTest {
    @Test
    void medicalWheelUsesSharedCenteredLayoutInsteadOfChatBounds() throws IOException {
        String source = Files.readString(Path.of(
            "src/main/java/com/xtdpotato/xero_delta/client/MedicalWheelClient.java"));

        assertTrue(source.contains("WheelLayout.center"));
        assertTrue(source.contains("DeltaSpotWheelLayoutCompat.scalePercent"));
        assertFalse(source.contains("getChat().getWidth"));
        assertFalse(source.contains("getChat().getHeight"));
    }
}
