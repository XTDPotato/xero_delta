package com.xtdpotato.xero_delta.grid;

import net.minecraft.world.item.ItemStack;
import java.util.regex.Pattern;

public class WildcardMatcher {
    private final String pattern;

    private WildcardMatcher(String pattern) {
        this.pattern = pattern;
    }

    public static WildcardMatcher of(String pattern) {
        return new WildcardMatcher(pattern);
    }

    public boolean matches(String id) {
        if (pattern.equals(id)) return true;
        if (pattern.endsWith("*")) {
            String prefix = pattern.substring(0, pattern.length() - 1);
            return id.startsWith(prefix);
        }
        // Support namespace:* and namespace:*_suffix patterns
        int colonIdx = pattern.indexOf(':');
        if (colonIdx >= 0) {
            String namespace = pattern.substring(0, colonIdx);
            String path = pattern.substring(colonIdx + 1);
            if ("*".equals(path)) {
                return id.startsWith(namespace + ":");
            }
            if (path.startsWith("*")) {
                String suffix = path.substring(1);
                int idColon = id.indexOf(':');
                if (idColon >= 0 && id.substring(0, idColon).equals(namespace)) {
                    return id.substring(idColon + 1).endsWith(suffix);
                }
            }
        }
        return false;
    }

    public boolean matches(ItemStack stack) {
        if (stack.isEmpty()) return false;
        String id = stack.getItemHolder().getKey().location().toString();
        return matches(id);
    }
}
