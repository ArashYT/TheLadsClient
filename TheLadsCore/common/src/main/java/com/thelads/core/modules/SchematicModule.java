package com.thelads.core.modules;

import com.thelads.core.config.BoolOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.SliderOption;

/**
 * Schematic: displays 3D holographic schematic blueprints in world (parity with Badlion / Schematica).
 * GATED: shows structure outlines in world. Strictly NO printer mode.
 */
public class SchematicModule extends Module {
    public static final String NAME = "Schematic";

    public final SliderOption opacity;
    public final BoolOption highlightMismatch;
    public final SliderOption renderRange;

    public SchematicModule() {
        super(NAME, "Displays 3D schematic blueprints in world (visual overlay only; printer is prohibited).");
        opacity = addOption(new SliderOption("Overlay opacity", 60, 10, 100, 5).percent());
        highlightMismatch = addOption(new BoolOption("Highlight missing blocks", true));
        renderRange = addOption(new SliderOption("Render range", 16, 4, 32, 2));
        setEnabled(false); // GATED: defaults off
    }
}
