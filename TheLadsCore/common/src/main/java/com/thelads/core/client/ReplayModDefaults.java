package com.thelads.core.client;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * ReplayMod records every singleplayer and server session by default: disk use, and a full-screen "recover it?" dialog after a
 * killed game. A ReplayMod with no config yet starts with recording off; the player turns it on with ReplayMod's Record checkboxes
 * or settings. A config that exists is the player's and is never touched.
 */
public final class ReplayModDefaults {
    /** 1.8.9 ReplayMod's Forge config: it keeps these two and adds its other settings with their defaults. */
    static final String FORGE_189 = "recording {\n    B:recordServer=false\n    B:recordSingleplayer=false\n}\n";

    private ReplayModDefaults() {}

    /** Writes config/replaymod.cfg with recording off unless it exists (before ReplayMod's preInit reads it); true when written. */
    public static boolean recordingOff189(Path configDir) throws IOException {
        Files.createDirectories(configDir);
        try {
            Files.write(configDir.resolve("replaymod.cfg"), FORGE_189.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE_NEW);
            return true;
        } catch (FileAlreadyExistsException playersConfig) {
            return false;
        }
    }
}
