package com.thelads.core.features.alwayson.skinlayers.versionless.util.wrapper;

import com.thelads.core.features.alwayson.skinlayers.versionless.util.Direction;

public interface ModelBuilder {
    ModelBuilder textureSize(int width, int height);
    ModelBuilder uv(int u, int v);
    ModelBuilder mirror(boolean mirror);
    ModelBuilder addBox(float x, float y, float z, float pixelSize, Direction[] hide, Direction[][] corners);
    ModelBuilder addVanillaBox(float x, float y, float z, float width, float height, float depth);
    boolean isEmpty();
}
