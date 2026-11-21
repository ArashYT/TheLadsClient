package com.thelads.core.modules;

import com.thelads.core.config.BoolOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.SliderOption;
import com.thelads.core.config.StringOption;

/** Audio Engine: smarter OpenAL source use, sound dedup, decode preload, audio LOD, cheap occlusion and a sound blacklist. */
public final class AudioEngineModule extends Module {
    public static final String NAME = "Audio Engine";

    public final BoolOption pool, dedup, decodeCache, lod, occlusion, blacklist;
    public final SliderOption ambientCap, hostileCap, blockCap, dedupThreshold, dedupDistance, cullFactor,
        occlusionTicks, rayBudget;
    public final StringOption blacklistPatterns;

    public AudioEngineModule() {
        super(NAME, "Keeps important sounds playing when the sound pool is full, merges identical sounds, skips inaudible ones and can muffle sounds behind walls.");
        pool = addOption(new BoolOption("Smart Source Pool", true));
        ambientCap = addOption(new SliderOption("Ambient Cap", 50, 1, 256, 1));
        hostileCap = addOption(new SliderOption("Hostile Cap", 80, 1, 256, 1));
        blockCap = addOption(new SliderOption("Block Cap", 60, 1, 256, 1));
        dedup = addOption(new BoolOption("Deduplication", true));
        dedupThreshold = addOption(new SliderOption("Dedup Threshold", 5, 2, 100, 1));
        dedupDistance = addOption(new SliderOption("Dedup Distance", 8, 1, 64, 1).suffix(" blocks"));
        decodeCache = addOption(new BoolOption("Decode Cache", true));
        lod = addOption(new BoolOption("Audio LOD", true));
        cullFactor = addOption(new SliderOption("Cull Distance", 1.0, 0.5, 2.0, 0.1).suffix("x"));
        occlusion = addOption(new BoolOption("Occlusion", false));
        occlusionTicks = addOption(new SliderOption("Occlusion Cache Ticks", 5, 1, 40, 1));
        rayBudget = addOption(new SliderOption("Ray Budget", 64, 16, 256, 16).suffix("/tick"));
        blacklist = addOption(new BoolOption("Blacklist", false));
        blacklistPatterns = addOption(new StringOption("Blacklist Patterns", ""));
        setEnabled(true);
    }
}
