// Adapted from Raised 6.0.0 source by yurisuika, LGPL-3.0-or-later.
// Pinned source 4b6a3b8718316d681c3ae6832cdd19c3174a29a4; see META-INF/lads-sources/raised.
package com.thelads.core.v26_2.feature.raised.registry;

import com.thelads.core.v26_2.feature.raised.Raised;
import com.thelads.core.v26_2.feature.raised.client.gui.layer.Layer;
import com.thelads.core.v26_2.feature.raised.config.Config;
import net.minecraft.resources.Identifier;

import java.util.HashSet;
import java.util.Set;
import java.util.TreeMap;

public class LayerRegistry {

    public static final Set<Identifier> LAYERS = new HashSet<>();
    public static final TreeMap<Identifier, Layer> DEFAULT_LAYERS = new TreeMap<Identifier, Layer>();

    public static void register(String layerName) {
        register(layerName, new Layer(Layer.Anchor.NONE));
    }

    public static void register(Identifier layerName) {
        register(layerName, new Layer(Layer.Anchor.NONE));
    }

    public static void register(String layerName, Layer layer) {
        register(Identifier.tryParse(layerName), layer);
    }

    public static void register(Identifier layerName, Layer layer) {
        LAYERS.add(layerName);
        DEFAULT_LAYERS.putIfAbsent(layerName, layer);
        Config.update(o -> o.getLayers().putIfAbsent(layerName.toString(), layer));
        Raised.LOGGER.info("Registering Raised layer '{}'", layerName);
    }

    public static void addDefaultLayersToConfig() {
        DEFAULT_LAYERS.forEach((layerName, layer) -> Config.update(o -> o.getLayers().putIfAbsent(layerName.toString(), layer)));
    }

}
