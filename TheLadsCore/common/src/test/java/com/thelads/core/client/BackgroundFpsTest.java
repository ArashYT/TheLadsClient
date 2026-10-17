package com.thelads.core.client;

import static com.thelads.core.client.BackgroundFps.*;
import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import com.thelads.core.config.ConfigManager;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.SliderOption;
import org.junit.jupiter.api.Test;

class BackgroundFpsTest {
    private static final Profile DEFAULTS = new Profile();

    @Test void statesFollow26xOrder() {
        assertEquals(State.FOCUSED, state(BALANCED, true, true, false, 0, 0, 5));
        assertEquals(State.ABANDONED, state(BALANCED, true, true, false, 0, 5 * 60_000L, 5));
        assertEquals(State.FOCUSED, state(BALANCED, true, true, false, 0, Long.MAX_VALUE / 2, 0), "idle 0 = never");
        assertEquals(State.INVISIBLE, state(AGGRESSIVE, false, false, true, 0, 0, 5), "minimized wins over hovered");
        assertEquals(State.HOVERED, state(AGGRESSIVE, false, true, true, 0, 0, 5));
        assertEquals(State.UNFOCUSED, state(AGGRESSIVE, false, true, false, 0, 0, 5));
        assertEquals(State.FOCUSED, state(OFF, false, false, false, 99_999, 0, 5));
    }

    @Test void balancedWaitsThreeSecondsBeforeUnfocused() {
        assertEquals(State.FOCUSED, state(BALANCED, false, true, false, GRACE_MS - 1, 0, 5));
        assertEquals(State.UNFOCUSED, state(BALANCED, false, true, false, GRACE_MS, 0, 5));
        assertEquals(State.INVISIBLE, state(BALANCED, false, false, false, 0, 0, 5), "no grace when minimized");
    }

    @Test void targetsLimitsAndVolumesMatchTheDefaultProfiles() {
        assertEquals(60, target(State.UNFOCUSED, 60, 30, DEFAULTS));
        assertEquals(30, target(State.INVISIBLE, 60, 30, DEFAULTS));
        assertEquals(10, target(State.ABANDONED, 60, 30, DEFAULTS));
        assertEquals(HOVERED_FPS, target(State.HOVERED, 15, 5, DEFAULTS));
        assertEquals(UNLIMITED, target(State.FOCUSED, 15, 5, DEFAULTS));
        assertEquals(60, limit(60, 260), "caps an unlimited player limit");
        assertEquals(30, limit(60, 30), "never raises the player's own limit");
        assertEquals(144, limit(UNLIMITED, 144), "260 keeps the player's limit");
        assertEquals(0.25f, volume(State.UNFOCUSED, DEFAULTS));
        assertEquals(0f, volume(State.INVISIBLE, DEFAULTS));
        assertEquals(1f, volume(State.ABANDONED, DEFAULTS));
        assertArrayEquals(new int[] {15, 5}, preset(AGGRESSIVE));
        assertArrayEquals(new int[] {60, 30}, preset(BALANCED));
    }

    @Test void volumeFadesUpInOneSecondAndDownInTwo() {
        assertEquals(0.5f, fade(0f, 1f, 0.5f), 1e-6);
        assertEquals(1f, fade(0.9f, 1f, 0.5f), 1e-6);
        assertEquals(0.75f, fade(1f, 0.25f, 0.5f), 1e-6);
        assertEquals(0.25f, fade(0.3f, 0.25f, 0.5f), 1e-6);
    }

    @Test void profileClamps() {
        Profile p = new Profile();
        p.unfocusedVolume = 2; p.hiddenVolume = -1; p.idleFps = 999; p.idleMinutes = -3;
        p.clamp();
        assertEquals(1f, p.unfocusedVolume);
        assertEquals(0f, p.hiddenVolume);
        assertEquals(UNLIMITED, p.idleFps);
        assertEquals(0, p.idleMinutes);
    }

    /** 1.9.11 folded 1.8.9's Unfocused FPS cap into Dynamic FPS: a changed cap carries over unless Dynamic FPS was changed itself. */
    @Test void unfocusedCapMigratesIntoDynamicFps() {
        var before = ConfigManager.toJson();
        try {
            var changedCap = JsonParser.parseString("{\"UnfocusedFpsCap\":{\"enabled\":true,\"lastModified\":5,\"options\":{\"FPS limit\":25}},"
                + "\"DynamicFPS\":{\"enabled\":true,\"lastModified\":0}}").getAsJsonObject();
            ConfigManager.migrateUnfocusedCap(changedCap);
            assertFalse(changedCap.has("UnfocusedFpsCap"));
            assertEquals(25, changedCap.getAsJsonObject("DynamicFPS").getAsJsonObject("options").get("Unfocused FPS").getAsInt());

            var offCap = JsonParser.parseString("{\"UnfocusedFpsCap\":{\"enabled\":false,\"lastModified\":5}}").getAsJsonObject();
            ConfigManager.migrateUnfocusedCap(offCap);
            assertFalse(offCap.getAsJsonObject("DynamicFPS").get("enabled").getAsBoolean());

            var ownDynamic = JsonParser.parseString("{\"UnfocusedFpsCap\":{\"enabled\":false,\"lastModified\":5},"
                + "\"DynamicFPS\":{\"enabled\":true,\"lastModified\":9,\"options\":{\"Unfocused FPS\":40}}}").getAsJsonObject();
            ConfigManager.migrateUnfocusedCap(ownDynamic);
            assertTrue(ownDynamic.getAsJsonObject("DynamicFPS").get("enabled").getAsBoolean(), "Dynamic FPS's own change wins");
            assertEquals(40, ownDynamic.getAsJsonObject("DynamicFPS").getAsJsonObject("options").get("Unfocused FPS").getAsInt());

            var untouched = JsonParser.parseString("{\"UnfocusedFpsCap\":{\"enabled\":false,\"lastModified\":0}}").getAsJsonObject();
            ConfigManager.migrateUnfocusedCap(untouched);
            assertFalse(untouched.has("UnfocusedFpsCap"));
            assertFalse(untouched.has("DynamicFPS"), "an untouched cap was only the old default");

            ConfigManager.applyJson(JsonParser.parseString("{\"modules\":{\"UnfocusedFpsCap\":{\"enabled\":true,\"lastModified\":5,\"options\":{\"FPS limit\":45}}}}").getAsJsonObject());
            assertEquals(45, ((SliderOption) ModuleManager.getInstance().getModule("DynamicFPS").getOption("Unfocused FPS")).getValue(), 1e-9);
        } finally {
            ConfigManager.applyJson(before);
        }
    }
}
