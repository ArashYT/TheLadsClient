package com.thelads.core.v1_20_1.game.pvp.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.thelads.core.v1_20_1.game.pvp.PvpVisuals1201;
import net.minecraft.client.renderer.RenderStateShard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Glint Colorizer: every glint render type (items, armour, entities) sets up its texture scroll here, right before its draw: the
 * draw gets the colour (the shader colour, the glint shader's ColorModulator) and the scroll the module's speed.
 */
@Mixin(RenderStateShard.class)
public class PvpGlintMixin1201 {
    @Inject(method = "setupGlintTexturing", at = @At("TAIL"))
    private static void lads$tint(float scale, CallbackInfo ci) {
        PvpVisuals1201.tintGlint();
    }

    @ModifyExpressionValue(method = "setupGlintTexturing", at = @At(value = "INVOKE", target = "Lnet/minecraft/Util;getMillis()J"))
    private static long lads$speed(long millis) {
        return PvpVisuals1201.glintMillis(millis);
    }
}
