package com.thelads.core.e2e.mcmods;

import com.thelads.core.config.ConfigManager;
import com.thelads.core.config.Module;
import com.thelads.core.e2e.mcmods.condition.EnabledIfClassPresent;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.*;

public class ConfigurableModulesTest extends BaseMcModsTest {

    private void runConfigurableModuleTest(String moduleName, String... classNames) throws Exception {
        boolean anyPresent = false;
        for (String className : classNames) {
            if (isClassPresent(className)) {
                anyPresent = true;
                break;
            }
        }
        assertTrue(anyPresent, "At least one class for module " + moduleName + " should be present");
        Module module = getModule(moduleName);
        assertNotNull(module, "Module '" + moduleName + "' should be registered in ModuleManager");

        boolean initialState = module.isEnabled();
        try {
            // Test toggle
            module.setEnabled(true);
            assertTrue(module.isEnabled(), "Module should be enabled");
            module.setEnabled(false);
            assertFalse(module.isEnabled(), "Module should be disabled");

            // Test persisting state to JSON (saving true)
            module.setEnabled(true);
            ConfigManager.save();
            verifyModuleStateInFile(moduleName, true);

            // Test persisting state to JSON (saving false)
            module.setEnabled(false);
            ConfigManager.save();
            verifyModuleStateInFile(moduleName, false);

            // Test loading state from JSON (loading true)
            String jsonTrue = "{ \"modules\": { \"" + moduleName + "\": { \"enabled\": true } } }";
            Files.writeString(tempConfigFile.toPath(), jsonTrue);
            ConfigManager.load();
            assertTrue(module.isEnabled(), "Module should be enabled after loading config with true");

            // Test loading state from JSON (loading false)
            String jsonFalse = "{ \"modules\": { \"" + moduleName + "\": { \"enabled\": false } } }";
            Files.writeString(tempConfigFile.toPath(), jsonFalse);
            ConfigManager.load();
            assertFalse(module.isEnabled(), "Module should be disabled after loading config with false");

        } finally {
            module.setEnabled(initialState);
        }
    }

    @Test
    @EnabledIfClassPresent({
        "com.thelads.core.modules.SmoothHotbarModule",
        "com.thelads.core.features.configurable.smoothhotbar.SmoothHotbarModule",
        "com.thelads.core.features.configurable.SmoothHotbarModule"
    })
    public void testSmoothHotbar() throws Exception {
        runConfigurableModuleTest("SmoothHotbar",
            "com.thelads.core.modules.SmoothHotbarModule",
            "com.thelads.core.features.configurable.smoothhotbar.SmoothHotbarModule",
            "com.thelads.core.features.configurable.SmoothHotbarModule"
        );
    }

    @Test
    @EnabledIfClassPresent({
        "com.thelads.core.modules.ScreenFXModule",
        "com.thelads.core.features.configurable.screenfx.ScreenFXModule",
        "com.thelads.core.features.configurable.ScreenFXModule"
    })
    public void testScreenFX() throws Exception {
        runConfigurableModuleTest("ScreenFX",
            "com.thelads.core.modules.ScreenFXModule",
            "com.thelads.core.features.configurable.screenfx.ScreenFXModule",
            "com.thelads.core.features.configurable.ScreenFXModule"
        );
    }

    @Test
    @EnabledIfClassPresent({
        "com.thelads.core.modules.AppleSkinModule",
        "com.thelads.core.features.configurable.appleskin.AppleSkinModule",
        "com.thelads.core.features.configurable.AppleSkinModule"
    })
    public void testAppleSkin() throws Exception {
        runConfigurableModuleTest("AppleSkin",
            "com.thelads.core.modules.AppleSkinModule",
            "com.thelads.core.features.configurable.appleskin.AppleSkinModule",
            "com.thelads.core.features.configurable.AppleSkinModule"
        );
    }

    @Test
    @EnabledIfClassPresent({
        "com.thelads.core.modules.BetterF1Module",
        "com.thelads.core.features.configurable.betterf1.BetterF1Module",
        "com.thelads.core.features.configurable.BetterF1Module"
    })
    public void testBetterF1() throws Exception {
        runConfigurableModuleTest("BetterF1",
            "com.thelads.core.modules.BetterF1Module",
            "com.thelads.core.features.configurable.betterf1.BetterF1Module",
            "com.thelads.core.features.configurable.BetterF1Module"
        );
    }

