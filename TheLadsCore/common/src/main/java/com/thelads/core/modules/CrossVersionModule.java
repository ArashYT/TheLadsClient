package com.thelads.core.modules;

import com.thelads.core.config.DropdownOption;
import com.thelads.core.config.Module;

/**
 * Cross-Version Join: connects to modern or legacy multiplayer servers across protocol versions (ViaFabricPlus on 1.20.1/26.3).
 * GATED: sends translated packets.
 */
public class CrossVersionModule extends Module {
    public static final String NAME = "Cross-Version Join";

    public final DropdownOption targetProtocol;

    public CrossVersionModule() {
        super(NAME, "Protocol translation for connecting to different server versions (ViaFabricPlus on modern).");
        targetProtocol = addOption(new DropdownOption("Target version", 0, "Auto", "1.20.4", "1.20.1", "1.19.4", "1.16.5", "1.12.2", "1.8.9"));
        setEnabled(false); // GATED: defaults off
    }
}
