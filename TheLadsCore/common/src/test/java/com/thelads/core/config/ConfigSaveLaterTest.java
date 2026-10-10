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

    @Test void unmanagedModulesArePreservedAcrossSave(@TempDir Path dir) throws Exception {
        File config = dir.resolve("thelads_config.json").toFile();
        ConfigManager.setTestConfigFile(config);
        com.google.gson.JsonObject root = new com.google.gson.JsonObject();
        com.google.gson.JsonObject modules = new com.google.gson.JsonObject();
        com.google.gson.JsonObject external = new com.google.gson.JsonObject();
        external.addProperty("enabled", true);
        external.addProperty("favorite", true);
        modules.add("ExternalModuleFromAnotherVersion", external);
        root.add("modules", modules);
        ConfigManager.applyJson(root);

        ConfigManager.save();
        ConfigManager.flush();

        String saved = Files.readString(config.toPath(), StandardCharsets.UTF_8);
        assertTrue(saved.contains("ExternalModuleFromAnotherVersion"), "Unmanaged module is preserved in saved config");
        assertTrue(saved.contains("\"favorite\": true"), "Unmanaged module options/properties are preserved");
    }

    @Test void autohideDefaultsOffAndUnchangedConfigMigrates() {
        Module autohide = ModuleManager.getInstance().getModule("Autohide");
        boolean was = autohide.isEnabled();
        try {
            com.google.gson.JsonObject root = new com.google.gson.JsonObject();
            com.google.gson.JsonObject modules = new com.google.gson.JsonObject();
            com.google.gson.JsonObject ah = new com.google.gson.JsonObject();
            ah.addProperty("enabled", true);
            ah.addProperty("lastModified", 0);
            modules.add("Autohide", ah);
            root.add("modules", modules);
            ConfigManager.applyJson(root);
            assertFalse(autohide.isEnabled(), "old default of enabled:true with lastModified 0 migrates to off");

            ah.addProperty("enabled", true);
            ah.addProperty("lastModified", 1790000000000L);
            ConfigManager.applyJson(root);
            assertTrue(autohide.isEnabled(), "explicit player change is kept");
        } finally {
            autohide.setEnabled(was);
        }
    }
}
