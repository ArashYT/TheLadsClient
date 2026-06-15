/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.Minecraft
 */
package com.thelads.core.modules;

import com.thelads.core.config.CycleOption;
import com.thelads.core.config.Module;
import net.minecraft.client.Minecraft;

public class PerformanceManagerModule
extends Module {
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
        if (!this.isEnabled() || mc.level == null) {
            return;
        }
        long currentTime = System.currentTimeMillis();
        this.fpsSum += mc.getFps();
        ++this.fpsCount;
        if (currentTime - this.lastCheckTime > 5000L) {
            if (this.fpsCount > 0) {
                int avgFps = this.fpsSum / this.fpsCount;
                int targetFps = Integer.parseInt(((CycleOption)this.getOption("Target FPS")).getValue());
                int minDistance = Integer.parseInt(((CycleOption)this.getOption("Min Distance")).getValue());
                int maxDistance = Integer.parseInt(((CycleOption)this.getOption("Max Distance")).getValue());
                int currentDist = (Integer)mc.options.renderDistance().get();
                if (avgFps < targetFps - 10) {
                    int diff = targetFps - avgFps;
                    int step = Math.max(1, diff / 10);
                    if (currentDist > minDistance) {
                        mc.options.renderDistance().set((Object)Math.max(minDistance, currentDist - step));
                    }
                } else if (avgFps > targetFps + 20) {
                    int diff = avgFps - targetFps;
                    int step = Math.max(1, diff / 10);
                    if (currentDist < maxDistance) {
                        mc.options.renderDistance().set((Object)Math.min(maxDistance, currentDist + step));
                    }
                }
            }
            this.fpsSum = 0;
            this.fpsCount = 0;
            this.lastCheckTime = currentTime;
        }
    }
}

