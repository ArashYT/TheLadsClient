package com.thelads.core.v1_20_1.render.mixin;

import com.mojang.blaze3d.platform.NativeImage;
import com.thelads.core.v1_20_1.render.AnimationCache1201;
import com.thelads.core.v1_20_1.render.PboUpload1201;
import com.thelads.core.v1_20_1.render.SpriteAtlasLink1201;
import net.minecraft.client.renderer.texture.SpriteContents;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lads Kerria (1.8.5): during an atlas tick, frame uploads of the sprite's own strip become GPU copies from its cached strip
 * (AnimationCache1201); everything else that tick (interpolated frames, sprites past the cache budget) goes through mapped
 * buffers (PboUpload1201). Sodium's and Embeddium's "animate only visible textures" cancel the whole tick before this runs.
 */
@Mixin(SpriteContents.class)
public abstract class SpriteContentsAnimationMixin implements SpriteAtlasLink1201 {
    @Shadow NativeImage[] byMipLevel;
    @Shadow @Final int width;
    @Shadow @Final int height;
    @Unique private int lads$atlas, lads$cache;

    @Override public void lads$atlas(int atlas) { lads$atlas = atlas; }

    @Inject(method = "upload(IIII[Lcom/mojang/blaze3d/platform/NativeImage;)V", at = @At("HEAD"), cancellable = true)
    private void lads$upload(int x, int y, int frameX, int frameY, NativeImage[] images, CallbackInfo ci) {
        if (lads$atlas == 0 || lads$atlas != AnimationCache1201.cyclingAtlas) return;
        if (images == byMipLevel) {
            int cache = AnimationCache1201.copy(lads$cache, lads$atlas, images, width, height, x, y, frameX, frameY);
            if (cache != 0) lads$cache = cache;
            if (cache > 0) { ci.cancel(); return; }
        }
        if (PboUpload1201.upload(images, width, height, x, y, frameX, frameY)) ci.cancel();
    }

    @Inject(method = "close()V", at = @At("HEAD"))
    private void lads$close(CallbackInfo ci) {
        AnimationCache1201.free(lads$cache, byMipLevel);
        lads$cache = -1;
    }
}
