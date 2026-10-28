package com.thelads.core.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigSaveLaterTest {
    @AfterEach void tearDown() {
        ConfigManager.setTestConfigFile(null);
    }

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

    @Test void aWronglyTypedValueIsSkippedAndAnUnreadableConfigIsKept(@TempDir Path dir) throws Exception {
        File config = dir.resolve("thelads_config.json").toFile();
        ConfigManager.setTestConfigFile(config);
        try {
            // Each of these threw out of load(), and so out of every version's mod start, at every launch.
            Files.writeString(config.toPath(), "{\"" + SkinLayersLodMigration.FLAG + "\":true,\"modules\":[],\"hud\":{\"hudScale\":\"auto\","
                + "\"globalColor\":{},\"textShadow\":[1,2],\"fadePlaylist\":{},\"positions\":[],\"favoriteColors\":7,\"backgrounds\":false}}");
            ConfigManager.load();
            assertFalse(HudSettings.getInstance().isBackgrounds(), "the values that are fine still apply");
            ConfigManager.applyJson(com.google.gson.JsonParser.parseString("{\"modules\":{\"FPS\":7},\"hud\":[]}").getAsJsonObject());

            String cut = "{\"modules\":{\"FPS\":{\"enabled\":";
            Files.writeString(config.toPath(), cut);
            ConfigManager.load();
            File[] kept = dir.toFile().listFiles((folder, name) -> name.startsWith("thelads_config.json.corrupt-"));
            assertEquals(1, kept.length, "an unreadable config is kept before a save can replace it");
            assertEquals(cut, Files.readString(kept[0].toPath()));
        } finally {
            ModuleManager.getInstance().resetToDefaults();
            HudSettings.getInstance().resetToDefaults();
        }
    }

    @Test void mirrorTargetsIncludeSharedRootProfilesAndInstances(@TempDir Path dir) throws Exception {
        com.thelads.core.client.util.ClientPaths.setBaseDir(dir);
        try {
            Path profileA = Files.createDirectories(dir.resolve("profiles").resolve("1.20.1"));
            Files.createDirectories(profileA.resolve("mods"));
            Path instanceB = Files.createDirectories(dir.resolve("instances").resolve("zero-smp-1.20.1").resolve("minecraft"));
            Files.createDirectories(instanceB.resolve("mods"));

            java.util.List<File> targets = com.thelads.core.client.util.ClientPaths.getMirrorTargetsForSharedFile("thelads_config.json");
            assertTrue(targets.contains(dir.resolve("shared").resolve("thelads_config.json").toFile()));
            assertTrue(targets.contains(dir.resolve("thelads_config.json").toFile()));
            assertTrue(targets.contains(profileA.resolve("thelads_config.json").toFile()));
            assertTrue(targets.contains(instanceB.resolve("thelads_config.json").toFile()));
        } finally {
            com.thelads.core.client.util.ClientPaths.setBaseDir(null);
        }
    }

    @Test void clientPathsCachesConfigAndProfilesFileAndInvalidatesOnSetBaseDir(@TempDir Path dirA, @TempDir Path dirB) {
        try {
            com.thelads.core.client.util.ClientPaths.setBaseDir(dirA);
            File cfgA1 = com.thelads.core.client.util.ClientPaths.getConfigFile();
            File cfgA2 = com.thelads.core.client.util.ClientPaths.getConfigFile();
            File profA1 = com.thelads.core.client.util.ClientPaths.getProfilesFile();
            assertEquals(cfgA1.toPath(), cfgA2.toPath());
            assertEquals(dirA.resolve("thelads_config.json"), cfgA1.toPath());
            assertEquals(dirA.resolve("thelads_profiles.json"), profA1.toPath());

            com.thelads.core.client.util.ClientPaths.setBaseDir(dirB);
            File cfgB = com.thelads.core.client.util.ClientPaths.getConfigFile();
            File profB = com.thelads.core.client.util.ClientPaths.getProfilesFile();
            assertEquals(dirB.resolve("thelads_config.json"), cfgB.toPath());
            assertEquals(dirB.resolve("thelads_profiles.json"), profB.toPath());
        } finally {
            com.thelads.core.client.util.ClientPaths.setBaseDir(null);
        }
    }

    @Test void loadDoesNotRewriteOrMirrorWhenConfigIsAlreadyCurrent(@TempDir Path dir) throws Exception {
        ConfigManager.setTestConfigFile(null);
        com.thelads.core.client.util.ClientPaths.setBaseDir(dir);
        try {
            Path profileA = Files.createDirectories(dir.resolve("profiles").resolve("1.20.1"));
            Files.createDirectories(profileA.resolve("mods"));
            ConfigManager.save();

            File primary = com.thelads.core.client.util.ClientPaths.getConfigFile();
            File mirrored = profileA.resolve("thelads_config.json").toFile();
            assertTrue(primary.exists());
            assertTrue(mirrored.exists());

            // Delete the mirror and set an older mtime on primary; warm load() of an already-migrated config must not touch disk.
            assertTrue(mirrored.delete());
            long sentinelTime = 1_700_000_000_000L;
            assertTrue(primary.setLastModified(sentinelTime));

            ConfigManager.load();

            assertEquals(sentinelTime, primary.lastModified(), "warm load() must not rewrite an up-to-date config file");
            assertFalse(mirrored.exists(), "warm load() must not mirror across instances when no migration ran");
        } finally {
            com.thelads.core.client.util.ClientPaths.setBaseDir(null);
        }
    }
}
