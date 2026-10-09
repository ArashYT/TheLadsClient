package com.thelads.core.v1_20_1.render.mixin;

import com.thelads.core.v1_20_1.render.AnimationCache1201;
import com.thelads.core.v1_20_1.render.PboUpload1201;
import com.thelads.core.v1_20_1.render.SpriteAtlasLink1201;
import java.util.List;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.SpriteLoader;
import net.minecraft.client.renderer.texture.TextureAtlas;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Lads Kerria (1.8.5): tells each stitched sprite its atlas, and marks the atlas whose animations are ticking. */
@Mixin(TextureAtlas.class)
public abstract class TextureAtlasAnimationMixin {
    @Shadow private List<SpriteContents> sprites;

    @Inject(method = "upload(Lnet/minecraft/client/renderer/texture/SpriteLoader$Preparations;)V", at = @At("RETURN"))
    private void lads$link(SpriteLoader.Preparations preparations, CallbackInfo ci) {
        int atlas = ((AbstractTexture) (Object) this).getId();
        for (SpriteContents contents : sprites) ((SpriteAtlasLink1201) contents).lads$atlas(atlas);
    }

    @Inject(method = "cycleAnimationFrames()V", at = @At("HEAD"))
    private void lads$tickStart(CallbackInfo ci) {
        AnimationCache1201.cyclingAtlas = ((AbstractTexture) (Object) this).getId();
    }

    @Inject(method = "cycleAnimationFrames()V", at = @At("RETURN"))
    private void lads$tickEnd(CallbackInfo ci) {
        AnimationCache1201.cyclingAtlas = 0;
        PboUpload1201.endTick();
    }
}
