package com.xtdpotato.xero_delta.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.loading.FMLPaths;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public final class WidgetImageCache {
    private record CachedImage(ResourceLocation texture, int width, int height, long modified) {
    }

    private static final Map<Path, CachedImage> CACHE = new HashMap<>();

    private WidgetImageCache() {
    }

    public static boolean render(GuiGraphics graphics, String pathValue, int x, int y, int width, int height) {
        Path path = resolve(pathValue);
        if (path == null || !Files.isRegularFile(path)) return false;
        try {
            long modified = Files.getLastModifiedTime(path).toMillis();
            CachedImage image = CACHE.get(path);
            if (image == null || image.modified != modified) image = load(path, modified, image);
            // Destination dimensions and source UV dimensions are different. Passing
            // the destination size as the source area repeats small textures.
            graphics.blit(image.texture, x, y, width, height,
                0, 0, image.width, image.height, image.width, image.height);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    public static Path resolve(String pathValue) {
        if (pathValue == null || pathValue.isBlank()) return null;
        try {
            Path path = Path.of(pathValue);
            if (path.isAbsolute()) return null;
            Path gameDirectory = FMLPaths.GAMEDIR.get().toAbsolutePath().normalize();
            Path resolved = gameDirectory.resolve(path).normalize();
            return resolved.startsWith(gameDirectory) ? resolved : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    /** Keeps disk image values portable when the Minecraft instance directory changes. */
    public static String normalizeReference(String value) {
        if (value == null || value.isBlank()) return "";
        String trimmed = value.trim().replace('\\', '/');
        if (trimmed.startsWith("#") || trimmed.startsWith("item:")) return trimmed;
        try {
            Path path = Path.of(trimmed);
            if (path.isAbsolute()) return "";
            Path gameDirectory = FMLPaths.GAMEDIR.get().toAbsolutePath().normalize();
            Path resolved = gameDirectory.resolve(path).normalize();
            if (!resolved.startsWith(gameDirectory)) return "";
            return gameDirectory.relativize(resolved).toString().replace('\\', '/');
        } catch (Exception ignored) {
            // A namespaced resource location, such as minecraft:textures/gui/...png.
            return trimmed.contains(":") ? trimmed : "";
        }
    }

    private static CachedImage load(Path path, long modified, CachedImage previous) throws Exception {
        Minecraft minecraft = Minecraft.getInstance();
        if (previous != null) minecraft.getTextureManager().release(previous.texture);
        try (InputStream stream = Files.newInputStream(path)) {
            NativeImage image = NativeImage.read(stream);
            int width = image.getWidth();
            int height = image.getHeight();
            DynamicTexture texture = new DynamicTexture(image);
            ResourceLocation location = minecraft.getTextureManager().register(
                "xero_delta/layout_widget/" + Integer.toHexString(path.toString().hashCode()), texture);
            CachedImage cached = new CachedImage(location, width, height, modified);
            CACHE.put(path, cached);
            return cached;
        }
    }
}
