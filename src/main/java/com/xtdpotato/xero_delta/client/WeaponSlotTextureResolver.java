package com.xtdpotato.xero_delta.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.xtdpotato.xero_delta.data.TaczCompatibilityRules;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** Resolves component-backed TACZ guns to their content-pack slot artwork. */
public final class WeaponSlotTextureResolver {
    private static final Map<String, Optional<Icon>> CACHE = new HashMap<>();
    private static List<ResourceLocation> slotTextures;

    private WeaponSlotTextureResolver() {
    }

    public static Icon resolve(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !TaczCompatibilityRules.isTaczGun(stack)) {
            return null;
        }
        String descriptor = TaczCompatibilityRules.descriptor(stack);
        return CACHE.computeIfAbsent(descriptor,
            ignored -> Optional.ofNullable(find(descriptor))).orElse(null);
    }

    public static void clear() {
        CACHE.clear();
        slotTextures = null;
    }

    private static Icon find(String descriptor) {
        ResourceLocation best = null;
        int bestScore = 0;
        for (ResourceLocation candidate : textures()) {
            int score = score(descriptor, candidate);
            if (score > bestScore) {
                bestScore = score;
                best = candidate;
            }
        }
        return best == null ? null : load(best);
    }

    private static List<ResourceLocation> textures() {
        if (slotTextures != null) return slotTextures;
        List<ResourceLocation> discovered = new ArrayList<>();
        Minecraft.getInstance().getResourceManager().listResources("textures", location -> {
            String path = location.getPath().toLowerCase(Locale.ROOT);
            return path.endsWith(".png") && path.contains("/slot/");
        }).keySet().forEach(discovered::add);
        slotTextures = List.copyOf(discovered);
        return slotTextures;
    }

    static int score(String descriptor, ResourceLocation candidate) {
        if (descriptor == null || descriptor.isBlank() || candidate == null) return 0;
        String value = descriptor.toLowerCase(Locale.ROOT);
        String path = candidate.getPath().toLowerCase(Locale.ROOT);
        int slash = path.lastIndexOf('/');
        String file = slash < 0 ? path : path.substring(slash + 1);
        String stem = file.endsWith(".png") ? file.substring(0, file.length() - 4) : file;
        if (stem.length() < 2) return 0;

        int score = 0;
        if (value.contains(candidate.getNamespace() + ":" + stem)) score += 160;
        if (value.contains(":" + stem)) score += 120;
        if (containsToken(value, stem)) score += 100;
        String normalizedStem = stem.replace('-', '_').replace('.', '_');
        if (!normalizedStem.equals(stem) && containsToken(value, normalizedStem)) score += 80;
        if (value.contains(candidate.getNamespace() + ":")) score += 12;
        for (String token : stem.split("[_.\\-/]+")) {
            if (token.length() >= 4 && containsToken(value, token)) score += 8;
        }
        return score;
    }

    private static boolean containsToken(String value, String token) {
        int from = 0;
        while (true) {
            int index = value.indexOf(token, from);
            if (index < 0) return false;
            int end = index + token.length();
            boolean left = index == 0 || !Character.isLetterOrDigit(value.charAt(index - 1));
            boolean right = end == value.length() || !Character.isLetterOrDigit(value.charAt(end));
            if (left && right) return true;
            from = index + 1;
        }
    }

    private static Icon load(ResourceLocation texture) {
        try {
            var resource = Minecraft.getInstance().getResourceManager().getResource(texture);
            if (resource.isEmpty()) return null;
            try (InputStream stream = resource.get().open();
                 NativeImage image = NativeImage.read(stream)) {
                return new Icon(texture, Math.max(1, image.getWidth()),
                    Math.max(1, image.getHeight()));
            }
        } catch (Exception ignored) {
            return null;
        }
    }

    public record Icon(ResourceLocation texture, int width, int height) {
    }
}
