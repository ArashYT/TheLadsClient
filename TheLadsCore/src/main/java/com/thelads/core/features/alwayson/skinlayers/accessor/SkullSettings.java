package com.thelads.core.features.alwayson.skinlayers.accessor;

import com.thelads.core.features.alwayson.skinlayers.api.Mesh;
import com.thelads.core.features.alwayson.skinlayers.api.SkullData;
import net.minecraft.resources.Identifier;

public interface SkullSettings extends SkullData {
    Mesh getHeadLayers();
    void setupHeadLayers(Mesh mesh);
    boolean initialized();
    void setInitialized(boolean initialized);
    void setLastTexture(Identifier texture);
    Identifier getLastTexture();

    @Override
    default Mesh getMesh() {
        return this.getHeadLayers();
    }
}
