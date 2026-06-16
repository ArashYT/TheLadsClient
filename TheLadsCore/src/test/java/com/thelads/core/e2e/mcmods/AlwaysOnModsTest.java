package com.thelads.core.e2e.mcmods;

import com.thelads.core.e2e.mcmods.condition.EnabledIfClassPresent;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class AlwaysOnModsTest extends BaseMcModsTest {

    @Test
    @EnabledIfClassPresent("com.thelads.core.features.alwayson.immediatelyfast.ImmediatelyFast")
    public void testImmediatelyFast() throws Exception {
        Class<?> clazz = Class.forName("com.thelads.core.features.alwayson.immediatelyfast.ImmediatelyFast");
        clazz.getMethod("earlyInit").invoke(null);
        java.lang.reflect.Field configField = clazz.getField("config");
        Object config = configField.get(null);
        assertNotNull(config, "ImmediatelyFast.config should be initialized");
        java.lang.reflect.Field enhancedBatchingField = config.getClass().getField("enhanced_batching");
        boolean enhancedBatching = enhancedBatchingField.getBoolean(config);
        assertTrue(enhancedBatching, "enhanced_batching should default to true");
    }

    @Test
    @EnabledIfClassPresent("com.thelads.core.features.alwayson.entityculling.EntityCullingMod")
    public void testEntityCulling() {
        assertTrue(isClassPresent("com.thelads.core.features.alwayson.entityculling.EntityCullingMod"), "EntityCullingMod class should be present");
    }

    @Test
    @EnabledIfClassPresent("com.thelads.core.features.alwayson.vmp.VMPMod")
    public void testVmp() throws Exception {
        Class<?> clazz = Class.forName("com.thelads.core.features.alwayson.vmp.VMPMod");
        assertNotNull(clazz, "VMPMod class should be present");
        Class<?> configClazz = Class.forName("com.thelads.core.features.alwayson.vmp.common.config.Config");
        assertNotNull(configClazz, "Config class should exist");
        java.lang.reflect.Field field = configClazz.getField("USE_OPTIMIZED_ENTITY_TRACKING");
        boolean useOptimized = field.getBoolean(null);
        assertTrue(useOptimized, "USE_OPTIMIZED_ENTITY_TRACKING should be true");
    }

    @Test
    @EnabledIfClassPresent("com.thelads.core.features.alwayson.betterrenderdistance.ClientHooks")
    public void testBetterRenderDistance() {
        assertTrue(isClassPresent("com.thelads.core.features.alwayson.betterrenderdistance.ClientHooks"), "ClientHooks class should be present");
    }

    @Test
    @EnabledIfClassPresent("com.thelads.core.features.alwayson.letmedespawn.LadsEquipmentTracker")
    public void testLetMeRespawn() {
        assertTrue(isClassPresent("com.thelads.core.features.alwayson.letmedespawn.LadsEquipmentTracker"), "LadsEquipmentTracker class should be present");
    }

    @Test
    @EnabledIfClassPresent("com.thelads.core.features.alwayson.hyperlaunch.HyperLaunch")
    public void testHyperLaunch() throws Exception {
        Class<?> clazz = Class.forName("com.thelads.core.features.alwayson.hyperlaunch.HyperLaunch");
        assertNotNull(clazz, "HyperLaunch class should be present");
        java.lang.reflect.Method bootstrapExecutor = clazz.getMethod("bootstrapExecutor");
        Object executor = bootstrapExecutor.invoke(null);
        assertNotNull(executor, "bootstrapExecutor() should return a non-null executor");
    }

    @Test
    @EnabledIfClassPresent("com.thelads.core.features.alwayson.smoothscrolling.ScrollMath")
    public void testSmoothScrolling() {
        assertTrue(isClassPresent("com.thelads.core.features.alwayson.smoothscrolling.ScrollMath"), "ScrollMath class should be present");
    }

    @Test
    @EnabledIfClassPresent("com.thelads.core.features.alwayson.raisesoundlimit.RSLSMod")
    public void testRaiseSoundLimit() throws Exception {
        Class<?> clazz = Class.forName("com.thelads.core.features.alwayson.raisesoundlimit.RSLSMod");
        assertNotNull(clazz, "RSLSMod class should be present");
        Class<?> configClazz = Class.forName("com.thelads.core.features.alwayson.raisesoundlimit.RSLSConfig");
        assertNotNull(configClazz, "RSLSConfig class should exist");
        java.lang.reflect.Method initMethod = configClazz.getMethod("init");
        initMethod.invoke(null);
        java.lang.reflect.Field maxStreamingSourcesField = configClazz.getField("maxStreamingSources");
        int maxStreamingSources = maxStreamingSourcesField.getInt(null);
        assertEquals(8, maxStreamingSources, "maxStreamingSources should default to 8");
    }

    @Test
    @EnabledIfClassPresent("com.thelads.core.features.alwayson.threedskinlayers.ThreeDSkinLayers")
    public void testThreeDSkinLayers() {
        assertTrue(isClassPresent("com.thelads.core.features.alwayson.threedskinlayers.ThreeDSkinLayers"), "ThreeDSkinLayers class should be present");
    }

    @Test
    @EnabledIfClassPresent("com.thelads.core.features.alwayson.advancementsreloaded.AdvancementsReloadedFabric")
    public void testAdvancementsReloaded() {
        assertTrue(isClassPresent("com.thelads.core.features.alwayson.advancementsreloaded.AdvancementsReloadedFabric"), "AdvancementsReloaded class should be present");
    }

    @Test
    @EnabledIfClassPresent("com.thelads.core.features.alwayson.betterstatisticscreen.BetterStatisticScreen")
    public void testBetterStatisticScreen() {
        assertTrue(isClassPresent("com.thelads.core.features.alwayson.betterstatisticscreen.BetterStatisticScreen"), "BetterStatisticScreen class should be present");
    }

    @Test
    @EnabledIfClassPresent("com.thelads.core.features.alwayson.clientsort.ClientSort")
    public void testClientSort() {
        assertTrue(isClassPresent("com.thelads.core.features.alwayson.clientsort.ClientSort"), "ClientSort class should be present");
    }

    @Test
    @EnabledIfClassPresent("com.thelads.core.features.alwayson.quickpack.QuickPack")
    public void testQuickPack() {
        assertTrue(isClassPresent("com.thelads.core.features.alwayson.quickpack.QuickPack"), "QuickPack class should be present");
    }

    @Test
    @EnabledIfClassPresent("com.thelads.core.features.alwayson.resourcepackoptions.ResourcePackOptions")
    public void testResourcePackOptions() {
        assertTrue(isClassPresent("com.thelads.core.features.alwayson.resourcepackoptions.ResourcePackOptions"), "ResourcePackOptions class should be present");
    }

    @Test
    @EnabledIfClassPresent("com.thelads.core.features.alwayson.raised.Raised")
    public void testRaised() {
        assertTrue(isClassPresent("com.thelads.core.features.alwayson.raised.Raised"), "Raised class should be present");
    }

    @Test
    @EnabledIfClassPresent("com.thelads.core.features.alwayson.ixeris.Ixeris")
    public void testIxeris() {
        assertTrue(isClassPresent("com.thelads.core.features.alwayson.ixeris.Ixeris"), "Ixeris class should be present");
    }
}
