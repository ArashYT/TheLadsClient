package com.thelads.core;

import com.thelads.core.client.bridge.DefaultGameBridge;
import com.thelads.core.client.bridge.LadsGameBridge;
import com.thelads.core.client.gui.DraggableHudScreen;
import com.thelads.core.client.gui.LadsSettingsScreen;
import com.thelads.core.client.hud.HudGroupLayout;
import com.thelads.core.config.ConfigManager;
import com.thelads.core.config.HudSettings;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.ModuleSupport;
import com.thelads.core.config.SliderOption;
import com.thelads.core.modules.FullbrightModule;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Every reset asks first (Esc and Cancel change nothing, Enter and Confirm reset), and a percentage slider's typed field. */
class ConfirmResetTest {
    @TempDir Path dir;
    private static final class Screen extends LadsGraphicsTest.MockGraphics {
        final List<String> texts = new ArrayList<>();
        @Override public void drawText(String text, int x, int y, int color, boolean shadow) { texts.add(text); }
        @Override public void drawCenteredText(String text, int x, int y, int color, boolean shadow) { texts.add(text); }
        boolean shows(String text) { return texts.contains(text); }
        void frame(LadsSettingsScreen ui) { texts.clear(); ui.render(this, -1, -1); }
        void frame(DraggableHudScreen ui) { texts.clear(); ui.render(this, -1, -1); }
    }
    private ModsMenuTest.OwnershipFixture ownership;
    private final Map<String, int[]> savedPositions = new HashMap<>();
    private Module fullbright;
    private boolean fullbrightEnabled;
    private long fullbrightModified;

    @BeforeEach void setUp() throws Exception {
        ConfigManager.setTestConfigFile(dir.resolve("config.json").toFile());
        ownership = new ModsMenuTest.OwnershipFixture();
        LadsGameBridge.set(new DefaultGameBridge());
        HudSettings.getInstance().getPositions().forEach((name, value) -> savedPositions.put(name, value.clone()));
        fullbright = ModuleManager.getInstance().getModule("Fullbright");
        fullbrightEnabled = fullbright.isEnabled();
        fullbrightModified = fullbright.getLastModified();
        ModuleSupport.registerBuiltIn("Fullbright");
    }
    @AfterEach void tearDown() throws Exception {
        HudSettings.getInstance().getPositions().clear();
        HudSettings.getInstance().getPositions().putAll(savedPositions);
        fullbright.getOptions().forEach(option -> option.reset());
        fullbright.setEnabled(fullbrightEnabled); fullbright.setLastModified(fullbrightModified);
        ownership.close();
        ConfigManager.setTestConfigFile(null);
    }

    private static void click(DraggableHudScreen ui, HudGroupLayout.Rect r) {
        assertTrue(ui.mouseClicked(r.x() + r.width() / 2.0, r.y() + r.height() / 2.0, 0));
        ui.mouseReleased(r.x() + r.width() / 2.0, r.y() + r.height() / 2.0, 0);
    }
    private static void click(LadsSettingsScreen ui, LadsSettingsScreen.Rect r) {
        assertNotNull(r, "control not drawn");
        assertTrue(ui.mouseClicked(r.x() + r.width() / 2.0, r.y() + r.height() / 2.0, 0));
        ui.mouseReleased(r.x() + r.width() / 2.0, r.y() + r.height() / 2.0, 0);
    }
    private static void click(LadsSettingsScreen ui, HudGroupLayout.Rect r) {
        click(ui, new LadsSettingsScreen.Rect(r.x(), r.y(), r.width(), r.height()));
    }
    private static DraggableHudScreen.Control control(DraggableHudScreen ui, String id) {
        return ui.controls().stream().filter(c -> c.id().equals(id)).findFirst().orElseThrow();
    }
    private static void type(LadsSettingsScreen ui, String text) { text.codePoints().forEach(ui::charTyped); }

