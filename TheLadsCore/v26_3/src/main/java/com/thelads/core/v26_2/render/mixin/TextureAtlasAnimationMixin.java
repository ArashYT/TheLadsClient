package com.thelads.core.v26_2.render.mixin;

import com.mojang.renderpearl.api.textures.GpuTextureView;
import com.thelads.core.perf.BuiltInRender;
import com.thelads.core.v26_2.render.AnimationCopy26;
import java.util.List;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Lads Kerria (1.8.5): the atlas's animation upload goes through AnimationCopy26 (copies, the game's draws for the rest). */
@Mixin(TextureAtlas.class)
public abstract class TextureAtlasAnimationMixin {
    @Shadow private List<TextureAtlasSprite> sprites;
    @Shadow private List<SpriteContents.AnimationState> animatedTexturesStates;
    @Shadow private int maxMipLevel;
    @Shadow private GpuTextureView[] mipViews;
    @Shadow @Final private Identifier location;
    /** The animated sprites in the order of animatedTexturesStates, rebuilt whenever that list is replaced (atlas upload). */
    @Unique private List<TextureAtlasSprite> lads$animatedSprites = List.of();
    @Unique private Object lads$statesFor;
    @Unique private long lads$start;

    @Inject(method = "uploadAnimationFrames", at = @At("HEAD"), cancellable = true)
    private void lads$upload(CallbackInfo ci) {
        if (lads$statesFor != animatedTexturesStates) {
            lads$statesFor = animatedTexturesStates;
            lads$animatedSprites = sprites.stream().filter(TextureAtlasSprite::isAnimated).toList();
        }
        var texture = ((AbstractTexture) (Object) this).getTexture();
        if (AnimationCopy26.uploadFrames(animatedTexturesStates, lads$animatedSprites, texture, maxMipLevel, mipViews, () -> "Animate " + location)) {
            ci.cancel();
            return;
        }
        lads$start = System.nanoTime();
    }

    /** The game's own upload ran: its CPU time, for the before/after numbers. */
    @Inject(method = "uploadAnimationFrames", at = @At("RETURN"))
    private void lads$timed(CallbackInfo ci) {
        BuiltInRender.animationNanos += System.nanoTime() - lads$start;
        BuiltInRender.animationTicks++;
    }
}
