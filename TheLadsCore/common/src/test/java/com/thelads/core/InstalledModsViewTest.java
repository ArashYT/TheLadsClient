package com.thelads.core;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.thelads.core.client.bridge.DefaultGameBridge;
import com.thelads.core.client.bridge.LadsGameBridge;
import com.thelads.core.client.gui.LadsSettingsScreen;
import com.thelads.core.client.util.ClientPaths;
import com.thelads.core.config.ConfigManager;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.ModuleSupport;
import com.thelads.core.mods.InventorySnapshot;
import com.thelads.core.mods.LoadedMod;
import com.thelads.core.mods.ModDependencyPlanner;
import com.thelads.core.mods.ModInventoryModel;
import com.thelads.core.mods.ModStateStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Headless render of the in-game Installed mods view against a launcher snapshot and a fake Fabric loader. */
class InstalledModsViewTest {
    @TempDir Path dir;
    private ModsMenuTest.OwnershipFixture ownership;
    private Module fps;
    private boolean fpsEnabled;
    private long fpsModified;

    static final class Recorder extends LadsGraphicsTest.MockGraphics {
        record Text(String text, int x, int y, boolean centered) {}
        final List<Text> texts = new ArrayList<>();
        Recorder() { height = 8000; }
        @Override public void drawText(String text, int x, int y, int color, boolean shadow) {
            super.drawText(text, x, y, color, shadow); texts.add(new Text(text, x, y, false));
        }
        @Override public void drawCenteredText(String text, int x, int y, int color, boolean shadow) {
            super.drawCenteredText(text, x, y, color, shadow); texts.add(new Text(text, x, y, true));
        }
        void render(LadsSettingsScreen menu) { texts.clear(); drawCalls.clear(); menu.render(this, -1, -1); }
        boolean shows(String prefix) { return texts.stream().anyMatch(t -> t.text().startsWith(prefix)); }
        Text row(String name) {
            return texts.stream().filter(t -> !t.centered() && t.text().startsWith(name)).findFirst()
                .orElseThrow(() -> new AssertionError("Row not rendered: " + name));
        }
        /** The button drawn on the same row as the row's name, or null. */
        int[] rowButton(String name, String label) {
            Text row = row(name);
            return texts.stream().filter(t -> t.centered() && t.text().equals(label) && Math.abs(t.y() - row.y()) <= 6)
                .findFirst().map(t -> new int[]{t.x(), t.y() + 3}).orElse(null);
        }
        /** Second line of a row: ownership, id, status, loaded and next-launch state (after "Restart required"). */
        List<String> rowDetails(String name) {
            Text row = row(name);
            return texts.stream().filter(t -> !t.centered() && t.y() == row.y() + 13).map(Text::text).toList();
        }
        int[] button(String label) {
            return texts.stream().filter(t -> t.centered() && t.text().equals(label)).findFirst()
                .map(t -> new int[]{t.x(), t.y() + 3}).orElseThrow(() -> new AssertionError("Missing button: " + label));
        }
        void click(LadsSettingsScreen menu, int[] point) {
            assertNotNull(point, "button not rendered");
            assertTrue(menu.mouseClicked(point[0], point[1], 0));
            render(menu);
        }
    }

    @BeforeEach void setUp() throws Exception {
        ownership = new ModsMenuTest.OwnershipFixture();
        ConfigManager.setTestConfigFile(dir.resolve("config.json").toFile());
        ClientPaths.setBaseDir(dir);
        LadsGameBridge.set(new DefaultGameBridge() {
            @Override public List<LoadedMod> loadedMods() { return ModsFixture.LOADED; }
        });
        fps = ModuleManager.getInstance().getModule("FPS");
        fpsEnabled = fps.isEnabled(); fpsModified = fps.getLastModified();
        for (Module module : ModuleManager.getInstance().getModules())
            ModuleSupport.registerUnavailable(module.getName(), "Test fixture has no integration");
        ModuleSupport.registerBuiltIn("FPS");
        ModuleSupport.registerExternal("Lithium", "Lithium", "lithium", true);
        ModsFixture.writeInventory(dir);
    }
    @AfterEach void tearDown() throws Exception {
        fps.setEnabled(fpsEnabled); fps.setLastModified(fpsModified);
        ownership.close();
        ConfigManager.setTestConfigFile(null);
        ClientPaths.setBaseDir(null);
        LadsGameBridge.set(new DefaultGameBridge());
    }

