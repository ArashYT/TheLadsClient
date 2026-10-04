package com.thelads.core;

import com.google.gson.JsonElement;
import com.thelads.core.client.bridge.DefaultGameBridge;
import com.thelads.core.client.bridge.LadsGameBridge;
import com.thelads.core.client.bridge.LadsGraphics;
import com.thelads.core.client.gui.DraggableHudScreen;
import com.thelads.core.client.hud.FPSHudElement;
import com.thelads.core.client.hud.HudElement;
import com.thelads.core.client.hud.HudGroupLayout.Rect;
import com.thelads.core.client.hud.HudManager;
import com.thelads.core.config.BoolOption;
import com.thelads.core.config.HudSettings;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.Option;
import com.thelads.core.config.SliderOption;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class HudEditorVisibilityTest {
    private record ModuleState(Module module, boolean enabled, long modified) {}
    private record Fill(float x, float y, float width, float height, int color) {}
    private record Text(String value, int color) {}

    private static class Graphics extends LadsGraphicsTest.MockGraphics {
        final List<Fill> fills = new ArrayList<>();
        final List<Text> texts = new ArrayList<>();
        final List<String> instructions = new ArrayList<>();
        final ArrayDeque<float[]> poses = new ArrayDeque<>();
        float sx = 1, sy = 1, tx, ty;
        @Override public void pushPose() { poses.push(new float[] {sx, sy, tx, ty}); }
        @Override public void popPose() {
            float[] pose = poses.pop();
            sx = pose[0]; sy = pose[1]; tx = pose[2]; ty = pose[3];
        }
        @Override public void translate(float x, float y) { tx += x * sx; ty += y * sy; }
        @Override public void scale(float x, float y) { sx *= x; sy *= y; }
        @Override public void fill(int left, int top, int right, int bottom, int color) {
            fills.add(new Fill(left * sx + tx, top * sy + ty, (right - left) * sx, (bottom - top) * sy, color));
        }
        @Override public void drawText(String value, int x, int y, int color, boolean shadow) {
            texts.add(new Text(value, color));
        }
        @Override public void drawCenteredText(String value, int x, int y, int color, boolean shadow) {
            instructions.add(value);
        }
        void clearFrame() { fills.clear(); texts.clear(); instructions.clear(); }
    }

    private static class ProbeHud extends HudElement {
        boolean active;
        boolean available = true;
        int renders;
        ProbeHud(String name, boolean active, int x, int y) {
            this.active = active;
            setPosition(x, y);
            setModuleName(name);
        }
        @Override public boolean isEnabled() { return active; }
        @Override public boolean isAvailable() { return available; }
        @Override public void render(LadsGraphics graphics) {
            renders++;
            graphics.drawText(moduleName, x, y, 0xFFFFFFFF, false);
        }
    }

    private List<HudElement> savedElements;
    private final List<ModuleState> savedModules = new ArrayList<>();
    private final Map<Option, JsonElement> savedOptions = new HashMap<>();
    private final Map<String, int[]> savedPositions = new HashMap<>();
    private LadsGameBridge savedBridge;
    private Graphics graphics;
    private DraggableHudScreen editor;
    private AtomicInteger saves;

    @BeforeEach
    void setup() {
        savedElements = new ArrayList<>(HudManager.getInstance().getElements());
        HudManager.getInstance().getElements().clear();
        for (Module module : ModuleManager.getInstance().getModules()) {
            savedModules.add(new ModuleState(module, module.isEnabled(), module.getLastModified()));
        }
        for (Option option : ModuleManager.getInstance().getModule("FPS").getOptions()) {
            savedOptions.put(option, option.save());
        }
        HudSettings.getInstance().getPositions().forEach((name, value) -> savedPositions.put(name, value.clone()));
        HudSettings.getInstance().getPositions().clear();
        savedBridge = LadsGameBridge.get();
        LadsGameBridge.set(new DefaultGameBridge() {
            @Override public int getFps() { return 30; }
        });
        graphics = new Graphics();
        saves = new AtomicInteger();
        editor = new DraggableHudScreen(saves::incrementAndGet);
    }

    @AfterEach
    void restore() {
        HudManager.getInstance().getElements().clear();
        HudManager.getInstance().getElements().addAll(savedElements);
        savedModules.forEach(state -> {
            state.module().setEnabled(state.enabled());
            state.module().setLastModified(state.modified());
        });
        savedOptions.forEach(Option::load);
        HudSettings.getInstance().getPositions().clear();
        HudSettings.getInstance().getPositions().putAll(savedPositions);
        LadsGameBridge.set(savedBridge);
    }

    private ProbeHud add(String name, boolean enabled, int x, int y) {
        ProbeHud element = new ProbeHud(name, enabled, x, y);
        HudManager.getInstance().getElements().add(element);
        return element;
    }

    private void render() { editor.render(graphics, -1, -1); }
    /** Pointer input at a game GUI position, through the preview's mapping to the screen. */
    private boolean click(double x, double y) { return editor.mouseClicked(editor.screenX(x), editor.screenY(y), 0); }
    private boolean move(double x, double y) { return editor.mouseDragged(editor.screenX(x), editor.screenY(y), 0); }
    private boolean release(double x, double y) { return editor.mouseReleased(editor.screenX(x), editor.screenY(y), 0); }
    private DraggableHudScreen.Control control(String id) {
        return editor.controls().stream().filter(control -> control.id().equals(id)).findFirst().orElseThrow();
    }
    private void press(DraggableHudScreen.Control control) {
        assertTrue(editor.mouseClicked(control.bounds().x() + 2, control.bounds().y() + 2, 0));
        render();
    }

    @Test
    void onlyEnabledHudRendersByDefaultEvenWhenDisabledRowsOverlapIt() {
        ProbeHud enabled = add("Enabled", true, 100, 100);
        ProbeHud disabled = add("Disabled", false, 100, 100);
        render();
        assertEquals(1, enabled.renders);
        assertEquals(0, disabled.renders);
        assertEquals(List.of("Enabled"), editor.listedNames());
        assertTrue(click(110, 110));
        move(210, 210);
        release(210, 210);
        assertEquals(200, enabled.getX());
        assertEquals(100, disabled.getX());
        assertFalse(HudSettings.getInstance().getPositions().containsKey("Disabled"));
    }

    @Test
    void defaultHiddenHudHasNoClickableBounds() {
        add("Disabled", false, 100, 100);
        render();
        assertFalse(click(110, 110));
        assertTrue(move(210, 210)); // Empty preview begins box selection, not a HUD move.
        assertTrue(editor.selectedNames().isEmpty());
        assertEquals(0, saves.get());
    }

    @Test
    void showDisabledListsAndDimsDisabledPreviewWithoutEnablingIt() {
        ProbeHud disabled = add("Disabled", false, 100, 100);
        add("Enabled", true, 300, 100);
        render();
        press(control("previews"));
        assertTrue(editor.isShowingAll());
        assertEquals(List.of("Disabled", "Enabled"), editor.listedNames());
        assertEquals(1, disabled.renders);
        assertFalse(disabled.active);
        assertFalse(graphics.texts.stream().anyMatch(text -> text.value().contains("· disabled")));
        editor.render(graphics, (int) editor.screenX(110), (int) editor.screenY(110));
        assertTrue(graphics.texts.stream().anyMatch(text -> text.value().contains("· disabled")));
        assertTrue(graphics.fills.stream().anyMatch(fill -> fill.color() == 0x88222222));
        assertTrue(click(110, 110));
        release(110, 110);
        assertFalse(disabled.active);
        assertTrue(editor.keyPressed(65));
        render();
        assertEquals(List.of("Enabled"), editor.listedNames(), "A switches back to switched-on HUDs only");
    }

    @Test
    void showAllDoesNotExposeUnavailableImplementations() {
        ProbeHud unavailable = add("Unavailable", true, 100, 100);
        unavailable.available = false;
        editor.keyPressed(65);
        render();
        assertEquals(0, unavailable.renders);
        assertFalse(click(110, 110));
        assertTrue(editor.listedNames().isEmpty());
    }

    @Test
    void togglingPreviewOffRejectsOldBoundsBeforeAnotherFrame() {
        ProbeHud disabled = add("Disabled", false, 100, 100);
        editor.keyPressed(65);
        render();
        editor.keyPressed(65);
        assertFalse(click(110, 110));
        int renders = disabled.renders;
        render();
        assertEquals(renders, disabled.renders);
    }

    @Test
    void newlyShownPreviewCannotBeHitUntilDrawn() {
        add("Disabled", false, 100, 100);
        render();
        editor.keyPressed(65);
        assertFalse(click(110, 110));
        render();
        assertTrue(click(110, 110));
        release(110, 110);
    }

    @Test
    void moduleDisabledBetweenFramesCannotBeSelectedThroughStaleBounds() {
        ProbeHud element = add("Enabled", true, 100, 100);
        render();
        element.active = false;
        assertFalse(click(110, 110));
    }

    @Test
    void hidingADraggedPreviewFinishesItsLastVisibleMoveOnce() {
        ProbeHud disabled = add("Disabled", false, 100, 100);
        editor.keyPressed(65);
        render();
        click(110, 110);
        move(210, 210);
        editor.keyPressed(65);
        assertEquals(1, saves.get());
        assertArrayEquals(new int[] {200, 200}, HudSettings.getInstance().getPosition("Disabled"));
        assertFalse(move(310, 310));
        assertFalse(release(310, 310));
        assertEquals(200, disabled.getX());
        assertEquals(1, saves.get());
    }

    @Test
    void disablingDuringDragStopsFurtherMovement() {
        ProbeHud element = add("Enabled", true, 100, 100);
        render();
        click(110, 110);
        element.active = false;
        assertFalse(move(210, 210));
        assertEquals(100, element.getX());
        assertEquals(0, saves.get()); // Selecting without moving cannot rebase a saved position.
    }

    @Test
    void newEditorStartsEnabledOnlyAfterAnotherEditorUsedShowAll() {
        ProbeHud disabled = add("Disabled", false, 100, 100);
        editor.keyPressed(65);
        render();
        editor = new DraggableHudScreen(saves::incrementAndGet);
        render();
        assertEquals(1, disabled.renders);
        assertFalse(click(110, 110));
        assertTrue(editor.listedNames().isEmpty());
    }

    @Test
    void gridToggleRemainsIndependentOfPreviewMode() {
        ProbeHud disabled = add("Disabled", false, 100, 100);
        editor.keyPressed(71);
        render();
        assertEquals(0, disabled.renders);
        assertFalse(graphics.fills.stream().anyMatch(fill -> fill.color() == 0x15FFFFFF));
        graphics.clearFrame();
        editor.keyPressed(65);
        editor.keyPressed(71);
        render();
        assertEquals(1, disabled.renders);
        assertTrue(graphics.fills.stream().anyMatch(fill -> fill.color() == 0x15FFFFFF));
    }

    @Test
    void onlyFpsAt125PercentRendersRealThirtyFpsScaledIntoThePreview() {
        for (Module module : ModuleManager.getInstance().getModules()) module.setEnabled(module.getName().equals("FPS"));
        Module fpsModule = ModuleManager.getInstance().getModule("FPS");
        ((BoolOption) fpsModule.getOption("Smooth")).set(false);
        ((SliderOption) fpsModule.getOption("Size")).setValue(125);
        HudManager.getInstance().getElements().addAll(savedElements);
        render();
        assertTrue(graphics.texts.contains(new Text("30 FPS", HudSettings.getInstance().getGlobalColor())));
        assertEquals(List.of("FPS"), editor.listedNames());
        assertFalse(editor.isShowingAll());
        // The FPS background (75x20 at 125%) at its game position 5,40, drawn through the preview's one scale.
        double s = editor.previewScale();
        assertTrue(graphics.fills.stream().anyMatch(fill -> Math.abs(fill.x() - editor.screenX(5)) < .01 && Math.abs(fill.y() - editor.screenY(40)) < .01
            && Math.abs(fill.width() - 75 * s) < .01 && Math.abs(fill.height() - 20 * s) < .01), "FPS box scaled at its game position");
        assertTrue(graphics.poses.isEmpty());
        assertEquals(1, graphics.sx);
    }

    @Test
    void listAndPreviewSelectionFollowEachOther() {
        add("Alpha", true, 100, 100);
        add("Beta", true, 300, 100);
        render();
        Rect beta = editor.rowBoundsFor("Beta");
        assertNotNull(beta);
        assertTrue(editor.mouseClicked(beta.x() + 30, beta.y() + 4, 0));
        assertEquals(Set.of("Beta"), editor.selectedNames(), "a list click selects in the preview");
        render();
        Rect alpha = editor.rowBoundsFor("Alpha");
        assertTrue(editor.mouseClicked(alpha.x() + 30, alpha.y() + 4, 0, 2));
        assertEquals(Set.of("Alpha", "Beta"), editor.selectedNames(), "Ctrl adds from the list");
        assertTrue(click(110, 110));
        release(110, 110);
        assertEquals(Set.of("Alpha", "Beta"), editor.selectedNames(), "pressing a selected HUD keeps the selection to drag it");
        click(5, 300);
        release(5, 300);
        assertTrue(click(310, 110));
        release(310, 110);
        assertEquals(Set.of("Beta"), editor.selectedNames(), "a preview click selects that HUD's list row");
        assertFalse(editor.isDragging());
    }

    @Test
    void searchFiltersTheListAndKeepsShortcutLettersAsText() {
        add("Alpha", true, 100, 100);
        add("Beta", true, 300, 100);
        AtomicInteger closes = new AtomicInteger();
        editor.setOnClose(closes::incrementAndGet);
        render();
        press(control("search"));
        for (char c : "ag".toCharArray()) {
            editor.keyPressed(Character.toUpperCase(c));
            editor.charTyped(c);
        }
        render();
        assertFalse(editor.isShowingAll(), "A typed into the search is text, not Show disabled");
        assertTrue(control("snap").label().endsWith("on"), "G typed into the search is text, not Snap");
        assertEquals(List.of(), editor.listedNames());
        editor.keyPressed(259);
        render();
        assertEquals(List.of("Alpha", "Beta"), editor.listedNames());
        editor.charTyped('l');
        render();
        assertEquals(List.of("Alpha"), editor.listedNames());
        assertTrue(editor.keyPressed(256));
        assertEquals(0, closes.get(), "Escape leaves the search first");
        assertTrue(editor.keyPressed(256));
        assertEquals(1, closes.get());
    }

    @Test
    void statusLineExplainsShiftGroupingDuringADrag() {
        add("Enabled", true, 100, 100);
        render();
        click(110, 110);
        move(130, 130);
        graphics.clearFrame();
        render();
        assertTrue(graphics.texts.stream().anyMatch(text -> text.value().contains("hold Shift")));
        release(130, 130);
        graphics.clearFrame();
        render();
        assertFalse(graphics.texts.stream().anyMatch(text -> text.value().contains("hold Shift")));
    }
}
