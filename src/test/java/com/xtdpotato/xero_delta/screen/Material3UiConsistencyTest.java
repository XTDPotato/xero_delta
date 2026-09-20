package com.xtdpotato.xero_delta.screen;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Material3UiConsistencyTest {
    private static final Path SCREEN_ROOT = Path.of(
        "src/main/java/com/xtdpotato/xero_delta/screen");

    @Test
    void screensDoNotFreezeMutableThemeColorsAtClassLoad() throws IOException {
        for (Path source : javaSources()) {
            assertFalse(Files.readString(source).matches(
                "(?s).*static final int \\w+ = (?:[\\w.]+\\.)?Material3Theme\\.\\w+;.*"),
                () -> source + " caches a mutable theme token");
        }
    }

    @Test
    void screensDoNotReintroduceVanillaButtonsOrTextFields() throws IOException {
        for (Path source : javaSources()) {
            String text = Files.readString(source);
            assertFalse(text.contains("import net.minecraft.client.gui.components.Button;"),
                () -> source + " uses the vanilla button style");
            if (!source.endsWith("MaterialEditBox.java")) {
                assertFalse(text.contains("new EditBox("),
                    () -> source + " creates an unthemed text field");
            }
        }
    }

    @Test
    void coloredScreensUseTheSharedTheme() throws IOException {
        for (Path source : javaSources()) {
            String text = Files.readString(source);
            boolean screen = text.contains("extends Screen")
                || text.contains("extends AbstractContainerScreen");
            boolean hasLiteralColor = text.matches("(?s).*0x[0-9A-Fa-f]{6,8}.*");
            if (screen && hasLiteralColor) {
                assertTrue(text.contains("Material3Theme")
                        || text.contains("TradingHtmlThemeParser.Theme"),
                    () -> source + " has an independent UI palette");
            }
        }
    }

    private static List<Path> javaSources() throws IOException {
        try (var paths = Files.list(SCREEN_ROOT)) {
            return paths.filter(path -> path.getFileName().toString().endsWith(".java"))
                .toList();
        }
    }
}
