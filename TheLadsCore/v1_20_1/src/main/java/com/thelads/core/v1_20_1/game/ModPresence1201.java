package com.thelads.core.v1_20_1.game;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Whether an upstream mod is installed, on plain Fabric and on Forge through Sinytra Connector (where a Forge mod may not be
 * listed by FabricLoader, so Forge's own ModList is asked as well). The answer is cached after the mods loaded.
 */
public final class ModPresence1201 {
    private static final Map<String, Boolean> CACHE = new ConcurrentHashMap<>();
    private static Boolean forge;

    private ModPresence1201() {}

    public static boolean loaded(String id) {
        return CACHE.computeIfAbsent(id, ModPresence1201::probe);
    }

    private static boolean probe(String id) {
        FabricLoader loader = FabricLoader.getInstance();
        if (loader.isModLoaded(id) || loader.isModLoaded(id.replace("_", "")) || loader.isModLoaded(id.replace("-", ""))) return true;
        return forge() && forgeLoaded(id);
    }

    /** Running under Forge (through Connector). */
    public static boolean forge() {
        if (forge == null) {
            boolean found;
            try {
                Class.forName("net.minecraftforge.fml.ModList", false, ModPresence1201.class.getClassLoader());
                found = true;
            } catch (Throwable absent) {
                found = false;
            }
            forge = found;
        }
        return forge;
    }

    private static boolean forgeLoaded(String id) {
        try {
            Class<?> list = Class.forName("net.minecraftforge.fml.ModList", true, ModPresence1201.class.getClassLoader());
            Object modList = list.getMethod("get").invoke(null);
            return modList != null && (boolean) list.getMethod("isLoaded", String.class).invoke(modList, id);
        } catch (Throwable unavailable) {
            return false;
        }
    }
}
