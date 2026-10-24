package com.thelads.core.modules;

import com.thelads.core.config.BoolOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.TextOption;

/**
 * Pack Profiles: save and switch between named sets of active resource packs (parity with OneClient packed_packs).
 */
public class PackProfilesModule extends Module {
    public static final String NAME = "Pack Profiles";

    public final TextOption activeProfile;
    public final BoolOption autoSave;

    public PackProfilesModule() {
        super(NAME, "Save and load named presets of enabled resource packs.");
        activeProfile = addOption(new TextOption("Active profile", ""));
        autoSave = addOption(new BoolOption("Auto-sync profile", true));
        setEnabled(false);
    }
}
