package com.thelads.core.modules;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OptiFine189ModuleTest {
    private OptiFine189Module module;

    @BeforeEach
    void setUp() {
        module = new OptiFine189Module();
        module.resetDefaults();
    }

    @Test
    void defaultsMatchOptiFine189() {
        assertEquals(0, module.ofTrees.getIndex(), "Trees default is Default (0)");
        assertEquals(0, module.ofRain.getIndex(), "Rain & snow default is Default (0)");
        assertTrue(module.ofSky.get(), "Sky is ON by default");
        assertTrue(module.ofStars.get(), "Stars is ON by default");
        assertTrue(module.ofSunMoon.get(), "Sun & Moon is ON by default");
        assertEquals(1, module.ofFogType.getIndex(), "Fog default is Fancy (1)");
        assertEquals(0.8, module.ofFogStart.getValue(), 1e-4, "Fog Start default is 0.8");
        assertEquals(0, module.ofDroppedItems.getIndex(), "Dropped Items default is Default (0)");
        assertEquals(0, module.ofVignette.getIndex(), "Vignette default is Default (0)");

        assertTrue(module.animWater.get(), "Water animation is ON by default");
        assertTrue(module.animLava.get(), "Lava animation is ON by default");
        assertTrue(module.animFire.get(), "Fire animation is ON by default");
        assertTrue(module.animPortal.get(), "Portal animation is ON by default");
        assertTrue(module.animRedstone.get(), "Redstone animation is ON by default");
        assertTrue(module.animExplosion.get(), "Explosion animation is ON by default");
        assertTrue(module.animFlame.get(), "Flame animation is ON by default");
        assertTrue(module.animSmoke.get(), "Smoke animation is ON by default");
        assertTrue(module.animVoidParticles.get(), "Void particles is ON by default");
        assertTrue(module.animWaterParticles.get(), "Water particles is ON by default");
        assertTrue(module.animRainSplash.get(), "Rain splash is ON by default");
        assertTrue(module.animPortalParticles.get(), "Portal particles is ON by default");
        assertTrue(module.animPotionParticles.get(), "Potion particles is ON by default");
        assertTrue(module.animDrippingWaterLava.get(), "Dripping water/lava is ON by default");
        assertTrue(module.animTerrain.get(), "Terrain animation is ON by default");
        assertTrue(module.animTextures.get(), "Textures animation is ON by default");
        assertTrue(module.animFirework.get(), "Firework particles is ON by default");

        assertFalse(module.ofClearWater.get(), "Clear Water default is OFF");
        assertTrue(module.ofSmoothBiomes.get(), "Smooth Biomes default is ON");

        assertFalse(module.ofSmoothFps.get(), "Smooth FPS default is OFF");
        assertEquals(1.0, module.ofChunkUpdates.getValue(), 1e-4, "Chunk Updates default is 1");
        assertFalse(module.ofDynamicUpdates.get(), "Dynamic Updates default is OFF");

        assertTrue(module.ofWeather.get(), "Weather default is ON");
        assertEquals(0, module.ofTime.getIndex(), "Time default is Default (0)");
        assertTrue(module.ofShowGlErrors.get(), "Show GL Errors default is ON");

        // Verify synced static fields and fast-path flags
        assertTrue(OptiFine189Module.sAnimWater);
        assertEquals(1, OptiFine189Module.sOfFogType);
        assertEquals(0.8f, OptiFine189Module.sOfFogStart, 1e-4f);
        assertFalse(OptiFine189Module.sAnimAnyTextureDisabled, "Fast path flag: no textures disabled at default");
        assertTrue(OptiFine189Module.sAllParticlesEnabled, "Fast path flag: all particles enabled at default");
    }

    @Test
    void valueCyclingWorksAcrossOptions() {
        // Trees cycle 0..3
        assertEquals(4, module.ofTrees.getChoices().length);
        module.ofTrees.setIndex(2); // Fancy
        module.sync();
        assertEquals(2, OptiFine189Module.sOfTrees);

        // Rain cycle 0..3
        assertEquals(4, module.ofRain.getChoices().length);
        module.ofRain.setIndex(3); // OFF
        module.sync();
        assertEquals(3, OptiFine189Module.sOfRain);

        // Fog cycle 0..2
        assertEquals(3, module.ofFogType.getChoices().length);
        module.ofFogType.setIndex(2); // OFF
        module.sync();
        assertEquals(2, OptiFine189Module.sOfFogType);

        // Dropped items 0..2
        assertEquals(3, module.ofDroppedItems.getChoices().length);
        module.ofDroppedItems.setIndex(1); // Fast
        module.sync();
        assertEquals(1, OptiFine189Module.sOfDroppedItems);

        // Vignette 0..2
        assertEquals(3, module.ofVignette.getChoices().length);
        module.ofVignette.setIndex(1); // Fast
        module.sync();
        assertEquals(1, OptiFine189Module.sOfVignette);

        // Chunk Updates 1..5
        module.ofChunkUpdates.setValue(3.0);
        module.sync();
        assertEquals(3, OptiFine189Module.sOfChunkUpdates);
    }

    @Test
    void particleFilteringMatchesAnimationToggles() {
        assertTrue(OptiFine189Module.allowParticle(0), "Explosion particle allowed initially");
        module.animExplosion.set(false);
        module.sync();
        assertFalse(OptiFine189Module.sAllParticlesEnabled);
        assertFalse(OptiFine189Module.allowParticle(0), "Explosion particle suppressed when animExplosion is false");
        assertFalse(OptiFine189Module.allowParticle(1), "Explosion large particle suppressed");
        assertFalse(OptiFine189Module.allowParticle(2), "Explosion huge particle suppressed");

        assertTrue(OptiFine189Module.allowParticle(3), "Fireworks allowed initially");
        module.animFirework.set(false);
        module.sync();
        assertFalse(OptiFine189Module.allowParticle(3), "Fireworks suppressed when animFirework is false");

        assertTrue(OptiFine189Module.allowParticle(4), "Water bubble allowed initially");
        module.animWaterParticles.set(false);
        module.sync();
        assertFalse(OptiFine189Module.allowParticle(4), "Water bubble suppressed");
        assertFalse(OptiFine189Module.allowParticle(5), "Water splash suppressed");

        assertTrue(OptiFine189Module.allowParticle(7), "Void dust allowed initially");
        module.animVoidParticles.set(false);
        module.sync();
        assertFalse(OptiFine189Module.allowParticle(7), "Void dust suppressed");

        assertTrue(OptiFine189Module.allowParticle(11), "Smoke allowed initially");
        module.animSmoke.set(false);
        module.sync();
        assertFalse(OptiFine189Module.allowParticle(11), "Smoke suppressed");

        assertTrue(OptiFine189Module.allowParticle(13), "Spell potion allowed initially");
        module.animPotionParticles.set(false);
        module.sync();
        assertFalse(OptiFine189Module.allowParticle(13), "Spell potion suppressed");

        assertTrue(OptiFine189Module.allowParticle(18), "Drip water allowed initially");
        module.animDrippingWaterLava.set(false);
        module.sync();
        assertFalse(OptiFine189Module.allowParticle(18), "Drip water suppressed");

        assertTrue(OptiFine189Module.allowParticle(24), "Portal particle allowed initially");
        module.animPortalParticles.set(false);
        module.sync();
        assertFalse(OptiFine189Module.allowParticle(24), "Portal particle suppressed");

        assertTrue(OptiFine189Module.allowParticle(26), "Flame particle allowed initially");
        module.animFlame.set(false);
        module.sync();
        assertFalse(OptiFine189Module.allowParticle(26), "Flame particle suppressed");

        assertTrue(OptiFine189Module.allowParticle(30), "Redstone particle allowed initially");
        module.animRedstone.set(false);
        module.sync();
        assertFalse(OptiFine189Module.allowParticle(30), "Redstone particle suppressed");

        assertTrue(OptiFine189Module.allowParticle(38), "Rain splash allowed initially");
        module.animRainSplash.set(false);
        module.sync();
        assertFalse(OptiFine189Module.allowParticle(38), "Rain splash suppressed");
    }

    @Test
    void allAnimationsBulkToggle() {
        module.setAllAnimations(false);
        assertFalse(OptiFine189Module.sAnimWater);
        assertFalse(OptiFine189Module.sAnimLava);
        assertFalse(OptiFine189Module.sAnimFire);
        assertFalse(OptiFine189Module.sAnimPortal);
        assertFalse(OptiFine189Module.sAnimRedstone);
        assertFalse(OptiFine189Module.sAnimExplosion);
        assertFalse(OptiFine189Module.sAnimFlame);
        assertFalse(OptiFine189Module.sAnimSmoke);
        assertFalse(OptiFine189Module.sAnimVoidParticles);
        assertFalse(OptiFine189Module.sAnimWaterParticles);
        assertFalse(OptiFine189Module.sAnimRainSplash);
        assertFalse(OptiFine189Module.sAnimPortalParticles);
        assertFalse(OptiFine189Module.sAnimPotionParticles);
        assertFalse(OptiFine189Module.sAnimDrippingWaterLava);
        assertFalse(OptiFine189Module.sAnimTerrain);
        assertFalse(OptiFine189Module.sAnimTextures);
        assertFalse(OptiFine189Module.sAnimFirework);
        assertTrue(OptiFine189Module.sAnimAnyTextureDisabled);
        assertFalse(OptiFine189Module.sAllParticlesEnabled);

        module.setAllAnimations(true);
        assertTrue(OptiFine189Module.sAnimWater);
        assertTrue(OptiFine189Module.sAnimLava);
        assertTrue(OptiFine189Module.sAnimFire);
        assertTrue(OptiFine189Module.sAnimPortal);
        assertTrue(OptiFine189Module.sAnimRedstone);
        assertTrue(OptiFine189Module.sAnimExplosion);
        assertTrue(OptiFine189Module.sAnimFlame);
        assertTrue(OptiFine189Module.sAnimSmoke);
        assertTrue(OptiFine189Module.sAnimVoidParticles);
        assertTrue(OptiFine189Module.sAnimWaterParticles);
        assertTrue(OptiFine189Module.sAnimRainSplash);
        assertTrue(OptiFine189Module.sAnimPortalParticles);
        assertTrue(OptiFine189Module.sAnimPotionParticles);
        assertTrue(OptiFine189Module.sAnimDrippingWaterLava);
        assertTrue(OptiFine189Module.sAnimTerrain);
        assertTrue(OptiFine189Module.sAnimTextures);
        assertTrue(OptiFine189Module.sAnimFirework);
        assertFalse(OptiFine189Module.sAnimAnyTextureDisabled);
        assertTrue(OptiFine189Module.sAllParticlesEnabled);
    }

    @Test
    void resetDefaultsRestoresAllOptions() {
        module.ofTrees.setIndex(2);
        module.ofFogType.setIndex(2);
        module.ofFogStart.setValue(0.2);
        module.ofClearWater.set(true);
        module.setAllAnimations(false);
        module.sync();

        assertEquals(2, OptiFine189Module.sOfTrees);
        assertEquals(2, OptiFine189Module.sOfFogType);
        assertEquals(0.2f, OptiFine189Module.sOfFogStart, 1e-4f);
        assertTrue(OptiFine189Module.sOfClearWater);
        assertFalse(OptiFine189Module.sAnimWater);

        module.resetDefaults();

        assertEquals(0, OptiFine189Module.sOfTrees);
        assertEquals(1, OptiFine189Module.sOfFogType);
        assertEquals(0.8f, OptiFine189Module.sOfFogStart, 1e-4f);
        assertFalse(OptiFine189Module.sOfClearWater);
        assertTrue(OptiFine189Module.sAnimWater);
        assertFalse(OptiFine189Module.sAnimAnyTextureDisabled);
        assertTrue(OptiFine189Module.sAllParticlesEnabled);
    }
}
