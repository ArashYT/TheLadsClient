package com.thelads.core.modules;

import com.thelads.core.config.BoolOption;
import com.thelads.core.config.DropdownOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.SliderOption;

/**
 * OptiFine 1.8.9 settings module: stores and manages all OptiFine video, quality, details,
 * animations, performance, and other settings inside the Lads Client.
 * <p>
 * Hot paths check static volatile booleans/integers directly without map lookups or object allocations.
 */
public class OptiFine189Module extends Module {
    public static final String NAME = "OptiFine189";

    // ---- Details Options ----
    public final DropdownOption ofClouds;
    public final SliderOption ofCloudHeight;
    public final DropdownOption ofTrees;
    public final DropdownOption ofRain;
    public final BoolOption ofSky;
    public final BoolOption ofStars;
    public final BoolOption ofSunMoon;
    public final BoolOption ofShowCapes;
    public final DropdownOption ofFogType;
    public final SliderOption ofFogStart;
    public final DropdownOption ofTranslucentBlocks;
    public final DropdownOption ofDroppedItems;
    public final DropdownOption ofVignette;

    // ---- Animations Options ----
    public final BoolOption animWater;
    public final BoolOption animLava;
    public final BoolOption animFire;
    public final BoolOption animPortal;
    public final BoolOption animRedstone;
    public final BoolOption animExplosion;
    public final BoolOption animFlame;
    public final BoolOption animSmoke;
    public final BoolOption animVoidParticles;
    public final BoolOption animWaterParticles;
    public final BoolOption animRainSplash;
    public final BoolOption animPortalParticles;
    public final BoolOption animPotionParticles;
    public final BoolOption animDrippingWaterLava;
    public final BoolOption animTerrain;
    public final BoolOption animTextures;
    public final BoolOption animFirework;

    // ---- Quality Options ----
    public final DropdownOption ofMipmapType;
    public final DropdownOption ofAfLevel;
    public final BoolOption ofClearWater;
    public final DropdownOption ofBetterGrass;
    public final BoolOption ofBetterSnow;
    public final BoolOption ofCustomFonts;
    public final BoolOption ofSwampColors;
    public final BoolOption ofSmoothBiomes;
    public final BoolOption ofRandomEntities;

    // ---- Performance Options ----
    public final BoolOption ofSmoothFps;
    public final BoolOption ofSmoothWorld;
    public final BoolOption ofFastMath;
    public final SliderOption ofChunkUpdates;
    public final BoolOption ofDynamicUpdates;
    public final BoolOption ofLazyChunkLoading;

    // ---- Other Options ----
    public final BoolOption ofWeather;
    public final DropdownOption ofTime;
    public final DropdownOption ofAutosaveTicks;
    public final DropdownOption ofScreenshotSize;
    public final BoolOption ofShowGlErrors;

    // ==========================================
    // Fast static fields for zero-allocation hot paths
    // ==========================================
    public static volatile boolean sAnimWater = true;
    public static volatile boolean sAnimLava = true;
    public static volatile boolean sAnimFire = true;
    public static volatile boolean sAnimPortal = true;
    public static volatile boolean sAnimRedstone = true;
    public static volatile boolean sAnimExplosion = true;
    public static volatile boolean sAnimFlame = true;
    public static volatile boolean sAnimSmoke = true;
    public static volatile boolean sAnimVoidParticles = true;
    public static volatile boolean sAnimWaterParticles = true;
    public static volatile boolean sAnimRainSplash = true;
    public static volatile boolean sAnimPortalParticles = true;
    public static volatile boolean sAnimPotionParticles = true;
    public static volatile boolean sAnimDrippingWaterLava = true;
    public static volatile boolean sAnimTerrain = true;
    public static volatile boolean sAnimTextures = true;
    public static volatile boolean sAnimFirework = true;

    public static volatile int sOfClouds = 0; // 0=Default, 1=Fast, 2=Fancy, 3=OFF
    public static volatile float sOfCloudHeight = 256.0f; // 64..256
    public static volatile int sOfTrees = 0; // 0=Default, 1=Fast, 2=Fancy, 3=Smart
    public static volatile int sOfRain = 0; // 0=Default, 1=Fast, 2=Fancy, 3=OFF
    public static volatile boolean sOfSky = true;
    public static volatile boolean sOfStars = true;
    public static volatile boolean sOfSunMoon = true;
    public static volatile boolean sOfShowCapes = true;
    public static volatile int sOfFogType = 1; // 0=Fast, 1=Fancy, 2=OFF
    public static volatile float sOfFogStart = 0.8f;
    public static volatile int sOfTranslucentBlocks = 0; // 0=Default, 1=Fast, 2=Fancy
    public static volatile int sOfDroppedItems = 0; // 0=Default, 1=Fast, 2=Fancy
    public static volatile int sOfVignette = 0; // 0=Default, 1=Fast, 2=Fancy

