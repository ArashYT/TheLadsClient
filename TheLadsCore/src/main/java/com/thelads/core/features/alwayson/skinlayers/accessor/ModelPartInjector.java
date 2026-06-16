package com.thelads.core.features.alwayson.skinlayers.accessor;

import com.mojang.blaze3d.vertex.PoseStack;
import com.thelads.core.features.alwayson.skinlayers.api.Mesh;
import com.thelads.core.features.alwayson.skinlayers.api.OffsetProvider;

public interface ModelPartInjector {
    void setInjectedMesh(Mesh mesh, OffsetProvider offsetProvider);
    boolean isVisible();
    Mesh getInjectedMesh();
    OffsetProvider getOffsetProvider();
    void prepareTranslateAndRotate(PoseStack poseStack);
}
