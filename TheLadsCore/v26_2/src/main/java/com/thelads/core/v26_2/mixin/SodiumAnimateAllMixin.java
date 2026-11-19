package com.thelads.core.v26_2.mixin;

import com.thelads.core.client.ladsapi.SodiumAnimation;
import java.util.List;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Server rule Sodium "Animate Only Visible Textures: false": every animated sprite counts as drawn for Sodium, whose saved option
 * stays as it is. Sprites Sodium skipped meanwhile redraw their current frame once when the rule turns on (SodiumAnimation).
 */
@Mixin(TextureAtlas.class)
public abstract class SodiumAnimateAllMixin {
    @Shadow private List<TextureAtlasSprite> sprites;
    @Shadow private List<SpriteContents.AnimationState> animatedTexturesStates;
    @Unique private Object lads$spritesFor;
    @Unique private List<SpriteContents> lads$animated = List.of();
    @Unique private int lads$epoch;

    @Inject(method = "cycleAnimationFrames", at = @At("HEAD"), require = 0)
    private void ladsAnimateAll(CallbackInfo ci) {
        if (!SodiumAnimation.on()) return;
        if (lads$spritesFor != sprites) {
            lads$spritesFor = sprites;
            lads$animated = sprites.stream().filter(TextureAtlasSprite::isAnimated).map(TextureAtlasSprite::contents).toList();
        }
        SodiumAnimation.markActive(lads$animated);
    }

    /** The states' own tick ran, so a state dirty here is drawn by this upload whatever Sodium would have decided. */
    @Inject(method = "uploadAnimationFrames", at = @At("HEAD"), require = 0)
    private void ladsRedrawAll(CallbackInfo ci) {
        if (lads$epoch == SodiumAnimation.epoch) return;
        lads$epoch = SodiumAnimation.epoch;
        if (SodiumAnimation.on()) for (SpriteContents.AnimationState state : animatedTexturesStates) ((AnimationStateDirtyAccess) (Object) state).lads$dirty(true);
    }
}
