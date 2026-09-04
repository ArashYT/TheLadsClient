package com.thelads.core.modules;

import com.thelads.core.config.BoolOption;
import com.thelads.core.config.Module;

public class ZoomModule extends Module {
    private static ZoomModule instance;
    private float currentFovMultiplier = 1.0f;
    private float prevFovMultiplier = 1.0f;
    private float targetFovMultiplier = 1.0f;
    private final BoolOption smoothZoom;
    private final BoolOption scrollZoom;

    public ZoomModule() {
        super("Zoom", "Hold the zoom key to narrow your FOV.");
        smoothZoom = new BoolOption("Smooth Zoom", true);
        scrollZoom = new BoolOption("Scroll to Zoom", true);
        addOption(smoothZoom);
        addOption(scrollZoom);
        instance = this;
    }

    public static ZoomModule getInstance() {
        return instance;
    }

    public void tick(boolean keyDown) {
        prevFovMultiplier = currentFovMultiplier;
        boolean active = isEnabled() && keyDown;
        if (!active) {
            targetFovMultiplier = 1.0f;
        } else if (targetFovMultiplier == 1.0f) {
            targetFovMultiplier = 0.25f;
        }

        if (smoothZoom.get()) {
            currentFovMultiplier = lerp(0.3f, currentFovMultiplier, targetFovMultiplier);
        } else {
            currentFovMultiplier = targetFovMultiplier;
        }
    }

    public float getFovMultiplier(float tickDelta) {
        if (smoothZoom.get()) {
            return lerp(tickDelta, prevFovMultiplier, currentFovMultiplier);
        }
        return currentFovMultiplier;
    }

    public boolean isActive() {
        return targetFovMultiplier != 1.0f;
    }

    public void onScroll(double amount) {
        if (!isEnabled() || !scrollZoom.get()) {
            return;
        }
        if (amount > 0) {
            targetFovMultiplier = clamp(targetFovMultiplier - 0.05f, 0.05f, 0.8f);
        } else if (amount < 0) {
            targetFovMultiplier = clamp(targetFovMultiplier + 0.05f, 0.05f, 0.8f);
        }
    }

    private static float lerp(float delta, float start, float end) {
        return start + delta * (end - start);
    }

    private static float clamp(float val, float min, float max) {
        return Math.max(min, Math.min(max, val));
    }
}
