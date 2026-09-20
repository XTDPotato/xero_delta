package com.xtdpotato.xero_delta.client;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

/** Ampersand colors plus a compact gradient tag used by the mail composer. */
public final class MailTextFormatter {
    private static final String GRADIENT_PREFIX = "<gradient:#";
    private MailTextFormatter() {}

    public static Component parse(String line) {
        MutableComponent output = Component.empty();
        if (line == null || line.isEmpty()) return output;
        int index = 0;
        ChatFormatting active = ChatFormatting.WHITE;
        while (index < line.length()) {
            if (line.startsWith(GRADIENT_PREFIX, index)) {
                int firstEnd = line.indexOf(':', index + GRADIENT_PREFIX.length());
                int openEnd = line.indexOf('>', firstEnd + 1);
                int close = line.indexOf("</gradient>", openEnd + 1);
                if (firstEnd > 0 && openEnd > firstEnd && close > openEnd) {
                    String first = line.substring(index + GRADIENT_PREFIX.length(), firstEnd);
                    String second = line.substring(firstEnd + 2, openEnd);
                    String text = line.substring(openEnd + 1, close);
                    try {
                        appendGradient(output, text, Integer.parseInt(first, 16), Integer.parseInt(second, 16));
                        index = close + "</gradient>".length();
                        continue;
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
            if (line.charAt(index) == '&' && index + 1 < line.length()) {
                ChatFormatting formatting = ChatFormatting.getByCode(line.charAt(index + 1));
                if (formatting != null) {
                    active = formatting;
                    index += 2;
                    continue;
                }
            }
            int next = index + 1;
            while (next < line.length() && line.charAt(next) != '&'
                && !line.startsWith(GRADIENT_PREFIX, next)) next++;
            output.append(Component.literal(line.substring(index, next)).withStyle(active));
            index = next;
        }
        return output;
    }

    private static void appendGradient(MutableComponent output, String text, int from, int to) {
        int denominator = Math.max(1, text.length() - 1);
        for (int i = 0; i < text.length(); i++) {
            float progress = i / (float) denominator;
            int r = lerp(from >> 16 & 255, to >> 16 & 255, progress);
            int g = lerp(from >> 8 & 255, to >> 8 & 255, progress);
            int b = lerp(from & 255, to & 255, progress);
            output.append(Component.literal(String.valueOf(text.charAt(i))).setStyle(
                Style.EMPTY.withColor(TextColor.fromRgb(r << 16 | g << 8 | b))));
        }
    }

    private static int lerp(int from, int to, float progress) {
        return Math.round(from + (to - from) * progress);
    }
}
