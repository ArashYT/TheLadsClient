// Adapted from Raised 6.0.0 source by yurisuika, LGPL-3.0-or-later.
// Pinned source 4b6a3b8718316d681c3ae6832cdd19c3174a29a4; see META-INF/lads-sources/raised.
package com.thelads.core.v26_2.feature.raised.option;

import com.thelads.core.v26_2.feature.raised.client.gui.group.Group;
import com.thelads.core.v26_2.feature.raised.client.gui.layer.Layer;
import com.thelads.core.v26_2.feature.raised.client.gui.layer.Layers;

import java.util.TreeMap;
import java.util.TreeSet;

public class Options {

    public TreeMap<String, Group> groups = new TreeMap<String, Group>() {{
        put(
            "Default",
            new Group(
                new Group.Offset(0, 2),
                new TreeSet<String>() {{
                    add(Layers.HOTBAR.toString());
                    add(Layers.ACTION_BAR.toString());
                }}
            )
        );
    }};
    public TreeMap<String, Layer> layers = new TreeMap<String, Layer>() {};
    public AdditionalSettings additionalSettings = new AdditionalSettings(AdditionalSettings.HotbarSelectionFix.AUTO);

    public TreeMap<String, Group> getGroups() {
        return groups;
    }

    public void setGroups(TreeMap<String, Group> groups) {
        this.groups = groups;
    }

    public TreeMap<String, Layer> getLayers() {
        return layers;
    }

    public void setLayers(TreeMap<String, Layer> layers) {
        this.layers = layers;
    }

    public AdditionalSettings getAdditionalSettings() {
        return additionalSettings;
    }

    public void setAdditionalSettings(AdditionalSettings additionalSettings) {
        this.additionalSettings = additionalSettings;
    }

}