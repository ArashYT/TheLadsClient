package com.thelads.core.features.alwayson.skinlayers.accessor;

import com.thelads.core.features.alwayson.skinlayers.api.Mesh;
import com.thelads.core.features.alwayson.skinlayers.api.PlayerData;
import net.minecraft.resources.Identifier;

public interface PlayerSettings extends PlayerData {
    void setHeadMesh(Mesh mesh);
    void setTorsoMesh(Mesh mesh);
    void setLeftArmMesh(Mesh mesh);
    void setRightArmMesh(Mesh mesh);
    void setLeftLegMesh(Mesh mesh);
    void setRightLegMesh(Mesh mesh);
    Identifier getCurrentSkin();
    void setCurrentSkin(Identifier skin);
    boolean hasThinArms();
    void setThinArms(boolean thinArms);

    default void clearMeshes() {
        this.setHeadMesh(null);
        this.setTorsoMesh(null);
        this.setLeftArmMesh(null);
        this.setRightArmMesh(null);
        this.setLeftLegMesh(null);
        this.setRightLegMesh(null);
    }
}
