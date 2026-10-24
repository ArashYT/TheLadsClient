package com.thelads.core.modules;

import com.thelads.core.client.schematic.SchematicManager;
import com.thelads.core.config.ActionOption;
import com.thelads.core.config.BoolOption;
import com.thelads.core.config.ColorOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.SliderOption;

/**
 * Schematic blueprint overlay visualizer module (parity with Badlion Schematica).
 * Strictly read-only holographic ghost blueprint display without automated placement mechanics.
 * Gated by HypixelSafetyManager (default OFF).
 */
public final class SchematicModule extends Module {
    public static final String NAME = SchematicManager.NAME;

    public final ColorOption ghostTint = addOption(new ColorOption(SchematicManager.TINT_OPTION, false, 0x8055FFFF));
    public final SliderOption renderDistance = addOption(new SliderOption(SchematicManager.DISTANCE_OPTION, 32, 8, 64, 8).suffix("m"));
    public final BoolOption boundingBoxOnly = addOption(new BoolOption(SchematicManager.BOUNDING_BOX_ONLY, false));
    public final ActionOption clearAction = addOption(new ActionOption("Clear Blueprint", "Clear"));

    public SchematicModule() {
        super(NAME, "Read-only ghost blueprint visualizer projecting schematic build guides in the world without automated placement.");
        setEnabled(false); // Default OFF per anti-ban rule
        clearAction.setAction(SchematicManager::clear);
    }
}
