package com.thelads.core.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** The HUD FPS cap is opt-in: off by default, and the default 1.4.1-1.4.4 wrote into every config is not an opt-in. */
class HudFpsCapConfigTest {
    private final HudSettings settings = HudSettings.getInstance();

    @AfterEach void reset() { settings.setHudFpsCapEnabled(false); settings.setHudFpsLimit(60); }

    private static void apply(String hud) { ConfigManager.applyJson(JsonParser.parseString("{\"hud\":" + hud + "}").getAsJsonObject()); }

    @Test void oldSavedDefaultLeavesTheHudUncapped() {
        settings.setHudFpsCapEnabled(true);
        apply("{\"hudFpsCapEnabled\":true,\"hudFpsLimit\":60}");
        assertFalse(settings.isHudFpsCapEnabled());
    }

    @Test void oldChangedLimitWasAChoiceAndStays() {
        apply("{\"hudFpsCapEnabled\":true,\"hudFpsLimit\":144}");
        assertTrue(settings.isHudFpsCapEnabled());
    }

    @Test void optInRoundTripsAtTheOldDefaultLimit() {
        settings.setHudFpsCapEnabled(true);
        var saved = ConfigManager.toJson();
        settings.setHudFpsCapEnabled(false);
        ConfigManager.applyJson(saved);
        assertTrue(settings.isHudFpsCapEnabled());
    }
}
