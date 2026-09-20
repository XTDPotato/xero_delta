package com.xtdpotato.xero_delta.trading;

/** Stable, Minecraft-independent address for an item inside a carried storage item. */
public record TradingSourceAddress(Kind kind, int inventorySlot, String curioIdentifier,
                                   int curioIndex, int containerIndex, int innerSlot,
                                   String fingerprint) {
    public enum Kind { INVENTORY, CURIO }

    public TradingSourceAddress {
        curioIdentifier = curioIdentifier == null ? "" : curioIdentifier;
        fingerprint = fingerprint == null ? "" : fingerprint;
    }

    public static TradingSourceAddress inventory(int inventorySlot, int innerSlot, String fingerprint) {
        return inventory(inventorySlot, 0, innerSlot, fingerprint);
    }

    public static TradingSourceAddress inventory(int inventorySlot, int containerIndex,
                                                 int innerSlot, String fingerprint) {
        return new TradingSourceAddress(Kind.INVENTORY, inventorySlot, "", -1,
            containerIndex, innerSlot, fingerprint);
    }

    public static TradingSourceAddress curio(String identifier, int curioIndex,
                                             int innerSlot, String fingerprint) {
        return curio(identifier, curioIndex, 0, innerSlot, fingerprint);
    }

    public static TradingSourceAddress curio(String identifier, int curioIndex,
                                             int containerIndex, int innerSlot,
                                             String fingerprint) {
        return new TradingSourceAddress(Kind.CURIO, -1, identifier, curioIndex,
            containerIndex, innerSlot, fingerprint);
    }

    public String encode() {
        if (!isValid()) return "";
        String suffix = fingerprint.isBlank() ? "" : "|" + fingerprint;
        if (containerIndex > 0) {
            return kind == Kind.INVENTORY
                ? "grid|inv|" + inventorySlot + "|" + containerIndex + "|" + innerSlot + suffix
                : "grid|curio|" + curioIdentifier + "|" + curioIndex + "|"
                    + containerIndex + "|" + innerSlot + suffix;
        }
        return kind == Kind.INVENTORY
            ? "bag|inv|" + inventorySlot + "|" + innerSlot + suffix
            : "bag|curio|" + curioIdentifier + "|" + curioIndex + "|" + innerSlot + suffix;
    }

    public boolean isValid() {
        if (containerIndex < 0 || innerSlot < 0
            || fingerprint.indexOf('|') >= 0 || fingerprint.length() > 64) return false;
        if (kind == Kind.INVENTORY) return inventorySlot >= 0;
        return curioIndex >= 0 && !curioIdentifier.isBlank()
            && curioIdentifier.length() <= 64 && curioIdentifier.indexOf('|') < 0;
    }

    public static TradingSourceAddress parse(String sourceId) {
        if (sourceId == null || sourceId.length() > 256) return null;
        String[] parts = sourceId.split("\\|", -1);
        if (parts.length < 4) return null;
        if (parts[0].equals("bag") && parts[1].equals("inv")
            && (parts.length == 4 || parts.length == 5)) {
            TradingSourceAddress value = inventory(parseInt(parts[2]), parseInt(parts[3]),
                parts.length == 5 ? parts[4] : "");
            return value.isValid() ? value : null;
        }
        if (parts[0].equals("bag") && parts[1].equals("curio")
            && (parts.length == 5 || parts.length == 6)) {
            TradingSourceAddress value = curio(parts[2], parseInt(parts[3]), parseInt(parts[4]),
                parts.length == 6 ? parts[5] : "");
            return value.isValid() ? value : null;
        }
        if (parts[0].equals("grid") && parts[1].equals("inv")
            && (parts.length == 5 || parts.length == 6)) {
            TradingSourceAddress value = inventory(parseInt(parts[2]), parseInt(parts[3]),
                parseInt(parts[4]), parts.length == 6 ? parts[5] : "");
            return value.isValid() ? value : null;
        }
        if (parts[0].equals("grid") && parts[1].equals("curio")
            && (parts.length == 6 || parts.length == 7)) {
            TradingSourceAddress value = curio(parts[2], parseInt(parts[3]),
                parseInt(parts[4]), parseInt(parts[5]), parts.length == 7 ? parts[6] : "");
            return value.isValid() ? value : null;
        }
        return null;
    }

    private static int parseInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }
}
