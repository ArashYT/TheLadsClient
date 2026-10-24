package com.thelads.core.client;

/**
 * Handles hotbar mouse-wheel scrolling adjustments: invert scroll direction and disable wrap-around.
 */
public final class HotbarScrollHelper {
    private HotbarScrollHelper() {}

    public static int computeTargetSlot(int currentSlot, int direction, boolean invert, boolean noWrap) {
        if (invert) {
            direction = -direction;
        }
        int target = currentSlot - direction;
        if (noWrap) {
            if (target < 0) return 0;
            if (target > 8) return 8;
            return target;
        } else {
            return (target % 9 + 9) % 9;
        }
    }
}
