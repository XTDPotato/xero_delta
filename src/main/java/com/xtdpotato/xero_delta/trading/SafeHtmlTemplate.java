package com.xtdpotato.xero_delta.trading;

import com.xtdpotato.xero_delta.XeroDelta;
import com.xtdpotato.xero_delta.data.ConfigPaths;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

final class SafeHtmlTemplate {
    private SafeHtmlTemplate() {
    }

    static Path path(String filename) {
        return ConfigPaths.file(filename);
    }

    static TradingHtmlThemeParser.Document load(String filename, String resource, String marker) {
        Path target = path(filename);
        ensure(target, resource, marker);
        try {
            return TradingHtmlThemeParser.parseDocument(Files.readString(target, StandardCharsets.UTF_8));
        } catch (Exception error) {
            XeroDelta.LOGGER.warn("Unable to read {}, using built-in defaults", filename, error);
            return TradingHtmlThemeParser.parseDocument("");
        }
    }

    private static void ensure(Path target, String resource, String marker) {
        if (Files.isRegularFile(target)) {
            try {
                if (Files.readString(target, StandardCharsets.UTF_8).contains(marker)) return;
            } catch (Exception ignored) {
            }
        }
        try (InputStream input = SafeHtmlTemplate.class.getResourceAsStream(resource)) {
            if (input == null) return;
            Files.createDirectories(target.getParent());
            Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception error) {
            XeroDelta.LOGGER.warn("Unable to generate {}", target, error);
        }
    }
}
