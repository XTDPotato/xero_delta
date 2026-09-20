package com.xtdpotato.xero_delta.client;

import com.xtdpotato.xero_delta.XeroDelta;
import com.xtdpotato.xero_delta.grid.GridBackingStore;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Short client-side lock pulse shown after a confirmed safety-box grid change. */
public final class SafetyBoxLockAnimation {
    private static final ResourceLocation LOCK_TEXTURE = ResourceLocation.fromNamespaceAndPath(
        XeroDelta.MOD_ID, "textures/quality/lock.png");
    private static final int TEXTURE_WIDTH = 76;
    private static final int TEXTURE_HEIGHT = 76;
    private static final int TOP_LAYER_Z = 2_000;
    private static final Map<Key, Long> START_TIMES = new HashMap<>();

    private SafetyBoxLockAnimation() {
    }

    public static void onGridSync(int containerIndex, GridBackingStore previous, List<ItemStack> updated) {
        if (previous == null) return;
        int limit = Math.min(previous.getSize(), updated.size());
        long now = System.nanoTime();
        for (int index = 0; index < limit; index++) {
            ItemStack before = previous.getItemRaw(index % previous.getWidth(), index / previous.getWidth());
            ItemStack after = updated.get(index);
            Key key = new Key(Math.max(0, containerIndex), index);
            if (hasChanged(before, after)) {
                START_TIMES.put(key, now);
            }
        }
    }

    public static void render(GuiGraphics graphics, int containerIndex, int anchorIndex,
                              int x, int y, int width, int height) {
        Key key = new Key(Math.max(0, containerIndex), anchorIndex);
        Long started = START_TIMES.get(key);
        if (started == null) return;
        long elapsed = System.nanoTime() - started;
        if (elapsed >= LockFadeCurve.DURATION_NANOS) {
            START_TIMES.remove(key);
            return;
        }
        float alpha = LockFadeCurve.alpha(elapsed);
        if (alpha <= 0.0F) return;

        LockIconLayout.Bounds bounds = LockIconLayout.contain(
            x, y, width, height, TEXTURE_WIDTH, TEXTURE_HEIGHT);
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(0, 0, TOP_LAYER_Z);
        try {
            graphics.setColor(1.0F, 1.0F, 1.0F, alpha);
            graphics.blit(LOCK_TEXTURE, bounds.x(), bounds.y(), bounds.width(), bounds.height(),
                0, 0, TEXTURE_WIDTH, TEXTURE_HEIGHT, TEXTURE_WIDTH, TEXTURE_HEIGHT);
        } finally {
            graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
            pose.popPose();
        }
    }

    public static void reset() {
        START_TIMES.clear();
    }

    public static boolean hasActiveAnimations() {
        long now = System.nanoTime();
        START_TIMES.entrySet().removeIf(entry -> now - entry.getValue() >= LockFadeCurve.DURATION_NANOS);
        return !START_TIMES.isEmpty();
    }

    static boolean hasChanged(ItemStack before, ItemStack after) {
        boolean beforeEmpty = before == null || before.isEmpty();
        boolean afterEmpty = after == null || after.isEmpty();
        if (beforeEmpty || afterEmpty) return beforeEmpty != afterEmpty;
        if (!GridBackingStore.isSameItemIgnoringRotation(before, after)) return true;
        return before.getCount() != after.getCount()
            || GridBackingStore.isRotated(before) != GridBackingStore.isRotated(after);
    }

    private record Key(int containerIndex, int anchorIndex) {
    }
}
