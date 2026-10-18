package com.thelads.core.v1_20_1.hud;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Whether another mod is in this game, on plain Fabric and under Sinytra Connector (Zero SMP: Forge mods). Lads modules that
 * duplicate an installed mod stand down for it; once its jar is removed they take over.
 */
public final class Mods1201 {
    private Mods1201() {}

    public static boolean loaded(String... ids) {
        for (String id : ids) if (loaded(id)) return true;
        return false;
    }

    /**
     * 1.9.12 perf: answers are cached. The Raised hooks ask several times a frame, and on plain Fabric every ask was a
     * Class.forName miss for Forge's ModList, which makes the class loader search every jar: ~1.8 ms a frame, the 1.20.1 HUD went
     * from ~1650 FPS (hidden) to ~400 (shown) in the Legion bench. A "no" is only kept once the answer can no longer change
     * (plain Fabric, or Forge's mod list exists).
     */
    private static final java.util.Map<String, Boolean> CACHE = new java.util.concurrent.ConcurrentHashMap<>();
    /** Forge's ModList class (Zero SMP under Connector), or null on plain Fabric; looked up once. */
    private static volatile Class<?> forgeModList;
    private static volatile boolean forgeChecked;

    public static boolean loaded(String id) {
        Boolean known = CACHE.get(id);
        if (known != null) return known;
        if (FabricLoader.getInstance().isModLoaded(id)) {
            CACHE.put(id, Boolean.TRUE);
            return true;
        }
        if (!forgeChecked) {
            try {
                forgeModList = Class.forName("net.minecraftforge.fml.ModList");
            } catch (ClassNotFoundException | LinkageError ignored) {
                forgeModList = null;
            }
            forgeChecked = true;
        }
        if (forgeModList == null) {
            CACHE.put(id, Boolean.FALSE);
            return false;
        }
        try {
            // Forge's own list, for Forge mods Connector does not mirror into Fabric Loader.
            Object list = forgeModList.getMethod("get").invoke(null);
            if (list == null) return false; // Forge has not built its list yet: ask again later
            boolean result = Boolean.TRUE.equals(list.getClass().getMethod("isLoaded", String.class).invoke(list, id));
            CACHE.put(id, result);
            return result;
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return false;
        }
    }
}
