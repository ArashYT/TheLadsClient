package com.thelads.core.v26_2.embedded.quickpack;

import com.thelads.core.v26_2.embedded.quickpack.config.Config;
import com.thelads.core.v26_2.embedded.quickpack.config.ConfigManager;

import java.nio.file.Files;
import java.nio.file.Path;

public final class QuickPackConfigTest {
    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError("Check failed: " + message);
        }
    }

    public static void main(String[] args) throws Exception {
        Path tempDir = Files.createTempDirectory("quickpack-test");
        try {
            // Case 1: Empty file
            Path configFile = tempDir.resolve("quick-pack.json");
            Files.writeString(configFile, "");
            ConfigManager.config = new Config();
            ConfigManager.load(tempDir);
            check(ConfigManager.config != null, "config should not be null after empty file");

            // Case 2: "null" text
            Files.writeString(configFile, "null");
            ConfigManager.config = new Config();
            ConfigManager.load(tempDir);
            check(ConfigManager.config != null, "config should not be null after 'null' file");

            // Case 3: "{}"
            Files.writeString(configFile, "{}");
            ConfigManager.config = new Config();
            ConfigManager.load(tempDir);
            check(ConfigManager.config != null, "config should not be null after '{}' file");
        } finally {
            try {
                Files.deleteIfExists(tempDir.resolve("quick-pack.json"));
                Files.deleteIfExists(tempDir);
            } catch (Exception ignored) {}
        }
    }
}