    private LadsSettingsScreen open(Recorder g) {
        var menu = new LadsSettingsScreen();
        g.render(menu);
        g.click(menu, g.button("Installed mods"));
        assertTrue(menu.isModsViewOpen());
        return menu;
    }
    private JsonObject requests() throws Exception {
        return JsonParser.parseString(Files.readString(dir.resolve(ModStateStore.FILE_NAME))).getAsJsonObject().getAsJsonObject("mods");
    }

    @Test void everyInventoryEntryIsRenderedAndEscReturnsToTheCatalog() throws Exception {
        var g = new Recorder();
        var menu = open(g);
        var model = ModInventoryModel.load();
        for (var row : model.rows()) g.row(row.displayName());
        assertTrue(g.rowDetails("Sodium").getFirst().contains("sodium · Installed · Loaded · Next launch: On"));
        assertTrue(g.rowDetails("My Jar").getFirst().contains("User · myjar · Disabled · Not loaded · Next launch: Off"));
        assertTrue(g.rowDetails("Iris Shaders").contains("Restart required"));
        assertTrue(g.shows("Not in the Lads pack for 26.3 (included for 1.21.11)."), "unavailable entries explain the version limitation");
        assertNull(g.rowButton("Minecraft  26.3", "ON"), "platform components have no toggle");

        g.click(menu, g.button("Libraries & dependencies"));
        for (String child : List.of("CatConfig MC", "CatConfig  1.0", "Fabric API Base", "Fabric Networking API (v1)")) g.row(child);
        assertNotNull(g.rowButton("CatConfig MC", "Disable The Lads Core..."), "embedded libraries offer the parent operation");
        assertFalse(g.shows("Sodium"));
        g.click(menu, g.button("Reset"));
        g.row("Sodium");

        menu.keyPressed(256, 0);
        assertFalse(menu.isModsViewOpen());
        g.render(menu);
        assertTrue(g.shows("MODS / MAKE IT YOURS"));
        assertEquals(List.of("FPS"), menu.visibleModuleNames(), "the native catalog stays native-only");
        assertFalse(Files.exists(dir.resolve(ModStateStore.FILE_NAME)), "browsing never writes requests");
        assertFalse(Files.exists(dir.resolve("config.json")), "browsing never rewrites module settings");
    }

    @Test void countsLineShowsTheLaunchersFileCounts() {
        var g = new Recorder();
        open(g);
        assertTrue(g.shows("4 enabled · 1 disabled · 1 pending · 1 unavailable · "), "the launcher's counts for the same snapshot");
    }

    @Test void unavailableEntriesSayNotAvailableInsteadOfANextLaunchState() throws Exception {
        new ModStateStore(dir).setRequested(java.util.Map.of("appleskin", true), java.util.Map.of());
        var g = new Recorder();
        open(g);
        String details = String.join(" ", g.rowDetails("AppleSkin"));
        assertTrue(details.contains("Unavailable for 26.3 · Not loaded · Not available for 26.3"), details);
        assertFalse(details.contains("Next launch"), details);
        assertNotNull(g.rowButton("AppleSkin", "OFF"), "a saved enabled key does not turn it on");
    }