    public static volatile int sOfMipmapType = 0; // 0=Nearest, 1=Bilinear, 2=Linear, 3=Trilinear
    public static volatile int sOfAfLevel = 0; // 0=OFF, 1=2x, 2=4x, 3=8x, 4=16x
    public static volatile boolean sOfClearWater = false;
    public static volatile int sOfBetterGrass = 0; // 0=OFF, 1=Fast, 2=Fancy
    public static volatile boolean sOfBetterSnow = false;
    public static volatile boolean sOfCustomFonts = true;
    public static volatile boolean sOfSwampColors = true;
    public static volatile boolean sOfSmoothBiomes = true;
    public static volatile boolean sOfRandomEntities = true;

    public static volatile boolean sOfSmoothFps = false;
    public static volatile boolean sOfSmoothWorld = false;
    public static volatile boolean sOfFastMath = true;
    public static volatile int sOfChunkUpdates = 1;
    public static volatile boolean sOfDynamicUpdates = false;
    public static volatile boolean sOfLazyChunkLoading = false;

    public static volatile boolean sOfWeather = true;
    public static volatile int sOfTime = 0; // 0=Default, 1=Day Only, 2=Night Only
    public static volatile int sOfAutosaveTicks = 0; // 0=Default, 1=2s, 2=20s, 3=2min, 4=10min, 5=30min
    public static volatile int sOfScreenshotSize = 0; // 0=Default, 1=2x, 2=3x, 3=4x
    public static volatile boolean sOfShowGlErrors = true;

    private static OptiFine189Module INSTANCE;

    public OptiFine189Module() {
        super(NAME, "All OptiFine video, quality, details, animations, and performance options inside Lads Client.");

        // Details
        ofClouds = addOption(new DropdownOption("Clouds", 0, "Default", "Fast", "Fancy", "OFF"));
        ofCloudHeight = addOption(new SliderOption("Cloud Height", 256.0, 64.0, 256.0, 4.0));
        ofTrees = addOption(new DropdownOption("Trees", 0, "Default", "Fast", "Fancy", "Smart"));
        ofRain = addOption(new DropdownOption("Rain & Snow", 0, "Default", "Fast", "Fancy", "OFF"));
        ofSky = addOption(new BoolOption("Sky", true));
        ofStars = addOption(new BoolOption("Stars", true));
        ofSunMoon = addOption(new BoolOption("Sun & Moon", true));
        ofShowCapes = addOption(new BoolOption("Show Capes", true));
        ofFogType = addOption(new DropdownOption("Fog", 1, "Fast", "Fancy", "OFF"));
        ofFogStart = addOption(new SliderOption("Fog Start", 0.8, 0.2, 0.8, 0.2));
        ofTranslucentBlocks = addOption(new DropdownOption("Translucent Blocks", 0, "Default", "Fast", "Fancy"));
        ofDroppedItems = addOption(new DropdownOption("Dropped Items", 0, "Default", "Fast", "Fancy"));
        ofVignette = addOption(new DropdownOption("Vignette", 0, "Default", "Fast", "Fancy"));

        // Animations
        animWater = addOption(new BoolOption("Animated Water", true));
        animLava = addOption(new BoolOption("Animated Lava", true));
        animFire = addOption(new BoolOption("Animated Fire", true));
        animPortal = addOption(new BoolOption("Animated Portal", true));
        animRedstone = addOption(new BoolOption("Animated Redstone", true));
        animExplosion = addOption(new BoolOption("Animated Explosion", true));
        animFlame = addOption(new BoolOption("Animated Flame", true));
        animSmoke = addOption(new BoolOption("Animated Smoke", true));
        animVoidParticles = addOption(new BoolOption("Void Particles", true));
        animWaterParticles = addOption(new BoolOption("Water Particles", true));
        animRainSplash = addOption(new BoolOption("Rain Splash", true));
        animPortalParticles = addOption(new BoolOption("Portal Particles", true));
        animPotionParticles = addOption(new BoolOption("Potion Particles", true));
        animDrippingWaterLava = addOption(new BoolOption("Dripping Water/Lava", true));
        animTerrain = addOption(new BoolOption("Animated Terrain", true));
        animTextures = addOption(new BoolOption("Animated Textures", true));
        animFirework = addOption(new BoolOption("Firework Particles", true));

        // Quality
        ofMipmapType = addOption(new DropdownOption("Mipmap Type", 0, "Nearest", "Bilinear", "Linear", "Trilinear"));
        ofAfLevel = addOption(new DropdownOption("Anisotropic Filtering", 0, "OFF", "2", "4", "8", "16"));
        ofClearWater = addOption(new BoolOption("Clear Water", false));
        ofBetterGrass = addOption(new DropdownOption("Better Grass", 0, "OFF", "Fast", "Fancy"));
        ofBetterSnow = addOption(new BoolOption("Better Snow", false));
        ofCustomFonts = addOption(new BoolOption("Custom Fonts", true));
        ofSwampColors = addOption(new BoolOption("Swamp Colors", true));
        ofSmoothBiomes = addOption(new BoolOption("Smooth Biomes", true));
        ofRandomEntities = addOption(new BoolOption("Random Entities", true));

        // Performance
        ofSmoothFps = addOption(new BoolOption("Smooth FPS", false));
        ofSmoothWorld = addOption(new BoolOption("Smooth World", false));
        ofFastMath = addOption(new BoolOption("Fast Math", true));
        ofChunkUpdates = addOption(new SliderOption("Chunk Updates", 1.0, 1.0, 5.0, 1.0));
        ofDynamicUpdates = addOption(new BoolOption("Dynamic Updates", false));
        ofLazyChunkLoading = addOption(new BoolOption("Lazy Chunk Loading", false));

        // Other
        ofWeather = addOption(new BoolOption("Weather", true));
        ofTime = addOption(new DropdownOption("Time", 0, "Default", "Day Only", "Night Only"));
        ofAutosaveTicks = addOption(new DropdownOption("Autosave", 0, "Default", "2s", "20s", "2min", "10min", "30min"));
        ofScreenshotSize = addOption(new DropdownOption("Screenshot Size", 0, "Default", "2x", "3x", "4x"));
        ofShowGlErrors = addOption(new BoolOption("Show GL Errors", true));

        setEnabled(true);
        INSTANCE = this;
        sync();
    }

