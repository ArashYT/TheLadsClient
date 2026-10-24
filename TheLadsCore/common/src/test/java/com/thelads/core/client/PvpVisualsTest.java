package com.thelads.core.client;

import com.google.gson.JsonObject;
import com.thelads.core.config.ParticleReductionOption;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The pure sums behind PARITY-1023 pvp rows 30 and 32: per-type particle looks and the light overlay's marker store. */
class PvpVisualsTest {
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