    @Test void thirdPartyToggleWritesANextLaunchRequest() throws Exception {
        var g = new Recorder();
        var menu = open(g);
        g.click(menu, g.rowButton("Sodium", "ON"));
        JsonObject sodium = requests().getAsJsonObject("sodium");
        assertFalse(sodium.get("enabled").getAsBoolean());
        assertEquals("game", sodium.get("source").getAsString());
        assertEquals("AANobbMI", sodium.get("projectId").getAsString());
        assertNotNull(g.rowButton("Sodium", "OFF"));
        assertEquals("Restart required", g.rowDetails("Sodium").getFirst());
        assertTrue(g.rowDetails("Sodium").get(1).contains("Loaded · Next launch: Off"), "loaded state stays separate from the request");
        assertTrue(g.shows("Saved for the next launch"));
    }

    @Test void cascadeIsConfirmedAsAWholeAndCoreDisableWarns() throws Exception {
        var g = new Recorder();
        var menu = open(g);
        g.click(menu, g.rowButton("Fabric API", "ON"));
        assertTrue(g.shows("Disable Fabric API at the next launch?"));
        assertTrue(g.shows("Also disable (they need it): The Lads Core, LambDynamicLights"));
        assertTrue(g.shows("The in-game Lads menu and all Lads features will be absent"));
        assertFalse(Files.exists(dir.resolve(ModStateStore.FILE_NAME)), "nothing is written before confirmation");
        menu.keyPressed(256, 0);
        assertTrue(menu.isModsViewOpen(), "Esc cancels the confirmation first");
        g.render(menu);
        assertFalse(g.shows("Disable Fabric API at the next launch?"));

        g.click(menu, g.rowButton("Fabric API", "ON"));
        g.click(menu, g.button("Confirm"));
        JsonObject mods = requests();
        for (String id : List.of("fabric-api", "lambdynlights", ModDependencyPlanner.CORE_ID))
            assertFalse(mods.getAsJsonObject(id).get("enabled").getAsBoolean(), id);
        assertFalse(mods.has("myjar"), "only the plan's ids get explicit keys");
    }

    @Test void coreToggleAloneAlsoAsksWithTheWarning() throws Exception {
        var g = new Recorder();
        var menu = open(g);
        g.click(menu, g.rowButton("The Lads Core", "ON"));
        assertTrue(g.shows("The in-game Lads menu and all Lads features will be absent"));
        g.click(menu, g.button("Confirm"));
        assertFalse(requests().getAsJsonObject(ModDependencyPlanner.CORE_ID).get("enabled").getAsBoolean());
    }

    @Test void qaRequestProbeRecordsTheSameCascadeAsAConfirmedToggle() throws Exception {
        assertEquals("fabric-api -> false", ModInventoryModel.recordRequestForQa("fabric-api:false", dir, ModInventoryModel.load()));
        JsonObject mods = requests();
        for (String id : List.of("fabric-api", "lambdynlights", ModDependencyPlanner.CORE_ID)) {
            assertFalse(mods.getAsJsonObject(id).get("enabled").getAsBoolean(), id);
            assertEquals("game", mods.getAsJsonObject(id).get("source").getAsString(), id);
        }
        assertFalse(mods.has("myjar"), "only the plan's ids get explicit keys");
        assertThrows(IllegalArgumentException.class, () -> ModInventoryModel.recordRequestForQa("sodium:maybe", dir, ModInventoryModel.load()));
        assertThrows(IllegalStateException.class, () -> ModInventoryModel.recordRequestForQa("minecraft:false", dir, ModInventoryModel.load()));
    }

    @Test void failedWriteShowsAnErrorAndTheSavedState() throws Exception {
        Files.createDirectories(dir.resolve(ModStateStore.FILE_NAME).resolve("blocker"));
        var g = new Recorder();
        var menu = open(g);
        g.click(menu, g.rowButton("Sodium", "ON"));
        assertTrue(g.shows("Could not save the mod request"));
        assertNotNull(g.rowButton("Sodium", "ON"), "the row is restored from the file");
        assertTrue(Files.isDirectory(dir.resolve(ModStateStore.FILE_NAME).resolve("blocker")));
    }

