package com.thelads.core.modules;

import com.thelads.core.config.BoolOption;
import com.thelads.core.config.Module;

/**
 * Mumble Link: integrates positional 3D audio tracking with external Mumble VoIP client (parity with Lunar / Badlion).
 */
public class MumbleLinkModule extends Module {
    public static final String NAME = "Mumble Link";

    public final BoolOption link3dAudio;
    public final BoolOption showStatus;

    public MumbleLinkModule() {
        super(NAME, "Integrates positional 3D directional audio with external Mumble client.");
        link3dAudio = addOption(new BoolOption("Positional 3D audio", true));
        showStatus = addOption(new BoolOption("Show status indicator", false));
        setEnabled(false);
    }
}
