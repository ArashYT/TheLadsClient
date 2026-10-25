package com.thelads.core.modules;

import com.google.gson.JsonElement;
import com.thelads.core.config.BoolOption;
import com.thelads.core.config.Module;
import com.thelads.core.perf.FontWidthCache;
import com.thelads.core.perf.Perf189Settings;

public class PerformanceManagerModule extends Module {
    public PerformanceManagerModule() {
        super("Performance", "Tweak memory allocation, GC pacing, and rendering hints.");
        addOption(new BoolOption("Aggressive Memory Cleaner", false));
        addOption(new BoolOption("Reduce Background FPS", true));
        addOption(new BoolOption("Global Text Width Caching", true) {
            @Override
            public void set(boolean value) {
                super.set(value);
                FontWidthCache.enabled = value;
            }

            @Override
            public void toggle() {
                super.toggle();
                FontWidthCache.enabled = get();
            }

            @Override
            public void reset() {
                super.reset();
                FontWidthCache.enabled = get();
            }

            @Override
            public void load(JsonElement element) {
                super.load(element);
                FontWidthCache.enabled = get();
            }
        });
        addOption(new BoolOption("Camera Parallax Fix (1.8.9)", false) {
            @Override
            public void set(boolean value) {
                super.set(value);
                Perf189Settings.cameraParallaxFix = value;
            }

            @Override
            public void toggle() {
                super.toggle();
                Perf189Settings.cameraParallaxFix = get();
            }

            @Override
            public void reset() {
                super.reset();
                Perf189Settings.cameraParallaxFix = get();
            }

            @Override
            public void load(JsonElement element) {
                super.load(element);
                Perf189Settings.cameraParallaxFix = get();
            }
        });
    }
}
