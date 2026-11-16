package com.thelads.core;

import com.thelads.core.client.bridge.LadsGraphics;
import com.thelads.core.client.gui.DraggableHudScreen;
import com.thelads.core.client.hud.HudElement;
import com.thelads.core.client.hud.HudGroupLayout.Rect;
import com.thelads.core.client.hud.HudManager;
import com.thelads.core.client.hud.HudResize;
import com.thelads.core.client.hud.HudScale;
import com.thelads.core.config.ConfigManager;
import com.thelads.core.config.HudSettings;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.SliderOption;
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

/**
 * The HUD editor on every window and GUI scale: a pointer that stays inside the window can drag every HUD to all four edges and
 * corners of the game screen, it never ends off screen, and the mouse wheel resizes the selected HUD in 25% / 5% steps.
 */
class HudEditorReachTest {
    private static final class Element extends HudElement {
        Element(String name, int w, int h) { width = w; height = h; setModuleName(name); }
        @Override public boolean isEnabled() { return true; }
        @Override public void render(LadsGraphics graphics) { graphics.fill(x, y, x + width, y + height, 0xffffffff); }
    }
    /** A window of {@code pixelsWide x pixelsHigh} at a vanilla GUI scale, as the versions' adapters report it. */
    private static final class Window extends LadsGraphicsTest.MockGraphics {
        final int pixelsWide, pixelsHigh, gui;
        final double exact;
        Window(int pixelsWide, int pixelsHigh, int requestedScale) { this(pixelsWide, pixelsHigh, requestedScale <= 0 ? 0 : requestedScale, 0); }
        /** {@code fraction} is a GUI Scale between vanilla's whole steps (1.20.1 and 1.8.9), such as 2.5. */
        Window(int pixelsWide, int pixelsHigh, int requestedScale, double fraction) {
            this.pixelsWide = pixelsWide; this.pixelsHigh = pixelsHigh;
            int max = HudScale.max(pixelsWide, pixelsHigh);
            exact = fraction > 0 ? Math.max(1, Math.min(fraction, Math.min(pixelsWide / 320.0, pixelsHigh / 240.0))) : requestedScale <= 0 ? max : Math.min(requestedScale, max);
            gui = (int) exact;
            width = (int) Math.ceil(pixelsWide / exact);
            height = (int) Math.ceil(pixelsHigh / exact);
        }
        @Override public int guiScale() { return gui; }
        @Override public double exactGuiScale() { return exact; }
    }
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
        ((SliderOption) ModuleManager.getInstance().getModule("FPS").getOption("Size")).setValue(100);
    }

    private static final int[][] WINDOWS = {{160, 120}, {200, 150}, {256, 144}, {320, 240}, {427, 240}, {640, 360}, {854, 480}, {1024, 768}, {1280, 720}, {1366, 768}, {1600, 900},
        {1920, 1080}, {2560, 1440}, {3440, 1440}, {3840, 2160}, {800, 600}, {1280, 1024}, {5120, 1440}};

    /** Drags the element from its centre to a pointer position (clamped to the window, as the OS does) in a few moves, then drops it. */
    private Rect dragTo(Window window, double pointerX, double pointerY) {
        editor.render(window, -1, -1);
        Rect box = editor.boundsFor("FPS");
        assertNotNull(box, "the element is drawn in the preview");
        Rect onScreen = editor.toScreen(box);
        Rect frame = editor.previewBounds();
        double startX = Math.min(frame.right() - 0.5, onScreen.x() + onScreen.width() / 2.0), startY = Math.min(frame.bottom() - 0.5, onScreen.y() + onScreen.height() / 2.0);
        assertTrue(editor.mouseClicked(startX, startY, 0, 0), "click on the element at " + startX + "," + startY + " window " + window.width + "x" + window.height + " box " + box + " preview " + editor.previewBounds() + " scale " + editor.previewScale() + " hudScale " + settings.getHudScale());
        double endX = Math.max(0, Math.min(window.width - 1, pointerX)), endY = Math.max(0, Math.min(window.height - 1, pointerY));
        for (int i = 1; i <= 6; i++) {
            editor.mouseDragged(startX + (endX - startX) * i / 6, startY + (endY - startY) * i / 6, 0);
            editor.render(window, -1, -1);
        }
        editor.mouseReleased(endX, endY, 0, 0);
        editor.render(window, -1, -1);
        return editor.boundsFor("FPS");
    }

    @Test void everyHudReachesAllFourEdgesAndCornersAtEveryResolutionAndGuiScale() {
        int checked = 0;
        for (int[] pixels : WINDOWS) for (int requested : new int[]{0, 1, 2, 3, 4, 25, 15}) for (int hudScale : new int[]{0, 1, 2, 3, 4}) {
            // 25 and 15 are GUI Scale 2.5x and 1.5x (a fraction; 1.20.1 and 1.8.9); hudScale is the Lads HUD's own scale (0 = Auto).
            var window = requested >= 10 ? new Window(pixels[0], pixels[1], 0, requested / 10.0) : new Window(pixels[0], pixels[1], requested);
            settings.setHudScale(hudScale);
            var hud = HudScale.units(window);
            int hudW = hud.getScaledWidth(), hudH = hud.getScaledHeight();
            for (int[] size : new int[][]{{24, 10}, {90, 30}, {160, 60}}) for (int percent : new int[]{50, 100, 200}) {
                ((SliderOption) ModuleManager.getInstance().getModule("FPS").getOption("Size")).setValue(percent);
                int w = (int) Math.ceil(size[0] * percent / 100.0), h = (int) Math.ceil(size[1] * percent / 100.0);
                if (w > hudW / 2 || h > hudH / 2) continue; // an element wider than half the screen is not a case worth a corner
                HudManager.getInstance().getElements().clear();
                settings.clearPositions();
                var element = new Element("FPS", size[0], size[1]);
                element.setPosition(hudW / 2 - w / 2, hudH / 2 - h / 2);
                settings.setPosition("FPS", element.getX(), element.getY());
                HudManager.getInstance().getElements().add(element);
                editor = new DraggableHudScreen(() -> {}); // its paint-order cache is keyed by the list, not the elements in it
                editor.render(window, -1, -1);
                if (editor.previewScale() * Math.min(w, h) < 3) continue; // under 3 screen pixels in the preview: nothing to grab with a pointer
                String where = pixels[0] + "x" + pixels[1] + " gui " + window.exact + " hudScale " + hudScale + " " + hudW + "x" + hudH + " element " + w + "x" + h;
                for (double[] corner : new double[][]{{-1e6, -1e6}, {1e6, -1e6}, {-1e6, 1e6}, {1e6, 1e6}, {window.width / 2.0, -1e6}, {window.width / 2.0, 1e6}, {-1e6, window.height / 2.0}, {1e6, window.height / 2.0}}) {
                    Rect landed = dragTo(window, corner[0], corner[1]);
                    String case_ = where + " to " + (int) Math.signum(corner[0]) + "," + (int) Math.signum(corner[1]) + " landed " + landed;
                    assertTrue(landed.x() >= 0 && landed.y() >= 0 && landed.right() <= hudW && landed.bottom() <= hudH, "off screen: " + case_);
                    if (corner[0] < -1e5) assertEquals(0, landed.x(), "left edge: " + case_);
                    if (corner[0] > 1e5) assertEquals(hudW, landed.right(), "right edge: " + case_);
                    if (corner[1] < -1e5) assertEquals(0, landed.y(), "top edge: " + case_);
                    if (corner[1] > 1e5) assertEquals(hudH, landed.bottom(), "bottom edge: " + case_);
                    checked++;
                }
            }
        }
        assertTrue(checked > 1000, "swept " + checked);
    }

    private SliderOption sizeOption() { return (SliderOption) ModuleManager.getInstance().getModule("FPS").getOption("Size"); }
    private void wheel(Window window, double amount, boolean control) {
        assertTrue(editor.mouseScrolled(editor.screenX(10), editor.screenY(10), amount, control ? 2 : 0));
        editor.render(window, -1, -1);
    }

    @Test void wheelStepsAreOnTheGridAndStayInsideTheRange() {
        assertEquals(125, HudResize.next(100, true, false, 50, 200));
        assertEquals(75, HudResize.next(100, false, false, 50, 200));
        assertEquals(125, HudResize.next(110, true, false, 50, 200));
        assertEquals(100, HudResize.next(110, false, false, 50, 200));
        assertEquals(105, HudResize.next(100, true, true, 50, 200));
        assertEquals(95, HudResize.next(100, false, true, 50, 200));
        assertEquals(115, HudResize.next(112, true, true, 50, 200));
        assertEquals(110, HudResize.next(112, false, true, 50, 200));
        assertEquals(200, HudResize.next(200, true, false, 50, 200));
        assertEquals(50, HudResize.next(50, false, false, 50, 200));
        assertEquals(150, HudResize.next(125, true, false, 50, 150));
        assertEquals(150, HudResize.next(150, true, true, 50, 150));
    }

    @Test void theWheelResizesTheSelectedHudInPercentAndSavesIt() {
        var window = new Window(1280, 720, 2);
        int[] saves = {0};
        var element = new Element("FPS", 60, 16);
        element.setPosition(300, 100);
        settings.setPosition("FPS", 300, 100);
        HudManager.getInstance().getElements().add(element);
        editor = new DraggableHudScreen(() -> saves[0]++);
        editor.render(window, -1, -1);
        // Nothing selected: the wheel is the list's, not a resize.
        assertFalse(editor.mouseScrolled(editor.screenX(10), editor.screenY(10), 1, 0));
        assertEquals(100, sizeOption().getValue());
        Rect box = editor.toScreen(editor.boundsFor("FPS"));
        assertTrue(editor.mouseClicked(box.x() + box.width() / 2.0, box.y() + box.height() / 2.0, 0, 0));
        editor.mouseReleased(box.x() + box.width() / 2.0, box.y() + box.height() / 2.0, 0, 0);
        wheel(window, 1, false);
        assertEquals(125, sizeOption().getValue());
        assertEquals(75, editor.boundsFor("FPS").width(), "60 wide at 125%");
        assertEquals(108, editor.boundsFor("FPS").y() + editor.boundsFor("FPS").height() / 2, 1, "nearer the screen's centre than an edge it is anchored there and grows around it");
        assertEquals(330, editor.boundsFor("FPS").x() + editor.boundsFor("FPS").width() / 2, 1);
        wheel(window, -1, false);
        wheel(window, -1, false);
        assertEquals(75, sizeOption().getValue());
        wheel(window, 1, true);
        assertEquals(80, sizeOption().getValue());
        wheel(window, -1, true);
        wheel(window, -1, true);
        assertEquals(70, sizeOption().getValue());
        for (int i = 0; i < 12; i++) wheel(window, 1, false);
        assertEquals(200, sizeOption().getValue(), "clamped to the editor's top size");
        for (int i = 0; i < 12; i++) wheel(window, -1, false);
        assertEquals(50, sizeOption().getValue(), "clamped to the editor's smallest size");
        assertTrue(saves[0] >= 8, "every notch is saved");
        // What was saved loads back: the Size option, as the settings screen saves it.
        wheel(window, 1, true);
        var json = ConfigManager.toJson();
        sizeOption().setValue(100);
        ConfigManager.applyJson(json);
        assertEquals(55, sizeOption().getValue(), "5% survives a restart");
    }

    @Test void aResizedHudKeepsItsCornerAndStaysOnScreen() {
        var window = new Window(1280, 720, 2); // 640 x 360
        var element = new Element("FPS", 60, 16);
        element.setPosition(640 - 60, 360 - 16);
        settings.setPosition("FPS", element.getX(), element.getY());
        HudManager.getInstance().getElements().add(element);
        editor = new DraggableHudScreen(() -> {});
        editor.render(window, -1, -1);
        Rect box = editor.toScreen(editor.boundsFor("FPS"));
        assertTrue(editor.mouseClicked(box.x() + box.width() / 2.0, box.y() + box.height() / 2.0, 0, 0));
        editor.mouseReleased(box.x(), box.y(), 0, 0);
        for (int i = 0; i < 4; i++) {
            wheel(window, 1, false);
            Rect grown = editor.boundsFor("FPS");
            assertEquals(640, grown.right(), "bottom-right corner holds at " + sizeOption().getValue());
            assertEquals(360, grown.bottom());
            assertTrue(grown.x() >= 0 && grown.y() >= 0);
        }
        for (int i = 0; i < 8; i++) wheel(window, -1, i % 2 == 0);
        Rect shrunk = editor.boundsFor("FPS");
        assertEquals(640, shrunk.right());
        assertEquals(360, shrunk.bottom());
    }

    /**
     * The default layout, every HUD with its neighbours: the default HUDs touch each other, and a drop beside one docks it to what
     * it touches. Before the fix, what was docked to a dragged HUD held it back from the screen's edge as one block (Potion Effects
     * dropped on the bottom edge sat 18 px above it, over its docked Toggle Sprint). Each drag here starts from where the previous
     * one dropped the HUD, as a player moves one around.
     */
    @Test void everyDefaultHudReachesAllEdgesAmongItsNeighbours() {
        var failures = new ArrayList<String>();
        int checked = 0, grabbed = 0;
        for (int[] pixels : new int[][]{{320, 240}, {640, 360}, {854, 480}, {1024, 768}, {1280, 720}, {1920, 1080}, {2560, 1440}, {3440, 1440}, {3840, 2160}}) for (int requested : new int[]{0, 1, 2, 3, 4}) {
            var window = new Window(pixels[0], pixels[1], requested);
            for (var element : previousElements) {
                if (element.getModuleName() == null || !element.isAvailable() || !element.isEnabled()) continue;
                HudManager.getInstance().getElements().clear();
                HudManager.getInstance().getElements().addAll(previousElements);
                settings.clearPositions();
                editor = new DraggableHudScreen(() -> {});
                editor.render(window, -1, -1);
                if (editor.boundsFor(element.getModuleName()) == null) continue;
                for (double[] corner : new double[][]{{-1e6, -1e6}, {1e6, -1e6}, {-1e6, 1e6}, {1e6, 1e6}, {window.width / 2.0, -1e6}, {window.width / 2.0, 1e6}, {-1e6, window.height / 2.0}, {1e6, window.height / 2.0}}) {
                    editor.render(window, -1, -1);
                    Rect onScreen = editor.toScreen(editor.boundsFor(element.getModuleName()));
                    Rect frame = editor.previewBounds();
                    double startX = Math.min(frame.right() - 0.5, onScreen.x() + onScreen.width() / 2.0), startY = Math.min(frame.bottom() - 0.5, onScreen.y() + onScreen.height() / 2.0);
                    boolean hit = editor.mouseClicked(startX, startY, 0, 0);
                    if (!hit || !editor.selectedNames().equals(Set.of(element.getModuleName()))) { editor.mouseReleased(startX, startY, 0, 0); continue; } // another HUD lies over it here
                    grabbed++;
                    double endX = Math.max(0, Math.min(window.width - 1, corner[0])), endY = Math.max(0, Math.min(window.height - 1, corner[1]));
                    for (int i = 1; i <= 6; i++) { editor.mouseDragged(startX + (endX - startX) * i / 6, startY + (endY - startY) * i / 6, 0); editor.render(window, -1, -1); }
                    editor.mouseReleased(endX, endY, 0, 0);
                    editor.render(window, -1, -1);
                    Rect landed = editor.boundsFor(element.getModuleName());
                    int hudW = editor.hudViewport().width(), hudH = editor.hudViewport().height();
                    checked++;
                    boolean ok = landed.x() >= 0 && landed.y() >= 0 && landed.right() <= hudW && landed.bottom() <= hudH;
                    if (corner[0] < -1e5) ok &= landed.x() == 0;
                    if (corner[0] > 1e5) ok &= landed.right() == hudW;
                    if (corner[1] < -1e5) ok &= landed.y() == 0;
                    if (corner[1] > 1e5) ok &= landed.bottom() == hudH;
                    if (!ok) failures.add(element.getModuleName() + " " + pixels[0] + "x" + pixels[1] + " gui " + window.exact + " " + hudW + "x" + hudH + " to " + (int) Math.signum(corner[0]) + "," + (int) Math.signum(corner[1]) + " landed " + landed);
                }
            }
        }
        assertTrue(checked > 1000, "swept " + checked + " of " + grabbed);
        assertEquals(List.of(), failures.stream().limit(20).toList(), failures.size() + " of " + checked + " drags missed their edge");
    }
}
