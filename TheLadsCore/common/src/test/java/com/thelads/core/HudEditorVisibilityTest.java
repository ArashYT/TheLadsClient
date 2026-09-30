package com.thelads.core;

import com.google.gson.JsonElement;
import com.thelads.core.client.bridge.DefaultGameBridge;
import com.thelads.core.client.bridge.LadsGameBridge;
import com.thelads.core.client.bridge.LadsGraphics;
import com.thelads.core.client.gui.DraggableHudScreen;
import com.thelads.core.client.hud.FPSHudElement;
import com.thelads.core.client.hud.HudElement;
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

    @Test
    void onlyEnabledHudRendersByDefaultEvenWhenDisabledRowsOverlapIt() {
        ProbeHud enabled = add("Enabled", true, 100, 100);
        ProbeHud disabled = add("Disabled", false, 100, 100);
        render();
        assertEquals(1, enabled.renders);
        assertEquals(0, disabled.renders);
        assertTrue(editor.mouseClicked(110, 110, 0));
        editor.mouseDragged(210, 210, 0);
        editor.mouseReleased(210, 210, 0);
        assertEquals(200, enabled.getX());
        assertEquals(100, disabled.getX());
        assertFalse(HudSettings.getInstance().getPositions().containsKey("Disabled"));
    }

    @Test
    void defaultHiddenHudHasNoClickableBounds() {
        add("Disabled", false, 100, 100);
        render();
        assertFalse(editor.mouseClicked(110, 110, 0));
        assertTrue(editor.mouseDragged(210, 210, 0)); // Empty canvas begins box selection, not a HUD move.
        assertTrue(editor.selectedNames().isEmpty());
        assertEquals(0, saves.get());
    }

    @Test
    void showAllOptInDimsAndLabelsDisabledPreviewWithoutEnablingIt() {
        ProbeHud disabled = add("Disabled", false, 100, 100);
        assertTrue(editor.keyPressed(65));
        render();
        assertEquals(1, disabled.renders);
        assertFalse(disabled.active);
        assertFalse(graphics.texts.stream().anyMatch(text -> text.value().contains("disabled")));
        editor.render(graphics,110,110);
        assertTrue(graphics.texts.stream().anyMatch(text -> text.value().contains("disabled")));
        assertTrue(graphics.fills.stream().anyMatch(fill -> fill.color() == 0x88222222));
        assertTrue(graphics.instructions.contains("All HUDs · disabled previews stay disabled"));
        assertTrue(editor.controls().stream().anyMatch(control -> control.id().equals("previews") && control.label().equals("Supported only")));
        assertTrue(editor.mouseClicked(110, 110, 0));
        editor.mouseReleased(110, 110, 0);
        assertFalse(disabled.active);
    }

    @Test
    void showAllDoesNotExposeUnavailableImplementations() {
        ProbeHud unavailable = add("Unavailable", true, 100, 100);
        unavailable.available = false;
        editor.keyPressed(65);
        render();
        assertEquals(0, unavailable.renders);
        assertFalse(editor.mouseClicked(110, 110, 0));
    }

    @Test
    void togglingPreviewOffRejectsOldBoundsBeforeAnotherFrame() {
        add("Disabled", false, 100, 100);
        editor.keyPressed(65);
        render();
        editor.keyPressed(65);
        assertFalse(editor.mouseClicked(110, 110, 0));
        graphics.clearFrame();
        render();
        assertTrue(graphics.texts.isEmpty());
    }

    @Test
    void newlyShownPreviewCannotBeHitUntilDrawn() {
        add("Disabled", false, 100, 100);
        render();
        editor.keyPressed(65);
        assertFalse(editor.mouseClicked(110, 110, 0));
        render();
        assertTrue(editor.mouseClicked(110, 110, 0));
        editor.mouseReleased(110, 110, 0);
    }

    @Test
    void moduleDisabledBetweenFramesCannotBeSelectedThroughStaleBounds() {
        ProbeHud element = add("Enabled", true, 100, 100);
        render();
        element.active = false;
        assertFalse(editor.mouseClicked(110, 110, 0));
    }

    @Test
    void hidingADraggedPreviewFinishesItsLastVisibleMoveOnce() {
        ProbeHud disabled = add("Disabled", false, 100, 100);
        editor.keyPressed(65);
        render();
        editor.mouseClicked(110, 110, 0);
        editor.mouseDragged(210, 210, 0);
        editor.keyPressed(65);
        assertEquals(1, saves.get());
        assertArrayEquals(new int[] {200, 200}, HudSettings.getInstance().getPosition("Disabled"));
        assertFalse(editor.mouseDragged(310, 310, 0));
        assertFalse(editor.mouseReleased(310, 310, 0));
        assertEquals(200, disabled.getX());
        assertEquals(1, saves.get());
    }

    @Test
    void disablingDuringDragStopsFurtherMovement() {
        ProbeHud element = add("Enabled", true, 100, 100);
        render();
        editor.mouseClicked(110, 110, 0);
        element.active = false;
        assertFalse(editor.mouseDragged(210, 210, 0));
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
        assertFalse(editor.mouseClicked(110, 110, 0));
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
    void onlyFpsAt125PercentRendersRealThirtyFpsWithTopLeftUncovered() {
        for (Module module : ModuleManager.getInstance().getModules()) module.setEnabled(module.getName().equals("FPS"));
        Module fpsModule = ModuleManager.getInstance().getModule("FPS");
        ((BoolOption) fpsModule.getOption("Smooth")).set(false);
        ((SliderOption) fpsModule.getOption("Size")).setValue(125);
        HudManager.getInstance().getElements().addAll(savedElements);
        render();
        assertEquals(List.of(new Text("30 FPS", HudSettings.getInstance().getGlobalColor())), graphics.texts);
        Fill bar = graphics.fills.stream().filter(fill -> fill.color() == com.thelads.core.client.gui.LadsPalette.PANEL).findFirst().orElseThrow();
        assertTrue(bar.y() > graphics.height / 2f);
        assertTrue(bar.width() < graphics.width);
        assertTrue(editor.controls().stream().anyMatch(control -> control.id().equals("previews") && control.label().equals("All previews")));
        assertTrue(graphics.fills.stream().anyMatch(fill -> fill.x() == 5 && fill.y() == 40 && fill.width() == 75 && fill.height() == 20));
        assertTrue(graphics.poses.isEmpty());
    }

    @Test
    void visibleToolbarRemainsClickableOnHoverWithoutHidingHudContent() {
        ProbeHud element = add("Enabled", true, 100, 100);
        render();
        Fill bar = graphics.fills.stream().filter(fill -> fill.color() == com.thelads.core.client.gui.LadsPalette.PANEL).findFirst().orElseThrow();
        graphics.clearFrame();
        editor.render(graphics, (int) bar.x() + 1, (int) bar.y() + 1);
        assertEquals(2, element.renders);
        assertFalse(graphics.instructions.isEmpty());
        assertFalse(editor.controls().isEmpty());
        assertTrue(graphics.fills.stream().anyMatch(fill -> fill.color() == com.thelads.core.client.gui.LadsPalette.PANEL));
    }

    @Test
    void instructionBarHidesDuringDragAndReturnsAfterRelease() {
        add("Enabled", true, 100, 100);
        render();
        editor.mouseClicked(110, 110, 0);
        graphics.clearFrame();
        render();
        assertTrue(graphics.instructions.isEmpty());
        editor.mouseReleased(110, 110, 0);
        render();
        assertFalse(graphics.instructions.isEmpty());
    }
}
