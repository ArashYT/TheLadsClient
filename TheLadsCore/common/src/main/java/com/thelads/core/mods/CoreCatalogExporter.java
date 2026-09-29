package com.thelads.core.mods;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.thelads.core.client.bridge.LadsGameBridge;
import com.thelads.core.client.gui.LadsSettingsScreen;
import com.thelads.core.client.util.ClientPaths;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.ModuleSupport;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Writes {@code <gameDir>/lads-core-catalog.json} so the launcher can list every Lads module with this version's real
 * ownership. Integrations register late (Minimap on the first tick), so callers check it from a tick hook and it is
 * rewritten whenever ModuleSupport.revision() changed. "enabled" is informational; thelads_config.json stays the truth.
 */
public final class CoreCatalogExporter {
    public static final String FILE_NAME = "lads-core-catalog.json";
    private static final Logger LOG = LoggerFactory.getLogger("TheLadsCore");
    private static long writtenRevision = Long.MIN_VALUE;

    private CoreCatalogExporter() {}

    /** Cheap when nothing changed; a failed write is logged once per revision instead of on every tick. */
    public static void exportIfChanged() {
        long revision = ModuleSupport.revision();
        if (revision == writtenRevision) return;
        writtenRevision = revision;
        Path file = ClientPaths.getBaseDir().resolve(FILE_NAME);
        try {
            write(file, loadedVersion(ModDependencyPlanner.CORE_ID), loadedVersion("minecraft"), ModuleManager.getInstance().getModules());
        } catch (IOException e) {
            LOG.error("Could not write {} for The Lads Launcher; its Mods page keeps the previous Lads module list: {}", file, e.toString());
        }
    }

    public static void write(Path file, String coreVersion, String minecraftVersion, Collection<Module> modules) throws IOException {
        ModStateStore.writeAtomically(file, new GsonBuilder().setPrettyPrinting().serializeNulls().create()
            .toJson(toJson(coreVersion, minecraftVersion, modules)));
    }

    public static JsonObject toJson(String coreVersion, String minecraftVersion, Collection<Module> modules) {
        JsonObject root = new JsonObject();
        root.addProperty("schema", 1);
        root.addProperty("coreVersion", coreVersion);
        root.addProperty("minecraftVersion", minecraftVersion);
        root.addProperty("writtenAt", Instant.now().truncatedTo(ChronoUnit.SECONDS).toString());
        JsonArray array = new JsonArray();
        for (Module module : modules) {
            String name = module.getName();
            var status = ModuleSupport.get(name);
            JsonObject o = new JsonObject();
            o.addProperty("name", name);
            o.addProperty("description", module.getDescription());
            o.addProperty("category", LadsSettingsScreen.categoryOf(module));
            o.addProperty("support", ModuleSupport.support(name));
            o.addProperty("label", status.label());
            o.addProperty("detail", status.description());
            o.addProperty("externalModId", ModuleSupport.externalModId(name));
            o.addProperty("enabled", module.isEnabled());
            o.addProperty("toggleable", ModuleSupport.isToggleable(name));
            array.add(o);
        }
        root.add("modules", array);
        return root;
    }

    private static String loadedVersion(String id) {
        return LadsGameBridge.get().loadedMods().stream().filter(mod -> mod.id().equals(id)).map(LoadedMod::version).findFirst().orElse(null);
    }
}
