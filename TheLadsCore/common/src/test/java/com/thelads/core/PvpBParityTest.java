package com.thelads.core;

import com.thelads.core.client.*;
import com.thelads.core.client.bridge.DefaultGameBridge;
import com.thelads.core.client.bridge.LadsGameBridge;
import com.thelads.core.client.sound.OofMod;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.modules.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class PvpBParityTest {

    private static class TestGameBridge extends DefaultGameBridge {
        private String server = "Singleplayer";
        private boolean ingame = true;

        public void setServer(String s) { this.server = s; }
        @Override public String getServerAddress() { return server; }
        @Override public boolean isIngame() { return ingame; }
    }

    private TestGameBridge bridge;

    @BeforeEach
    void setUp() {
        bridge = new TestGameBridge();
        LadsGameBridge.set(bridge);
        HypixelSafetyManager.qaOverride = false;
        HypixelSafetyManager.qaAddress = null;
        resetModules();
    }

    @AfterEach
    void tearDown() {
        resetModules();
        HypixelSafetyManager.qaOverride = false;
        HypixelSafetyManager.qaAddress = null;
        LadsGameBridge.set(new DefaultGameBridge());
    }

    private void resetModules() {
        try {
            var mm = ModuleManager.getInstance();
            if (mm != null) {
                for (String mod : List.of(DarkMode.MODULE_NAME, BedWarsBeds.MODULE_NAME, RenderTweaks.MODULE_NAME,
                        OverlayTweaks.MODULE_NAME, TotemAnimation.MODULE_NAME, OofMod.MODULE_NAME,
                        LightLevelOverlay.MODULE_NAME, LootBeams.MODULE_NAME, MobOverlay.MODULE_NAME,
                        MobSize.MODULE_NAME, ShulkerBulletHighlight.MODULE_NAME, ItemDespawnFlash.MODULE_NAME,
                        UhcOverlay.MODULE_NAME)) {
                    var m = mm.getModule(mod);
                    if (m != null) m.setEnabled(false);
                }
            }
            DarkMode.refresh();
            RenderTweaks.refresh();
            OverlayTweaks.refresh();
            TotemAnimation.refresh();
            ItemDespawnFlash.refresh();
            UhcOverlay.refresh();
        } catch (Throwable ignored) {}
    }

    @Test
    void testAllNewModulesRegisteredInModuleManager() {
        var mm = ModuleManager.getInstance();
        assertNotNull(mm.getModule(DarkMode.MODULE_NAME));
        assertNotNull(mm.getModule(BedWarsBeds.MODULE_NAME));
        assertNotNull(mm.getModule(RenderTweaks.MODULE_NAME));
        assertNotNull(mm.getModule(OverlayTweaks.MODULE_NAME));
        assertNotNull(mm.getModule(TotemAnimation.MODULE_NAME));
        assertNotNull(mm.getModule(OofMod.MODULE_NAME));
        assertNotNull(mm.getModule(LightLevelOverlay.MODULE_NAME));
        assertNotNull(mm.getModule(LootBeams.MODULE_NAME));
        assertNotNull(mm.getModule(MobOverlay.MODULE_NAME));
        assertNotNull(mm.getModule(MobSize.MODULE_NAME));
        assertNotNull(mm.getModule(ShulkerBulletHighlight.MODULE_NAME));
        assertNotNull(mm.getModule(ItemDespawnFlash.MODULE_NAME));
        assertNotNull(mm.getModule(UhcOverlay.MODULE_NAME));

        // Verify default off
        assertFalse(mm.getModule(DarkMode.MODULE_NAME).isEnabled());
        assertFalse(mm.getModule(LightLevelOverlay.MODULE_NAME).isEnabled());
        assertFalse(mm.getModule(LootBeams.MODULE_NAME).isEnabled());
        assertFalse(mm.getModule(MobOverlay.MODULE_NAME).isEnabled());
        assertFalse(mm.getModule(MobSize.MODULE_NAME).isEnabled());
        assertFalse(mm.getModule(ShulkerBulletHighlight.MODULE_NAME).isEnabled());
    }

    @Test
    void testOldAnimationsExtras() {
        var mod = new OldAnimationsModule();
        assertNotNull(mod.option(OldAnimationsModule.Feature.PICKUP_ANIMATION));
        assertNotNull(mod.option(OldAnimationsModule.Feature.ROD_AS_STICK));
        assertNotNull(mod.option(OldAnimationsModule.Feature.SWING_SPEED_HASTE));
        assertNotNull(mod.option(OldAnimationsModule.Feature.REEQUIP_CONTROL));
        assertNotNull(mod.option(OldAnimationsModule.Feature.ADVENTURE_PUNCH));
        assertNotNull(mod.option(OldAnimationsModule.Feature.CHEATBREAKER_BLOCKHIT));
        assertNotNull(mod.option(OldAnimationsModule.Feature.HELD_ITEM_LIGHTING));

        // Active checks on V1_8_9 and MODERN
        mod.setEnabled(true);
        assertTrue(mod.active(OldAnimationsModule.Feature.PICKUP_ANIMATION, OldAnimationsModule.Platform.V1_8_9));
        assertTrue(mod.active(OldAnimationsModule.Feature.PICKUP_ANIMATION, OldAnimationsModule.Platform.MODERN));

        // Helpers
        assertEquals(3, OldAnimations.swingDuration(6, 2, 0)); // haste 2 speeds up swing
        assertEquals(10, OldAnimations.swingDuration(6, 0, 1)); // fatigue 1 slows down swing
        assertFalse(OldAnimations.shouldReequip(false));
        assertTrue(OldAnimations.shouldReequip(true));
    }

    @Test
    void testCrosshairElytraIndicator() {
        var crosshair = (CrosshairModule) ModuleManager.getInstance().getModule("Crosshair Tweaks");
        assertNotNull(crosshair);
        assertNotNull(crosshair.elytraIndicator);
        assertNotNull(crosshair.elytraIndicatorColor);
        assertFalse(crosshair.elytraIndicator.get());
    }

    @Test
    void testNametagOptions() {
        var nametags = (ToggleNametagsModule) ModuleManager.getInstance().getModule("Nametags");
        assertNotNull(nametags);
        assertNotNull(nametags.scale);
        assertNotNull(nametags.backgroundOpacity);
        assertNotNull(nametags.distanceFade);
        assertNotNull(nametags.showHealth);
        assertNotNull(nametags.showPing);
        assertNotNull(nametags.showInF1);
        assertEquals(1.0, nametags.scale.getValue(), 0.01);
        assertEquals(25.0, nametags.backgroundOpacity.getValue(), 0.01);
    }

    @Test
    void testParticleCoreRecolorAndScale() {
        var pc = (ParticleCoreModule) ModuleManager.getInstance().getModule(ParticleCore.MODULE);
        assertNotNull(pc);
        assertNotNull(pc.particleScale);
        assertNotNull(pc.critRecolor);
        assertNotNull(pc.critColor);
        assertNotNull(pc.sharpnessRecolor);
        assertNotNull(pc.sharpnessColor);

        ParticleCore.refresh();
        assertEquals(1.0f, ParticleCore.particleScale(), 0.01f);
        assertFalse(ParticleCore.critRecolor());
        assertFalse(ParticleCore.sharpnessRecolor());
    }

    @Test
    void testDarkMode() {
        var dm = (DarkModeModule) ModuleManager.getInstance().getModule(DarkMode.MODULE_NAME);
        dm.setEnabled(true);
        DarkMode.refresh();
        assertTrue(DarkMode.isEnabled());
        assertTrue(DarkMode.shouldDarkenContainers());
        assertEquals(0.6f, DarkMode.getDimFraction(), 0.01f);
        assertTrue(DarkMode.getContainerTint() != 0);
        dm.setEnabled(false);
        DarkMode.refresh();
        assertFalse(DarkMode.isEnabled());
    }

    @Test
    void testRenderTweaksAndBobbing() {
        var rt = (RenderTweaksModule) ModuleManager.getInstance().getModule(RenderTweaks.MODULE_NAME);
        rt.setEnabled(true);
        rt.hideLightning.set(true);
        rt.hideFishingLine.set(true);
        rt.bobbingDisconnect.set(true);
        RenderTweaks.refresh();

        assertTrue(RenderTweaks.isHideLightning());
        assertTrue(RenderTweaks.isHideFishingLine());
        assertTrue(RenderTweaks.isElderGuardianCurseHidden());
        assertTrue(BobbingTweaks.isDisconnectEnabled());

        rt.setEnabled(false);
        RenderTweaks.refresh();
        assertFalse(RenderTweaks.isHideLightning());
        assertFalse(BobbingTweaks.isDisconnectEnabled());
    }

    @Test
    void testOverlayTweaks() {
        var ot = (OverlayTweaksModule) ModuleManager.getInstance().getModule(OverlayTweaks.MODULE_NAME);
        ot.setEnabled(true);
        ot.fireOpacity.setValue(50);
        ot.pumpkinOpacity.setValue(30);
        OverlayTweaks.refresh();

        assertEquals(0.5f, OverlayTweaks.getFireOpacity(false), 0.01f);
        assertEquals(0.0f, OverlayTweaks.getFireOpacity(true), 0.01f); // Fire res fade
        assertEquals(0.3f, OverlayTweaks.getPumpkinOpacity(), 0.01f);

        ot.setEnabled(false);
        OverlayTweaks.refresh();
        assertEquals(1.0f, OverlayTweaks.getFireOpacity(false), 0.01f);
    }

    @Test
    void testTotemAnimation() {
        var tm = (TotemAnimationModule) ModuleManager.getInstance().getModule(TotemAnimation.MODULE_NAME);
        tm.setEnabled(true);
        tm.hideAnimation.set(true);
        TotemAnimation.refresh();

        assertTrue(TotemAnimation.shouldHideAnimation());

        tm.setEnabled(false);
        TotemAnimation.refresh();
        assertFalse(TotemAnimation.shouldHideAnimation());
    }

    @Test
    void testOofModProceduralAudio() {
        byte[] oof = OofMod.getOofPcm();
        assertNotNull(oof);
        assertTrue(oof.length > 1000);
        byte[] bonk = OofMod.getBonkPcm();
        assertNotNull(bonk);
        assertTrue(bonk.length > 500);
        byte[] pop = OofMod.getPopPcm();
        assertNotNull(pop);
        assertTrue(pop.length > 200);
        byte[] bell = OofMod.getBellPcm();
        assertNotNull(bell);
        assertTrue(bell.length > 1000);
    }

    @Test
    void testGatedSafetyRules() {
        for (String mod : List.of("LightLevelOverlay", "LootBeams", "MobOverlay", "MobSize", "ShulkerBulletHighlight")) {
            bridge.setServer("Singleplayer");
            assertEquals(HypixelSafetyManager.Block.NONE, HypixelSafetyManager.blockOf(mod), "Trusted/singleplayer allows: " + mod);

            bridge.setServer("play.unknownserver.net");
            assertEquals(HypixelSafetyManager.Block.UNKNOWN, HypixelSafetyManager.blockOf(mod), "Unknown server blocks: " + mod);

            bridge.setServer("as.minemen.club");
            assertEquals(HypixelSafetyManager.Block.BANNED, HypixelSafetyManager.blockOf(mod), "Anticheat server blocks: " + mod);
        }
    }

    @Test
    void testLightLevelSpawnStatus() {
        assertEquals(2, LightLevelOverlay.getSpawnStatus(0, 0)); // Hostile mob spawns anytime
        assertEquals(1, LightLevelOverlay.getSpawnStatus(4, 15)); // Night spawn
        assertEquals(0, LightLevelOverlay.getSpawnStatus(10, 15)); // Safe
    }

    @Test
    void testLootBeamsRarityColors() {
        assertEquals(LootBeams.RARITY_COMMON, LootBeams.getColorForRarity(0));
        assertEquals(LootBeams.RARITY_UNCOMMON, LootBeams.getColorForRarity(1));
        assertEquals(LootBeams.RARITY_RARE, LootBeams.getColorForRarity(2));
        assertEquals(LootBeams.RARITY_EPIC, LootBeams.getColorForRarity(3));
    }

    @Test
    void testItemDespawnFlash() {
        var idf = (ItemDespawnFlashModule) ModuleManager.getInstance().getModule(ItemDespawnFlash.MODULE_NAME);
        idf.setEnabled(true);
        ItemDespawnFlash.refresh();

        assertFalse(ItemDespawnFlash.shouldFlash(1000)); // young item
        assertTrue(ItemDespawnFlash.shouldFlash(5800)); // approaching 6000 ticks
    }

    @Test
    void testUhcOverlayItemMatching() {
        var uhc = (UhcOverlayModule) ModuleManager.getInstance().getModule(UhcOverlay.MODULE_NAME);
        uhc.setEnabled(true);
        UhcOverlay.refresh();

        assertTrue(UhcOverlay.isHighlightedItem("minecraft:golden_apple"));
        assertTrue(UhcOverlay.isHighlightedItem("minecraft:player_head"));
        assertTrue(UhcOverlay.isHighlightedItem("minecraft:gold_ore"));
        assertTrue(UhcOverlay.isHighlightedItem("minecraft:diamond_ore"));
        assertFalse(UhcOverlay.isHighlightedItem("minecraft:dirt"));
        assertFalse(UhcOverlay.isHighlightedItem("minecraft:cobblestone"));
    }
}
