package com.thelads.core.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReplayModDefaultsTest {
    @Test void newConfigHasRecordingOffAndAPlayersConfigIsKept(@TempDir Path dir) throws Exception {
        Path config = dir.resolve("config");
        assertTrue(ReplayModDefaults.recordingOff189(config));
        String written = Files.readString(config.resolve("replaymod.cfg"));
        assertTrue(written.contains("B:recordServer=false") && written.contains("B:recordSingleplayer=false"), written);

        Files.writeString(config.resolve("replaymod.cfg"), "recording {\n    B:recordServer=true\n}\n");
        assertFalse(ReplayModDefaults.recordingOff189(config));
        assertEquals("recording {\n    B:recordServer=true\n}\n", Files.readString(config.resolve("replaymod.cfg")));
    }
}
