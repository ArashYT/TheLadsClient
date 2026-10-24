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
        addOption(new BoolOption("GPU Entity Instancing", false) {
            @Override
            public void set(boolean value) {
                super.set(value);
                Perf189Settings.gpuEntityInstancing = value;
            }

            @Override
            public void toggle() {
                super.toggle();
                Perf189Settings.gpuEntityInstancing = get();
            }

            @Override
            public void reset() {
                super.reset();
                Perf189Settings.gpuEntityInstancing = get();
            }

            @Override
            public void load(JsonElement element) {
                super.load(element);
                Perf189Settings.gpuEntityInstancing = get();
            }
        });
        addOption(new BoolOption("Starlight Lighting Engine", false) {
            @Override
            public void set(boolean value) {
                super.set(value);
                Perf189Settings.starlightLighting = value;
            }

            @Override
            public void toggle() {
                super.toggle();
                Perf189Settings.starlightLighting = get();
            }

            @Override
            public void reset() {
                super.reset();
                Perf189Settings.starlightLighting = get();
            }

            @Override
            public void load(JsonElement element) {
                super.load(element);
                Perf189Settings.starlightLighting = get();
            }
        });
        addOption(new BoolOption("Camera Parallax Fix", false) {
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
        addOption(new BoolOption("Chunk Culling Fix", false) {
            @Override
            public void set(boolean value) {
                super.set(value);
                Perf189Settings.chunkCullingFix = value;
            }

            @Override
            public void toggle() {
                super.toggle();
                Perf189Settings.chunkCullingFix = get();
            }

            @Override
            public void reset() {
                super.reset();
                Perf189Settings.chunkCullingFix = get();
            }

            @Override
            public void load(JsonElement element) {
                super.load(element);
                Perf189Settings.chunkCullingFix = get();
            }
        });
    }
}
