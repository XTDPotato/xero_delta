package com.xtdpotato.xero_delta.screen;

import org.joml.Matrix4f;
import org.joml.Vector3f;

/** The fixed loot window shared by clipped painting and pointer hit testing. */
record CorpseLootViewport(int left, int top, int right, int bottom) {
    boolean contains(double x, double y) {
        return x >= left && x < right && y >= top && y < bottom;
    }

    CorpseLootViewport screenBounds(Matrix4f pose, int offsetX, int offsetY) {
        Vector3f first = pose.transformPosition(new Vector3f(left + offsetX, top + offsetY, 0));
        Vector3f last = pose.transformPosition(new Vector3f(right + offsetX, bottom + offsetY, 0));
        int x = (int) Math.ceil(first.x);
        int y = (int) Math.ceil(first.y);
        return new CorpseLootViewport(x, y,
            Math.max(x, (int) Math.floor(last.x)), Math.max(y, (int) Math.floor(last.y)));
    }
}
