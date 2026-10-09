package com.thelads.core.v1_8_9.render.mixin;

import com.thelads.core.perf.BuiltInRender;
import com.thelads.core.v1_8_9.render.AnimationCache189;
import com.thelads.core.v1_8_9.render.RenderProbeHooks189;
import java.util.List;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.resources.IResourceManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Lads Kerria (1.8.5): the block atlas animation tick and rebuilds, for AnimationCache189. */
@Mixin(TextureMap.class)
public abstract class TextureMapAnimationMixin {
    @Shadow @Final private List<TextureAtlasSprite> listAnimatedSprites;
    @Unique private long lads$start;

    @Inject(method = "loadTextureAtlas", at = @At("HEAD"))
    private void lads$rebuild(IResourceManager manager, CallbackInfo ci) { AnimationCache189.clear(); }

    @Inject(method = "loadTextureAtlas", at = @At("RETURN"))
    private void lads$stitched(IResourceManager manager, CallbackInfo ci) { AnimationCache189.stitched(listAnimatedSprites); }

    @Inject(method = "updateAnimations", at = @At("HEAD"), cancellable = true)
    private void lads$tickStart(CallbackInfo ci) {
        if (RenderProbeHooks189.animationsHeld) { ci.cancel(); return; } // QA: animations held still for a comparison
        lads$start = System.nanoTime();
        AnimationCache189.cyclingAtlas = ((AbstractTexture) (Object) this).getGlTextureId();
    }

    @Inject(method = "updateAnimations", at = @At("RETURN"))
    private void lads$tickEnd(CallbackInfo ci) {
        AnimationCache189.cyclingAtlas = 0;
        BuiltInRender.animationNanos += System.nanoTime() - lads$start;
        BuiltInRender.animationTicks++;
    }
}
