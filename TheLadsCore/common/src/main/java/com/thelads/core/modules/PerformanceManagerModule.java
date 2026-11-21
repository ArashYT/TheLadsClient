package com.thelads.core.modules;

import com.google.gson.JsonElement;
import com.thelads.core.config.BoolOption;
import com.thelads.core.config.Module;
import com.thelads.core.perf.ChatAsync;
import com.thelads.core.perf.ChunkPriority;
import com.thelads.core.perf.FlushBatching;
import com.thelads.core.perf.FontWidthCache;
import com.thelads.core.perf.MemoryGuard;
import com.thelads.core.perf.Perf189Settings;
import java.util.function.Consumer;

public class PerformanceManagerModule extends Module {
    /** A BoolOption whose value is mirrored into a static flag on every change and load. */
    private static final class Mirrored extends BoolOption {
        private final Consumer<Boolean> sink;

        Mirrored(String name, boolean def, Consumer<Boolean> sink) {
            super(name, def);
            this.sink = sink;
            sink.accept(def);
        }

        @Override public void set(boolean value) { super.set(value); sink.accept(get()); }
        @Override public void toggle() { super.toggle(); sink.accept(get()); }
        @Override public void reset() { super.reset(); sink.accept(get()); }
        @Override public void load(JsonElement element) { super.load(element); sink.accept(get()); }
    }

    public PerformanceManagerModule() {
        super("Performance", "Tweak memory allocation, GC pacing, and rendering hints.");
        addOption(new Mirrored("Aggressive Memory Cleaner", false, v -> MemoryGuard.aggressiveCleaner = v));
        addOption(new Mirrored("Memory Guard", true, v -> MemoryGuard.enabled = v));
        addOption(new Mirrored("Packet Flush Batching", true, v -> FlushBatching.enabled = v));
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
        addOption(new BoolOption("Async Chat", true) {
            @Override
            public void set(boolean value) {
                super.set(value);
                ChatAsync.enabled = value;
            }

            @Override
            public void toggle() {
                super.toggle();
                ChatAsync.enabled = get();
            }

            @Override
            public void reset() {
                super.reset();
                ChatAsync.enabled = get();
            }

            @Override
            public void load(JsonElement element) {
                super.load(element);
                ChatAsync.enabled = get();
            }
        });
        addOption(new BoolOption("Smart Chunk Priority", true) {
            @Override
            public void set(boolean value) {
                super.set(value);
                ChunkPriority.enabled = value;
            }

            @Override
            public void toggle() {
                super.toggle();
                ChunkPriority.enabled = get();
            }

            @Override
            public void reset() {
                super.reset();
                ChunkPriority.enabled = get();
            }

            @Override
            public void load(JsonElement element) {
                super.load(element);
                ChunkPriority.enabled = get();
            }
        });
    }
}