    @Test void hudEditorResetAsksFirstAndEscOrCancelChangeNothing() {
        HudSettings.getInstance().setPosition("CPS", 12, 42);
        int[] saves = {0};
        boolean[] closed = {false};
        var ui = new DraggableHudScreen(() -> saves[0]++);
        ui.setOnClose(() -> closed[0] = true);
        var g = new Screen();
        g.frame(ui);
        click(ui, control(ui, "reset").bounds());
        g.frame(ui);
        assertTrue(ui.confirmDialog().isOpen());
        assertTrue(g.shows("Are you sure you want to reset?") && g.shows("Confirm") && g.shows("Cancel"));
        assertArrayEquals(new int[] {12, 42}, HudSettings.getInstance().getPosition("CPS"), "asking resets nothing");

        assertTrue(ui.keyPressed(256, 0));
        assertFalse(ui.confirmDialog().isOpen());
        assertFalse(closed[0], "Esc cancels the question, it does not leave the editor");
        assertArrayEquals(new int[] {12, 42}, HudSettings.getInstance().getPosition("CPS"));

        g.frame(ui);
        click(ui, control(ui, "reset").bounds());
        g.frame(ui);
        click(ui, ui.confirmDialog().cancelBounds());
        assertFalse(ui.confirmDialog().isOpen());
        assertArrayEquals(new int[] {12, 42}, HudSettings.getInstance().getPosition("CPS"), "Cancel keeps the layout");
        assertEquals(0, saves[0]);

        g.frame(ui);
        click(ui, control(ui, "reset").bounds());
        g.frame(ui);
        assertTrue(ui.mouseClicked(1, 1, 0), "a click outside the box");
        assertFalse(ui.confirmDialog().isOpen());
        assertArrayEquals(new int[] {12, 42}, HudSettings.getInstance().getPosition("CPS"), "outside cancels too");
    }

    @Test void hudEditorResetRunsOnConfirmOrEnter() {
        int[] saves = {0};
        var ui = new DraggableHudScreen(() -> saves[0]++);
        var g = new Screen();
        for (boolean byKey : new boolean[] {false, true}) {
            HudSettings.getInstance().setPosition("CPS", 12, 42);
            g.frame(ui);
            click(ui, control(ui, "reset").bounds());
            g.frame(ui);
            assertTrue(ui.confirmDialog().isOpen());
            if (byKey) assertTrue(ui.keyPressed(257, 0)); else click(ui, ui.confirmDialog().confirmBounds());
            assertFalse(ui.confirmDialog().isOpen());
            assertNull(HudSettings.getInstance().getPosition("CPS"), byKey ? "Enter resets" : "Confirm resets");
        }
        assertEquals(2, saves[0]);
    }

    @Test void theDialogTakesEveryInputWhileItIsOpen() {
        HudSettings.getInstance().setPosition("CPS", 12, 42);
        var ui = new DraggableHudScreen(() -> { });
        var g = new Screen();
        g.frame(ui);
        click(ui, control(ui, "reset").bounds());
        g.frame(ui);
        assertTrue(ui.mouseScrolled(100, 100, 1));
        assertTrue(ui.mouseDragged(100, 100, 0));
        assertTrue(ui.charTyped('x'));
        assertTrue(ui.keyPressed(71, 0), "the G (snap) shortcut is swallowed");
        assertTrue(ui.confirmDialog().isOpen());
        ui.close();
        assertFalse(ui.confirmDialog().isOpen(), "leaving the editor drops the question");
    }

