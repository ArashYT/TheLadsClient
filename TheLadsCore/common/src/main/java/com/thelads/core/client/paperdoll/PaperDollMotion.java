/* This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * See assets/theladscore/licenses/PaperDoll-MPL-2.0.txt in the distributed Core JAR.
 * Adapted from Fuzss Paper Doll 26.2.3, commit 5968f6f523a2ddc46e5890bd47dc6a5d9bf48b29.
 */
package com.thelads.core.client.paperdoll;

/** Tick-driven visibility and head easing, independent of native player/render objects. */
public final class PaperDollMotion {
    private int remaining, sinceRiding = Integer.MAX_VALUE;
    private float yaw, previousYaw;

    public void reset() { remaining = 0; sinceRiding = Integer.MAX_VALUE; yaw = previousYaw = 0; }
    public boolean recentlyRiding(int displayTime) { return sinceRiding < Math.max(0, displayTime - 1); }
    public boolean visible(boolean always, int displayTime) { return always || displayTime == 0 || remaining > 0; }
    public void tick(boolean enabled, boolean paused, boolean always, int displayTime,
                     boolean action, boolean riding, float headDelta, float maxYaw) {
        if (!enabled) { reset(); return; }
        if (paused) return;
        displayTime = Math.max(0, displayTime);
        if (!always && displayTime != 0) {
            if (action) remaining = displayTime;
            else if (remaining > 0) remaining--;
        }
        if (riding) sinceRiding = 0;
        else if (sinceRiding < Integer.MAX_VALUE) sinceRiding++;
        if (!visible(always, displayTime)) { yaw = previousYaw = 0; return; }
        previousYaw = yaw;
        float delta = Float.isFinite(headDelta) ? wrap(headDelta) : 0;
        float limit = Float.isFinite(maxYaw) ? Math.max(0, Math.min(90, maxYaw)) : 30;
        yaw = Math.max(-limit, Math.min(limit, yaw + delta * .5f)) * .9f;
    }
    public float yaw(float partialTick) {
        float partial = Float.isFinite(partialTick) ? Math.max(0, Math.min(1, partialTick)) : 0;
        return previousYaw + (yaw - previousYaw) * partial;
    }
    private static float wrap(float degrees) { float value = degrees % 360; return value >= 180 ? value - 360 : value < -180 ? value + 360 : value; }
}
