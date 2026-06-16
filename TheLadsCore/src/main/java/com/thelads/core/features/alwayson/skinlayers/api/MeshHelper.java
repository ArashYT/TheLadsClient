package com.thelads.core.features.alwayson.skinlayers.api;

import com.mojang.blaze3d.platform.NativeImage;

public interface MeshHelper {
    Mesh create3DMesh(NativeImage image, int width, int height, int depth, int textureU, int textureV, boolean topPivot, float rotationOffset);
    Mesh create3DMesh(NativeImage image, int width, int height, int depth, int textureU, int textureV, boolean topPivot, float rotationOffset, boolean mirror);
}
