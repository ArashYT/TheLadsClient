package com.thelads.core.v26_2.feature;

import net.minecraft.client.renderer.item.ItemStackRenderState;

public interface ShulkerIconState {
    ItemStackRenderState lads$shulkerIcon();
    float lads$iconScale();
    float lads$iconHeight();
    void lads$iconTransform(float scale, float height);
}
