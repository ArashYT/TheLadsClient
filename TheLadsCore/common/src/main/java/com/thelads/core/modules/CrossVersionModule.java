package com.thelads.core.modules;

import com.thelads.core.config.BoolOption;
import com.thelads.core.config.Module;

/**
 * Cross-version server join module (parity with ViaForge / ViaFabricPlus).
 * Allows 1.8.9 players to join 1.9 through modern Minecraft servers via protocol translation.
 * Gated by HypixelSafetyManager (default OFF).
 */
public final class CrossVersionModule extends Module {
    public static final String NAME = "Cross-Version Join";

    public final BoolOption autoDetect = addOption(new BoolOption("Auto Protocol Detect", true));
    public final BoolOption showVersionButton = addOption(new BoolOption("Show Server Version Button", true));

    public CrossVersionModule() {
        super(NAME, "Allows joining modern Minecraft servers on 1.8.9 via protocol translation (ViaForge / ViaFabricPlus).");
        setEnabled(false); // GATED default OFF
    }
}
