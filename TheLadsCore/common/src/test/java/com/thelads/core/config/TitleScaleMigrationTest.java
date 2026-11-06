package com.thelads.core.config;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

class TitleScaleMigrationTest {
    private static JsonObject migrated(String json) {
        JsonObject modules = JsonParser.parseString(json).getAsJsonObject();
        ConfigManager.migrateTitleScale(modules);
        assertFalse(modules.has("Title Scale"), "the old entry is gone");
        return modules;
    }
    @Test void aChosenScaleMovesToTheTitleScreenSettings() {
        JsonObject modules = migrated("{\"Title Scale\":{\"enabled\":true,\"options\":{\"Scale\":1.4}},\"TitleScreen\":{\"options\":{\"Layout\":1}}}");
        JsonObject options = modules.getAsJsonObject("TitleScreen").getAsJsonObject("options");
        assertEquals(1.4, options.get("Logo Scale").getAsDouble());
        assertEquals(1, options.get("Layout").getAsInt(), "what it had stays");
    }
    @Test void aTitleScaleThatWasOffKeepsTheDefaultScale() {
        assertFalse(migrated("{\"Title Scale\":{\"enabled\":false,\"options\":{\"Scale\":1.4}}}").has("TitleScreen"));
    }
    @Test void aConfigWithoutOneIsUntouched() {
        JsonObject modules = JsonParser.parseString("{\"TitleScreen\":{\"options\":{\"Layout\":1}}}").getAsJsonObject();
        ConfigManager.migrateTitleScale(modules);
        assertFalse(modules.getAsJsonObject("TitleScreen").getAsJsonObject("options").has("Logo Scale"));
    }
}
