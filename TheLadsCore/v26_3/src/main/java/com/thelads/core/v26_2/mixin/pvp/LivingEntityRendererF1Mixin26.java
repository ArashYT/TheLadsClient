package com.thelads.core.v26_2.mixin.pvp;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.thelads.core.client.PvpVisuals;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Nametags' Show with Hidden HUD: tags stay while F1 hides the HUD (vanilla's last check in shouldShowName). */
@Mixin(LivingEntityRenderer.class)
public class LivingEntityRendererF1Mixin26 {
    @ModifyExpressionValue(method = "shouldShowName(Lnet/minecraft/world/entity/LivingEntity;D)Z",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Hud;isHidden()Z"))
    private boolean lads$namesInF1(boolean hidden) {
        return hidden && !PvpVisuals.nametagsInF1;
    }
}
