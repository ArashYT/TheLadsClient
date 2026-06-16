package com.thelads.core.features.alwayson.skinlayers.api;

import com.thelads.core.features.alwayson.skinlayers.versionless.util.wrapper.SolidPixelWrapper;

public record Shape(float yOffsetMagicValue, SolidPixelWrapper.Dimensions dimensions) {
    public static final Shape HEAD = new Shape(0.0f, new SolidPixelWrapper.Dimensions(8, 8, 8));
    public static final Shape BODY = new Shape(-0.2f, new SolidPixelWrapper.Dimensions(8, 12, 4));
    public static final Shape LEGS = new Shape(-0.2f, new SolidPixelWrapper.Dimensions(4, 14, 4));
    public static final Shape ARMS = new Shape(-0.1f, new SolidPixelWrapper.Dimensions(4, 14, 4));
    public static final Shape ARMS_SLIM = new Shape(-0.1f, new SolidPixelWrapper.Dimensions(3, 14, 4));
}
