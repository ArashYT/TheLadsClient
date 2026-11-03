package com.thelads.core;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.thelads.core.config.ConfigManager;
import com.thelads.core.config.HudSettings;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.Option;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/** The importer's mapping table names real modules and options, and what it writes is what ConfigManager reads. */
class ClientImportTest {
    private static JsonObject map() throws Exception {
        try (var in = ClientImportTest.class.getClassLoader().getResourceAsStream("thelads/client_import_map.json")) {
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    @Test
    void everyTargetInTheTableExists() throws Exception {
        JsonObject table = map();
        int checked = 0;
        for (String client : new String[] {"lunar", "badlion", "feather"}) {
            JsonObject source = table.getAsJsonObject(client);
            for (JsonElement entry : source.getAsJsonArray("mods")) {
                JsonObject mod = entry.getAsJsonObject();
                String to = mod.get("to").getAsString();
                Module module = ModuleManager.getInstance().getModule(to);
                assertNotNull(module, client + " maps to a module that does not exist: " + to);
                if (mod.get("opts") instanceof JsonArray opts) {
                    for (JsonElement opt : opts) {
                        String option = opt.getAsJsonArray().get(1).getAsString();
                        Option found = module.getOption(option);
                        assertNotNull(found, client + " maps to an option that does not exist: " + to + " / " + option);
                        checked++;
                    }
                }
                if (mod.has("hud") && (!mod.has("common") || mod.get("common").getAsBoolean())) {
                    for (JsonElement opt : source.getAsJsonArray("common")) {
                        String option = opt.getAsJsonArray().get(1).getAsString();
                        assertNotNull(module.getOption(option), client + " common option missing on " + to + ": " + option);
                    }
                }
            }
            if (source.get("boxes") instanceof JsonObject boxes) {
                boxes.entrySet().forEach(e -> assertNotNull(ModuleManager.getInstance().getModule(e.getValue().getAsString()), e.getKey()));
            }
        }
        assertTrue(checked > 40, "the option mappings were read");
    }

    @Test
    void whatTheImporterWritesIsWhatTheGameReads() {
        Module fps = ModuleManager.getInstance().getModule("FPS");
        JsonObject config = JsonParser.parseString("""
            {"modules":{"FPS":{"enabled":false,"useGlobalColor":false,"customColor":-65536,
              "options":{"Text Color":{"useGlobal":false,"color":-65536},"Size":90.0,"Background":{"useGlobal":false,"color":0}}}},
             "hud":{"positions":{"FPS":[470,12]},"anchors":{"FPS":{"at":[470,12],"x":[2,2,-10,0],"y":[0,0,12,0]}}},
             "lads197PresetApplied":true}""").getAsJsonObject();
        boolean wasOn = fps.isEnabledSetting();
        try {
            ConfigManager.applyJson(config);
            assertFalse(fps.isEnabledSetting());
            HudSettings.Anchor anchor = HudSettings.getInstance().getAnchor("FPS");
            assertNotNull(anchor, "position and anchor agree");
            assertEquals(2, anchor.h().ref());
            assertEquals(-10, anchor.h().off());
            assertEquals(12, anchor.v().off());
            assertEquals(-65536, ((com.thelads.core.modules.HudModule) fps).getCustomColor());
        } finally {
            fps.setEnabled(wasOn);
            HudSettings.getInstance().resetToDefaults();
        }
    }
}