    @Test void moduleResetOptionsAsksWhichModuleAndWhatItResets() {
        var menu = new LadsSettingsScreen();
        var gamma = (SliderOption) fullbright.getOption("Gamma");
        gamma.setValue(30);
        menu.openModule("Fullbright");
        var g = new Screen();
        g.frame(menu);
        click(menu, menu.controlBounds("reset"));
        g.frame(menu);
        assertTrue(menu.confirmDialog().isOpen());
        assertTrue(g.shows("Are you sure you want to reset?") && g.texts.stream().anyMatch(t -> t.startsWith("Reset every Fullbright option")), "the box says which module");
        assertEquals(30, gamma.getValue(), "asking resets nothing");

        assertTrue(menu.keyPressed(256, 0));
        assertFalse(menu.confirmDialog().isOpen());
        g.frame(menu);
        assertTrue(g.shows("MODULE SETTINGS"), "Esc cancelled the question; the module page stays open");
        assertEquals(30, gamma.getValue(), "Esc keeps the options");

        click(menu, menu.controlBounds("reset"));
        g.frame(menu);
        click(menu, menu.confirmDialog().cancelBounds());
        assertEquals(30, gamma.getValue(), "Cancel keeps the options");

        g.frame(menu);
        click(menu, menu.controlBounds("reset"));
        g.frame(menu);
        click(menu, menu.confirmDialog().confirmBounds());
        assertFalse(menu.confirmDialog().isOpen());
        assertEquals(65, gamma.getValue(), "Confirm resets every option of that module");

        gamma.setValue(30);
        g.frame(menu);
        click(menu, menu.controlBounds("reset"));
        assertTrue(menu.keyPressed(257, 0));
        assertEquals(65, gamma.getValue(), "Enter confirms");
    }

    @Test void aPercentageSliderSnapsWhileDraggedAndTakesATypedWholeNumber() {
        var menu = new LadsSettingsScreen();
        var gamma = (SliderOption) fullbright.getOption("Gamma");
        menu.openModule("Fullbright");
        var g = new Screen();
        g.frame(menu);
        var track = menu.controlBounds("option:Gamma");
        assertNotNull(menu.controlBounds("option:Gamma:field"), "the value is a field beside the slider");
        assertTrue(g.shows("65%"));
        int y = track.y() + track.height() / 2;
        for (int x = track.x() + 4; x < track.x() + track.width() - 4; x += 3) {
            assertTrue(menu.mouseClicked(x, y, 0));
            assertEquals(0, gamma.getValue() % 5, 1e-9, "the drag at " + x + " stops at " + gamma.getValue());
            menu.mouseReleased(x, y, 0);
        }

        click(menu, menu.controlBounds("option:Gamma:field"));
        assertTrue(menu.isEditingText());
        type(menu, "70");
        type(menu, "x.%");
        g.frame(menu);
        assertTrue(g.texts.contains("70|"), "the field shows what is typed, with the cursor");
        assertTrue(menu.keyPressed(257, 0));
        assertEquals(70, gamma.getValue());
        assertFalse(menu.isEditingText());

        click(menu, menu.controlBounds("option:Gamma:field"));
        type(menu, "67");
        menu.keyPressed(257, 0);
        assertEquals(65, gamma.getValue(), "typing allows any whole number; it lands on a step");

        click(menu, menu.controlBounds("option:Gamma:field"));
        type(menu, "250");
        menu.keyPressed(257, 0);
        assertEquals(65, gamma.getValue(), "out of range reverts");
        g.frame(menu);
        assertTrue(g.texts.stream().anyMatch(t -> t.startsWith("Not a whole number from 0 to 100: kept 65%")));

        click(menu, menu.controlBounds("option:Gamma:field"));
        type(menu, "10");
        menu.keyPressed(256, 0);
        assertEquals(65, gamma.getValue(), "Esc leaves the value as it was");
        assertEquals(1 + 14 * 65 / 100.0, ((FullbrightModule) fullbright).getGamma(), 1e-9);

        click(menu, menu.controlBounds("option:Gamma:field"));
        type(menu, "40");
        click(menu, menu.controlBounds("option:Brightness Multiplier"));
        assertEquals(40, gamma.getValue(), "clicking away applies a valid number");
    }
}
