package com.thelads.core.modules;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.thelads.core.config.ConfigManager;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExordiumModuleTest {

    private static final String LEGACY_EXORDIUM_CONFIG_JSON = """
        {
          "modules": {
            "Exordium": {
              "enabled": true,
              "favorite": true,
              "lastModified": 1700000000000,
              "options": {
                "Limit GUI": true,
                "GUI FPS Limit": 60.0,
                "Limit Global": false,
                "Global FPS": 60.0,
                "Limit Inventory": true,
                "Inventory FPS": 60.0,
                "Limit Chat": true,
                "Chat FPS": 60.0,
                "Limit Pause Menu": true,
                "Pause Menu FPS": 60.0,
                "Limit Active Gameplay": false,
                "Active Gameplay FPS": 60.0
              }
            }
          }
        }
        """;

    private boolean initialEnabled;
    private boolean initialFavorite;

    @BeforeEach
    void setUp() {
        Module exordium = ModuleManager.getInstance().getModule("Exordium");
        if (exordium != null) {
            initialEnabled = exordium.isEnabled();
            initialFavorite = exordium.isFavorite();
        }
    }

    @AfterEach
    void tearDown() {
        ConfigManager.setTestConfigFile(null);
        Module exordium = ModuleManager.getInstance().getModule("Exordium");
        if (exordium != null) {
            exordium.setEnabled(initialEnabled);
            exordium.setFavorite(initialFavorite);
        }
    }

    @Test
    void moduleMetadataHasNoFakeOptionsAndMatchesReality() {
        ExordiumModule module = new ExordiumModule();
        assertEquals("Exordium", module.getName());
        assertEquals(
            "Exordium mod: redraws the HUD less often for higher FPS. Settings are in the Exordium mod's own screen.",
            module.getDescription()
        );
        assertTrue(module.getOptions().isEmpty(), "Exordium module must have zero internal options");
    }

    @Test
    void legacyConfigWithFpsOptionsLoadsWithoutError(@TempDir Path tempDir) throws IOException {
        File configFile = tempDir.resolve("thelads_config.json").toFile();
        Files.writeString(configFile.toPath(), LEGACY_EXORDIUM_CONFIG_JSON, StandardCharsets.UTF_8);

        Module exordium = ModuleManager.getInstance().getModule("Exordium");
        assertNotNull(exordium, "Exordium module must be registered in ModuleManager");
        exordium.setEnabled(false);
        exordium.setFavorite(false);

        ConfigManager.setTestConfigFile(configFile);

        assertDoesNotThrow(() -> ConfigManager.load(), "Loading config with legacy Exordium FPS options must succeed");

        assertTrue(exordium.isEnabled(), "Module enabled state should still be read from config");
        assertTrue(exordium.isFavorite(), "Module favorite state should still be read from config");
        assertTrue(exordium.getOptions().isEmpty(), "No leftover options should be created or attached");

        JsonObject snapshot = assertDoesNotThrow(
            () -> ConfigManager.toJson(),
            "Saving / serializing config after loading legacy JSON must succeed"
        );
        assertNotNull(snapshot);
    }

    @Test
    void applyJsonWithLegacyFpsOptionsSucceedsWithoutError() {
        JsonObject json = JsonParser.parseString(LEGACY_EXORDIUM_CONFIG_JSON).getAsJsonObject();
        Module exordium = ModuleManager.getInstance().getModule("Exordium");
        assertNotNull(exordium);

        exordium.setEnabled(false);
        assertDoesNotThrow(() -> ConfigManager.applyJson(json));
        assertTrue(exordium.isEnabled());
        assertTrue(exordium.getOptions().isEmpty());
    }
}
