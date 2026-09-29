// Adapted from Raised 6.0.0 source by yurisuika, LGPL-3.0-or-later.
// Pinned source 4b6a3b8718316d681c3ae6832cdd19c3174a29a4; see META-INF/lads-sources/raised.
package com.thelads.core.v26_2.feature.raised.util;

import com.thelads.core.v26_2.feature.raised.Raised;
import com.thelads.core.v26_2.feature.raised.registry.LayerRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

import java.util.TreeMap;

public class Icon {

    public static final TreeMap<Identifier, Identifier> LAYER_TEXTURES = new TreeMap<Identifier, Identifier>();

    public static void checkResources() {
        LAYER_TEXTURES.clear();

        LayerRegistry.LAYERS.forEach(layerName -> {
            Identifier identifier = Identifier.fromNamespaceAndPath(Raised.MOD_ID, "textures/gui/layer/" + layerName.getNamespace() + "/" + layerName.getPath() + ".png");
            if (Minecraft.getInstance().getResourceManager().getResource(identifier).isPresent()) {
                LAYER_TEXTURES.put(layerName, identifier);
            }
        });
    }

    public static Identifier getLayerIcon(Identifier layerName) {
        return LAYER_TEXTURES.getOrDefault(layerName, Identifier.fromNamespaceAndPath(Raised.MOD_ID, "textures/gui/layer/default.png"));
    }

}
