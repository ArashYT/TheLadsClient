package com.thelads.core.v1_20_1.mixin;

import com.thelads.core.client.ladsapi.SodiumAnimation;
import java.util.List;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlas;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Server rule Sodium "Animate Only Visible Textures: false": every sprite counts as drawn for Sodium, whose saved option stays as
 * it is. A ticker Sodium skipped meanwhile redraws its current frame once when the rule turns on (SodiumTickerMixin).
 */
@Mixin(TextureAtlas.class)
public abstract class SodiumAnimateAllMixin {
    @Shadow private List<SpriteContents> sprites;

    @Inject(method = "cycleAnimationFrames", at = @At("HEAD"), require = 0)
    private void ladsAnimateAll(CallbackInfo ci) {
        if (SodiumAnimation.on()) SodiumAnimation.markActive(sprites);
    }
}
