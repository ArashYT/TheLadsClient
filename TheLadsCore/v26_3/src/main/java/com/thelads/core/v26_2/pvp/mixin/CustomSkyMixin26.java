package com.thelads.core.v26_2.pvp.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.thelads.core.v26_2.pvp.CustomSky26;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.world.level.MoonPhase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Custom Skies: the packs' sky layers go into the sky's render pass right before the sun, moon and stars. */
@Mixin(SkyRenderer.class)
public abstract class CustomSkyMixin26 {
    @WrapOperation(method = "render", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/SkyRenderer;renderSunMoonAndStars(Lcom/mojang/renderpearl/api/commands/RenderPass;Lcom/mojang/blaze3d/vertex/PoseStack;FFFLnet/minecraft/world/level/MoonPhase;FF)V"))
    private void lads$customSky(SkyRenderer sky, RenderPass pass, PoseStack pose, float sunAngle, float moonAngle, float starAngle, MoonPhase phase,
            float rainBrightness, float starBrightness, Operation<Void> original) {
        CustomSky26.render(pass, sunAngle);
        original.call(sky, pass, pose, sunAngle, moonAngle, starAngle, phase, rainBrightness, starBrightness);
    }
}
