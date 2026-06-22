package com.thelads.core.e2e.mcmods;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class RecentlyAddedModsTest extends BaseMcModsTest {

    @Test
    public void testKerria() {
        assertTrue(isClassPresent("com.thelads.core.mixin.auto.kerria1301211fabric.AnimatedTextureMixin"), "Kerria AnimatedTextureMixin should be present");
    }

    @Test
    public void testDecentScreenshot() {
        assertTrue(isClassPresent("com.thelads.core.mixin.auto.decentscreenshot10262.ScreenshotGalleryScreen"), "DecentScreenshot ScreenshotGalleryScreen should be present");
    }

    @Test
    public void testDisableNarrator() {
        assertTrue(isClassPresent("com.thelads.core.mixin.auto.disablenarrator100.NoWindowsNarratorMixin"), "DisableNarrator NoWindowsNarratorMixin should be present");
    }

    @Test
    public void testFarBlockEntityRendering() {
        assertTrue(isClassPresent("com.thelads.core.mixin.auto.farblockentityrendering21.BlockEntityRendererMixin"), "FarBlockEntityRendering BlockEntityRendererMixin should be present");
    }

    @Test
    public void testModernAdvancementsScreen() {
        assertTrue(isClassPresent("com.thelads.core.mixin.auto.modernadvancementsscreen1901262.AdvancementScreenMixin"), "ModernAdvancementsScreen AdvancementScreenMixin should be present");
    }

    @Test
    public void testResourcify() {
        assertTrue(isClassPresent("com.thelads.core.mixin.auto.resourcify262fabric184.MixinGuiGraphics"), "Resourcify MixinGuiGraphics should be present");
    }

    @Test
    public void testShulkerBoxUtils() {
        assertTrue(isClassPresent("com.thelads.core.mixin.auto.shulkerboxutils130.ClientLevelMixin"), "ShulkerBoxUtils ClientLevelMixin should be present");
    }

    @Test
    public void testSignalLoss() {
        assertTrue(isClassPresent("com.thelads.core.mixin.auto.signalloss121262.ClientConnectionMixin"), "SignalLoss ClientConnectionMixin should be present");
    }

    @Test
    public void testAnimatium() {
        assertTrue(isClassPresent("com.thelads.core.mixin.auto.animatium322612fabric.BlockEntityRenderDispatcherMixin"), "Animatium BlockEntityRenderDispatcherMixin should be present");
    }

    @Test
    public void testObe() {
        assertTrue(isClassPresent("com.thelads.core.mixin.auto.obe2621010.BlockEntityRendererMixin"), "OBE BlockEntityRendererMixin should be present");
    }

    @Test
    public void testRetromod() {
        assertTrue(isClassPresent("com.thelads.core.mixin.auto.safemod110rc1262.MixinMinecraft"), "Retromod (safemod) MixinMinecraft should be present");
    }
}
