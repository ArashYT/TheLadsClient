package com.thelads.core;

import com.thelads.core.client.bridge.LadsGraphics;
import com.thelads.core.client.gui.DraggableHudScreen;
import com.thelads.core.client.hud.HudAnchors;
import com.thelads.core.client.hud.HudElement;
import com.thelads.core.client.hud.HudGroupLayout.Rect;
import com.thelads.core.client.hud.HudManager;
import com.thelads.core.client.hud.HudScale;
import com.thelads.core.config.ConfigManager;
import com.thelads.core.config.HudSettings;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** 1.9.0: HUD positions keep their place across GUI scales, the Lads HUD's own scale, the hotbar as a snap target, docked HUDs push. */
class HudAnchorsTest {
    private static class Element extends HudElement {
        Element(String name, int w, int h) { width = w; height = h; setModuleName(name); }
        void size(int w, int h) { width = w; height = h; }
        @Override public boolean isEnabled() { return true; }
        @Override public void render(LadsGraphics graphics) { graphics.fill(x, y, x + width, y + height, 0xffffffff); }
    }
    /** A screen with vanilla's GUI scale, as the versions' adapters report it. */
    private static class Screen extends LadsGraphicsTest.MockGraphics {
        int gui;
        @Override public int guiScale() { return gui; }
        Screen at(int w, int h, int scale) { width = w; height = h; gui = scale; return this; }
    }
    private final Screen screen = new Screen();
    private final HudSettings settings = HudSettings.getInstance();
    private List<HudElement> previousElements;
    private final Map<String, int[]> previousPositions = new HashMap<>();
    private final Map<String, HudSettings.Anchor> previousAnchors = new HashMap<>();
    private final List<Set<String>> previousGroups = new ArrayList<>();
    private int previousScale;
    private DraggableHudScreen editor;

    @BeforeEach void isolate() {
        previousElements = new ArrayList<>(HudManager.getInstance().getElements());
        HudManager.getInstance().getElements().clear();
        previousPositions.putAll(settings.getPositions());
        previousAnchors.putAll(settings.getAnchors());
        settings.getGroups().forEach(group -> previousGroups.add(new HashSet<>(group)));
        previousScale = settings.getHudScale();
        settings.clearPositions();
        settings.setHudScale(0);
        settings.setHudFpsCapEnabled(false);
        editor = new DraggableHudScreen(() -> {});
    }

    @AfterEach void restore() {
        editor.close();
        HudManager.getInstance().getElements().clear();
        HudManager.getInstance().getElements().addAll(previousElements);
        settings.clearPositions();
        settings.getPositions().putAll(previousPositions);
        settings.getAnchors().putAll(previousAnchors);
        settings.replaceGroups(previousGroups);
        settings.setHudScale(previousScale);
    }

    private Element add(String name, int w, int h) {
        var element = new Element(name, w, h);
        HudManager.getInstance().getElements().add(element);
        return element;
    }
    /** A position saved before 1.9.0: a bare x, y in the GUI units of the scale it was saved at. */
    private Element saved(String name, int x, int y, int w, int h) { settings.setPosition(name, x, y); return add(name, w, h); }
    private Rect frame(String name) { HudManager.getInstance().render(screen); return HudManager.getInstance().lastBounds(name); }
    private Rect editorFrame(String name) { editor.render(screen, -1, -1); return editor.boundsFor(name); }
    /** An editor drag in Lads HUD pixels (snapping as set). */
    private void drag(String name, int dx, int dy) {
        var b = editorFrame(name);
        assertTrue(editor.mouseClicked(editor.screenX(b.x() + 1), editor.screenY(b.y() + 1), 0, 0));
        editor.mouseDragged(editor.screenX(b.x() + 1 + dx), editor.screenY(b.y() + 1 + dy), 0);
        editor.mouseReleased(editor.screenX(b.x() + 1 + dx), editor.screenY(b.y() + 1 + dy), 0, 0);
        editorFrame(name);
    }