    @Test void ladsModulesToggleImmediatelyThroughTheModuleConfig() {
        var g = new Recorder();
        var menu = open(g);
        boolean before = fps.isEnabled();
        g.click(menu, g.rowButton("FPS", before ? "ON" : "OFF"));
        assertEquals(!before, fps.isEnabled());
        assertTrue(Files.exists(dir.resolve("config.json")));
        assertFalse(Files.exists(dir.resolve(ModStateStore.FILE_NAME)), "native modules need no next-launch request");
        var exordium = ModuleManager.getInstance().getModule("Exordium");
        boolean exordiumBefore = exordium.isEnabled();
        g.click(menu, g.rowButton("Exordium", exordiumBefore ? "ON" : "OFF"));
        assertEquals(exordiumBefore, exordium.isEnabled(), "an unavailable Lads module cannot be toggled");
        assertTrue(g.shows("Test fixture has no integration"), "unavailable Lads modules explain why");
    }

    @Test void ctrlFSearchesTheModsViewOnly() {
        var g = new Recorder();
        var menu = open(g);
        menu.keyPressed(70, 2);
        "iris shaders".codePoints().forEach(menu::charTyped);
        g.render(menu);
        assertEquals("", menu.getSearchQuery(), "the native catalog search is untouched");
        g.row("Iris Shaders");
        assertFalse(g.shows("Sodium"));
        menu.keyPressed(256, 0);
        menu.keyPressed(256, 0);
        assertFalse(menu.isModsViewOpen());
    }

    @Test void moduleSettingsReturnToTheModsViewOnlyWhenOpenedFromIt() {
        var g = new Recorder();
        var menu = open(g);
        g.click(menu, g.rowButton("FPS", "Settings"));
        assertTrue(g.shows("MODULE SETTINGS"));
        menu.keyPressed(256, 0);
        assertTrue(menu.isModsViewOpen(), "Esc returns to the Installed mods view");

        g.render(menu);
        g.click(menu, g.rowButton("FPS", "Settings"));
        menu.keyPressed(70, 2); // Ctrl+F leaves that detail for the catalog search
        menu.keyPressed(256, 0);
        menu.openModule("FPS");
        menu.keyPressed(256, 0);
        assertFalse(menu.isModsViewOpen(), "a detail opened from the catalog goes back to the catalog");
    }

    @Test void settingsLinkOpensUpstreamSettingsOnlyWhenWired() {
        var g = new Recorder();
        var menu = open(g);
        assertNull(g.rowButton("Sodium", "Settings"), "hidden without a settings route (1.21.1)");
        List<String> opened = new ArrayList<>();
        menu.setOnOpenModSettings(opened::add);
        g.render(menu);
        g.click(menu, g.rowButton("Sodium", "Settings"));
        assertEquals(List.of("sodium"), opened);
    }

    @Test void smallWindowsKeepRowsReachable() {
        for (int[] size : new int[][]{{320, 180}, {427, 240}, {640, 360}}) {
            var g = new Recorder(); g.width = size[0]; g.height = size[1];
            var menu = new LadsSettingsScreen(); menu.openMods(); g.render(menu);
            assertNotNull(g.rowButton("The Lads Core", "ON"), java.util.Arrays.toString(size));
        }
    }

    @Test void missingSnapshotShowsLoadedModsAndTheNotice() throws Exception {
        Files.delete(InventorySnapshot.file(dir));
        var g = new Recorder();
        open(g);
        assertTrue(g.shows(InventorySnapshot.MISSING_NOTICE));
        g.row("Sodium");
        g.row("The Lads Core");
    }

    @Test void launcherChangesAreReReadWhileTheViewIsOpen() throws Exception {
        var g = new Recorder();
        var menu = open(g);
        new ModStateStore(dir).setRequested(java.util.Map.of("sodium", false), java.util.Map.of());
        g.render(menu);
        assertNotNull(g.rowButton("Sodium", "ON"), "re-read at most every 2 seconds");
        Thread.sleep(2100);
        g.render(menu);
        assertNotNull(g.rowButton("Sodium", "OFF"));
    }
}
