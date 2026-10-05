package com.thelads.core;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.thelads.core.client.bridge.DefaultGameBridge;
import com.thelads.core.client.bridge.LadsGameBridge;
import com.thelads.core.client.util.ClientPaths;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleSupport;
import com.thelads.core.mods.CoreCatalogExporter;
import com.thelads.core.mods.LoadedMod;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class CoreCatalogExporterTest {
    @TempDir Path dir;
    private ModsMenuTest.OwnershipFixture ownership;
    private final List<Module> modules = List.of(new Module("FPS", "Show FPS."), new Module("DiscordRPC", "Presence."),
        new Module("Lithium", "Wrapper."), new Module("BetterF3", "Wrapper, not installed."), new Module("Exordium", "HUD cache."),
        new Module("PendingThing", "Not connected."), new Module("AppleSkin", "Food overlay."));

    @BeforeEach void setUp() throws Exception {
        ownership = new ModsMenuTest.OwnershipFixture();
        ModuleSupport.registerBuiltIn("FPS", "DiscordRPC");
        ModuleSupport.registerExternal("Lithium", "Lithium", "lithium", true);
        ModuleSupport.registerExternal("BetterF3", "BetterF3", "betterf3", false);
        ModuleSupport.registerUnavailable("Exordium", "Renderer not supported.");
        ModuleSupport.registerExternal("AppleSkin", "AppleSkin", "appleskin", false);
        ModuleSupport.registerBuiltIn("AppleSkin");
    }
    @AfterEach void tearDown() throws Exception {
        ownership.close();
        ClientPaths.setBaseDir(null);
        LadsGameBridge.set(new DefaultGameBridge());
    }

    private static Map<String, JsonObject> byName(JsonObject catalog) {
        Map<String, JsonObject> result = new HashMap<>();
        catalog.getAsJsonArray("modules").forEach(e -> result.put(e.getAsJsonObject().get("name").getAsString(), e.getAsJsonObject()));
        return result;
    }

    @Test void toggleableMatchesTheInGameCardAndExternalIdsAreAlwaysExported() {
        var modules = byName(CoreCatalogExporter.toJson("1.2.0", "26.3", this.modules));
        assertEquals(this.modules.size(), modules.size());
        assertTrue(modules.get("FPS").get("toggleable").getAsBoolean());
        assertEquals("builtIn", modules.get("FPS").get("support").getAsString());
        assertTrue(modules.get("DiscordRPC").get("toggleable").getAsBoolean(), "Discord RPC is a normal module since 1.7.1");
        assertEquals("builtIn", modules.get("DiscordRPC").get("support").getAsString());
        assertFalse(modules.get("Lithium").get("toggleable").getAsBoolean());
        assertEquals("external", modules.get("Lithium").get("support").getAsString());
        assertEquals("lithium", modules.get("Lithium").get("externalModId").getAsString());
        assertEquals("betterf3", modules.get("BetterF3").get("externalModId").getAsString(), "exported even when not installed");
        assertEquals("appleskin", modules.get("AppleSkin").get("externalModId").getAsString(), "a native replacement names the mod it replaces");
        assertTrue(modules.get("AppleSkin").get("toggleable").getAsBoolean());
        assertEquals("unavailable", modules.get("Exordium").get("support").getAsString());
        assertEquals("Renderer not supported.", modules.get("Exordium").get("detail").getAsString());
        assertEquals("pending", modules.get("PendingThing").get("support").getAsString());
        assertFalse(modules.get("PendingThing").get("toggleable").getAsBoolean());
        assertTrue(modules.get("PendingThing").get("externalModId").isJsonNull());
    }

    @Test void aVersionLimitIsTheBuiltInModulesDetail() {
        ModuleSupport.registerBuiltInLimited("FPS", "Minecraft 1.8.9 has no attack cooldown.");
        var fps = byName(CoreCatalogExporter.toJson("1.5.0", "1.8.9", modules)).get("FPS");
        assertEquals("builtIn", fps.get("support").getAsString());
        assertTrue(fps.get("toggleable").getAsBoolean());
        assertEquals("Included in The Lads Client. Changes apply immediately. Minecraft 1.8.9 has no attack cooldown.", fps.get("detail").getAsString());
    }

    @Test void writesAtomicallyOnlyWhenTheRevisionChanges() throws Exception {
        ClientPaths.setBaseDir(dir);
        LadsGameBridge.set(new DefaultGameBridge() {
            @Override public List<LoadedMod> loadedMods() {
                return List.of(ModsFixture.mod("theladscore", "The Lads Core", null, "fabric", false),
                    new LoadedMod("minecraft", "Minecraft", "26.3", null, List.of(), null, List.of(), List.of(), false, "builtin"));
            }
        });
        Path file = dir.resolve(CoreCatalogExporter.FILE_NAME);
        CoreCatalogExporter.exportIfChanged();
        CoreCatalogExporter.flush();
        JsonObject catalog = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        assertEquals(1, catalog.get("schema").getAsInt());
        assertEquals("1.0", catalog.get("coreVersion").getAsString());
        assertEquals("26.3", catalog.get("minecraftVersion").getAsString());
        assertDoesNotThrow(() -> java.time.Instant.parse(catalog.get("writtenAt").getAsString()));
        try (var files = Files.list(dir)) { assertEquals(1, files.count(), "no temporary files are left"); }

        Files.delete(file);
        CoreCatalogExporter.exportIfChanged();
        CoreCatalogExporter.flush();
        assertFalse(Files.exists(file), "an unchanged revision is not rewritten on every tick");
        ModuleSupport.registerBuiltIn("Minimap");
        CoreCatalogExporter.exportIfChanged();
        CoreCatalogExporter.flush();
        assertTrue(Files.exists(file), "a late registration rewrites the catalog");
    }
}