    @Test void aHudBesideTheHotbarStaysBesideItAtEveryGuiScale() {
        // 1920 x 1080 at GUI scale 3 is 640 x 360: the hotbar spans 229..411 x 338..360.
        screen.at(640, 360, 3);
        saved("Right", 417, 338, 60, 22);
        saved("Left", 112, 338, 82, 22);
        assertEquals(new Rect(417, 338, 60, 22), frame("Right"), "no jump at the scale the layout was saved at");
        assertEquals(new Rect(112, 338, 82, 22), frame("Left"));
        assertEquals(HudSettings.HOTBAR, HudAnchors.holder("Right"));
        assertEquals(HudSettings.HOTBAR, HudAnchors.holder("Left"));
        screen.at(960, 540, 2); // scale 2: the hotbar spans 389..571 x 518..540
        assertEquals(new Rect(577, 518, 60, 22), frame("Right"), "6 px right of the hotbar, on its row (it was mid-screen before 1.9.0)");
        assertEquals(new Rect(272, 518, 82, 22), frame("Left"));
        screen.at(480, 270, 4); // scale 4: 149..331 x 248..270
        assertEquals(new Rect(337, 248, 60, 22), frame("Right"));
        assertArrayEquals(new int[]{417, 338}, settings.getPosition("Right"), "the saved position itself is left alone");
    }

    @Test void cornersAndTheCentreKeepTheirOffsets() {
        screen.at(640, 360, 3);
        saved("Corner", 585, 5, 50, 10);
        saved("Middle", 300, 5, 40, 10);
        saved("Bottom", 5, 345, 70, 10);
        frame("Corner");
        screen.at(960, 540, 2);
        assertEquals(new Rect(905, 5, 50, 10), frame("Corner"));
        assertEquals(new Rect(460, 5, 40, 10), frame("Middle"));
        assertEquals(new Rect(5, 525, 70, 10), frame("Bottom"));
    }

    @Test void aDockedHudIsPushedPastTheRoomItsHolderHadAndNeverOverlapped() {
        screen.at(640, 360, 3);
        var upper = saved("Upper", 10, 40, 80, 30);
        saved("Lower", 10, 100, 80, 20);
        editor.keyPressed(71); // snap off: exact moves
        drag("Lower", 0, -30); // its top on Upper's bottom (70)
        assertEquals("Upper", HudAnchors.holder("Lower"));
        assertEquals(70, frame("Lower").y());
        upper.size(80, 60); // more potion effects than in the editor
        assertEquals(100, frame("Lower").y(), "pushed down by what Upper grew");
        upper.size(80, 10); // fewer: Lower keeps its place, Upper keeps the room it had
        assertEquals(70, frame("Lower").y());
        upper.size(80, 30);
        screen.at(960, 540, 2);
        assertEquals(new Rect(10, 70, 80, 20), frame("Lower"), "the stack holds together at another GUI scale");
    }

    @Test void aStackOnTheBottomGrowsUpwards() {
        screen.at(640, 360, 3);
        var base = saved("Base", 5, 330, 80, 20);
        saved("Top", 5, 200, 80, 20);
        editor.keyPressed(71);
        drag("Top", 0, 110); // its bottom on Base's top (330)
        assertEquals("Base", HudAnchors.holder("Top"), "the lower one is nearer its edge, so it holds");
        base.size(80, 40);
        assertEquals(310, frame("Base").y(), "pinned 10 px off the bottom, Base grows upwards");
        assertEquals(290, frame("Top").y(), "and pushes Top up");
    }

    @Test void aScoreboardUnderTheMinimapStaysUnderIt() {
        screen.at(640, 360, 3);
        saved("Map", 535, 44, 100, 100);
        var board = saved("Board", 535, 200, 100, 50);
        editor.keyPressed(71);
        drag("Board", 0, -54); // top 2 px under the map
        assertEquals("Map", HudAnchors.holder("Board"));
        board.size(100, 160); // more lines
        assertEquals(146, frame("Board").y(), "it grows down, never into the map");
        screen.at(960, 540, 2);
        assertEquals(new Rect(855, 146, 100, 160), frame("Board"));
        assertEquals(new Rect(855, 44, 100, 100), frame("Map"));
    }

