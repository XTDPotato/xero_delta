package com.xtdpotato.xero_delta.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/** Reads item texture dimensions so non-square artwork can be fitted without distortion. */
public final class ItemTextureAspectCache {
    private static final float MIN_ASPECT = 0.25F;
    private static final float MAX_ASPECT = 4.0F;
    private static final Map<ResourceLocation, Float> ASPECTS = new HashMap<>();

    private ItemTextureAspectCache() {
    }

    public static float get(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 1.0F;
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        ResourceLocation texture = ResourceLocation.fromNamespaceAndPath(
            itemId.getNamespace(), "textures/item/" + itemId.getPath() + ".png");
        return ASPECTS.computeIfAbsent(texture, ItemTextureAspectCache::load);
    }

    private static float load(ResourceLocation texture) {
        try {
            var resource = Minecraft.getInstance().getResourceManager().getResource(texture);
            if (resource.isEmpty()) return 1.0F;
            try (InputStream stream = resource.get().open();
                 NativeImage image = NativeImage.read(stream)) {
                int width = image.getWidth();
                int height = image.getHeight();
                if (width <= 0 || height <= 0) return 1.0F;
                float aspect = animationFrameAspect(texture, width, height);
                return Math.max(MIN_ASPECT, Math.min(MAX_ASPECT, aspect));
            }
        } catch (Exception ignored) {
            return 1.0F;
        }
    }

    private static float animationFrameAspect(ResourceLocation texture,
                                              int imageWidth, int imageHeight) {
        ResourceLocation metadata = ResourceLocation.fromNamespaceAndPath(
            texture.getNamespace(), texture.getPath() + ".mcmeta");
        try {
            var resource = Minecraft.getInstance().getResourceManager().getResource(metadata);
            if (resource.isEmpty()) return imageWidth / (float) imageHeight;
            try (InputStreamReader reader = new InputStreamReader(
                resource.get().open(), StandardCharsets.UTF_8)) {
                JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                if (!root.has("animation") || !root.get("animation").isJsonObject()) {
                    return imageWidth / (float) imageHeight;
                }
                JsonObject animation = root.getAsJsonObject("animation");
                int fallbackFrameSize = Math.min(imageWidth, imageHeight);
                int frameWidth = positiveDimension(animation, "width", fallbackFrameSize);
                int frameHeight = positiveDimension(animation, "height", fallbackFrameSize);
                return frameWidth / (float) frameHeight;
            }
        } catch (Exception ignored) {
            return imageWidth / (float) imageHeight;
        }
    }

    private static int positiveDimension(JsonObject object, String key, int fallback) {
        if (!object.has(key) || !object.get(key).isJsonPrimitive()) return fallback;
        try {
            return Math.max(1, object.get(key).getAsInt());
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }
}
