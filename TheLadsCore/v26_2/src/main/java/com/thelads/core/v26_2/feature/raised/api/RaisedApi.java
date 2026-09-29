// Adapted from Raised 6.0.0 source by yurisuika, LGPL-3.0-or-later.
// Pinned source 4b6a3b8718316d681c3ae6832cdd19c3174a29a4; see META-INF/lads-sources/raised.
package com.thelads.core.v26_2.feature.raised.api;

import com.thelads.core.v26_2.feature.raised.client.gui.layer.Layer;
import com.thelads.core.v26_2.feature.raised.registry.LayerRegistry;
import com.thelads.core.v26_2.feature.raised.util.Translate;
import net.minecraft.resources.Identifier;

public class RaisedApi {

    /**
     * <p>Retrieves the calculated horizontal offset for the given layer.
     *
     * <p>The sum of x-axis offsets from all groups containing this layer is sign mapped based on its anchor.
     *
     * @param layerName the {@link Layer} key as a {@link String}
     *
     * @return the horizontal offset int
     */
    public static int getX(String layerName) {
        return Translate.getX(layerName);
    }

    /**
     * <p>Retrieves the calculated horizontal offset for the given layer.
     *
     * <p>The sum of x-axis offsets from all groups containing this layer is sign mapped based on its anchor.
     *
     * @param layerName the {@link Layer} key as an {@link Identifier}
     *
     * @return the horizontal offset int
     */
    public static int getX(Identifier layerName) {
        return Translate.getX(layerName);
    }

    /**
     * <p>Retrieves the calculated vertical offset for the given layer.
     *
     * <p>The sum of y-axis offsets from all groups containing this layer is sign mapped based on its anchor.
     *
     * @param layerName the {@link Layer} key as a {@link String}
     *
     * @return the vertical offset int
     */
    public static int getY(String layerName) {
        return Translate.getY(layerName);
    }

    /**
     * <p>Retrieves the calculated vertical offset for the given layer.
     *
     * <p>The sum of y-axis offsets from all groups containing this layer is sign mapped based on its anchor.
     *
     * @param layerName the {@link Layer} key as an {@link Identifier}
     *
     * @return the vertical offset int
     */
    public static int getY(Identifier layerName) {
        return Translate.getY(layerName);
    }

    /**
     * <p>Registers a layer for the user to configure with default anchor.
     *
     * @param layerName the {@link Layer} key to register as a {@link String}
     */
    public static void register(String layerName) {
        LayerRegistry.register(layerName);
    }

    /**
     * <p>Registers a layer for the user to configure with default anchor.
     *
     * @param layerName the {@link Layer} key to register as an {@link Identifier}
     */
    public static void register(Identifier layerName) {
        LayerRegistry.register(layerName);
    }

    /**
     * <p>Registers a layer for the user to configure.
     *
     * @param layerName the {@link Layer} key to register as a {@link String}
     * @param anchor the {@link Layer.Anchor} of the layer, used when generating an entry in the config
     */
    public static void register(String layerName, Layer.Anchor anchor) {
        LayerRegistry.register(layerName, new Layer(anchor));
    }

    /**
     * <p>Registers a layer for the user to configure.
     *
     * @param layerName the {@link Layer} key to register as an {@link Identifier}
     * @param anchor the {@link Layer.Anchor} of the layer, used when generating an entry in the config
     */
    public static void register(Identifier layerName, Layer.Anchor anchor) {
        LayerRegistry.register(layerName, new Layer(anchor));
    }

}