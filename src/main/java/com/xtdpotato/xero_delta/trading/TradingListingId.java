package com.xtdpotato.xero_delta.trading;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Stable, human-readable listing identifiers with a minute timestamp and world sequence. */
public final class TradingListingId {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmm");

    private TradingListingId() {
    }

    public static String format(long epochMillis, long sequence) {
        String timestamp = TIME.format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()));
        return timestamp + "_" + String.format(Locale.ROOT, "%06d", Math.max(0L, sequence));
    }

    public static long createdAtMillis(String publicId) {
        if (publicId == null || publicId.length() < 12) return -1L;
        try {
            return LocalDateTime.parse(publicId.substring(0, 12), TIME)
                .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        } catch (RuntimeException ignored) {
            return -1L;
        }
    }
}
