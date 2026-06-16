package com.thelads.core.features.alwayson.skinlayers.versionless.util.wrapper;

public interface TextureData {
    boolean isPresent(SolidPixelWrapper.UV uv);
    boolean isSolid(SolidPixelWrapper.UV uv);

    default int getWidth() {
        return 64;
    }

    default int getHeight() {
        return 64;
    }
}
