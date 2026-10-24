package com.thelads.core.modules;

import com.thelads.core.config.BoolOption;
import com.thelads.core.config.Module;

/**
 * Replay: camera path recording and playback indicator (parity with ReplayMod / Flashback / Badlion Replay).
 */
public class ReplayModule extends Module {
    public static final String NAME = "Replay";

    public final BoolOption recordingIndicator;
    public final BoolOption autoRecord;

    public ReplayModule() {
        super(NAME, "Cinematic replay recording and indicator (ReplayMod on 1.20.1, Flashback on 26.3).");
        recordingIndicator = addOption(new BoolOption("Recording indicator", true));
        autoRecord = addOption(new BoolOption("Auto-record matches", false));
        setEnabled(false);
    }
}
