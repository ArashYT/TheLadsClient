package com.thelads.core.v26_2.pvp.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.thelads.core.v26_2.pvp.PvpVisuals26;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Menu Blur on 26.x: while the module is on in a world, vanilla's menu blur uses its Blur Strength as the radius. */
@Mixin(GameRenderer.class)
public abstract class MenuBlurRadiusMixin26 {
    @ModifyExpressionValue(method = "extractOptions", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Options;getMenuBackgroundBlurriness()I"))
    private int lads$blurStrength(int vanilla) {
        return PvpVisuals26.blurStrength(vanilla);
    }
}
