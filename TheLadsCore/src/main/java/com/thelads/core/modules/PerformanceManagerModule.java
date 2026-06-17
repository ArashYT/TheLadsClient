package com.thelads.core.modules;

import com.thelads.core.config.CycleOption;
import com.thelads.core.config.Module;
import net.minecraft.client.Minecraft;

public class PerformanceManagerModule extends Module {
    private long lastCheckTime;
    private int fpsSum = 0;
    private int fpsCount = 0;

    public PerformanceManagerModule() {
        super("PerformanceManager", "Dynamically adjusts render distance based on FPS to improve performance.");
        this.addOption(new CycleOption("Target FPS", 1, "30", "60", "120", "144", "240"));
        this.addOption(new CycleOption("Min Distance", 0, "2", "4", "6", "8"));
        this.addOption(new CycleOption("Max Distance", 3, "8", "12", "16", "24", "32"));
        this.lastCheckTime = System.currentTimeMillis();
    }

    public void tick(Minecraft mc) {
        if (!this.isEnabled() || mc.level == null) return;

        // Don't adjust render distance while the window is unfocused. DynamicFPS caps the
        // framerate when unfocused (e.g. 60 or lower), so the measured FPS is artificial —
        // letting it shrink the render distance here would wrongly stick after refocusing.
        if (!mc.isWindowActive()) {
            fpsSum = 0;
            fpsCount = 0;
            lastCheckTime = System.currentTimeMillis();
            return;
        }

        long currentTime = System.currentTimeMillis();
        fpsSum += mc.getFps();
        fpsCount++;

        if (currentTime - lastCheckTime > 5000) { // evaluate every 5 seconds
            if (fpsCount > 0) {
                int avgFps = fpsSum / fpsCount;
                int targetFps = Integer.parseInt(((CycleOption) getOption("Target FPS")).getValue());
                int minDistance = Integer.parseInt(((CycleOption) getOption("Min Distance")).getValue());
                int maxDistance = Integer.parseInt(((CycleOption) getOption("Max Distance")).getValue());

                int currentDist = mc.options.renderDistance().get();

                if (avgFps < targetFps - 10) {
                    int diff = targetFps - avgFps;
                    int step = Math.max(1, diff / 10);
                    if (currentDist > minDistance) {
                        mc.options.renderDistance().set(Math.max(minDistance, currentDist - step));
                    }
                } else if (avgFps > targetFps + 20) {
                    int diff = avgFps - targetFps;
                    int step = Math.max(1, diff / 10);
                    if (currentDist < maxDistance) {
                        mc.options.renderDistance().set(Math.min(maxDistance, currentDist + step));
                    }
                }
            }

            fpsSum = 0;
            fpsCount = 0;
            lastCheckTime = currentTime;
        }
    }
}
