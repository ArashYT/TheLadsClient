package com.thelads.core.features.alwayson.skinlayers.api;

import net.minecraft.client.model.geom.ModelPart;
import org.joml.Vector3f;
import org.joml.Vector4f;

public interface MeshTransformer {
    MeshTransformer EMPTY_TRANSFORMER = new MeshTransformer() {
        @Override
        public void transform(ModelPart.Cube cube) {
        }

        @Override
        public void transform(Vector3f position, Vector4f[] vertexData) {
        }
    };

    void transform(Vector3f position, Vector4f[] vertexData);
    void transform(ModelPart.Cube cube);
}
