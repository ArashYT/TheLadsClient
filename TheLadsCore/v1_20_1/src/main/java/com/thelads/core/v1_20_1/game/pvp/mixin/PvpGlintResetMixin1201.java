package com.thelads.core.v1_20_1.game.pvp.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import com.thelads.core.v1_20_1.game.pvp.PvpVisuals1201;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Glint Colorizer: a glint render type's clear resets the texture matrix, so the tint ends there (drawn by vanilla or ImmediatelyFast). */
@Mixin(value = RenderSystem.class, remap = false)
public class PvpGlintResetMixin1201 {
    @Inject(method = "resetTextureMatrix()V", at = @At("HEAD"))
    private static void lads$untint(CallbackInfo ci) {
        PvpVisuals1201.untintGlint();
    }
}
