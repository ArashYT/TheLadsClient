package com.thelads.core.features.alwayson.skinlayers.api;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDefinition;
import net.minecraft.client.model.geom.builders.CubeListBuilder;

public interface BoxBuilder {
    BoxBuilder DEFAULT = new BoxBuilder() {
        @Override
        public ModelPart.Cube build(BoxDefinition box) {
            CubeListBuilder cubeList = CubeListBuilder.create();
            cubeList.texOffs(box.u(), box.v()).mirror(box.mirror()).addBox(box.x(), box.y(), box.z(), box.width(), box.height(), box.depth());
            return ((CubeDefinition) cubeList.getCubes().get(0)).bake(box.textureWidth(), box.textureHeight());
        }
    };

    ModelPart.Cube build(BoxDefinition box);

    record BoxDefinition(int u, int v, boolean mirror, float x, float y, float z, float width, float height, float depth, int textureWidth, int textureHeight) {
    }
}