    @Test
    @EnabledIfClassPresent({
        "com.thelads.core.modules.ClientTweaksModule",
        "com.thelads.core.features.configurable.clienttweaks.ClientTweaksModule",
        "com.thelads.core.features.configurable.ClientTweaksModule"
    })
    public void testClientTweaks() throws Exception {
        runConfigurableModuleTest("ClientTweaks",
            "com.thelads.core.modules.ClientTweaksModule",
            "com.thelads.core.features.configurable.clienttweaks.ClientTweaksModule",
            "com.thelads.core.features.configurable.ClientTweaksModule"
        );
    }

    @Test
    @EnabledIfClassPresent({
        "com.thelads.core.modules.CursorsExtendedModule",
        "com.thelads.core.features.configurable.cursorsextended.CursorsExtendedModule",
        "com.thelads.core.features.configurable.CursorsExtendedModule"
    })
    public void testCursorsExtended() throws Exception {
        runConfigurableModuleTest("CursorsExtended",
            "com.thelads.core.modules.CursorsExtendedModule",
            "com.thelads.core.features.configurable.cursorsextended.CursorsExtendedModule",
            "com.thelads.core.features.configurable.CursorsExtendedModule"
        );
    }

    @Test
    @EnabledIfClassPresent({
        "com.thelads.core.modules.EnhancedToolbarsModule",
        "com.thelads.core.features.configurable.enhancedtoolbars.EnhancedToolbarsModule",
        "com.thelads.core.features.configurable.EnhancedToolbarsModule"
    })
    public void testEnhancedToolbars() throws Exception {
        runConfigurableModuleTest("EnhancedToolbars",
            "com.thelads.core.modules.EnhancedToolbarsModule",
            "com.thelads.core.features.configurable.enhancedtoolbars.EnhancedToolbarsModule",
            "com.thelads.core.features.configurable.EnhancedToolbarsModule"
        );
    }

    @Test
    @EnabledIfClassPresent({
        "com.thelads.core.modules.FancyDoorAnimationsModule",
        "com.thelads.core.features.configurable.fancydooranimations.FancyDoorAnimationsModule",
        "com.thelads.core.features.configurable.FancyDoorAnimationsModule"
    })
    public void testFancyDoorAnimations() throws Exception {
        runConfigurableModuleTest("FancyDoorAnimations",
            "com.thelads.core.modules.FancyDoorAnimationsModule",
            "com.thelads.core.features.configurable.fancydooranimations.FancyDoorAnimationsModule",
            "com.thelads.core.features.configurable.FancyDoorAnimationsModule"
        );
    }

    @Test
    @EnabledIfClassPresent({
        "com.thelads.core.modules.NotEnoughAnimationsModule",
        "com.thelads.core.features.configurable.notenoughanimations.NotEnoughAnimationsModule",
        "com.thelads.core.features.configurable.NotEnoughAnimationsModule"
    })
    public void testNotEnoughAnimations() throws Exception {
        runConfigurableModuleTest("NotEnoughAnimations",
            "com.thelads.core.modules.NotEnoughAnimationsModule",
            "com.thelads.core.features.configurable.notenoughanimations.NotEnoughAnimationsModule",
            "com.thelads.core.features.configurable.NotEnoughAnimationsModule"
        );
    }

    @Test
    @EnabledIfClassPresent({
        "com.thelads.core.modules.WaveyCapesModule",
        "com.thelads.core.features.configurable.waveycapes.WaveyCapesModule",
        "com.thelads.core.features.configurable.WaveyCapesModule"
    })
    public void testWaveyCapes() throws Exception {
        runConfigurableModuleTest("WaveyCapes",
            "com.thelads.core.modules.WaveyCapesModule",
            "com.thelads.core.features.configurable.waveycapes.WaveyCapesModule",
            "com.thelads.core.features.configurable.WaveyCapesModule"
        );
    }

    @Test
    @EnabledIfClassPresent({
        "com.thelads.core.modules.ExtremeSoundMufflerModule",
        "com.thelads.core.features.configurable.extremesoundmuffler.ExtremeSoundMufflerModule",
        "com.thelads.core.features.configurable.ExtremeSoundMufflerModule"
    })
    public void testExtremeSoundMuffler() throws Exception {
        runConfigurableModuleTest("ExtremeSoundMuffler",
            "com.thelads.core.modules.ExtremeSoundMufflerModule",
            "com.thelads.core.features.configurable.extremesoundmuffler.ExtremeSoundMufflerModule",
            "com.thelads.core.features.configurable.ExtremeSoundMufflerModule"
        );
    }

