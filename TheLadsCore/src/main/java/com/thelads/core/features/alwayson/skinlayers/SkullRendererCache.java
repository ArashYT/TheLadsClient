package com.thelads.core.features.alwayson.skinlayers;

import com.mojang.authlib.GameProfile;
import com.thelads.core.features.alwayson.skinlayers.accessor.SkullSettings;
import com.thelads.core.features.alwayson.skinlayers.api.Mesh;
import java.util.WeakHashMap;
import net.minecraft.resources.Identifier;

public class SkullRendererCache {
    public static boolean renderNext = false;
    public static SkullSettings lastSkull = null;
    public static WeakHashMap<GameProfile, SkullSettings> itemCache = new WeakHashMap<>();

    public static void clearCache() {
        itemCache.clear();
    }

    public static class ItemSettings implements SkullSettings {
        private Mesh hatModel = null;
        private boolean initialized = false;

        @Override
        public Mesh getHeadLayers() {
            return this.hatModel;
        }

        @Override
        public void setupHeadLayers(Mesh box) {
            this.hatModel = box;
        }

        @Override
        public boolean initialized() {
            return this.initialized;
        }

        @Override
        public void setInitialized(boolean initialized) {
            this.initialized = initialized;
        }

        @Override
        public void setLastTexture(Identifier texture) {
        }

        @Override
        public Identifier getLastTexture() {
            return null;
        }
    }
}
