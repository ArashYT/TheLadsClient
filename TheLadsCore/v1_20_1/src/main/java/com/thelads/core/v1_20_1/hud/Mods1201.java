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

    public static boolean loaded(String id) {
        if (FabricLoader.getInstance().isModLoaded(id)) return true;
        try {
            // Forge's own list, for Forge mods Connector does not mirror into Fabric Loader.
            Object list = Class.forName("net.minecraftforge.fml.ModList").getMethod("get").invoke(null);
            return list != null && Boolean.TRUE.equals(list.getClass().getMethod("isLoaded", String.class).invoke(list, id));
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return false;
        }
    }
}
