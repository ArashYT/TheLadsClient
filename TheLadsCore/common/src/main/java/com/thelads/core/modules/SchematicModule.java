package com.thelads.core.modules;

import com.thelads.core.client.bridge.LadsGameBridge;
import com.thelads.core.client.schematic.SchematicManager;
import com.thelads.core.client.util.FileDialogs;
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

    public final SliderOption originX = addOption(new SliderOption(SchematicManager.ORIGIN_X_OPTION, 0, -10000, 10000, 1));
    public final SliderOption originY = addOption(new SliderOption(SchematicManager.ORIGIN_Y_OPTION, 64, -64, 320, 1));
    public final SliderOption originZ = addOption(new SliderOption(SchematicManager.ORIGIN_Z_OPTION, 0, -10000, 10000, 1));

    public final ActionOption loadFileAction = addOption(new ActionOption("Load Schematic", "Browse..."));
    public final ActionOption setOriginAction = addOption(new ActionOption("Set Origin to Player", "Set Origin"));
    public final ActionOption clearAction = addOption(new ActionOption("Clear Blueprint", "Clear"));

    public SchematicModule() {
        super(NAME, "Read-only ghost blueprint visualizer projecting schematic build guides in the world without automated placement.");
        setEnabled(false); // Default OFF per anti-ban rule

        loadFileAction.setAction(() -> {
            FileDialogs.openFile("Choose a schematic (.schematic)", "Schematic files (*.schematic)", "schematic")
                .thenAccept(path -> {
                    if (path != null) {
                        SchematicManager.loadSchematicFile(path.toFile());
                    }
                });
        });

        setOriginAction.setAction(() -> {
            LadsGameBridge bridge = LadsGameBridge.get();
            SchematicManager.setOrigin(bridge.getPlayerX(), bridge.getPlayerY(), bridge.getPlayerZ());
        });

        clearAction.setAction(SchematicManager::clear);
    }
}
