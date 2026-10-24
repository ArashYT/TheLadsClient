package com.thelads.core.modules;

import com.thelads.core.config.BoolOption;
import com.thelads.core.config.DropdownOption;
import com.thelads.core.config.Module;

public class PerformanceManagerModule extends Module {
    public PerformanceManagerModule() {
        super("Performance", "Tweak memory allocation, GC pacing, and rendering hints.");
        addOption(new BoolOption("Aggressive Memory Cleaner", false));
        addOption(new BoolOption("Reduce Background FPS", true));
        addOption(new BoolOption("Turbo Entities", true));
        addOption(new BoolOption("Global Text Width Caching", true));
        addOption(new BoolOption("GUI Item Baking", true));
        addOption(new BoolOption("Deferred Sky Rendering", true));
        addOption(new BoolOption("Lighting & Shadow Caching", true));
        addOption(new BoolOption("Particle Section Culling", true));
        addOption(new BoolOption("Batched Particle Pipeline", true));
        addOption(new BoolOption("Sodium Vertex Pipeline Hook", true));
        addOption(new DropdownOption("Low Latency Pacing", 1, "Off", "On", "Boost"));
        addOption(new BoolOption("Vendor Low-Latency APIs", true));
        addOption(new BoolOption("Vulkan Present Wait", true));
    }

    public static boolean bool(String name, boolean fallback) {
        var manager = com.thelads.core.config.ModuleManager.getInstance();
        if (manager == null) return fallback;
        var mod = manager.getModule("Performance");
        if (mod instanceof PerformanceManagerModule pmm) {
            var opt = pmm.getOption(name);
            if (opt instanceof BoolOption bo) return bo.get();
        }
        return fallback;
    }

    public static boolean isTurboEntities() {
        return bool("Turbo Entities", true);
    }

    public static int lowLatencyMode() {
        var manager = com.thelads.core.config.ModuleManager.getInstance();
        if (manager == null) return 1;
        var mod = manager.getModule("Performance");
        if (mod instanceof PerformanceManagerModule pmm) {
            var opt = pmm.getOption("Low Latency Pacing");
            if (opt instanceof DropdownOption drop) return drop.getIndex();
        }
        return 1; // Default: On
    }
}
