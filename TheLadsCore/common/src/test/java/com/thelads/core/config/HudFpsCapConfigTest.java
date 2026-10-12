package com.thelads.core.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** 1.9.2: the HUD FPS cap is on by default at 60; configs from 1.9.1 and older with the uncapped default move to on at 60, once. */
class HudFpsCapConfigTest {
    private final HudSettings settings = HudSettings.getInstance();

    @AfterEach void reset() { settings.setHudFpsCapEnabled(true); settings.setHudFpsLimit(60); }

    private static void apply(String hud) { ConfigManager.applyJson(JsonParser.parseString("{\"hud\":" + hud + "}").getAsJsonObject()); }

    @Test void onAt60IsTheDefault() {
        assertTrue(settings.isHudFpsCapEnabled());
        assertEquals(60, settings.getHudFpsLimit());
    }

    @Test void oldSavedDefaultBecomesCappedAt60() {
        settings.setHudFpsCapEnabled(false);
        apply("{\"hudFpsCapOptIn\":false,\"hudFpsLimit\":60}");
        assertTrue(settings.isHudFpsCapEnabled());
        assertEquals(60, settings.getHudFpsLimit());
    }

    @Test void oldSavedDefaultHudFpsCapEnabledBecomesCappedAt60() {
        settings.setHudFpsCapEnabled(false);
        apply("{\"hudFpsCapEnabled\":false,\"hudFpsLimit\":60}");
        assertTrue(settings.isHudFpsCapEnabled());
        assertEquals(60, settings.getHudFpsLimit());
    }

    @Test void oldOptInWithCustomLimitStays() {
        apply("{\"hudFpsCapOptIn\":true,\"hudFpsLimit\":144}");
        assertTrue(settings.isHudFpsCapEnabled());
        assertEquals(144, settings.getHudFpsLimit());
    }

    @Test void oldChangedLimitInAncient144Stays() {
        apply("{\"hudFpsCapEnabled\":true,\"hudFpsLimit\":144}");
        assertTrue(settings.isHudFpsCapEnabled());
        assertEquals(144, settings.getHudFpsLimit());
    }

    @Test void offChosenAfterTheMigrationStays() {
        settings.setHudFpsCapEnabled(false);
        var saved = ConfigManager.toJson();
        assertTrue(saved.getAsJsonObject("hud").has("hudFpsCapOnDefault"));
        settings.setHudFpsCapEnabled(true);
        ConfigManager.applyJson(saved);
        assertFalse(settings.isHudFpsCapEnabled());
    }

    @Test void capRoundTrips() {
        settings.setHudFpsCapEnabled(true);
        var saved = ConfigManager.toJson();
        settings.setHudFpsCapEnabled(false);
        ConfigManager.applyJson(saved);
        assertTrue(settings.isHudFpsCapEnabled());
    }
}
