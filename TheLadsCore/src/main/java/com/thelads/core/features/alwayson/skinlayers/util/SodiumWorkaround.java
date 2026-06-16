package com.thelads.core.features.alwayson.skinlayers.util;

import com.thelads.core.features.alwayson.skinlayers.SkinLayersModBase;

public class SodiumWorkaround {
    public static final boolean IS_SODIUM_WORKAROUND_NEEDED = SodiumWorkaround.isSodiumWorkaroundNeeded();
    private static final boolean IS_SODIUM_LOADED = SodiumWorkaround.isSodiumLoaded();

    private static boolean isSodiumWorkaroundNeeded() {
        try {
            Class.forName("net.caffeinemc.mods.sodium.client.render.immediate.model.ModelPartData");
            return true;
        } catch (ClassNotFoundException e1) {
            try {
                Class.forName("me.jellysquid.mods.sodium.client.render.immediate.model.ModelPartData");
                return true;
            } catch (ClassNotFoundException e2) {
                return false;
            }
        }
    }

    private static boolean isSodiumLoaded() {
        try {
            Class.forName("net.caffeinemc.mods.sodium.client.render.immediate.model.ModelCuboid");
            return true;
        } catch (ClassNotFoundException e1) {
            try {
                Class.forName("me.jellysquid.mods.sodium.client.render.immediate.model.ModelCuboid");
                return true;
            } catch (ClassNotFoundException e2) {
                return false;
            }
        }
    }

    public static boolean applySodiumWorkaround() {
        return IS_SODIUM_LOADED && SkinLayersModBase.config.applySodiumWorkaround;
    }
}
