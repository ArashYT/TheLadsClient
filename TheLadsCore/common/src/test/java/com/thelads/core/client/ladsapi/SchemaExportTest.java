package com.thelads.core.client.ladsapi;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SchemaExportTest {

    private static final Set<String> VALID_CONTROLS = Set.of("FULL", "LIMIT_ONLY", "NEVER");
    private static final Set<String> VALID_TYPES = Set.of("bool", "slider", "dropdown", "color", "text", "other");

    @Test
    void testSchemaParsesAndValidates() {
        String json = SchemaExport.generateSchemaJson();
        assertNotNull(json);
        assertFalse(json.isBlank());

        JsonElement rootEl = JsonParser.parseString(json);
        assertTrue(rootEl.isJsonObject(), "Root must be a JSON object");
        JsonObject root = rootEl.getAsJsonObject();

        assertEquals(1, root.get("schema").getAsInt(), "Schema version must be 1");
        assertTrue(root.has("lads") && !root.get("lads").getAsString().isBlank(), "Lads version must be present");

        assertTrue(root.has("modules"), "Root must contain modules array");
        JsonArray modules = root.getAsJsonArray("modules");
        assertTrue(modules.size() > 0, "Modules array must not be empty");

        JsonObject fullbright = null;
        JsonObject useWhileMining = null;
        int totalOptions = 0;

        for (JsonElement mEl : modules) {
            assertTrue(mEl.isJsonObject(), "Module must be a JSON object");
            JsonObject mod = mEl.getAsJsonObject();

            assertTrue(mod.has("name") && !mod.get("name").getAsString().isBlank(), "Module must have name");
            String modName = mod.get("name").getAsString();

            assertTrue(mod.has("category"), "Module must have category: " + modName);
            assertTrue(mod.has("neverForced"), "Module must have neverForced: " + modName);
            assertTrue(mod.has("automation"), "Module must have automation: " + modName);
            assertTrue(mod.has("options"), "Module must have options array: " + modName);

            if ("Fullbright".equals(modName)) {
                fullbright = mod;
            } else if ("UseWhileMining".equals(modName)) {
                useWhileMining = mod;
            }

            JsonArray options = mod.getAsJsonArray("options");
            for (JsonElement oEl : options) {
                totalOptions++;
                assertTrue(oEl.isJsonObject(), "Option must be a JSON object");
                JsonObject opt = oEl.getAsJsonObject();

                assertTrue(opt.has("name") && !opt.get("name").getAsString().isBlank(),
                        "Option must have name in module " + modName);
                String optName = opt.get("name").getAsString();

                assertTrue(opt.has("type"), "Option must have type: " + modName + "." + optName);
                String type = opt.get("type").getAsString();
                assertTrue(VALID_TYPES.contains(type), "Invalid option type: " + type + " on " + modName + "." + optName);

                assertTrue(opt.has("control"), "Every option must have control: " + modName + "." + optName);
                String control = opt.get("control").getAsString();
                assertTrue(VALID_CONTROLS.contains(control), "Invalid control value: " + control + " on " + modName + "." + optName);

                switch (type) {
                    case "slider" -> {
                        assertTrue(opt.has("default"), "Slider option must have default: " + modName + "." + optName);
                        assertTrue(opt.has("min"), "Slider option must have min: " + modName + "." + optName);
                        assertTrue(opt.has("max"), "Slider option must have max: " + modName + "." + optName);
                        assertTrue(opt.has("step"), "Slider option must have step: " + modName + "." + optName);
                        assertTrue(opt.get("min").getAsDouble() <= opt.get("max").getAsDouble(),
                                "Slider min must be <= max on " + modName + "." + optName);
                    }
                    case "bool" -> {
                        assertTrue(opt.has("default"), "Bool option must have default: " + modName + "." + optName);
                        assertTrue(opt.get("default").isJsonPrimitive() && opt.get("default").getAsJsonPrimitive().isBoolean(),
                                "Bool default must be boolean on " + modName + "." + optName);
                    }
                    case "dropdown" -> {
                        assertTrue(opt.has("default"), "Dropdown option must have default: " + modName + "." + optName);
                        assertTrue(opt.has("choices"), "Dropdown option must have choices: " + modName + "." + optName);
                        JsonArray choices = opt.getAsJsonArray("choices");
                        assertTrue(choices.size() > 0, "Dropdown choices must not be empty on " + modName + "." + optName);
                    }
                    case "color" -> {
                        assertTrue(opt.has("default"), "Color option must have default: " + modName + "." + optName);
                        String colorStr = opt.get("default").getAsString();
                        assertTrue(colorStr.startsWith("#") && colorStr.length() == 9,
                                "Color default must be #AARRGGBB: " + colorStr + " on " + modName + "." + optName);
                    }
                }
            }
        }

        // Verify Fullbright.Brightness
        assertNotNull(fullbright, "Fullbright module must be present in schema");
        assertEquals("MECHANIC", fullbright.get("category").getAsString());
        assertFalse(fullbright.get("neverForced").getAsBoolean());
        assertFalse(fullbright.get("automation").getAsBoolean());

        JsonObject brightnessOpt = null;
        for (JsonElement oEl : fullbright.getAsJsonArray("options")) {
            JsonObject opt = oEl.getAsJsonObject();
            if ("Brightness".equals(opt.get("name").getAsString())) {
                brightnessOpt = opt;
                break;
            }
        }

        assertNotNull(brightnessOpt, "Fullbright.Brightness option must be present in schema");
        assertEquals("slider", brightnessOpt.get("type").getAsString());
        assertEquals(100, brightnessOpt.get("default").getAsInt());
        assertEquals(0, brightnessOpt.get("min").getAsInt());
        assertEquals(100, brightnessOpt.get("max").getAsInt());
        assertEquals(5, brightnessOpt.get("step").getAsInt());
        assertEquals("FULL", brightnessOpt.get("control").getAsString());

        // Verify UseWhileMining pseudo-module
        assertNotNull(useWhileMining, "UseWhileMining pseudo-module must be present in schema");
        assertEquals("MECHANIC", useWhileMining.get("category").getAsString());
        assertTrue(useWhileMining.get("neverForced").getAsBoolean(), "UseWhileMining must be neverForced");
        assertTrue(useWhileMining.get("automation").getAsBoolean(), "UseWhileMining must be automation");
        assertEquals(0, useWhileMining.getAsJsonArray("options").size(), "UseWhileMining must have 0 options");

        assertTrue(totalOptions > 0, "Total options must be greater than zero");
    }
}
