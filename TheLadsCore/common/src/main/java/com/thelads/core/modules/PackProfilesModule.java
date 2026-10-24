package com.thelads.core.modules;

import com.thelads.core.client.pack.PackProfileManager;
import com.thelads.core.config.BoolOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.TextOption;

/**
 * Pack Profiles: save and switch between named sets of active resource packs (parity with OneClient packed_packs).
 */
public class PackProfilesModule extends Module {
    public static final String NAME = "Pack Profiles";

    public final TextOption activeProfile;
    public final TextOption profileData;
    public final BoolOption autoSync;

    public PackProfilesModule() {
        super(NAME, "Save and load named presets of enabled resource packs.");
        activeProfile = addOption(new TextOption("Active profile", ""));
        profileData = addOption(new TextOption("Saved profiles", ""));
        autoSync = addOption(new BoolOption("Auto-sync profile", true));
        setEnabled(false);
    }

    public void syncToManager() {
        PackProfileManager.deserialize(profileData.getValue());
        PackProfileManager.setActiveProfile(activeProfile.getValue());
    }

    public void syncFromManager() {
        profileData.setValue(PackProfileManager.serialize());
        activeProfile.setValue(PackProfileManager.getActiveProfile());
    }
}
