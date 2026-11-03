package com.thelads.core.modules;

import com.thelads.core.config.BoolOption;
import com.thelads.core.config.Module;

/** Nick Detector: marks players who are probably nicked (NickDetector). Blocked by server rules until the player bypasses it. */
public class NickDetectorModule extends Module {
    public static final String NAME = "Nick Detector";

    public final BoolOption chatNotice;

    public NickDetectorModule() {
        super(NAME, "Hypixel only. Marks players who are probably nicked on their name tag and in the tab list. It never shows who they are.");
        chatNotice = addOption(new BoolOption("Chat notice", false));
        setEnabled(false);
    }
}
