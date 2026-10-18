package com.thelads.core.v1_20_1.compat;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Is another mod installed? Answers for plain Fabric and for Forge under Sinytra Connector (Zero SMP), where a Forge jar may
 * not be a Fabric mod at all. Every Forge class is reached by reflection, so nothing here needs Forge at compile time and the
 * same jar works on both loaders.
 *
 * <p>Order of checks: Fabric loader, Forge's {@code ModList} (available once mods are constructed), and Forge's
 * {@code LoadingModList} (available from mod discovery on, so mixin config plugins can use it before any class is
 * transformed). Results that say "installed" are cached; "not installed" is not, because the loader lists fill in during
 * startup.
 */
public final class ModPresence1201 {
    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore-ModPresence");
    private static final Map<String, Boolean> INSTALLED = new ConcurrentHashMap<>();
    private static final Map<String, String> SOURCE = new ConcurrentHashMap<>();
    private static volatile Boolean forge;
    private static volatile boolean modListReady;
    private static volatile java.util.List<String> jarNames;

    private ModPresence1201() {}

    /** True when any of the ids is installed under either loader. */
    public static boolean loaded(String... ids) {
        for (String id : ids) if (loaded(id)) return true;
        return false;
    }

    /** QA/benchmark switch (1.9.12 perf lane): -Dthelads.embeddedOff=<original mod id>,... counts those mods as installed, so the
     *  embedded copies stand down exactly as they do next to the original jar. */
    private static final java.util.Set<String> EMBEDDED_OFF =
        java.util.Set.copyOf(java.util.Arrays.asList(System.getProperty("thelads.embeddedOff", "").split(",")));

    public static boolean loaded(String id) {
        if (id == null || id.isEmpty()) return false;
        if (EMBEDDED_OFF.contains(id)) return true;
        Boolean known = INSTALLED.get(id);
        if (known != null) return known;
        String where = where(id);
        if (where == null) {
            if (isModListReady()) {
                INSTALLED.put(id, Boolean.FALSE);
            }
            return false;
        }
        INSTALLED.put(id, Boolean.TRUE);
        SOURCE.put(id, where);
        LOGGER.info("Mod '{}' is installed ({})", id, where);
        return true;
    }

    private static boolean isModListReady() {
        if (modListReady) return true;
        try {
            if (!FabricLoader.getInstance().isModLoaded("minecraft")) return false;
        } catch (RuntimeException | LinkageError ignored) {
            return false;
        }
        if (onForge()) {
            try {
                Class<?> loader = Class.forName("net.minecraftforge.fml.loading.FMLLoader");
                if (loader.getMethod("getLoadingModList").invoke(null) == null) return false;
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                return false;
            }
        }
        modListReady = true;
        return true;
    }

    /** "fabric", "forge-modlist" or "forge-discovery", or null when the mod is not installed. */
    public static String where(String id) {
        String cached = SOURCE.get(id);
        if (cached != null) return cached;
        try {
            if (FabricLoader.getInstance().isModLoaded(id)) return "fabric";
        } catch (RuntimeException | LinkageError ignored) {
            // The loader is not up yet (a very early mixin plugin): fall through to Forge.
        }
        String forge = forgeWhere(id);
        return forge != null ? forge : folderWhere(id);
    }

    /** True when running on Forge (including Forge with Sinytra Connector). */
    public static boolean onForge() {
        Boolean value = forge;
        if (value == null) {
            value = classExists("net.minecraftforge.fml.loading.FMLLoader") || classExists("net.minecraftforge.fml.ModList");
            forge = value;
        }
        return value;
    }

    public static boolean forge() {
        return onForge();
    }

    /** True when Sinytra Connector is the one running Fabric mods. */
    public static boolean underConnector() {
        return classExists("dev.su5ed.sinytra.connector.loader.ConnectorLoaderMod")
            || classExists("dev.su5ed.sinytra.connector.loader.ConnectorEarlyLoader");
    }

    /**
     * Last resort on Forge when neither Forge list can be read: a jar in the mods folder whose name starts with the id (letters and
     * digits only, case-insensitive; 'dynamic_fps' matches 'dynamic-fps-3.11.4+minecraft-1.20.0-forge.jar'). Never used on plain Fabric.
     */
    private static String folderWhere(String id) {
        if (!onForge()) return null;
        java.util.List<String> names = jarNames;
        if (names == null) {
            names = new java.util.ArrayList<>();
            try {
                java.nio.file.Path mods = FabricLoader.getInstance().getGameDir().resolve("mods");
                try (java.util.stream.Stream<java.nio.file.Path> files = java.nio.file.Files.list(mods)) {
                    files.map(p -> p.getFileName().toString().toLowerCase(java.util.Locale.ROOT)).filter(n -> n.endsWith(".jar"))
                        .map(n -> n.replaceAll("[^a-z0-9]", "")).forEach(names::add);
                }
            } catch (java.io.IOException | RuntimeException | LinkageError ignored) {
                // No mods folder to read.
            }
            jarNames = names;
        }
        String wanted = id.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]", "");
        for (String name : names) if (name.startsWith(wanted)) return "mods-folder";
        return null;
    }

    private static String forgeWhere(String id) {
        if (!onForge()) return null;
        try {
            Class<?> modList = Class.forName("net.minecraftforge.fml.ModList");
            Object list = modList.getMethod("get").invoke(null);
            if (list != null && Boolean.TRUE.equals(modList.getMethod("isLoaded", String.class).invoke(list, id))) return "forge-modlist";
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            // Not constructed yet, or hidden from this class loader.
        }
        try {
            Class<?> loader = Class.forName("net.minecraftforge.fml.loading.FMLLoader");
            Object list = loader.getMethod("getLoadingModList").invoke(null);
            if (list != null) {
                Method byId = list.getClass().getMethod("getModFileById", String.class);
                if (byId.invoke(list, id) != null) return "forge-discovery";
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            // Discovery has not run yet.
        }
        return null;
    }

    private static boolean classExists(String name) {
        for (ClassLoader loader : new ClassLoader[] {ModPresence1201.class.getClassLoader(), Thread.currentThread().getContextClassLoader(), ClassLoader.getSystemClassLoader()}) {
            if (loader == null) continue;
            try {
                Class.forName(name, false, loader);
                return true;
            } catch (ClassNotFoundException | LinkageError ignored) {
                // Try the next loader.
            }
        }
        return false;
    }
}
