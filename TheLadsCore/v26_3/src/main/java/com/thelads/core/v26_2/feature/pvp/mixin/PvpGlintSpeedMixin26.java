package com.thelads.core.v26_2.feature.pvp.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.thelads.core.v26_2.feature.pvp.PvpVisuals26;
import net.minecraft.client.renderer.rendertype.TextureTransform;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Glint Colorizer's speed: the clock every glint scroll (items, entities, armour) moves by. */
@Mixin(TextureTransform.class)
public class PvpGlintSpeedMixin26 {
    @ModifyExpressionValue(method = "setupGlintTexturing", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Util;getMillis()J"))
    private static long lads$speed(long millis) {
        return PvpVisuals26.glintMillis(millis);
    }
}