    @Test void theHotbarIsASnapTargetInTheEditor() {
        screen.at(640, 360, 3);
        saved("Voice", 430, 200, 50, 22);
        drag("Voice", -17, 137); // near the hotbar's right end: 413, 337
        assertEquals(new Rect(411, 338, 50, 22), editor.boundsFor("Voice"), "flush with the hotbar, on its row");
        assertEquals(HudSettings.HOTBAR, HudAnchors.holder("Voice"));
    }

    @Test void draggingAHolderAwayLeavesWhatWasDockedToItWhereItIs() {
        screen.at(640, 360, 3);
        saved("Upper", 10, 40, 80, 30);
        saved("Lower", 10, 100, 80, 20);
        editor.keyPressed(71);
        drag("Lower", 0, -30);
        drag("Upper", 200, 0);
        assertEquals(new Rect(10, 70, 80, 20), editor.boundsFor("Lower"));
        assertEquals("screen", HudAnchors.holder("Lower"));
        assertEquals(new Rect(210, 40, 80, 30), frame("Upper"));
        assertEquals(new Rect(10, 70, 80, 20), frame("Lower"));
    }

    @Test void anchorsThatLoopFallBackToWhereTheHudsMeasured() {
        screen.at(640, 360, 3);
        saved("A", 10, 10, 20, 20);
        saved("B", 10, 30, 20, 20);
        var axis = new HudSettings.Axis(0, 0, 0, 0);
        settings.setAnchor("A", new HudSettings.Anchor(10, 10, "B", axis, axis));
        settings.setAnchor("B", new HudSettings.Anchor(10, 30, "A", axis, axis));
        assertNotNull(frame("A"));
        assertNotNull(frame("B"));
    }

    @Test void theLadsHudHasItsOwnScaleAutoFollowsVanilla() {
        screen.at(640, 360, 3);
        assertSame(screen, HudScale.units(screen), "Auto: the Lads HUD uses vanilla's GUI scale");
        settings.setHudScale(2);
        var hud = HudScale.units(screen);
        assertEquals(960, hud.getScaledWidth());
        assertEquals(540, hud.getScaledHeight());
        assertEquals(new Rect(344, 507, 273, 33), HudAnchors.hotbar(hud), "the hotbar as the Lads HUD sees it at scale 2");
        saved("Right", 417, 338, 60, 22);
        screen.drawCalls.clear();
        frame("Right");
        assertTrue(screen.drawCalls.contains("scale:0.6666667,0.6666667"), "drawn at 2/3 of vanilla's scale 3");
        settings.setHudScale(6);
        assertEquals(4, HudScale.of(screen), "capped at what a 1920 x 1080 window allows, as vanilla's");
        settings.setHudScale(3);
        assertSame(screen, HudScale.units(screen));
    }

    @Test void theEditorShowsAndEditsAtTheLadsHudScale() {
        screen.at(640, 360, 3);
        settings.setHudScale(2);
        add("Box", 40, 20).setPosition(900, 500);
        var bounds = editorFrame("Box");
        assertEquals(new Rect(900, 500, 40, 20), bounds, "the editor's viewport is 960 x 540 Lads HUD pixels");
        assertEquals(960 * editor.previewScale(), editor.previewBounds().width(), 1);
    }

    @Test void anchorsAndTheHudScaleSurviveTheConfigRoundTrip() {
        screen.at(640, 360, 3);
        saved("Right", 417, 338, 60, 22);
        frame("Right");
        settings.setHudScale(2);
        var anchor = settings.getAnchor("Right");
        var json = ConfigManager.toJson();
        settings.clearPositions();
        settings.setHudScale(0);
        ConfigManager.applyJson(json);
        assertEquals(anchor, settings.getAnchor("Right"));
        assertEquals(2, settings.getHudScale());
        settings.setPosition("Right", 0, 0);
        assertNull(settings.getAnchor("Right"), "a position saved without its anchor is anchored again where it shows");
    }
}
