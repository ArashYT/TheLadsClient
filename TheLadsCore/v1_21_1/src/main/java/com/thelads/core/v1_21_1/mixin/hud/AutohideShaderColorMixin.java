package com.thelads.core.v1_21_1.mixin.hud;

import com.mojang.blaze3d.systems.RenderSystem;
import com.thelads.core.v1_21_1.feature.NativeAutohide;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Inside a faded Autohide scope every shader colour keeps the scope's alpha, so draws that set their own colour (AppleSkin) fade too. */
@Mixin(value = RenderSystem.class, remap = false)
public class AutohideShaderColorMixin {
    @ModifyVariable(method = "setShaderColor", at = @At("HEAD"), argsOnly = true, ordinal = 3)
    private static float ladsFade(float alpha) { return alpha * NativeAutohide.scopeOpacity; }
}
