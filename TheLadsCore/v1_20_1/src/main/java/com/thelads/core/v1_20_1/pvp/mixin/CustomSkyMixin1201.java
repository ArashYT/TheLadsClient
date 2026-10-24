package com.thelads.core.v1_20_1.pvp.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.thelads.core.v1_20_1.pvp.CustomSky1201;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Custom Skies: drawn where vanilla has turned the sky -90 degrees about Y and is about to turn it with the time of day for the sun
 * (the second getTimeOfDay; the first picks the sunrise colour).
 */
@Mixin(LevelRenderer.class)
public abstract class CustomSkyMixin1201 {
    @Shadow private ClientLevel level;

    @Inject(method = "renderSky", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;getTimeOfDay(F)F", ordinal = 1))
    private void lads$customSky(PoseStack pose, Matrix4f projection, float partialTick, Camera camera, boolean foggy, Runnable fog, CallbackInfo ci) {
        if (level != null) CustomSky1201.render(level, pose, partialTick);
    }
}
