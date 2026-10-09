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

    private ModPresence1201() {}

    /** True when any of the ids is installed under either loader. */
    public static boolean loaded(String... ids) {
        for (String id : ids) if (loaded(id)) return true;
        return false;
    }

    public static boolean loaded(String id) {
        if (id == null || id.isEmpty()) return false;
        Boolean known = INSTALLED.get(id);
        if (known != null) return known;
        String where = where(id);
        if (where == null) return false;
        INSTALLED.put(id, true);
        SOURCE.put(id, where);
        LOGGER.info("Mod '{}' is installed ({})", id, where);
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
        return forgeWhere(id);
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

    /** True when Sinytra Connector is the one running Fabric mods. */
    public static boolean underConnector() {
        return classExists("dev.su5ed.sinytra.connector.loader.ConnectorLoaderMod")
            || classExists("dev.su5ed.sinytra.connector.loader.ConnectorEarlyLoader");
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
