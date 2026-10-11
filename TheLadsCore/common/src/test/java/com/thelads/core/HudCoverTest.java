package com.thelads.core;

import com.thelads.core.client.bridge.LadsGraphics;
import com.thelads.core.client.hud.HudCover;
import com.thelads.core.client.hud.HudElement;
import com.thelads.core.client.hud.HudManager;
import com.thelads.core.config.HudSettings;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The tab list and the debug text cover the Lads HUD widgets under them (HudCover, HudManager). */
class HudCoverTest {
    @AfterEach
    void clear() {
        HudCover.frame();
        HudCover.frame();
    }

    @Test
    void tabBoxCoversOverlapsOnlyWhileDrawnThisFrame() {
        HudCover.frame();
        HudCover.add(HudCover.TAB, 200, 60, 100, 10); // any corner order
        assertTrue(HudCover.covered(150, 50, 260, 80, false));
        assertFalse(HudCover.covered(200, 10, 250, 60, false), "touching the edge is not under it");
        assertFalse(HudCover.covered(100, 60, 200, 90, true));
        HudCover.frame();
        assertFalse(HudCover.covered(150, 50, 260, 80, false), "the tab list closed: not drawn this frame");
    }

    @Test
    void debugColumnsCoverTheirLineBoxesWhileF3IsOpen() {
        HudCover.frame();
        // Left: "abc" (18 wide) at x 2, y 2; the empty line keeps its row; "defgh" (30 wide) at y 20.
        HudCover.debugColumn(List.of("abc", "", "defgh"), true, 320, s -> s.length() * 6);
        // Right: "xy" (12 wide) right aligned 2 from the edge.
        HudCover.debugColumn(List.of("xy"), false, 320, s -> s.length() * 6);
        assertTrue(HudCover.covered(0, 0, 5, 5, true));
        assertFalse(HudCover.covered(0, 0, 5, 5, false), "F3 closed");
        assertFalse(HudCover.covered(21, 1, 40, 10, true), "right of the short first line");
        assertFalse(HudCover.covered(1, 10, 40, 19, true), "the empty line has no box");
        assertTrue(HudCover.covered(30, 19, 40, 22, true), "the third line");
        assertTrue(HudCover.covered(310, 5, 312, 6, true));
        assertFalse(HudCover.covered(300, 5, 305, 6, true), "left of the right column's short line");
        // 26.x draws the debug screen after the HUD: the HUD sees the last frame's boxes while F3 stays open.
        HudCover.frame();
        assertTrue(HudCover.covered(0, 0, 5, 5, true));
        assertFalse(HudCover.covered(0, 0, 5, 5, false));
    }

    @Test
    void vanillaPartsDrawnBeforeTheCoverSeeTheLastFramesBoxes() {
        HudCover.frame();
        HudCover.add(HudCover.TAB, 600, 0, 640, 30); // the top right of a 640 wide screen
        HudCover.frame(); // the next frame: vanilla's effects and boss bars draw before the tab list does
        assertTrue(HudCover.effectsCovered(640, 1, 0), "one effect icon at 615..640");
        assertFalse(HudCover.effectsCovered(640, 0, 0), "no effects");
        assertFalse(HudCover.bossBarsCovered(640, 1), "the boss bar is in the middle");
        assertFalse(HudCover.covered(610, 5, 620, 10, false), "the Lads HUD, drawn after the tab list, goes by this frame's");
        HudCover.frame();
        assertFalse(HudCover.effectsCovered(640, 1, 0), "two frames later the tab list is gone");
    }

    @Test
    void debugTextAtItsOwnScaleIsMeasuredInGuiUnits() {
        HudCover.frame();
        HudCover.add(HudCover.TAB, 0, 0, 10, 10);
        int mark = HudCover.mark();
        // 26.x: the debug text at GUI scale 1 under a GUI scale of 2, a 1600 wide column end on an 800 wide GUI.
        HudCover.debugColumn(List.of("abcdefghij"), false, 1600, s -> s.length() * 6);
        HudCover.scaleSince(mark, 0.5, 0.5, 0, 0);
        assertTrue(HudCover.covered(780, 1, 800, 3, true), "the right column's line ends at the GUI's right edge");
        assertFalse(HudCover.covered(810, 1, 900, 3, true));
        assertTrue(HudCover.covered(5, 5, 6, 6, false), "boxes before the mark are kept as they were");
    }

    /** A widget of its own (no game state of its own, so other tests' widgets are not touched). */
    private static final class Probe extends HudElement {
        Probe() {
            setModuleName("CoverProbe");
            setPosition(100, 100);
            width = 50;
            height = 20;
        }
        @Override public boolean isEnabled() { return true; }
        @Override public boolean isAvailable() { return true; }
        @Override public void render(LadsGraphics graphics) { graphics.drawText("CoverProbe", x, y, 0xFFFFFFFF, false); }
    }

    @Test
    void hudManagerSkipsAndBringsBackACoveredWidget() {
        boolean wasCapped = HudSettings.getInstance().isHudFpsCapEnabled();
        HudSettings.getInstance().setHudFpsCapEnabled(false);
        HudManager hud = HudManager.getInstance();
        Probe probe = new Probe();
        hud.getElements().add(probe);
        try {
            HudCover.frame();
            var graphics = new LadsGraphicsTest.MockGraphics();
            hud.render(graphics);
            var at = hud.lastBounds("CoverProbe");
            assertNotNull(at);
            assertTrue(drew(graphics));
            assertFalse(hud.coverChanged());

            // The tab list opens over a corner of it: the capped HUD is rebuilt at once, and the widget is left out.
            HudCover.frame();
            HudCover.add(HudCover.TAB, at.right() - 2, at.bottom() - 2, at.right() + 40, at.bottom() + 40);
            assertTrue(hud.coverChanged());
            graphics = new LadsGraphicsTest.MockGraphics();
            hud.render(graphics);
            assertFalse(drew(graphics));
            assertTrue(hud.coveredHuds().contains("CoverProbe"));
            assertTrue(hud.isCovered("CoverProbe"));
            assertTrue(hud.coverChanged(), "one more build for what follows a covered HUD (Xaero's map on 26.x)");
            assertFalse(hud.coverChanged());

            // It closes: the widget is back on that frame.
            HudCover.frame();
            assertTrue(hud.coverChanged());
            graphics = new LadsGraphicsTest.MockGraphics();
            hud.render(graphics);
            assertTrue(drew(graphics));
            assertFalse(hud.coveredHuds().contains("CoverProbe"));
        } finally {
            hud.getElements().remove(probe);
            HudSettings.getInstance().setHudFpsCapEnabled(wasCapped);
        }
    }

    private static boolean drew(LadsGraphicsTest.MockGraphics graphics) {
        return graphics.drawCalls.stream().anyMatch(call -> call.startsWith("text:CoverProbe"));
    }
}