    public static OptiFine189Module getInstance() {
        return INSTANCE;
    }

    /**
     * Synchronizes all internal options to fast static fields.
     * Called whenever an option is modified or when config is loaded.
     */
    public void sync() {
        sAnimWater = animWater.get();
        sAnimLava = animLava.get();
        sAnimFire = animFire.get();
        sAnimPortal = animPortal.get();
        sAnimRedstone = animRedstone.get();
        sAnimExplosion = animExplosion.get();
        sAnimFlame = animFlame.get();
        sAnimSmoke = animSmoke.get();
        sAnimVoidParticles = animVoidParticles.get();
        sAnimWaterParticles = animWaterParticles.get();
        sAnimRainSplash = animRainSplash.get();
        sAnimPortalParticles = animPortalParticles.get();
        sAnimPotionParticles = animPotionParticles.get();
        sAnimDrippingWaterLava = animDrippingWaterLava.get();
        sAnimTerrain = animTerrain.get();
        sAnimTextures = animTextures.get();
        sAnimFirework = animFirework.get();

        sOfClouds = ofClouds.getIndex();
        sOfCloudHeight = (float) ofCloudHeight.getValue();
        sOfTrees = ofTrees.getIndex();
        sOfRain = ofRain.getIndex();
        sOfSky = ofSky.get();
        sOfStars = ofStars.get();
        sOfSunMoon = ofSunMoon.get();
        sOfShowCapes = ofShowCapes.get();
        sOfFogType = ofFogType.getIndex();
        sOfFogStart = (float) ofFogStart.getValue();
        sOfTranslucentBlocks = ofTranslucentBlocks.getIndex();
        sOfDroppedItems = ofDroppedItems.getIndex();
        sOfVignette = ofVignette.getIndex();

        sOfMipmapType = ofMipmapType.getIndex();
        sOfAfLevel = ofAfLevel.getIndex();
        sOfClearWater = ofClearWater.get();
        sOfBetterGrass = ofBetterGrass.getIndex();
        sOfBetterSnow = ofBetterSnow.get();
        sOfCustomFonts = ofCustomFonts.get();
        sOfSwampColors = ofSwampColors.get();
        sOfSmoothBiomes = ofSmoothBiomes.get();
        sOfRandomEntities = ofRandomEntities.get();

        sOfSmoothFps = ofSmoothFps.get();
        sOfSmoothWorld = ofSmoothWorld.get();
        sOfFastMath = ofFastMath.get();
        sOfChunkUpdates = (int) ofChunkUpdates.getValue();
        sOfDynamicUpdates = ofDynamicUpdates.get();
        sOfLazyChunkLoading = ofLazyChunkLoading.get();

        sOfWeather = ofWeather.get();
        sOfTime = ofTime.getIndex();
        sOfAutosaveTicks = ofAutosaveTicks.getIndex();
        sOfScreenshotSize = ofScreenshotSize.getIndex();
        sOfShowGlErrors = ofShowGlErrors.get();
    }

