package com.thelads.core.client;

import static org.junit.jupiter.api.Assertions.*;

import com.thelads.core.config.BoolOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.Option;
import com.thelads.core.config.SliderOption;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class PvpVisualsTest {
    private static Module module(String name) { return ModuleManager.getInstance().getModule(name); }

    @AfterEach void restore() {
        for (String name : new String[] {PvpVisuals.RENDER_TWEAKS, PvpVisuals.OVERLAYS, PvpVisuals.CLEAR_WATER, PvpVisuals.TOTEM,
            PvpVisuals.DARK_MODE, PvpVisuals.NAMETAG_HEALTH, "Nametags"})
            for (Option option : module(name).getOptions()) option.reset();
        module(PvpVisuals.CLEAR_WATER).setEnabled(false);
        module(PvpVisuals.DARK_MODE).setEnabled(false);
        module(PvpVisuals.NAMETAG_HEALTH).setEnabled(false);
        module(PvpVisuals.OVERLAYS).setEnabled(true);
        PvpVisuals.containerLabels = false;
        PvpVisuals.refresh();
    }

    @Test void defaultsLookLikeVanilla() {
        PvpVisuals.refresh();
        assertFalse(PvpVisuals.hideShadows || PvpVisuals.hideLightning || PvpVisuals.hideFishingLine || PvpVisuals.hideElderGuardian
            || PvpVisuals.bobbingDisconnect || PvpVisuals.hideFireWithResistance || PvpVisuals.hideTotem);
        assertEquals(1, PvpVisuals.fireOpacity);
        assertEquals(1, PvpVisuals.pumpkinOpacity);
        assertEquals(1, PvpVisuals.portalOpacity);
        assertEquals(1, PvpVisuals.waterFog, "Clear Water starts off");
        assertEquals(1, PvpVisuals.totemScale);
        assertEquals(0, PvpVisuals.darkFill, "Dark Mode starts off");
        assertEquals(1, PvpVisuals.nametagScale);
        assertEquals(64, PvpVisuals.nametagBackground, "25% is vanilla's name tag background");
        assertFalse(PvpVisuals.nametagHealth || PvpVisuals.nametagPing || PvpVisuals.nametagsInF1 || PvpVisuals.nametagFade);
    }

    @Test void optionsReachTheFieldsAndAModuleOffKeepsVanilla() {
        ((BoolOption) module(PvpVisuals.RENDER_TWEAKS).getOption(PvpVisuals.SHADOWS)).set(false);
        ((BoolOption) module(PvpVisuals.RENDER_TWEAKS).getOption(PvpVisuals.BOBBING_DISCONNECT)).set(true);
        ((SliderOption) module(PvpVisuals.OVERLAYS).getOption(PvpVisuals.FIRE_OPACITY)).setValue(40);
        module(PvpVisuals.CLEAR_WATER).setEnabled(true);
        module(PvpVisuals.DARK_MODE).setEnabled(true);
        PvpVisuals.refresh();
        assertTrue(PvpVisuals.hideShadows);
        assertTrue(PvpVisuals.bobbingDisconnect);
        assertEquals(0.4f, PvpVisuals.fireOpacity, 1e-6);
        assertEquals(0.3f, PvpVisuals.waterFog, 1e-6, "Clear Water's default Water Fog is 30%");
        assertEquals(0x99000000, PvpVisuals.darkFill, "60% black");

        module(PvpVisuals.OVERLAYS).setEnabled(false);
        PvpVisuals.refresh();
        assertEquals(1, PvpVisuals.fireOpacity, "Overlays off: vanilla fire");
    }

    @Test void alphaScalesOnlyTheAlphaAndClamps() {
        assertEquals(0x73FFFFFF, PvpVisuals.alpha(0xE5FFFFFF, 0.5f), "the fire overlay's 0.9 alpha at half opacity");
        assertEquals(0xFF123456, PvpVisuals.alpha(0xFF123456, 3));
        assertEquals(0x00123456, PvpVisuals.alpha(0xFF123456, -1));
    }

    @Test void darkModeLightensOnlyTheDarkGrayLabelsWhileTheyDraw() {
        module(PvpVisuals.DARK_MODE).setEnabled(true);
        PvpVisuals.refresh();
        assertEquals(4210752, PvpVisuals.label(4210752), "outside a container's labels nothing changes");
        PvpVisuals.containerLabels = true;
        assertEquals(0xE0E0E0, PvpVisuals.label(4210752), "1.8.9 and 1.20.1 pass the gray with no alpha");
        assertEquals(0xFFE0E0E0, PvpVisuals.label(0xFF404040), "26.x passes it opaque");
        assertEquals(0xFFFFFF, PvpVisuals.label(0xFFFFFF), "other colors stay");
        module(PvpVisuals.DARK_MODE).setEnabled(false);
        PvpVisuals.refresh();
        assertEquals(4210752, PvpVisuals.label(4210752), "Dark Mode off");
    }

    @Test void distanceFadeKeepsNearTagsAndNeverHidesFarOnes() {
        PvpVisuals.refresh();
        assertEquals(1, PvpVisuals.fade(60 * 60), "off: no fade");
        ((BoolOption) module("Nametags").getOption("Distance Fade")).set(true);
        PvpVisuals.refresh();
        assertEquals(1, PvpVisuals.fade(10 * 10));
        assertEquals(0.625f, PvpVisuals.fade(40 * 40), 1e-6);
        assertEquals(0.25f, PvpVisuals.fade(200 * 200), 1e-6);
    }

    @Test void numbersAreBuiltOnceAndClamped() {
        assertSame(PvpVisuals.number(42), PvpVisuals.number(42));
        assertEquals("42", PvpVisuals.number(42));
        assertEquals("999", PvpVisuals.number(5000));
        assertEquals("0", PvpVisuals.number(-3));
        assertEquals(0xFF55FF55, PvpVisuals.pingColor(40));
        assertEquals(0xFFFF5555, PvpVisuals.pingColor(900));
    }

    @Test void onlyTheModulesThatShowMoreThanVanillaAreGated() {
        assertTrue(HypixelSafetyManager.isRiskyModule(PvpVisuals.CLEAR_WATER));
        assertTrue(HypixelSafetyManager.isRiskyModule(PvpVisuals.NAMETAG_HEALTH));
        for (String visual : new String[] {PvpVisuals.RENDER_TWEAKS, PvpVisuals.OVERLAYS, PvpVisuals.TOTEM, PvpVisuals.DARK_MODE})
            assertFalse(HypixelSafetyManager.isRiskyModule(visual), visual);
        assertFalse(module(PvpVisuals.CLEAR_WATER).isEnabledSetting(), "gated modules start off");
        assertFalse(module(PvpVisuals.NAMETAG_HEALTH).isEnabledSetting());
    }
}
