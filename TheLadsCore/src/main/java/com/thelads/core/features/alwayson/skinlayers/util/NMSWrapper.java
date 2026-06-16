package com.thelads.core.features.alwayson.skinlayers.util;

import com.mojang.blaze3d.platform.NativeImage;
import com.thelads.core.features.alwayson.skinlayers.versionless.util.wrapper.SolidPixelWrapper;
import com.thelads.core.features.alwayson.skinlayers.versionless.util.wrapper.TextureData;

public class NMSWrapper {
    public static class WrappedNativeImage implements TextureData {
        private final NativeImage natImage;

        public WrappedNativeImage(NativeImage natImage) {
            this.natImage = natImage;
        }

        @Override
        public boolean isPresent(SolidPixelWrapper.UV onTextureUV) {
            return this.natImage.getLuminanceOrAlpha(onTextureUV.u(), onTextureUV.v()) != 0;
        }

        @Override
        public boolean isSolid(SolidPixelWrapper.UV onTextureUV) {
            return this.natImage.getLuminanceOrAlpha(onTextureUV.u(), onTextureUV.v()) == -1;
        }

        @Override
        public int getWidth() {
            return this.natImage.getWidth();
        }

        @Override
        public int getHeight() {
            return this.natImage.getHeight();
        }
    }
}
