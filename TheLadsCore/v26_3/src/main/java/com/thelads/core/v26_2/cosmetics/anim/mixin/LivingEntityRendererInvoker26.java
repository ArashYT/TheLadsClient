package com.thelads.core.v26_2.cosmetics.anim.mixin;

import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** addLayer lives on LivingEntityRenderer, not AvatarRenderer, so the wearables layer is added through this invoker. */
@Mixin(LivingEntityRenderer.class)
public interface LivingEntityRendererInvoker26 {
    @Invoker("addLayer")
    boolean lads$addLayer(RenderLayer<?, ?> layer);
}
