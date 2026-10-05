package com.thelads.core.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigSaveLaterTest {
    @Test void laterSavesWriteOnceInTheBackgroundAndFlushWritesAtOnce(@TempDir Path dir) throws Exception {
        File config = dir.resolve("thelads_config.json").toFile();
        ConfigManager.setTestConfigFile(config);
        ConfigManager.saveLater();
        ConfigManager.saveLater();
        assertFalse(config.exists(), "nothing is written on the calling thread");
        ConfigManager.flush();
        assertTrue(config.exists(), "flush writes what is waiting");
        long modified = Files.getLastModifiedTime(config.toPath()).toMillis();
        Thread.sleep(800);
        assertEquals(modified, Files.getLastModifiedTime(config.toPath()).toMillis(), "the scheduled write finds nothing left");
    }

    @Test void aDirectSaveSupersedesAWaitingOne(@TempDir Path dir) throws IOException {
        File config = dir.resolve("thelads_config.json").toFile();
        ConfigManager.setTestConfigFile(config);
        Module module = ModuleManager.getInstance().getModule("Autohide");
        boolean was = module.isEnabled();
        try {
            module.setEnabled(!was);
            ConfigManager.saveLater();
            module.setEnabled(was);
            ConfigManager.save();
            ConfigManager.flush();
            String json = Files.readString(config.toPath(), StandardCharsets.UTF_8);
            assertEquals(was, ConfigManager.savedEnabled("Autohide", !was), "the newer direct save is on disk: " + json.length());
        } finally {
            module.setEnabled(was);
        }
    }
}
