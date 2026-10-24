package com.thelads.core.client;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.thelads.core.config.BoolOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.Option;
import com.thelads.core.config.ParticleReductionOption;
import com.thelads.core.config.SliderOption;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class PvpVisualsTest {
    private static Module module(String name) { return ModuleManager.getInstance().getModule(name); }

    @AfterEach void restore() {
        for (String name : new String[] {PvpVisuals.RENDER_TWEAKS, PvpVisuals.OVERLAYS, PvpVisuals.TOTEM, PvpVisuals.DARK_MODE,
            PvpVisuals.NAMETAG_HEALTH, "Nametags"})
            for (Option option : module(name).getOptions()) option.reset();
        module(PvpVisuals.DARK_MODE).setEnabled(false);
        module(PvpVisuals.NAMETAG_HEALTH).setEnabled(false);
        module(PvpVisuals.OVERLAYS).setEnabled(true);
        PvpVisuals.containerLabels = false;
        PvpVisuals.tick();
    }

    @Test void defaultsLookLikeVanilla() {
        PvpVisuals.tick();
        assertFalse(PvpVisuals.hideShadows || PvpVisuals.hideLightning || PvpVisuals.hideFishingLine || PvpVisuals.hideElderGuardian
            || PvpVisuals.bobbingDisconnect || PvpVisuals.hideFireWithResistance || PvpVisuals.hideTotem);
        assertEquals(1, PvpVisuals.fireOpacity);
        assertEquals(1, PvpVisuals.pumpkinOpacity);
        assertEquals(1, PvpVisuals.portalOpacity);
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
        module(PvpVisuals.DARK_MODE).setEnabled(true);
        PvpVisuals.tick();
        assertTrue(PvpVisuals.hideShadows);
        assertTrue(PvpVisuals.bobbingDisconnect);
        assertEquals(0.4f, PvpVisuals.fireOpacity, 1e-6);
        assertEquals(0x99000000, PvpVisuals.darkFill, "60% black");

        module(PvpVisuals.OVERLAYS).setEnabled(false);
        PvpVisuals.tick();
        assertEquals(1, PvpVisuals.fireOpacity, "Overlays off: vanilla fire");
    }

    @Test void alphaScalesOnlyTheAlphaAndClamps() {
        assertEquals(0x73FFFFFF, PvpVisuals.alpha(0xE5FFFFFF, 0.5f), "the fire overlay's 0.9 alpha at half opacity");
        assertEquals(0xFF123456, PvpVisuals.alpha(0xFF123456, 3));
        assertEquals(0x00123456, PvpVisuals.alpha(0xFF123456, -1));
    }

    @Test void darkModeLightensOnlyTheDarkGrayLabelsWhileTheyDraw() {
        module(PvpVisuals.DARK_MODE).setEnabled(true);
        PvpVisuals.tick();
        assertEquals(4210752, PvpVisuals.label(4210752), "outside a container's labels nothing changes");
        PvpVisuals.containerLabels = true;
        assertEquals(0xE0E0E0, PvpVisuals.label(4210752), "1.8.9 and 1.20.1 pass the gray with no alpha");
        assertEquals(0xFFE0E0E0, PvpVisuals.label(0xFF404040), "26.x passes it opaque");
        assertEquals(0xFFFFFF, PvpVisuals.label(0xFFFFFF), "other colors stay");
        module(PvpVisuals.DARK_MODE).setEnabled(false);
        PvpVisuals.tick();
        assertEquals(4210752, PvpVisuals.label(4210752), "Dark Mode off");
    }

    @Test void distanceFadeKeepsNearTagsAndNeverHidesFarOnes() {
        PvpVisuals.tick();
        assertEquals(1, PvpVisuals.fade(60 * 60), "off: no fade");
        ((BoolOption) module("Nametags").getOption("Distance Fade")).set(true);
        PvpVisuals.tick();
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

    @Test void onlyTheModuleThatShowsMoreThanVanillaIsGated() {
        assertTrue(HypixelSafetyManager.isRiskyModule(PvpVisuals.NAMETAG_HEALTH));
        for (String visual : new String[] {PvpVisuals.RENDER_TWEAKS, PvpVisuals.OVERLAYS, PvpVisuals.TOTEM, PvpVisuals.DARK_MODE})
            assertFalse(HypixelSafetyManager.isRiskyModule(visual), visual);
        assertFalse(module(PvpVisuals.NAMETAG_HEALTH).isEnabledSetting(), "the gated module starts off");
    }

    // Rows 30 and 32: per-type particle looks and the light overlay's marker store.
    @Test void sizesKeepTheirOwnRangeAndVanillaValue() {
        var sizes = new ParticleReductionOption("Sizes", 25, 400, 100, "hint");
        sizes.set("crit", 1000);
        sizes.set("flame", 5);
        sizes.set("smoke", 100);
        assertEquals(400, sizes.value("crit"), "clamped to the top of the range");
        assertEquals(25, sizes.value("flame"), "clamped to the bottom");
        assertEquals(100, sizes.value("heart"), "never edited: vanilla");
        assertEquals(2, sizes.changed(), "the vanilla value is not kept");
        var loaded = new ParticleReductionOption("Sizes", 25, 400, 100, "hint");
        loaded.load(sizes.save());
        assertEquals(400, loaded.value("crit"));
        JsonObject vanilla = new JsonObject();
        vanilla.addProperty("smoke", 100);
        loaded.load(vanilla);
        assertEquals(0, loaded.changed(), "a saved vanilla value loads as unchanged");
    }

    @Test void fadeStartsAtZeroAsVanilla() {
        var fade = new ParticleReductionOption("Fade out", 0, 100, 0, "hint");
        fade.set("crit", 60);
        fade.set("smoke", 0);
        assertEquals(60, fade.value("crit"));
        assertEquals(1, fade.changed());
    }

    @Test void colorsAreOpaqueSoBlackIsNotVanilla() {
        var colors = ParticleReductionOption.colors("Colors", "hint");
        colors.set("crit", 0x00000000 | 0x1);  // a nearly black, fully transparent pick
        colors.set("flame", 0x80FF0000);
        assertEquals(0xFF000001, colors.value("crit"), "alpha forced opaque");
        assertEquals(0xFFFF0000, colors.value("flame"));
        assertEquals(0, colors.value("smoke"), "never edited: keeps its own colors");
        colors.set("flame", 0);
        assertEquals(0, colors.value("flame"), "0 resets the type");
        var loaded = ParticleReductionOption.colors("Colors", "hint");
        loaded.load(colors.save());
        assertEquals(0xFF000001, loaded.value("crit"), "a negative ARGB survives saving");
        assertEquals(1, loaded.changed());
    }

    @Test void fadeAlphaRunsFromFullToTheFadeShare() {
        assertEquals(1, ParticleCore.fadeAlpha(0, 10, 20), "no fade");
        assertEquals(1, ParticleCore.fadeAlpha(1, 0, 20), "fresh particle");
        assertEquals(0.5f, ParticleCore.fadeAlpha(1, 10, 20), 1e-6, "half way, full fade");
        assertEquals(0.75f, ParticleCore.fadeAlpha(0.5f, 10, 20), 1e-6, "half way, half fade");
        assertEquals(0, ParticleCore.fadeAlpha(1, 30, 20), 1e-6, "past its life it stays gone");
        assertEquals(1, ParticleCore.fadeAlpha(1, 5, 0), "no lifetime: nothing to fade over");
    }

    @Test void lightMarkersFollowBlockAndSkyLight() {
        assertEquals(LightOverlay.ALWAYS, LightOverlay.kind(0, 0, 0), "dark cave: spawns any time");
        assertEquals(LightOverlay.NIGHT_ONLY, LightOverlay.kind(0, 15, 0), "open field: only at night");
        assertEquals(LightOverlay.NONE, LightOverlay.kind(1, 0, 0), "1.18+: any block light stops spawning");
        assertEquals(LightOverlay.ALWAYS, LightOverlay.kind(7, 7, 7), "1.8.9: light 7 still spawns");
        assertEquals(LightOverlay.NONE, LightOverlay.kind(8, 0, 7), "1.8.9: a torch's 8 stops it");
    }

    @Test void theSweepVisitsEveryColumnAndKeepsFourMarkersEach() {
        LightOverlay.clear();
        int columns = LightOverlay.columns(), perTick = LightOverlay.columnsThisTick();
        assertTrue(perTick * LightOverlay.SWEEP_TICKS >= columns, "a sweep covers the area");
        boolean[] seen = new boolean[columns];
        for (int i = 0; i < columns; i++) seen[LightOverlay.nextColumn()] = true;
        for (boolean column : seen) assertTrue(column, "every column is rescanned");
        int column = LightOverlay.nextColumn();
        for (int y = 0; y < 6; y++) LightOverlay.add(column, 1, y, 2, LightOverlay.ALWAYS);
        assertEquals(LightOverlay.PER_COLUMN, LightOverlay.count(column), "extra markers are dropped");
        LightOverlay.add(column, 1, 9, 2, LightOverlay.NONE);
        int slot = column * LightOverlay.PER_COLUMN;
        assertEquals(1, LightOverlay.X[slot]);
        assertEquals(0, LightOverlay.Y[slot]);
        assertEquals(2, LightOverlay.Z[slot]);
        assertEquals(LightOverlay.ALWAYS, LightOverlay.KIND[slot]);
        LightOverlay.clear();
        assertEquals(0, LightOverlay.count(column));
    }
}
