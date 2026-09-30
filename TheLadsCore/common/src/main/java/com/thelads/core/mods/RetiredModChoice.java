package com.thelads.core.mods;

import com.thelads.core.config.ConfigManager;
import com.thelads.core.config.Module;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The first launch after the pack retires an upstream mod for a Lads module: a profile whose player had turned that jar off
 * (the launcher keeps the choice in lads-mod-state.json) starts with the module off, since its default is on like the always-on jar.
 * Runs once per profile (a marker file), so a later choice in the Lads menu stands.
 */
public final class RetiredModChoice {
    private RetiredModChoice() {}

    /** True when the module was turned off for the retired mod's saved choice. */
    public static boolean adopt(Path gameDirectory, String modId, String projectId, Module module) {
        Path marker = gameDirectory.resolve(".lads-adopted-" + modId);
        if (module == null || Files.exists(marker)) return false;
        boolean off = Boolean.FALSE.equals(new ModStateStore(gameDirectory).read().enabled(modId, projectId)) && module.isEnabled();
        if (off) { module.setEnabled(false); ConfigManager.save(); }
        try { Files.writeString(marker, modId + " was retired; its on/off choice was adopted by the " + module.getName() + " module.\n"); }
        catch (IOException ignored) {}
        return off;
    }
}
