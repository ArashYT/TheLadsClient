package com.thelads.core.modules;

import com.thelads.core.config.DropdownOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.SliderOption;

/**
 * Chunk Animator (Feather): chunks coming into view rise from below (or drop from above) into place, easing out over Duration
 * (client.ChunkAnimator). Needs a renderer that draws chunk by chunk: 1.8.9 with OptiFine (Render Regions off) or with neither
 * OptiFine nor Ionium; Ionium's and Sodium's renderers draw whole regions at once, so there it is unavailable. Visual only.
 */
public final class ChunkAnimatorModule extends Module {
    public static final String NAME = "ChunkAnimator";

    public final DropdownOption mode = addOption(new DropdownOption("Mode", 0, "From Below", "From Above"));
    public final SliderOption duration = addOption(new SliderOption("Duration", 1000, 250, 3000, 50).suffix(" ms"));

    public ChunkAnimatorModule() {
        super(NAME, "Chunks coming into view rise from below or drop from above into place.");
    }
}
