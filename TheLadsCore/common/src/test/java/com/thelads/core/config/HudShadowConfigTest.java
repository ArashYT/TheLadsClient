package com.thelads.core.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** 1.9.1: the global text shadow is On by default; the Soft that 1.9.0 and older shipped as the default moves to On, once. */
class HudShadowConfigTest {
    private final HudSettings settings = HudSettings.getInstance();

    @AfterEach void reset() { settings.setTextShadow(true); settings.setSoftShadow(false); }

    private static void apply(String hud) { ConfigManager.applyJson(JsonParser.parseString("{\"hud\":" + hud + "}").getAsJsonObject()); }

    @Test void onIsTheDefault() {
        assertTrue(settings.isTextShadow());
        assertFalse(settings.isSoftShadow());
    }

    @Test void softSavedByAnOlderClientBecomesOn() {
        settings.setSoftShadow(true);
        apply("{\"textShadow\":true,\"softShadow\":true}");
        assertTrue(settings.isTextShadow());
        assertFalse(settings.isSoftShadow());
    }

    @Test void offStaysOff() {
        apply("{\"textShadow\":false,\"softShadow\":true}");
        assertFalse(settings.isTextShadow());
    }

    @Test void softChosenAfterTheMigrationStays() {
        settings.setSoftShadow(true);
        var saved = ConfigManager.toJson();
        assertTrue(saved.getAsJsonObject("hud").has("shadowOnDefault"));
        settings.setSoftShadow(false);
        ConfigManager.applyJson(saved);
        assertTrue(settings.isSoftShadow());
        assertEquals(true, settings.isTextShadow());
    }
}