    @Test
    @EnabledIfClassPresent({
        "com.thelads.core.modules.EntityViewDistanceModule",
        "com.thelads.core.features.configurable.entityviewdistance.EntityViewDistanceModule",
        "com.thelads.core.features.configurable.EntityViewDistanceModule"
    })
    public void testEntityViewDistance() throws Exception {
        runConfigurableModuleTest("EntityViewDistance",
            "com.thelads.core.modules.EntityViewDistanceModule",
            "com.thelads.core.features.configurable.entityviewdistance.EntityViewDistanceModule",
            "com.thelads.core.features.configurable.EntityViewDistanceModule"
        );
    }

    @Test
    @EnabledIfClassPresent({
        "com.thelads.core.modules.JustEnoughItemsModule",
        "com.thelads.core.features.configurable.justenoughitems.JustEnoughItemsModule",
        "com.thelads.core.features.configurable.JustEnoughItemsModule"
    })
    public void testJustEnoughItems() throws Exception {
        runConfigurableModuleTest("JustEnoughItems",
            "com.thelads.core.modules.JustEnoughItemsModule",
            "com.thelads.core.features.configurable.justenoughitems.JustEnoughItemsModule",
            "com.thelads.core.features.configurable.JustEnoughItemsModule"
        );
    }

    @Test
    @EnabledIfClassPresent({
        "com.thelads.core.modules.LambDynamicLightsModule",
        "com.thelads.core.features.configurable.lambdynamiclights.LambDynamicLightsModule",
        "com.thelads.core.features.configurable.LambDynamicLightsModule"
    })
    public void testLambDynamicLights() throws Exception {
        runConfigurableModuleTest("LambDynamicLights",
            "com.thelads.core.modules.LambDynamicLightsModule",
            "com.thelads.core.features.configurable.lambdynamiclights.LambDynamicLightsModule",
            "com.thelads.core.features.configurable.LambDynamicLightsModule"
        );
    }

    @Test
    @EnabledIfClassPresent({
        "com.thelads.core.modules.PassiveShieldModule",
        "com.thelads.core.features.configurable.passiveshield.PassiveShieldModule",
        "com.thelads.core.features.configurable.PassiveShieldModule"
    })
    public void testPassiveShield() throws Exception {
        runConfigurableModuleTest("PassiveShield",
            "com.thelads.core.modules.PassiveShieldModule",
            "com.thelads.core.features.configurable.passiveshield.PassiveShieldModule",
            "com.thelads.core.features.configurable.PassiveShieldModule"
        );
    }

    @Test
    @EnabledIfClassPresent({
        "com.thelads.core.modules.ServerPingerFixerModule",
        "com.thelads.core.features.configurable.serverpingerfixer.ServerPingerFixerModule",
        "com.thelads.core.features.configurable.ServerPingerFixerModule"
    })
    public void testServerPingerFixer() throws Exception {
        runConfigurableModuleTest("ServerPingerFixer",
            "com.thelads.core.modules.ServerPingerFixerModule",
            "com.thelads.core.features.configurable.serverpingerfixer.ServerPingerFixerModule",
            "com.thelads.core.features.configurable.ServerPingerFixerModule"
        );
    }

    @Test
    @EnabledIfClassPresent({
        "com.thelads.core.modules.ThreadsModule",
        "com.thelads.core.features.configurable.threads.ThreadsModule",
        "com.thelads.core.features.configurable.ThreadsModule"
    })
    public void testThreads() throws Exception {
        runConfigurableModuleTest("Threads",
            "com.thelads.core.modules.ThreadsModule",
            "com.thelads.core.features.configurable.threads.ThreadsModule",
            "com.thelads.core.features.configurable.ThreadsModule"
        );
    }

