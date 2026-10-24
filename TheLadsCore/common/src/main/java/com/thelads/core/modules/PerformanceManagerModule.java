package com.thelads.core.modules;

import com.google.gson.JsonElement;
import com.thelads.core.config.BoolOption;
import com.thelads.core.config.Module;
import com.thelads.core.perf.FontWidthCache;

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
    }
}
