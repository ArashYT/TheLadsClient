package com.thelads.core.v1_20_1.mixin;

import com.thelads.core.client.ladsapi.SodiumAnimation;
import net.minecraft.client.renderer.texture.SpriteContents;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A sprite's ticker: when the Sodium rule turns on it redraws its current frame once, because Sodium may have skipped its last frame change. */
@Mixin(targets = "net.minecraft.client.renderer.texture.SpriteContents$Ticker")
public abstract class SodiumTickerMixin {
    @Shadow int frame;
    @Shadow @Final SpriteContents.AnimatedTexture animationInfo;
    @Unique private int lads$epoch;

    @Inject(method = "tickAndUpload", at = @At("HEAD"), require = 0)
    private void ladsRedraw(int x, int y, CallbackInfo ci) {
        if (lads$epoch == SodiumAnimation.epoch) return;
        lads$epoch = SodiumAnimation.epoch;
        if (SodiumAnimation.on()) animationInfo.uploadFrame(x, y, animationInfo.frames.get(frame).index);
    }
}
