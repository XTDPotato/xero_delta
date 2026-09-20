package com.xtdpotato.xero_delta.compat;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/** Reads the wheel layout stored by Delta Spot without a compile dependency. */
public final class DeltaSpotWheelLayoutCompat {
    private DeltaSpotWheelLayoutCompat() {
    }

    public static int scalePercent() {
        return read("wheelScalePercent", 100);
    }

    public static int centerXPercent() {
        return read("wheelCenterXPercent", 50);
    }

    public static int centerYPercent() {
        return read("wheelCenterYPercent", 50);
    }

    public static int slots() {
        return read("markerWheelSlots", 8);
    }

    public static double holdSeconds() {
        return readNumber("markerWheelHoldSeconds", 0.75D).doubleValue();
    }

    public static double doubleClickSeconds() {
        return readNumber("markerDoubleClickSeconds", 1.0D).doubleValue();
    }

    public static double markerLifetimeSeconds() {
        return readNumber("markerLifetimeSeconds", 20.0D).doubleValue();
    }

    public static double enemyMarkerLifetimeSeconds() {
        return readNumber("enemyMarkerLifetimeSeconds", 10.0D).doubleValue();
    }

    public static List<String> wheelSounds() {
        try {
            Object config = config();
            Object values = config.getClass().getField("markerWheelSounds").get(config);
            if (!(values instanceof List<?> list)) return List.of();
            List<String> result = new ArrayList<>(list.size());
            for (Object value : list) result.add(String.valueOf(invokeGet(value)));
            return result;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            return List.of();
        }
    }

    public static String quickMarkerSound() {
        Object value = readValue("quickMarkerSound");
        return value instanceof String text ? text : "minecraft:block.note_block.pling";
    }

    public static void save(int slots, int scalePercent, int centerXPercent, int centerYPercent,
                            double holdSeconds, double doubleClickSeconds,
                            double markerLifetimeSeconds, double enemyMarkerLifetimeSeconds,
                            List<String> wheelSounds, String quickMarkerSound) {
        write("markerWheelSlots", slots);
        write("wheelScalePercent", scalePercent);
        write("wheelCenterXPercent", centerXPercent);
        write("wheelCenterYPercent", centerYPercent);
        write("markerWheelHoldSeconds", holdSeconds);
        write("markerDoubleClickSeconds", doubleClickSeconds);
        write("markerLifetimeSeconds", markerLifetimeSeconds);
        write("enemyMarkerLifetimeSeconds", enemyMarkerLifetimeSeconds);
        write("quickMarkerSound", quickMarkerSound);
        try {
            Object config = config();
            Object values = config.getClass().getField("markerWheelSounds").get(config);
            if (values instanceof List<?> list) {
                for (int index = 0; index < Math.min(list.size(), wheelSounds.size()); index++) {
                    invokeSet(list.get(index), wheelSounds.get(index));
                }
            }
            Class<?> configClass = config.getClass();
            Object spec = configClass.getField("SPEC").get(null);
            spec.getClass().getMethod("save").invoke(spec);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
        }
    }

    private static int read(String fieldName, int fallback) {
        return readNumber(fieldName, fallback).intValue();
    }

    private static Number readNumber(String fieldName, Number fallback) {
        Object value = readValue(fieldName);
        return value instanceof Number number ? number : fallback;
    }

    private static Object readValue(String fieldName) {
        try {
            Object config = config();
            Field field = config.getClass().getField(fieldName);
            Object value = field.get(config);
            return invokeGet(value);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            return null;
        }
    }

    private static void write(String fieldName, Object nextValue) {
        try {
            Object config = config();
            Object value = config.getClass().getField(fieldName).get(config);
            invokeSet(value, nextValue);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
        }
    }

    private static Object config() throws ReflectiveOperationException {
        Class<?> configClass = Class.forName("com.xtdpotato.delta_spot.Config");
        return configClass.getField("INSTANCE").get(null);
    }

    private static Object invokeGet(Object value) throws ReflectiveOperationException {
        return value.getClass().getMethod("get").invoke(value);
    }

    private static void invokeSet(Object value, Object nextValue) throws ReflectiveOperationException {
        Method set = java.util.Arrays.stream(value.getClass().getMethods())
            .filter(candidate -> candidate.getName().equals("set") && candidate.getParameterCount() == 1)
            .findFirst().orElseThrow();
        set.invoke(value, nextValue);
    }
}
