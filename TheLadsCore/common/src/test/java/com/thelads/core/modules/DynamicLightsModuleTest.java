package com.thelads.core.modules;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.thelads.core.config.ConfigManager;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.Option;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DynamicLightsModuleTest {
    @Test void defaultsMatchTheLambDynamicLightsItReplaces() {
        var module = new DynamicLightsModule();
        assertTrue(module.isEnabled());
        assertTrue(module.fancy());
        assertTrue(module.entities());
        assertTrue(module.droppedItems());
        assertTrue(module.explosionsAndTnt());
        assertTrue(module.glowingMobsAndProjectiles());
        assertFalse(module.underwater(), "torches go out underwater");
        assertEquals(15f, module.radius());
        for (Option option : module.getOptions()) assertFalse(module.hidden(option), "26.x shows every option");
    }

    @Test void drivesOptiFineBothWays() {
        var module = new DynamicLightsModule();
        module.drivesOptiFine();
        assertEquals(DynamicLightsModule.OPTIFINE_FANCY, module.optiFineSetting());
        module.followOptiFine(DynamicLightsModule.OPTIFINE_FAST);
        assertTrue(module.isEnabled());
        assertFalse(module.fancy());
        assertEquals(DynamicLightsModule.OPTIFINE_FAST, module.optiFineSetting());
        module.followOptiFine(DynamicLightsModule.OPTIFINE_OFF);
        assertFalse(module.isEnabled());
        assertFalse(module.fancy(), "off keeps the last quality");
        assertEquals(DynamicLightsModule.OPTIFINE_OFF, module.optiFineSetting());
        module.followOptiFine(DynamicLightsModule.OPTIFINE_FANCY);
        assertEquals(DynamicLightsModule.OPTIFINE_FANCY, module.optiFineSetting());
        for (Option option : module.getOptions())
            assertEquals(!option.getName().equals("Quality"), module.hidden(option), option.getName() + ": OptiFine has only Off/Fast/Fancy");
    }

    @Test void unchangedOneSixSettingsGiveWayToTheNewDefaults() {
        var live = (DynamicLightsModule) ModuleManager.getInstance().getModule(DynamicLightsModule.NAME);
        JsonObject before = ConfigManager.toJson();
        try {
            live.setEnabled(true); // a fresh start's defaults
            live.getOptions().forEach(Option::reset);
            // 1.6.0 saved the never-switchable module's old defaults, with lastModified 0.
            ConfigManager.applyJson(JsonParser.parseString("{\"modules\":{\"DynamicLights\":{\"enabled\":false,\"options\":{\"Light Radius\":15.0,"
                + "\"Quality\":0,\"Entities\":true,\"Dropped Items\":true,\"Underwater\":true},\"favorite\":false,\"lastModified\":0}}}").getAsJsonObject());
            assertTrue(live.isEnabled());
            assertTrue(live.fancy());
            assertFalse(live.underwater());
            // A player's change since (lastModified set) is kept.
            ConfigManager.applyJson(JsonParser.parseString("{\"modules\":{\"DynamicLights\":{\"enabled\":false,\"options\":{\"Light Radius\":9.0,"
                + "\"Quality\":0,\"Underwater\":true},\"lastModified\":1790000000000}}}").getAsJsonObject());
            assertFalse(live.isEnabled());
            assertFalse(live.fancy());
            assertTrue(live.underwater());
            assertEquals(9f, live.radius());
        } finally {
            ConfigManager.applyJson(before);
        }
    }

    @Test
    void optionTogglesDisableExplosionsAndGlowingMobs() {
        var module = new DynamicLightsModule();
        assertTrue(module.explosionsAndTnt());
        assertTrue(module.glowingMobsAndProjectiles());

        // Toggle Explosions & TNT off
        module.getOption("Explosions & TNT").load(new com.google.gson.JsonPrimitive(false));
        assertFalse(module.explosionsAndTnt());

        // Toggle Glowing Mobs & Projectiles off
        module.getOption("Glowing Mobs & Projectiles").load(new com.google.gson.JsonPrimitive(false));
        assertFalse(module.glowingMobsAndProjectiles());

        // When Explosions & TNT is off, verify DynamicLights collects no flash sources
        com.thelads.core.client.DynamicLights lights = new com.thelads.core.client.DynamicLights();
        com.thelads.core.client.ExplosionFlashes flashes = new com.thelads.core.client.ExplosionFlashes();
        flashes.add(10.5, 64.5, 10.5, 4.0f);
        lights.begin();
        if (module.explosionsAndTnt()) {
            flashes.collect(lights);
        }
        lights.end(15, 0.25f, 1, (a, b, c, d, e, f) -> {});
        assertEquals(0, lights.at(10, 64, 10), "no flash dynamic light when Explosions & TNT is off");

        // Reset restores defaults
        module.getOption("Explosions & TNT").reset();
        module.getOption("Glowing Mobs & Projectiles").reset();
        assertTrue(module.explosionsAndTnt());
        assertTrue(module.glowingMobsAndProjectiles());

        // With option enabled, flashes collect properly
        lights.begin();
        if (module.explosionsAndTnt()) {
            flashes.collect(lights);
        }
        lights.end(15, 0.25f, 1, (a, b, c, d, e, f) -> {});
        assertEquals(15, lights.at(10, 64, 10), "flash dynamic light present when Explosions & TNT is on");
    }
}
