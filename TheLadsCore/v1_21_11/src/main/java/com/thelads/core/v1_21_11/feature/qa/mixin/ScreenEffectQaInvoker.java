package com.thelads.core.v1_21_11.feature.qa.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** The first-person fire overlay through the real renderFire (1.7 Animations probe, Low Fire). */
@Mixin(ScreenEffectRenderer.class)
public interface ScreenEffectQaInvoker {
    @Invoker("renderFire") static void ladsQaFire(PoseStack pose, MultiBufferSource buffers, TextureAtlasSprite sprite) {
        throw new AssertionError();
    }
}
