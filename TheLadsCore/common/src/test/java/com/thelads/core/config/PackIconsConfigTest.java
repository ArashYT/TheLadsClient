package com.thelads.core.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** The Pack module's Show Icons is on by default, also for a player whose config was saved before the option existed. */
class PackIconsConfigTest {
    private final Module packs = ModuleManager.getInstance().getModule("TexturePacks");

    @AfterEach void reset() {
        packs.getOptions().forEach(Option::reset);
        packs.setEnabled(false);
    }

    @Test void newPlayersGetIcons() {
        assertTrue(((BoolOption) packs.getOption("Show Icons")).get());
    }

    @Test void anOldSavedModuleKeepsItsOptionsAndGainsIconsOn() {
        ConfigManager.applyJson(JsonParser.parseString(
            "{\"modules\":{\"TexturePacks\":{\"enabled\":true,\"options\":{\"Show All\":true,\"Max Packs\":4}}}}").getAsJsonObject());
        assertTrue(packs.isEnabled());
        assertTrue(((BoolOption) packs.getOption("Show All")).get());
        assertEquals(4, ((DropdownOption) packs.getOption("Max Packs")).getIndex());
        assertTrue(((BoolOption) packs.getOption("Show Icons")).get());
    }

    @Test void switchingIconsOffIsSaved() {
        ((BoolOption) packs.getOption("Show Icons")).set(false);
        var saved = ConfigManager.toJson();
        ((BoolOption) packs.getOption("Show Icons")).set(true);
        ConfigManager.applyJson(saved);
        assertFalse(((BoolOption) packs.getOption("Show Icons")).get());
    }
}
