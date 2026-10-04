package com.thelads.core;

import com.google.gson.JsonParser;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleSupport;
import com.thelads.core.mods.InventorySnapshot;
import com.thelads.core.mods.ModDependencyPlanner;
import com.thelads.core.mods.ModInventoryModel;
import com.thelads.core.mods.ModInventoryModel.Filter;
import com.thelads.core.mods.ModInventoryModel.Row;
import com.thelads.core.mods.ModStateStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ModInventoryModelTest {
    @TempDir Path dir;
    private ModsMenuTest.OwnershipFixture ownership;
    private final List<Module> modules = List.of(new Module("FPS", "Show FPS."), new Module("DiscordRPC", "Presence."),
        new Module("Exordium", "HUD cache."), new Module("PendingThing", "Not connected."), new Module("Lithium", "Wrapper."),
        new Module("AppleSkin", "Food overlay."));
    private static final ModStateStore.State NO_REQUESTS = new ModStateStore.State(Map.of(), null);

    @BeforeEach void registerModules() throws Exception {
        ownership = new ModsMenuTest.OwnershipFixture();
        ModuleSupport.registerBuiltIn("FPS", "DiscordRPC");
        ModuleSupport.registerUnavailable("Exordium", "Renderer not supported on 26.3.");
        ModuleSupport.registerExternal("Lithium", "Lithium", "lithium", true);
        ModuleSupport.registerExternal("AppleSkin", "AppleSkin", "appleskin", false);
        ModuleSupport.registerBuiltIn("AppleSkin");
    }
    @AfterEach void restore() throws Exception { ownership.close(); }

    private ModInventoryModel model(ModStateStore.State state) {
        return ModInventoryModel.build(ModsFixture.LOADED, InventorySnapshot.parse(ModsFixture.INVENTORY), state, modules);
    }
    private static Set<String> keys(List<Row> rows) { return rows.stream().map(Row::key).collect(java.util.stream.Collectors.toSet()); }

    @Test void mergesSnapshotLoadedModsAndLiveLadsModules() {
        var model = model(NO_REQUESTS);
        assertEquals(Set.of("mod/theladscore", "mod/fabric-api", "mod/sodium", "mod/lambdynlights", "mod/myjar", "mod/iris",
            "mod/appleskin", "mod/zoomify", "mod/minecraft", "mod/lithium", "mod/java", "mod/fabricloader", "module/FPS", "module/DiscordRPC",
            "module/Exordium", "module/PendingThing", "module/AppleSkin"), keys(model.rows()),
            "external wrappers stay upstream rows and the launcher's stale module copy is replaced by live state");
        assertEquals("FPS", model.find("module/FPS").name());

        Row sodium = model.find("mod/sodium");
        assertEquals("Sodium", sodium.displayName());
        assertTrue(sodium.loaded() && sodium.requested() && !sodium.restartRequired());
        Row myJar = model.find("mod/myjar");
        assertFalse(myJar.loaded() || myJar.requested() || myJar.restartRequired());
        assertTrue(model.find("mod/iris").restartRequired(), "a pending download is only loaded after a restart");
        assertFalse(model.find("mod/appleskin").restartRequired());

        Row networking = model.find("mod/fabric-networking-api-v1");
        assertEquals("fabric-api", networking.parentId());
        assertEquals("embedded", networking.ownership());
        assertFalse(networking.canToggle());
        assertTrue(networking.blockedReason().contains("Embedded inside Fabric API"));
        Row catconfig = model.find("mod/io_github_lgatodu47_catconfig");
        assertEquals("catconfig-mc", catconfig.parentId());
        assertEquals("theladscore", catconfig.rootId());
        assertTrue(catconfig.loaded());

        Row lithium = model.find("mod/lithium");
        assertEquals("runtime", lithium.ownership(), "loaded, but not a jar the launcher lists");
        assertEquals("Third-party", ModInventoryModel.ownershipLabel(lithium));
        assertTrue(lithium.note().contains("Lads integration: Lithium"));
        assertEquals("Not in the Lads pack for 26.3 (included for 1.21.11). Lads provides its own AppleSkin instead",
            model.find("mod/appleskin").note());
        assertEquals("Unavailable for 26.3", model.statusLabel(model.find("mod/appleskin")));
        assertFalse(model.find("mod/java").canToggle());
        assertEquals(ModInventoryModel.PLATFORM_REASON, model.find("mod/java").blockedReason());
    }

    @Test void filtersIncludingLibrariesAndResetExposesEverything() {
        var model = model(NO_REQUESTS);
        var lads = keys(model.visible(Filter.LADS, ""));
        assertTrue(lads.containsAll(Set.of("mod/theladscore", "module/FPS", "module/Exordium")));
        assertFalse(lads.contains("mod/sodium"));
        var thirdParty = keys(model.visible(Filter.THIRD_PARTY, ""));
        assertEquals(Set.of("mod/fabric-api", "mod/sodium", "mod/lambdynlights", "mod/myjar", "mod/iris", "mod/appleskin", "mod/zoomify", "mod/lithium"), thirdParty);
        assertTrue(keys(model.visible(Filter.DISABLED, "")).contains("mod/myjar"));
        assertFalse(keys(model.visible(Filter.ENABLED, "")).contains("mod/appleskin"), "unavailable is neither enabled nor disabled");
        assertFalse(keys(model.visible(Filter.DISABLED, "")).contains("mod/appleskin"));

        var libraries = model.visible(Filter.LIBRARIES, "");
        assertEquals(Set.of("mod/theladscore", "mod/fabric-api"), keys(libraries), "embedded libraries never disappear");
        Row core = model.find("mod/theladscore");
        assertTrue(model.autoExpanded(core, Filter.LIBRARIES, ""), "parents shown for a library child open");
        assertTrue(model.autoExpanded(model.find("mod/fabric-api"), Filter.LIBRARIES, ""), "a library's own libraries unfold too");
        assertFalse(model.autoExpanded(model.find("mod/fabric-api"), Filter.ENABLED, ""), "Enabled does not unfold every jar-in-jar");
        assertTrue(keys(model.visible(Filter.DISABLED, "")).contains("mod/zoomify"));
        assertEquals(5, model.counts().get(Filter.LIBRARIES));
        assertEquals(model.flatten().size(), model.counts().get(Filter.ALL));

        assertEquals(model.rows(), model.visible(Filter.ALL, ""), "reset shows the full inventory");
    }

    @Test void searchCoversUpstreamNameIdAndChildren() {
        var model = model(NO_REQUESTS);
        assertEquals(Set.of("mod/iris"), keys(model.visible(Filter.ALL, "iris shaders")));
        assertEquals(Set.of("mod/sodium"), keys(model.visible(Filter.ALL, "SODIUM")));
        assertEquals(Set.of("mod/fabric-api"), keys(model.visible(Filter.ALL, "fabric-networking")));
        assertTrue(model.autoExpanded(model.find("mod/fabric-api"), Filter.ALL, "fabric-networking"));
        assertEquals(Set.of("mod/theladscore"), keys(model.visible(Filter.ALL, "lgatodu47")));
        assertTrue(model.autoExpanded(model.find("mod/theladscore"), Filter.ALL, "lgatodu47"));
    }

    @Test void explicitRequestsOverrideDiskStateIncludingByProjectId() {
        var model = model(new ModStateStore.State(Map.of("sodium-old-id", new ModStateStore.Request(false, "AANobbMI"),
            "lithium", new ModStateStore.Request(false, null), "myjar", new ModStateStore.Request(true, null)), null));
        Row sodium = model.find("mod/sodium");
        assertFalse(sodium.requested());
        assertTrue(sodium.restartRequired());
        assertTrue(model.find("mod/myjar").requested() && model.find("mod/myjar").restartRequired());
        assertTrue(keys(model.visible(Filter.DISABLED, "")).contains("mod/sodium"));
        Row lithium = model.find("mod/lithium");
        assertTrue(lithium.requested() && !lithium.restartRequired(), "no jar in mods: the launcher could never apply the request");
        assertFalse(keys(model.visible(Filter.DISABLED, "")).contains("mod/lithium"));
    }

    // REQ-C-1, core-java-4: the counts line is the launcher's ModInventoryCounts for the same snapshot, and Enabled/Disabled
    // list mods and Lads modules only, never the platform or embedded libraries.
    @Test void countsAndEnabledFiltersMatchTheLauncher() {
        var model = model(NO_REQUESTS);
        var files = model.fileCounts();
        assertEquals(new ModInventoryModel.FileCounts(4, 1, 1, 1, 5, 4), files,
            "enabled/disabled are the jars in mods (theladscore, fabric-api, sodium, lambdynlights / myjar); iris is pending, "
                + "appleskin unavailable; lithium, loaded at runtime, is no jar; Lads modules and embedded libraries apart");
        assertEquals("4 enabled · 1 disabled · 1 pending · 1 unavailable · 5 Lads modules · 4 embedded libraries", files.text());
        for (Filter filter : List.of(Filter.ENABLED, Filter.DISABLED))
            for (Row row : model.flatten())
                if (model.matches(row, filter))
                    assertFalse(row.embedded() || "platform".equals(row.ownership()), filter + " must not list " + row.key());
        assertEquals(keys(model.visible(Filter.ENABLED, "")).size(), model.counts().get(Filter.ENABLED),
            "the Enabled count is the rows the Enabled filter shows");
        assertTrue(keys(model.visible(Filter.ENABLED, "")).containsAll(Set.of("mod/theladscore", "mod/sodium", "mod/iris")));
        var json = JsonParser.parseString(model.toJson("test")).getAsJsonObject().getAsJsonObject("fileCounts");
        assertEquals(4, json.get("enabled").getAsInt());
        assertEquals(1, json.get("disabled").getAsInt());
    }

    // REQ-C-2: a mod another entry depends on (or whose provided id it depends on) is a dependency, badge or not.
    @Test void librariesAndDependenciesMatchesTheLauncherFilter() {
        var snapshot = InventorySnapshot.parse("""
            {"minecraftVersion": "26.3", "entries": [
              {"id": "sodium", "ownership": "pack", "status": "installed", "requestedEnabled": true, "canToggle": true, "depends": ["minecraft"]},
              {"id": "iris", "ownership": "pack", "status": "installed", "requestedEnabled": true, "canToggle": true,
               "depends": ["minecraft", "sodium >=0.6"]},
              {"id": "fabric-language-kotlin", "ownership": "pack", "status": "installed", "requestedEnabled": true, "canToggle": true,
               "provides": ["kotlin"]},
              {"id": "uses-kotlin", "ownership": "user", "status": "installed", "requestedEnabled": true, "canToggle": true, "depends": ["kotlin"]},
              {"id": "zoomify", "ownership": "pack", "status": "installed", "requestedEnabled": true, "canToggle": true},
              {"id": "minecraft", "ownership": "platform", "status": "installed", "requestedEnabled": true, "canToggle": false}]}""");
        var model = ModInventoryModel.build(List.of(), snapshot, NO_REQUESTS, List.of());
        assertEquals("Libraries & dependencies", Filter.LIBRARIES.label());
        assertEquals(Set.of("mod/sodium", "mod/fabric-language-kotlin"), keys(model.visible(Filter.LIBRARIES, "")),
            "required by iris / provides what uses-kotlin requires; the platform is not a library");
    }

    // core-java-5: a saved key cannot make an entry that is not available for this version load at the next launch.
    @Test void unavailableAndRetiredEntriesAreNeverRequested() {
        var snapshot = InventorySnapshot.parse("""
            {"minecraftVersion": "26.3", "entries": [
              {"id": "appleskin", "ownership": "pack", "status": "unavailable", "requestedEnabled": false, "canToggle": false,
               "projectId": "EsAfCjCV"},
              {"id": "gone", "ownership": "retired", "status": "retiredCopy", "filePath": "C:/game/mods/gone.jar", "enabledOnDisk": true,
               "requestedEnabled": true, "canToggle": false}]}""");
        var model = ModInventoryModel.build(List.of(), snapshot, new ModStateStore.State(Map.of(
            "appleskin-121", new ModStateStore.Request(true, "EsAfCjCV"), "gone", new ModStateStore.Request(true, null)), null), List.of());
        for (String key : List.of("mod/appleskin", "mod/gone")) {
            Row row = model.find(key);
            assertFalse(row.available() || row.requested() || row.restartRequired(), key);
        }
        assertTrue(model.visible(Filter.ENABLED, "").isEmpty() && model.visible(Filter.DISABLED, "").isEmpty());
    }

    // Mods another mod loads at runtime (Essential's libraries) are not jars in mods: third-party, never toggled here,
    // and nested under the mod Fabric names as their parent.
    @Test void modsLoadedAtRuntimeAreThirdPartyAndSitUnderTheirParent() {
        var snapshot = InventorySnapshot.parse("""
            {"minecraftVersion": "26.3", "entries": [
              {"id": "essential-container", "ownership": "pack", "status": "installed", "filePath": "C:/game/mods/essential.jar",
               "enabledOnDisk": true, "requestedEnabled": true, "canToggle": true,
               "children": [{"id": "essential-loader", "ownership": "embedded", "status": "embedded", "isLibrary": true}]}]}""");
        var loaded = List.of(ModsFixture.mod("essential-container", "Essential Container", null, "fabric", false),
            ModsFixture.mod("essential-loader", "Essential Loader", "essential-container", "fabric", false),
            ModsFixture.mod("essential", "Essential", null, "fabric", false, "elementa"),
            ModsFixture.mod("elementa", "Elementa", "essential", "fabric", false),
            ModsFixture.mod("vigilance", "Vigilance", "essential-loader", "fabric", false));
        var model = ModInventoryModel.build(loaded, snapshot,
            new ModStateStore.State(Map.of("essential", new ModStateStore.Request(false, null)), null), List.of());
        Row essential = model.find("mod/essential");
        assertEquals("runtime", essential.ownership());
        assertEquals("Third-party", ModInventoryModel.ownershipLabel(essential));
        assertFalse(essential.canToggle());
        assertEquals(ModInventoryModel.RUNTIME_REASON, essential.blockedReason());
        assertTrue(essential.requested() && !essential.restartRequired(), "a saved key for it is never applied, so it is ignored");
        assertEquals("essential", model.find("mod/elementa").parentId());
        assertEquals("essential-loader", model.find("mod/vigilance").parentId());
        assertEquals("essential-container", model.find("mod/vigilance").rootId());
        assertEquals(1, model.fileCounts().enabled(), "only the jar in mods counts");
        assertTrue(keys(model.visible(Filter.THIRD_PARTY, "")).contains("mod/essential"));
    }

    @Test void missingSnapshotShowsLoadedModsWithTheNotice() throws Exception {
        var model = ModInventoryModel.build(ModsFixture.LOADED, InventorySnapshot.read(dir), NO_REQUESTS, modules);
        assertEquals(InventorySnapshot.MISSING_NOTICE, model.notice());
        assertEquals("1.0", model.minecraftVersion(), "without a snapshot the loaded game version is used");
        assertEquals("core", model.find("mod/theladscore").ownership());
        assertEquals("platform", model.find("mod/minecraft").ownership());
        assertTrue(model.find("mod/sodium").canToggle());
        assertNotNull(model.find("mod/fabric-api-base"));

        Files.createDirectories(dir.resolve(".lads-mod-cache"));
        Files.writeString(dir.resolve(".lads-mod-cache").resolve("inventory.json"), "{\"entries\": [{\"displayName\": \"no id\"}]}");
        var damaged = ModInventoryModel.build(ModsFixture.LOADED, InventorySnapshot.read(dir),
            new ModStateStore.State(Map.of(), "lads-mod-state.json is damaged"), modules);
        assertTrue(damaged.notice().contains("could not be read"));
        assertTrue(damaged.notice().contains("lads-mod-state.json is damaged"));
    }

    @Test void unavailableLadsModulesExplainWhy() {
        var model = model(NO_REQUESTS);
        Row exordium = model.find("module/Exordium");
        assertFalse(exordium.canToggle() || exordium.available());
        assertEquals("Renderer not supported on 26.3.", exordium.blockedReason());
        assertEquals(ModuleSupport.get("PendingThing").description(), model.find("module/PendingThing").blockedReason());
        assertTrue(model.find("module/DiscordRPC").canToggle());
        assertTrue(model.find("module/FPS").canToggle());
    }

    @Test void planUsesTheInventoryGraph() {
        var model = model(NO_REQUESTS);
        var plan = model.plan(List.of("fabric-api"), false);
        assertEquals(Set.of("lambdynlights", "theladscore"), Set.copyOf(plan.alsoDisable()),
            "launcher dependencies carry version predicates; myjar is already disabled");
        assertTrue(plan.warnings().contains(ModDependencyPlanner.CORE_WARNING));
        assertEquals(List.of(ModDependencyPlanner.CORE_WARNING), plan.warnings(),
            "the launcher knows iris's dependencies (dependenciesKnown), zoomify stays disabled");
        assertTrue(model.plan(List.of("zoomify"), true).warnings().stream().anyMatch(w -> w.contains("not downloaded yet")));
        assertTrue(model.plan(List.of("myjar"), true).alsoEnable().isEmpty());
        assertFalse(model.plan(List.of("minecraft"), false).blockers().isEmpty());
        assertEquals(Map.of("fabric-api", "P7dR8mSH", "sodium", "AANobbMI"), model.projectIds());
    }

    @Test void problemJarsOnDiskKeepTheirStateAndPlanLikeTheLauncher() {
        var snapshot = InventorySnapshot.parse("""
            {"minecraftVersion": "26.3", "entries": [
              {"id": "oldlib", "displayName": "Old Lib", "ownership": "user", "status": "unsupported", "requestedEnabled": true,
               "canToggle": true, "depends": ["minecraft 1.21.1"]},
              {"id": "uses-old", "displayName": "Uses Old", "ownership": "user", "status": "installed", "requestedEnabled": true,
               "canToggle": true, "depends": ["oldlib >=2.0"]},
              {"id": "broken.jar", "ownership": "user", "status": "invalid", "requestedEnabled": true, "canToggle": false,
               "toggleBlockedReason": "Not a loadable Fabric mod"},
              {"id": "gone", "ownership": "retired", "status": "retiredCopy", "requestedEnabled": true, "canToggle": false}]}""");
        var model = ModInventoryModel.build(List.of(), snapshot, NO_REQUESTS, modules);
        assertEquals(Set.of("mod/oldlib", "mod/uses-old", "mod/broken.jar"), keys(model.visible(Filter.ENABLED, "")),
            "unsupported and invalid jars are real enabled files; a retired copy leaves at the next launch");
        assertTrue(model.find("mod/oldlib").restartRequired());
        assertFalse(model.find("mod/broken.jar").restartRequired(), "Fabric never loads an invalid jar, so a restart changes nothing");
        assertEquals(List.of("uses-old"), model.plan(List.of("oldlib"), false).alsoDisable(),
            "an unsupported jar still provides its id to the planner, as in the launcher");
        assertTrue(model.plan(List.of("oldlib"), true).blockers().isEmpty());
    }

    @Test void qaJsonListsEveryRowAndFilterCount() {
        var model = model(NO_REQUESTS);
        var json = JsonParser.parseString(model.toJson("test")).getAsJsonObject();
        assertEquals(model.flatten().size(), json.getAsJsonArray("rows").size());
        assertEquals(5, json.getAsJsonObject("counts").get(Filter.LIBRARIES.label()).getAsInt());
        var first = json.getAsJsonArray("rows").get(0).getAsJsonObject();
        assertTrue(first.has("parentId") && first.has("restartRequired") && first.has("requested") && first.has("loaded"));
    }
}
