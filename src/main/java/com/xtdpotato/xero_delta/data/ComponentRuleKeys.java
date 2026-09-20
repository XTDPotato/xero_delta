package com.xtdpotato.xero_delta.data;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;

/** Stable text keys for component-bearing stacks without a Minecraft runtime dependency. */
final class ComponentRuleKeys {
    private ComponentRuleKeys() {
    }

    static String canonicalizeRuleKey(String key) {
        if (key == null) return "";
        int start = key.indexOf("{v2:");
        if (start < 0 || !key.endsWith("}")) return key;
        try {
            String encoded = key.substring(start + 4, key.length() - 1);
            String text = new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
            String canonical = canonicalizeComponentText(text);
            String normalized = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(canonical.getBytes(StandardCharsets.UTF_8));
            return key.substring(0, start + 4) + normalized + "}";
        } catch (IllegalArgumentException ignored) {
            return key;
        }
    }

    /** DataComponentPatch does not guarantee top-level textual ordering. */
    static String canonicalizeComponentText(String text) {
        if (text == null || text.length() < 2 || text.charAt(0) != '{' || text.charAt(text.length() - 1) != '}') {
            return text == null ? "" : text;
        }
        String body = text.substring(1, text.length() - 1).trim();
        if (body.isEmpty()) return "{}";
        List<String> entries = new ArrayList<>();
        int depth = 0;
        int start = 0;
        boolean quoted = false;
        boolean escaped = false;
        for (int i = 0; i < body.length(); i++) {
            char c = body.charAt(i);
            if (quoted) {
                if (escaped) escaped = false;
                else if (c == '\\') escaped = true;
                else if (c == '"') quoted = false;
                continue;
            }
            if (c == '"') quoted = true;
            else if (c == '{' || c == '[' || c == '(') depth++;
            else if (c == '}' || c == ']' || c == ')') depth--;
            else if (c == ',' && depth == 0) {
                entries.add(body.substring(start, i).trim());
                start = i + 1;
            }
        }
        entries.add(body.substring(start).trim());
        Collections.sort(entries);
        return "{" + String.join(", ", entries) + "}";
    }
}
