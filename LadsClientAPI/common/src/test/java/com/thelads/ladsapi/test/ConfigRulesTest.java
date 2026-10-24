package com.thelads.ladsapi.test;

import com.thelads.ladsapi.config.LadsConfig;
import com.thelads.ladsapi.rules.LadsRules;
import com.thelads.ladsapi.rules.ModuleRule;
import com.thelads.ladsapi.rules.RuleOption;
import com.thelads.ladsapi.rules.RulesGenerator;
import com.thelads.ladsapi.schema.LadsSchema;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ConfigRulesTest {

    @Test
    public void testConfigDefaultsAndParse(@TempDir Path tempDir) {
        LadsConfig config = LadsConfig.load(tempDir, null);
        assertTrue(config.isEnabled());
        assertEquals("inherit", config.getUnlistedModules());
        assertTrue(config.isNotifyPlayers());
        assertTrue(config.isLetPlayersRefuseChanges());
        assertFalse(config.isRequireLadsClient());
        assertEquals("", config.getMinClientVersion());
        assertTrue(config.isBadgePresence());
        assertEquals("&c✿ ", config.getBadgePlaceholder());
        assertFalse(config.isDebug());

        // Now test modifying config.yml and reloading
        Path configFile = tempDir.resolve("config.yml");
        String custom = """
                enabled: false
                unlisted-modules: block
                require-lads-client: true
                min-client-version: "1.9.13"
                badge:
                  presence: false
                  placeholder: "[LADS]"
                debug: true
                """;
        assertDoesNotThrow(() -> Files.writeString(configFile, custom, StandardCharsets.UTF_8));
        config.readFrom(configFile, null);

        assertFalse(config.isEnabled());
        assertEquals("block", config.getUnlistedModules());
        assertTrue(config.isRequireLadsClient());
        assertEquals("1.9.13", config.getMinClientVersion());
        assertFalse(config.isBadgePresence());
        assertEquals("[LADS]", config.getBadgePlaceholder());
        assertTrue(config.isDebug());
    }

    @Test
    public void testRulesRegenerationKeepsOwnerValuesAndComments(@TempDir Path tempDir) throws IOException {
        Path rulesFile = tempDir.resolve("rules.yml");

        // Simulate an owner-written rules.yml with custom values and comments
        String ownerYaml = """
                # My custom server rules
                modules:
                  Fullbright:
                    state:                     # allow | block | on
                    Brightness: { max: 10 }    # owner custom limit: max 10%
                  XaeroMinimap:
                    state: block               # owner explicitly blocked this
                worlds:
                  pvp_arena:
                    modules:
                      Fullbright:
                        state: block
                groups:
                  staff:
                    modules:
                      XaeroMinimap:
                        state: allow
                """;
        Files.writeString(rulesFile, ownerYaml, StandardCharsets.UTF_8);

        // Schema with Fullbright (Brightness + Gamma Boost), XaeroMinimap, and 1.7 Animations
        String schemaJson = """
                {
                  "schema": 1,
                  "lads": "1.9.13",
                  "modules": [
                    {
                      "name": "Fullbright",
                      "category": "MECHANIC",
                      "neverForced": false,
                      "automation": false,
                      "options": [
                        { "name": "Brightness", "type": "slider", "default": 100, "min": 0, "max": 100, "step": 5, "control": "FULL" },
                        { "name": "Gamma Boost", "type": "bool", "default": false, "control": "FULL" }
                      ]
                    },
                    {
                      "name": "XaeroMinimap",
                      "category": "HUD",
                      "neverForced": false,
                      "automation": false,
                      "options": [
                        { "name": "Radar", "type": "bool", "default": true, "control": "FULL" }
                      ]
                    },
                    {
                      "name": "1.7 Animations",
                      "category": "RENDER",
                      "neverForced": false,
                      "automation": false,
                      "options": [
                        { "name": "1.7 sword block pose", "type": "bool", "default": true, "control": "FULL" }
                      ]
                    }
                  ]
                }
                """;
        LadsSchema schema = LadsSchema.fromJson(schemaJson);

        // Run regeneration
        RulesGenerator.regenerate(rulesFile, schema, null);

        String regeneratedContent = Files.readString(rulesFile, StandardCharsets.UTF_8);

        // 1. Owner's comment and values must be preserved verbatim
        assertTrue(regeneratedContent.contains("# owner custom limit: max 10%"));
        assertTrue(regeneratedContent.contains("Brightness: { max: 10 }"));
        assertTrue(regeneratedContent.contains("state: block               # owner explicitly blocked this"));

        // 2. Newly introduced options/modules must be present
        assertTrue(regeneratedContent.contains("Gamma Boost:"));
        assertTrue(regeneratedContent.contains("Radar:"));
        assertTrue(regeneratedContent.contains("1.7 Animations:"));

        // 3. Now parse it with LadsRules and verify the parsed model
        LadsRules rules = new LadsRules();
        rules.readFrom(rulesFile, null);

        // Check Fullbright
        ModuleRule fullbright = rules.getModules().get("Fullbright");
        assertNotNull(fullbright);
        RuleOption brightness = fullbright.getOption("Brightness");
        assertNotNull(brightness);
        assertEquals(10.0, brightness.getMax());

        // Check XaeroMinimap
        ModuleRule xaero = rules.getModules().get("XaeroMinimap");
        assertNotNull(xaero);
        assertEquals("block", xaero.getState());

        // Check world pvp_arena
        assertNotNull(rules.getWorlds().get("pvp_arena"));
        assertEquals("block", rules.getWorlds().get("pvp_arena").get("Fullbright").getState());

        // Check group staff
        assertNotNull(rules.getGroups().get("staff"));
        assertEquals("allow", rules.getGroups().get("staff").get("XaeroMinimap").getState());
    }

    // QA 1.9.19: the real schema has names like "SP: Wood floats"; unquoted they broke rules.yml and every rule was lost.
    @Test
    public void testRegenerateWithRealSchemaStaysParseableAndIdempotent(@TempDir Path tempDir) throws IOException {
        Path rulesFile = tempDir.resolve("rules.yml");
        Files.writeString(rulesFile, "modules:\n  Fullbright:\n    state: allow\n    Brightness: { max: 10 }\n", StandardCharsets.UTF_8);
        LadsSchema schema = LadsSchema.loadDefault();
        LadsRules rules = LadsRules.load(tempDir, schema, null);
        assertEquals(10.0, rules.getModules().get("Fullbright").getOption("Brightness").getMax());
        assertTrue(Files.readString(rulesFile).contains("\"SP: Wood floats\":"));
        String first = Files.readString(rulesFile);
        LadsRules.load(tempDir, schema, null);
        assertEquals(first, Files.readString(rulesFile));
    }
}
