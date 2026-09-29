package com.thelads.core.v26_2.mixin;

import com.thelads.core.v26_2.feature.ShulkerIconState;
import net.minecraft.client.renderer.blockentity.state.ShulkerBoxRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(ShulkerBoxRenderState.class)
public class ShulkerIconStateMixin implements ShulkerIconState {
    @Unique private final ItemStackRenderState lads$icon = new ItemStackRenderState();
    @Unique private float lads$scale, lads$height;
    @Override public ItemStackRenderState lads$shulkerIcon() { return lads$icon; }
    @Override public float lads$iconScale() { return lads$scale; }
    @Override public float lads$iconHeight() { return lads$height; }
    @Override public void lads$iconTransform(float scale, float height) { lads$scale = scale; lads$height = height; }
}
