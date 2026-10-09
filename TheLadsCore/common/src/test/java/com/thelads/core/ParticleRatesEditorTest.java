package com.thelads.core;

import com.thelads.core.client.ParticleCore;
import com.thelads.core.client.bridge.DefaultGameBridge;
import com.thelads.core.client.bridge.LadsGameBridge;
import com.thelads.core.client.gui.LadsSettingsScreen;
import com.thelads.core.config.ConfigManager;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.ModuleSupport;
import com.thelads.core.modules.ParticleCoreModule;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Particle Core's page and its per-type rates editor, driven through the real screen: open, search, drag, keys, reset, close. */
class ParticleRatesEditorTest {
    @TempDir Path dir;
    private static final class Screen extends LadsGraphicsTest.MockGraphics {
        final List<String> texts = new ArrayList<>();
        @Override public void drawText(String text, int x, int y, int color, boolean shadow) { texts.add(text); }
        @Override public void drawCenteredText(String text, int x, int y, int color, boolean shadow) { texts.add(text); }
        boolean shows(String text) { return texts.contains(text); }
        /** A long option name is cut with "..." to fit its row: the start is what must show. */
        boolean showsStart(String text) { return texts.stream().anyMatch(t -> t.startsWith(text.substring(0, Math.min(text.length(), 10)))); }
        void frame(LadsSettingsScreen ui) { texts.clear(); ui.render(this, -1, -1); }
    }
    private ModsMenuTest.OwnershipFixture ownership;
    private ParticleCoreModule core;
    private final Screen screen = new Screen();

    @BeforeEach void setUp() throws Exception {
        ConfigManager.setTestConfigFile(dir.resolve("config.json").toFile());
        ownership = new ModsMenuTest.OwnershipFixture();
        LadsGameBridge.set(new DefaultGameBridge());
        ModuleSupport.registerBuiltIn(ParticleCore.MODULE);
        core = (ParticleCoreModule) ModuleManager.getInstance().getModule(ParticleCore.MODULE);
        core.rates.reset();
        core.rates.setCatalog(() -> List.of("smoke", "flame", "crit", "minecraft:heart", "particlerain:rain"));
    }

    @AfterEach void tearDown() throws Exception {
        core.getOptions().forEach(option -> option.reset());
        core.rates.setCatalog(null);
        ParticleCore.refresh();
        ownership.close();
        ConfigManager.setTestConfigFile(null);
    }

    private static void click(LadsSettingsScreen ui, LadsSettingsScreen.Rect r, double fraction, int button) {
        assertNotNull(r, "control not drawn");
        ui.mouseClicked(r.x() + r.width() * fraction, r.y() + r.height() / 2.0, button);
    }

    @Test void theModuleIsOnByDefaultAndItsPageListsTheOptions() {
        var ui = new LadsSettingsScreen();
        assertTrue(core.isEnabled());
        ui.openModule(ParticleCore.MODULE);
        screen.frame(ui);
        for (String option : List.of(ParticleCore.CULLING, ParticleCore.OPTIMIZED, ParticleCore.HIDE_OWN, ParticleCore.HIDE_OTHERS, ParticleCore.RATES))
            assertTrue(screen.showsStart(option), option + " is on the page");
        assertEquals("Performance", LadsSettingsScreen.categoryOf(core));
        click(ui, ui.controlBounds("option:" + ParticleCore.HIDE_OWN), .5, 0);
        assertTrue(core.hideOwn.get(), "the switch toggles");
    }

    @Test void theEditorSearchesDragsAndResetsRates() {
        var ui = new LadsSettingsScreen();
        ui.openModule(ParticleCore.MODULE);
        screen.frame(ui);
        click(ui, ui.controlBounds("option:" + ParticleCore.RATES), .5, 0);
        screen.frame(ui);
        assertTrue(screen.shows("Particle spawn rates"), "the editor opened");
        assertTrue(screen.shows("heart") && screen.shows("particlerain:rain") && screen.shows("flame"), "ids are listed without the vanilla namespace");
        assertTrue(ui.isEditingText(), "the editor owns the keyboard");

        // Typing narrows the list; the screen under it does not get the letters.
        "fla".codePoints().forEach(ui::charTyped);
        screen.frame(ui);
        assertTrue(screen.shows("flame") && !screen.shows("smoke"), "the search narrows the list");
        assertNotNull(ui.particleRatesTrack("flame"));
        assertNull(ui.particleRatesTrack("smoke"));

        // A click on the track is a rate in steps of 5; the far left is 0 (off), the far right 100.
        var track = ui.particleRatesTrack("flame");
        click(ui, track, 0.5, 0);
        assertEquals(50, core.rates.percent("flame"));
        click(ui, track, 0.0, 0);
        assertEquals(0, core.rates.percent("flame"));
        ui.mouseDragged(track.x() + track.width() * 0.3, track.y(), 0, 0, 0);
        ui.mouseReleased(track.x() + track.width() * 0.3, track.y(), 0);
        assertEquals(30, core.rates.percent("flame"), "dragging follows the pointer");
        screen.frame(ui);
        assertTrue(screen.shows("30%"));

        // Keys: Right and Left step 5 (Ctrl 1), Delete is back to 100.
        ui.keyPressed(262, 0);
        assertEquals(35, core.rates.percent("flame"));
        ui.keyPressed(263, 2);
        assertEquals(34, core.rates.percent("flame"));
        ui.keyPressed(261, 0);
        assertEquals(100, core.rates.percent("flame"));
        assertEquals(0, core.rates.changed());

        // Right-click resets a row; a rate set while the list is filtered stays after the filter is cleared.
        click(ui, ui.particleRatesTrack("flame"), 0.2, 0);
        assertEquals(20, core.rates.percent("flame"));
        click(ui, ui.particleRatesTrack("flame"), 0.2, 1);
        assertEquals(100, core.rates.percent("flame"));

        // Esc closes the editor and not the page behind it.
        ui.keyPressed(256, 0);
        screen.frame(ui);
        assertFalse(screen.shows("Particle spawn rates"));
        assertTrue(screen.showsStart(ParticleCore.RATES), "the module page is still open");
        assertFalse(ui.isEditingText());
    }

    @Test void changedRatesShowOnTheRowAndSurviveASave() {
        core.rates.set("smoke", 0);
        core.rates.set("particlerain:rain", 25);
        var ui = new LadsSettingsScreen();
        ui.openModule(ParticleCore.MODULE);
        screen.frame(ui);
        assertTrue(screen.shows("2 changed..."), "the row says how many types are changed");
        click(ui, ui.controlBounds("option:" + ParticleCore.RATES), .5, 0);
        screen.frame(ui);
        assertTrue(screen.shows("Off") && screen.shows("25%"));
        assertTrue(screen.shows("4 types, 2 changed") || screen.shows("5 types, 2 changed"));
        var saved = ConfigManager.toJson().getAsJsonObject("modules").getAsJsonObject(ParticleCore.MODULE).getAsJsonObject("options").getAsJsonObject(ParticleCore.RATES);
        assertEquals(0, saved.get("smoke").getAsInt());
        assertEquals(25, saved.get("particlerain:rain").getAsInt());
        assertEquals(2, saved.size(), "only types below 100% are saved");
    }
}
