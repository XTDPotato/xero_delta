package com.xtdpotato.xero_delta.mail;

import java.util.ArrayList;
import java.util.List;

/** Builds reusable /xero mail commands from the visual mail composer and sent history. */
public final class MailCommandFormatter {
    private MailCommandFormatter() {
    }

    public static String format(String recipients, String payloadJson) {
        String json = payloadJson == null || payloadJson.isBlank() ? "{}" : payloadJson.trim();
        return selectors(recipients).stream()
            .map(selector -> "/xero mail send " + selector + " " + json)
            .reduce((left, right) -> left + "\n" + right)
            .orElse("/xero mail send @s " + json);
    }

    static List<String> selectors(String recipients) {
        String raw = recipients == null ? "" : recipients.trim();
        if (raw.isBlank()) return List.of("@s");
        if ("*".equals(raw) || "@a".equalsIgnoreCase(raw)) return List.of("@a");
        if (raw.startsWith("@") && !containsSeparator(raw)) return List.of(raw);

        List<String> result = new ArrayList<>();
        for (String token : raw.split("[,;，；]")) {
            String value = token.trim();
            if (!value.isEmpty() && !result.contains(value)) result.add(value);
        }
        return result.isEmpty() ? List.of("@s") : List.copyOf(result);
    }

    private static boolean containsSeparator(String value) {
        return value.indexOf(',') >= 0 || value.indexOf(';') >= 0
            || value.indexOf('，') >= 0 || value.indexOf('；') >= 0;
    }
}
