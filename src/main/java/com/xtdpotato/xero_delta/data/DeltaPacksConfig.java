package com.xtdpotato.xero_delta.data;

import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** Shared storage and legacy migration for all Xero Delta configuration files. */
public final class DeltaPacksConfig {
    public static final String DIRECTORY_NAME = "delta_packs";

    private static final System.Logger LOGGER = System.getLogger(DeltaPacksConfig.class.getName());
    private static final List<String> LEGACY_CONFIG_PREFIXES = List.of(
        "xero_delta-common",
        "xero_delta_spot-common",
        "xtd_safety_box-common"
    );

    private DeltaPacksConfig() {
    }

    public static Path directory() {
        return ensureDirectory(FMLPaths.CONFIGDIR.get());
    }

    public static Path file(String fileName) {
        return directory().resolve(validateFileName(fileName));
    }

    /**
     * Migrates old root-level configs before NeoForge loads the requested config.
     * The returned name is relative to the normal Minecraft config directory.
     */
    public static String prepareConfigFile(String fileName) {
        Path configDirectory = FMLPaths.CONFIGDIR.get();
        migrateLegacyConfigFiles(configDirectory);
        return DIRECTORY_NAME + "/" + validateFileName(fileName);
    }

    public static void migrateLegacyConfigFiles(Path configDirectory) {
        Path destinationDirectory = ensureDirectory(configDirectory);
        try (Stream<Path> entries = Files.list(configDirectory)) {
            for (Path source : entries.filter(Files::isRegularFile).toList()) {
                String name = source.getFileName().toString();
                if (isLegacyConfigName(name)) {
                    mergeFile(source, destinationDirectory.resolve(name));
                }
            }
        } catch (IOException exception) {
            LOGGER.log(System.Logger.Level.WARNING,
                "Could not migrate Xero Delta configs into " + destinationDirectory, exception);
        }
    }

    /** Moves an old delta_packs directory into config/delta_packs without losing conflicts. */
    public static void migrateLegacyDirectory(Path legacyDirectory) {
        migrateLegacyDirectory(legacyDirectory, directory());
    }

    static void migrateLegacyDirectory(Path legacyDirectory, Path destinationDirectory) {
        Path normalizedSource = legacyDirectory.toAbsolutePath().normalize();
        Path normalizedDestination = destinationDirectory.toAbsolutePath().normalize();
        if (!Files.isDirectory(normalizedSource) || normalizedSource.equals(normalizedDestination)) {
            return;
        }

        try {
            Files.createDirectories(normalizedDestination);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not create Xero Delta config directory "
                + normalizedDestination, exception);
        }

        try (Stream<Path> paths = Files.walk(normalizedSource)) {
            for (Path source : paths.filter(Files::isRegularFile).toList()) {
                mergeFile(source, normalizedDestination.resolve(normalizedSource.relativize(source)));
            }
        } catch (IOException exception) {
            LOGGER.log(System.Logger.Level.WARNING,
                "Could not migrate legacy Xero Delta directory " + normalizedSource, exception);
            return;
        }

        try (Stream<Path> paths = Files.walk(normalizedSource)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        } catch (IOException exception) {
            LOGGER.log(System.Logger.Level.WARNING,
                "Could not remove migrated Xero Delta directory " + normalizedSource, exception);
        }
    }

    private static Path ensureDirectory(Path configDirectory) {
        Path directory = configDirectory.resolve(DIRECTORY_NAME);
        try {
            Files.createDirectories(directory);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not create Xero Delta config directory " + directory, exception);
        }
        return directory;
    }

    private static boolean isLegacyConfigName(String name) {
        for (String prefix : LEGACY_CONFIG_PREFIXES) {
            if (Pattern.matches(Pattern.quote(prefix) + "(?:-\\d+)?\\.toml(?:\\.bak)?", name)) {
                return true;
            }
        }
        return false;
    }

    private static void mergeFile(Path source, Path destination) throws IOException {
        Files.createDirectories(destination.getParent());
        if (!Files.exists(destination)) {
            move(source, destination);
            return;
        }
        if (Files.mismatch(source, destination) == -1L) {
            Files.delete(source);
            return;
        }

        if (Files.getLastModifiedTime(source).compareTo(Files.getLastModifiedTime(destination)) > 0) {
            move(destination, migrationBackup(destination));
            move(source, destination);
        } else {
            move(source, migrationBackup(destination));
        }
    }

    private static Path migrationBackup(Path destination) {
        String suffix = ".migrated-" + Instant.now().toEpochMilli();
        Path candidate = destination.resolveSibling(destination.getFileName() + suffix);
        int counter = 1;
        while (Files.exists(candidate)) {
            candidate = destination.resolveSibling(destination.getFileName() + suffix + "-" + counter++);
        }
        return candidate;
    }

    private static void move(Path source, Path destination) throws IOException {
        try {
            Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException ignored) {
            Files.move(source, destination);
        }
    }

    private static String validateFileName(String fileName) {
        if (fileName == null || fileName.isBlank() || !Path.of(fileName).getFileName().toString().equals(fileName)) {
            throw new IllegalArgumentException("Config file name must not contain a directory: " + fileName);
        }
        return fileName;
    }
}

