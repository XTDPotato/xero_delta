package com.xtdpotato.xero_delta.client;

import net.minecraft.client.gui.screens.Screen;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class RecipeOverlayAvoidance {
    public record Bounds(int x, int y, int width, int height) {
        int right() { return x + width; }
        int bottom() { return y + height; }

        boolean intersects(Bounds other) {
            return x < other.right() && right() > other.x && y < other.bottom() && bottom() > other.y;
        }

        int overlapArea(Bounds other) {
            int overlapWidth = Math.max(0, Math.min(right(), other.right()) - Math.max(x, other.x));
            int overlapHeight = Math.max(0, Math.min(bottom(), other.bottom()) - Math.max(y, other.y));
            return overlapWidth * overlapHeight;
        }

        Bounds expand(int amount) {
            return new Bounds(x - amount, y - amount, width + amount * 2, height + amount * 2);
        }
    }

    private static final int SCREEN_MARGIN = 2;
    private static final int OVERLAY_MARGIN = 4;
    private static Screen cachedScreen;
    private static Bounds cachedContainer;
    private static int cachedScreenWidth;
    private static int cachedScreenHeight;
    private static long cachedAt;
    private static List<Bounds> cachedOccupied = List.of();

    private RecipeOverlayAvoidance() {
    }

    public static int[] findShift(Screen screen, Bounds desired, Bounds container, int screenWidth, int screenHeight) {
        List<Bounds> occupied = collectOccupiedAreas(screen, container, screenWidth, screenHeight);
        if (occupied.stream().noneMatch(desired::intersects)) return new int[]{0, 0};

        List<int[]> candidates = new ArrayList<>();
        candidates.add(new int[]{desired.x(), desired.y()});
        for (Bounds area : occupied) {
            candidates.add(new int[]{area.x() - desired.width() - OVERLAY_MARGIN, desired.y()});
            candidates.add(new int[]{area.right() + OVERLAY_MARGIN, desired.y()});
            candidates.add(new int[]{desired.x(), area.y() - desired.height() - OVERLAY_MARGIN});
            candidates.add(new int[]{desired.x(), area.bottom() + OVERLAY_MARGIN});
        }
        candidates.add(new int[]{container.x() - desired.width() - OVERLAY_MARGIN, container.y()});
        candidates.add(new int[]{container.right() + OVERLAY_MARGIN, container.y()});
        candidates.add(new int[]{container.x(), container.y() - desired.height() - OVERLAY_MARGIN});
        candidates.add(new int[]{container.x(), container.bottom() + OVERLAY_MARGIN});

        int bestX = desired.x();
        int bestY = desired.y();
        long bestScore = Long.MAX_VALUE;
        for (int[] candidate : candidates) {
            int x = clamp(candidate[0], SCREEN_MARGIN, Math.max(SCREEN_MARGIN, screenWidth - desired.width() - SCREEN_MARGIN));
            int y = clamp(candidate[1], SCREEN_MARGIN, Math.max(SCREEN_MARGIN, screenHeight - desired.height() - SCREEN_MARGIN));
            Bounds placed = new Bounds(x, y, desired.width(), desired.height());
            long occupiedOverlap = 0;
            for (Bounds area : occupied) occupiedOverlap += placed.overlapArea(area);
            long containerOverlap = placed.overlapArea(container);
            long distance = Math.abs(x - desired.x()) + Math.abs(y - desired.y());
            long score = occupiedOverlap * 1_000_000L + containerOverlap * 10_000L + distance;
            if (score < bestScore) {
                bestScore = score;
                bestX = x;
                bestY = y;
            }
        }
        return new int[]{bestX - desired.x(), bestY - desired.y()};
    }

    private static List<Bounds> collectOccupiedAreas(Screen screen, Bounds container, int screenWidth, int screenHeight) {
        long now = System.currentTimeMillis();
        if (screen == cachedScreen && container.equals(cachedContainer)
            && screenWidth == cachedScreenWidth && screenHeight == cachedScreenHeight
            && now - cachedAt < 100L) return cachedOccupied;

        List<Bounds> result = new ArrayList<>();
        collectJei(result);
        collectRei(result);
        boolean emiPresent = collectEmi(result);
        if (emiPresent && result.isEmpty()) {
            if (container.x() >= 54) result.add(new Bounds(0, 0, container.x() - OVERLAY_MARGIN, screenHeight));
            if (screenWidth - container.right() >= 54) {
                result.add(new Bounds(container.right() + OVERLAY_MARGIN, 0,
                    screenWidth - container.right() - OVERLAY_MARGIN, screenHeight));
            }
        }
        cachedScreen = screen;
        cachedContainer = container;
        cachedScreenWidth = screenWidth;
        cachedScreenHeight = screenHeight;
        cachedAt = now;
        cachedOccupied = result.stream().filter(bounds -> bounds.width() > 0 && bounds.height() > 0)
            .map(bounds -> bounds.expand(OVERLAY_MARGIN)).toList();
        return cachedOccupied;
    }

    private static void collectJei(List<Bounds> result) {
        try {
            Class<?> internalClass = Class.forName("mezz.jei.common.Internal");
            Object runtime = internalClass.getMethod("getJeiRuntime").invoke(null);
            if (runtime == null) return;
            collectJeiOverlay(result, invoke(runtime, "getIngredientListOverlay"), true);
            collectJeiOverlay(result, invoke(runtime, "getBookmarkOverlay"), false);
        } catch (ReflectiveOperationException | LinkageError ignored) {
        }
    }

    private static void collectJeiOverlay(List<Bounds> result, Object overlay, boolean requireDisplayed) {
        if (overlay == null) return;
        if (requireDisplayed && !invokeBoolean(overlay, "isListDisplayed", false)) return;
        if (!requireDisplayed) {
            Method displayed = findMethod(overlay.getClass(), "isListDisplayed");
            if (displayed != null && !invokeBoolean(overlay, displayed, false)) return;
        }
        Object contents = readField(overlay, "contents");
        Bounds bounds = rectangleFrom(invoke(contents, "getBackgroundArea"));
        if (bounds != null) result.add(requireDisplayed
            ? new Bounds(bounds.x(), bounds.y(), bounds.width(), bounds.height() + 28)
            : bounds);
    }

    private static void collectRei(List<Bounds> result) {
        try {
            Class<?> runtimeClass = Class.forName("me.shedaniel.rei.api.client.REIRuntime");
            Object runtime = runtimeClass.getMethod("getInstance").invoke(null);
            if (runtime == null || !invokeBoolean(runtime, "isOverlayVisible", false)) return;
            Object overlay = optionalValue(invoke(runtime, "getOverlay"));
            if (overlay == null) return;
            Object entryList = invoke(overlay, "getEntryList");
            addRectangle(result, invoke(entryList, "getBounds"));
            Object favorites = optionalValue(invoke(overlay, "getFavoritesList"));
            if (favorites != null) addRectangle(result, invoke(favorites, "getBounds"));
        } catch (ReflectiveOperationException | LinkageError ignored) {
        }
    }

    private static boolean collectEmi(List<Bounds> result) {
        Class<?> managerClass;
        try {
            Class.forName("dev.emi.emi.api.EmiApi");
            managerClass = Class.forName("dev.emi.emi.screen.EmiScreenManager");
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return false;
        }

        Map<Object, Boolean> visited = new IdentityHashMap<>();
        for (Field field : managerClass.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers())) continue;
            String name = field.getName().toLowerCase(java.util.Locale.ROOT);
            if (!name.contains("panel") && !name.contains("sidebar") && !name.contains("bounds") && !name.contains("area")) continue;
            try {
                field.setAccessible(true);
                collectRectangles(result, field.get(null), visited, 0);
            } catch (ReflectiveOperationException | RuntimeException ignored) {
            }
        }
        return true;
    }

    private static void collectRectangles(List<Bounds> result, Object value, Map<Object, Boolean> visited, int depth) {
        if (value == null || depth > 2 || visited.put(value, true) != null) return;
        Bounds direct = rectangleFrom(value);
        if (direct != null) {
            result.add(direct);
            return;
        }
        for (String methodName : List.of("getBounds", "bounds", "getArea", "area")) {
            Object nested = invoke(value, methodName);
            Bounds bounds = rectangleFrom(nested);
            if (bounds != null) result.add(bounds);
        }
        if (value instanceof Iterable<?> iterable) {
            for (Object element : iterable) collectRectangles(result, element, visited, depth + 1);
        } else if (value instanceof Map<?, ?> map) {
            for (Object element : map.values()) collectRectangles(result, element, visited, depth + 1);
        } else if (value.getClass().isArray()) {
            int length = java.lang.reflect.Array.getLength(value);
            for (int i = 0; i < length; i++) collectRectangles(result, java.lang.reflect.Array.get(value, i), visited, depth + 1);
        }
    }

    private static void addRectangle(List<Bounds> result, Object rectangle) {
        Bounds bounds = rectangleFrom(rectangle);
        if (bounds != null) result.add(bounds);
    }

    private static Bounds rectangleFrom(Object rectangle) {
        if (rectangle == null) return null;
        Integer x = readInt(rectangle, "getX", "x");
        Integer y = readInt(rectangle, "getY", "y");
        Integer width = readInt(rectangle, "getWidth", "width");
        Integer height = readInt(rectangle, "getHeight", "height");
        if (x == null || y == null || width == null || height == null) return null;
        return new Bounds(x, y, width, height);
    }

    private static Integer readInt(Object target, String getter, String accessor) {
        Object value = invoke(target, getter);
        if (!(value instanceof Number)) value = invoke(target, accessor);
        if (value instanceof Number number) return number.intValue();
        try {
            Field field = target.getClass().getField(accessor);
            Object fieldValue = field.get(target);
            return fieldValue instanceof Number number ? number.intValue() : null;
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static Object optionalValue(Object value) {
        return value instanceof Optional<?> optional ? optional.orElse(null) : value;
    }

    private static Object invoke(Object target, String methodName) {
        if (target == null) return null;
        Method method = findMethod(target.getClass(), methodName);
        if (method == null) return null;
        try {
            method.setAccessible(true);
            return method.invoke(target);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    private static boolean invokeBoolean(Object target, String methodName, boolean fallback) {
        Method method = target == null ? null : findMethod(target.getClass(), methodName);
        return method == null ? fallback : invokeBoolean(target, method, fallback);
    }

    private static boolean invokeBoolean(Object target, Method method, boolean fallback) {
        try {
            method.setAccessible(true);
            Object value = method.invoke(target);
            return value instanceof Boolean bool ? bool : fallback;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return fallback;
        }
    }

    private static Method findMethod(Class<?> type, String name) {
        for (Method method : type.getMethods()) {
            if (method.getName().equals(name) && method.getParameterCount() == 0) return method;
        }
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            for (Method method : current.getDeclaredMethods()) {
                if (method.getName().equals(name) && method.getParameterCount() == 0) return method;
            }
        }
        return null;
    }

    private static Object readField(Object target, String fieldName) {
        if (target == null) return null;
        for (Class<?> current = target.getClass(); current != null; current = current.getSuperclass()) {
            try {
                Field field = current.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field.get(target);
            } catch (ReflectiveOperationException | RuntimeException ignored) {
            }
        }
        return null;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