    @Test
    @EnabledIfClassPresent({
        "com.thelads.core.modules.BetterF3Module",
        "com.thelads.core.features.configurable.betterf3.BetterF3Module",
        "com.thelads.core.features.configurable.BetterF3Module"
    })
    public void testBetterF3() throws Exception {
        Module module = getModule("BetterF3");
        assertNotNull(module);
        
        boolean initialEnabled = module.isEnabled();
        module.setEnabled(true);
        
        try {
            java.lang.reflect.Field showXYZField = module.getClass().getField("showXYZ");
            Object showXYZOption = showXYZField.get(module);
            java.lang.reflect.Method setBool = showXYZOption.getClass().getMethod("set", boolean.class);
            
            java.lang.reflect.Field showTargetBlockField = module.getClass().getField("showTargetBlock");
            Object showTargetBlockOption = showTargetBlockField.get(module);
            java.lang.reflect.Method setTargetBlockBool = showTargetBlockOption.getClass().getMethod("set", boolean.class);

            java.lang.reflect.Field showJavaField = module.getClass().getField("showJava");
            Object showJavaOption = showJavaField.get(module);
            java.lang.reflect.Method setJavaBool = showJavaOption.getClass().getMethod("set", boolean.class);

            java.lang.reflect.Field showMemoryField = module.getClass().getField("showMemory");
            Object showMemoryOption = showMemoryField.get(module);
            java.lang.reflect.Method setMemoryBool = showMemoryOption.getClass().getMethod("set", boolean.class);

            // Test filterLeftText with showXYZ = true
            setBool.invoke(showXYZOption, true);
            java.util.List<String> leftLines = new java.util.ArrayList<>(java.util.Arrays.asList("XYZ: 10 20 30", "Block: stone", "Chunk: 1 2", "Other: test"));
            java.lang.reflect.Method filterLeftTextMethod = module.getClass().getMethod("filterLeftText", java.util.List.class);
            
            @SuppressWarnings("unchecked")
            java.util.List<String> filteredLeft = (java.util.List<String>) filterLeftTextMethod.invoke(module, leftLines);
            assertTrue(filteredLeft.contains("XYZ: 10 20 30"), "Should contain XYZ when showXYZ is true");
            assertTrue(filteredLeft.contains("Block: stone"), "Should contain Block when showXYZ is true");
            assertTrue(filteredLeft.contains("Chunk: 1 2"), "Should contain Chunk when showXYZ is true");
            assertTrue(filteredLeft.contains("Other: test"), "Should contain Other line");

            // Test filterLeftText with showXYZ = false
            setBool.invoke(showXYZOption, false);
            @SuppressWarnings("unchecked")
            java.util.List<String> filteredLeftFalse = (java.util.List<String>) filterLeftTextMethod.invoke(module, leftLines);
            assertFalse(filteredLeftFalse.contains("XYZ: 10 20 30"), "Should NOT contain XYZ when showXYZ is false");
            assertFalse(filteredLeftFalse.contains("Block: stone"), "Should NOT contain Block when showXYZ is false");
            assertFalse(filteredLeftFalse.contains("Chunk: 1 2"), "Should NOT contain Chunk when showXYZ is false");
            assertTrue(filteredLeftFalse.contains("Other: test"), "Should still contain Other line");

            // Test filterLeftText with showTargetBlock = false
            setTargetBlockBool.invoke(showTargetBlockOption, false);
            java.util.List<String> leftTargetLines = new java.util.ArrayList<>(java.util.Arrays.asList("Targeted Block: copper", "Other: test"));
            @SuppressWarnings("unchecked")
            java.util.List<String> filteredLeftTarget = (java.util.List<String>) filterLeftTextMethod.invoke(module, leftTargetLines);
            assertFalse(filteredLeftTarget.contains("Targeted Block: copper"), "Should NOT contain Targeted Block when showTargetBlock is false");
            assertTrue(filteredLeftTarget.contains("Other: test"), "Should contain Other");

            // Test filterRightText with showJava = true
            setJavaBool.invoke(showJavaOption, true);
            java.util.List<String> rightLines = new java.util.ArrayList<>(java.util.Arrays.asList("Java: 17", "Mem: 50", "Allocated: 100", "Other: test"));
            java.lang.reflect.Method filterRightTextMethod = module.getClass().getMethod("filterRightText", java.util.List.class);
            
            @SuppressWarnings("unchecked")
            java.util.List<String> filteredRight = (java.util.List<String>) filterRightTextMethod.invoke(module, rightLines);
            assertTrue(filteredRight.contains("Java: 17"), "Should contain Java when showJava is true");
            
            // Test filterRightText with showJava = false
            setJavaBool.invoke(showJavaOption, false);
            @SuppressWarnings("unchecked")
            java.util.List<String> filteredRightFalse = (java.util.List<String>) filterRightTextMethod.invoke(module, rightLines);
            assertFalse(filteredRightFalse.contains("Java: 17"), "Should NOT contain Java when showJava is false");

            // Test filterRightText with showMemory = false
            setMemoryBool.invoke(showMemoryOption, false);
            @SuppressWarnings("unchecked")
            java.util.List<String> filteredRightMemory = (java.util.List<String>) filterRightTextMethod.invoke(module, rightLines);
            assertFalse(filteredRightMemory.contains("Mem: 50"), "Should NOT contain Mem when showMemory is false");
            assertFalse(filteredRightMemory.contains("Allocated: 100"), "Should NOT contain Allocated when showMemory is false");
        } finally {
            module.setEnabled(initialEnabled);
        }
    }

