package com.xtdpotato.xero_delta.util;

import net.minecraft.world.inventory.Slot;
import java.lang.reflect.Field;

public class SlotFieldUtil {
    private static final Field X_FIELD;
    private static final Field Y_FIELD;

    static {
        Field xf = null, yf = null;
        try {
            xf = Slot.class.getDeclaredField("x");
            xf.setAccessible(true);
            yf = Slot.class.getDeclaredField("y");
            yf.setAccessible(true);
        } catch (Exception e) {
            throw new RuntimeException("Failed to access Slot fields", e);
        }
        X_FIELD = xf;
        Y_FIELD = yf;
    }

    public static void setX(Slot slot, int x) {
        try { X_FIELD.setInt(slot, x); } catch (Exception ignored) {}
    }

    public static void setY(Slot slot, int y) {
        try { Y_FIELD.setInt(slot, y); } catch (Exception ignored) {}
    }
}