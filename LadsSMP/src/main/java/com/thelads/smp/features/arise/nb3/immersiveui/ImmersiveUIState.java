package com.thelads.smp.features.arise.nb3.immersiveui;

import com.thelads.smp.features.arise.nb3.AriseNb3Config;

/**
 * State tracking and interpolation math for Immersive UI animations:
 * - Smooth sliding hotbar selection box
 * - Floating carried item bobbing/rotation
 * - Subtle camera screen shake on damage
 */
public final class ImmersiveUIState {
    private static float currentSelectorX = Float.NaN;
    private static int lastTargetX = -1;
    private static int shakeTicks = 0;

    private ImmersiveUIState() {}

    /**
     * Calculates the smoothly sliding X coordinate for the hotbar selection box.
     */
    public static int getInterpolatedHotbarX(int targetX, float partialTick) {
        if (!AriseNb3Config.get().immersiveUI.enabled || !AriseNb3Config.get().immersiveUI.enableHotbarSelectorAnimation) {
            return targetX;
        }

        if (Float.isNaN(currentSelectorX) || lastTargetX == -1) {
            currentSelectorX = targetX;
            lastTargetX = targetX;
            return targetX;
        }

        lastTargetX = targetX;
        float speed = AriseNb3Config.get().immersiveUI.hotbarSelectorSpeed;
        float diff = targetX - currentSelectorX;

        if (Math.abs(diff) < 0.2f) {
            currentSelectorX = targetX;
        } else {
            // Smooth exponential slide toward target
            float step = Math.min(1.0f, speed * 0.25f);
            currentSelectorX += diff * step;
        }

        return Math.round(currentSelectorX);
    }

    /**
     * Calculates the subtle floating bobbing offset for cursor-carried items in container screens.
     */
    public static float getFloatingItemBob(long timeMs) {
        if (!AriseNb3Config.get().immersiveUI.enabled || !AriseNb3Config.get().immersiveUI.enableFloatingItemRotation) {
            return 0.0f;
        }
        float speed = AriseNb3Config.get().immersiveUI.floatingItemEasingSpeed;
        float amp = AriseNb3Config.get().immersiveUI.floatingItemRotationAmplitude;
        return (float) (Math.sin(timeMs * 0.003f * speed) * amp * 2.0f);
    }

    public static void onHurt() {
        if (AriseNb3Config.get().immersiveUI.enabled && AriseNb3Config.get().immersiveUI.enableScreenShake) {
            shakeTicks = AriseNb3Config.get().immersiveUI.shakeTimer;
        }
    }

    public static void tick() {
        if (shakeTicks > 0) shakeTicks--;
    }

    public static float getShakeOffset() {
        if (shakeTicks <= 0) return 0.0f;
        float progress = (float) shakeTicks / (float) AriseNb3Config.get().immersiveUI.shakeTimer;
        float amp = AriseNb3Config.get().immersiveUI.shakeAmplitude;
        return (float) ((Math.random() - 0.5) * 2.0 * amp * progress);
    }

    public static void reset() {
        currentSelectorX = Float.NaN;
        lastTargetX = -1;
        shakeTicks = 0;
    }
}