    @Test
    @EnabledIfClassPresent({
        "com.thelads.core.modules.DynamicFPSModule",
        "com.thelads.core.features.configurable.dynamicfps.DynamicFPSModule",
        "com.thelads.core.features.configurable.DynamicFPSModule"
    })
    public void testDynamicFPS() throws Exception {
        Module module = getModule("DynamicFPS");
        assertNotNull(module);
        
        boolean initialEnabled = module.isEnabled();
        module.setEnabled(true);
        
        try {
            java.lang.reflect.Method setOriginalLimit = module.getClass().getMethod("setOriginalFramerateLimit", int.class);
            java.lang.reflect.Method onWindowFocus = module.getClass().getMethod("onWindowFocusChanged", boolean.class);
            java.lang.reflect.Method getCurrentLimit = module.getClass().getMethod("getCurrentFramerateLimit");
            
            setOriginalLimit.invoke(module, 60);
            
            // Focus is true
            onWindowFocus.invoke(module, true);
            int limit = (int) getCurrentLimit.invoke(module);
            assertEquals(60, limit, "Framerate limit should be 60 when focused");
            
            // Focus is false
            onWindowFocus.invoke(module, false);
            int limitUnfocused = (int) getCurrentLimit.invoke(module);
            assertTrue(limitUnfocused < 60, "Framerate limit should be reduced when unfocused");
            
            // Disabled module
            module.setEnabled(false);
            int limitDisabled = (int) getCurrentLimit.invoke(module);
            assertEquals(60, limitDisabled, "Framerate limit should return original when module is disabled");
            
        } finally {
            module.setEnabled(initialEnabled);
        }
    }

    @Test
    @EnabledIfClassPresent({
        "com.thelads.core.modules.PingViewModule",
        "com.thelads.core.features.configurable.pingview.PingViewModule",
        "com.thelads.core.features.configurable.PingViewModule"
    })
    public void testPingView() throws Exception {
        Module module = getModule("PingView");
        assertNotNull(module);
        
        java.lang.reflect.Method getPingText = module.getClass().getMethod("getPingText", int.class);
        java.lang.reflect.Method getPingColor = module.getClass().getMethod("getPingColor", int.class);
        
        assertEquals("50ms", getPingText.invoke(module, 50));
        assertEquals("???", getPingText.invoke(module, -5));
        
        assertEquals(0xFF55FF55, getPingColor.invoke(module, 50));
        assertEquals(0xFFFFFF55, getPingColor.invoke(module, 150));
        assertEquals(0xFFFF5555, getPingColor.invoke(module, 250));
        assertEquals(0xFFFF5555, getPingColor.invoke(module, -10));
    }

    @Test
    @EnabledIfClassPresent({
        "com.thelads.core.modules.EnhancedTooltipsModule",
        "com.thelads.core.features.configurable.enhancedtooltips.EnhancedTooltipsModule",
        "com.thelads.core.features.configurable.EnhancedTooltipsModule"
    })
    public void testEnhancedTooltips() throws Exception {
        runConfigurableModuleTest("EnhancedTooltips",
            "com.thelads.core.modules.EnhancedTooltipsModule",
            "com.thelads.core.features.configurable.enhancedtooltips.EnhancedTooltipsModule",
            "com.thelads.core.features.configurable.EnhancedTooltipsModule"
        );
    }

    @Test
    @EnabledIfClassPresent({
        "com.thelads.core.modules.BetterStatsModule",
        "com.thelads.core.features.configurable.betterstats.BetterStatsModule",
        "com.thelads.core.features.configurable.BetterStatsModule"
    })
    public void testBetterStats() throws Exception {
        runConfigurableModuleTest("BetterStats",
            "com.thelads.core.modules.BetterStatsModule",
            "com.thelads.core.features.configurable.betterstats.BetterStatsModule",
            "com.thelads.core.features.configurable.BetterStatsModule"
        );
    }

    @Test
    @EnabledIfClassPresent({
        "com.thelads.core.modules.AdvancementsReloadedModule",
        "com.thelads.core.features.configurable.advancementsreloaded.AdvancementsReloadedModule",
        "com.thelads.core.features.configurable.AdvancementsReloadedModule"
    })
    public void testAdvancementsReloaded() throws Exception {
        runConfigurableModuleTest("AdvancementsReloaded",
            "com.thelads.core.modules.AdvancementsReloadedModule",
            "com.thelads.core.features.configurable.advancementsreloaded.AdvancementsReloadedModule",
            "com.thelads.core.features.configurable.AdvancementsReloadedModule"
        );
    }
}