    /**
     * Toggles all animations simultaneously (OptiFine "All ON" / "All OFF" buttons).
     */
    public void setAllAnimations(boolean on) {
        animWater.set(on);
        animLava.set(on);
        animFire.set(on);
        animPortal.set(on);
        animRedstone.set(on);
        animExplosion.set(on);
        animFlame.set(on);
        animSmoke.set(on);
        animVoidParticles.set(on);
        animWaterParticles.set(on);
        animRainSplash.set(on);
        animPortalParticles.set(on);
        animPotionParticles.set(on);
        animDrippingWaterLava.set(on);
        animTerrain.set(on);
        animTextures.set(on);
        animFirework.set(on);
        sync();
    }

    /**
     * Fast particle filter for EffectRenderer.spawnEffectParticle.
     * Evaluates in nanoseconds via static boolean lookup, zero allocations.
     *
     * @param particleId EnumParticleTypes ordinal (0..41 in 1.8.9)
     * @return true if the particle should be spawned, false if suppressed by animation settings
     */
    public static boolean allowParticle(int particleId) {
        switch (particleId) {
            case 0: // EXPLOSION_NORMAL
            case 1: // EXPLOSION_LARGE
            case 2: // EXPLOSION_HUGE
                return sAnimExplosion;
            case 3: // FIREWORKS_SPARK
                return sAnimFirework;
            case 4: // WATER_BUBBLE
            case 5: // WATER_SPLASH
            case 6: // WATER_WAKE
                return sAnimWaterParticles;
            case 7: // SUSPENDED
            case 8: // SUSPENDED_DEPTH
                return sAnimVoidParticles;
            case 11: // SMOKE_NORMAL
            case 12: // SMOKE_LARGE
                return sAnimSmoke;
            case 13: // SPELL
            case 14: // SPELL_INSTANT
            case 15: // SPELL_MOB
            case 16: // SPELL_MOB_AMBIENT
            case 17: // SPELL_WITCH
                return sAnimPotionParticles;
            case 18: // DRIP_WATER
            case 19: // DRIP_LAVA
                return sAnimDrippingWaterLava;
            case 24: // PORTAL
                return sAnimPortalParticles;
            case 26: // FLAME
                return sAnimFlame;
            case 30: // REDSTONE
                return sAnimRedstone;
            case 38: // WATER_DROP (Rain Splash)
                return sAnimRainSplash;
            default:
                return true;
        }
    }

    /**
     * Resets all settings to their default values.
     */
    public void resetDefaults() {
        ofClouds.setIndex(0);
        ofCloudHeight.setValue(256.0);
        ofTrees.setIndex(0);
        ofRain.setIndex(0);
        ofSky.set(true);
        ofStars.set(true);
        ofSunMoon.set(true);
        ofShowCapes.set(true);
        ofFogType.setIndex(1);
        ofFogStart.setValue(0.8);
        ofTranslucentBlocks.setIndex(0);
        ofDroppedItems.setIndex(0);
        ofVignette.setIndex(0);

        setAllAnimations(true);

        ofMipmapType.setIndex(0);
        ofAfLevel.setIndex(0);
        ofClearWater.set(false);
        ofBetterGrass.setIndex(0);
        ofBetterSnow.set(false);
        ofCustomFonts.set(true);
        ofSwampColors.set(true);
        ofSmoothBiomes.set(true);
        ofRandomEntities.set(true);

        ofSmoothFps.set(false);
        ofSmoothWorld.set(false);
        ofFastMath.set(true);
        ofChunkUpdates.setValue(1.0);
        ofDynamicUpdates.set(false);
        ofLazyChunkLoading.set(false);

        ofWeather.set(true);
        ofTime.setIndex(0);
        ofAutosaveTicks.setIndex(0);
        ofScreenshotSize.setIndex(0);
        ofShowGlErrors.set(true);

        sync();
    }
}
