package com.thelads.core.v1_8_9.feature;

/**
 * Accessor interface implemented on FontRenderer (via FontRendererMixin) to allow rendering
 * solely the darkened drop-shadow pass of text, bypassing the redundant foreground text pass.
 */
public interface FontShadow189 {
    int ladsDrawShadow(String text, float x, float y, int color);
}
