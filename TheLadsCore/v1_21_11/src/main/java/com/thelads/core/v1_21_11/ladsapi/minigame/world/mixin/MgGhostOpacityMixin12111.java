package com.thelads.core.v1_21_11.ladsapi.minigame.world.mixin;

import com.thelads.core.v1_21_11.ladsapi.minigame.world.GhostRender12111;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** A minigame ghost is submitted with the see-through render type and a half-transparent tint (the flag is set only around its submit). */
@Mixin(LivingEntityRenderer.class)
public abstract class MgGhostOpacityMixin12111 {
    private static final String SUBMIT = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;"
        + "Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/CameraRenderState;)V";

    @ModifyArg(method = SUBMIT, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/LivingEntityRenderer;getRenderType("
        + "Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;ZZZ)Lnet/minecraft/client/renderer/rendertype/RenderType;"), index = 2)
    private boolean lads$ghostSeeThrough(boolean forceTransparent) {
        return forceTransparent || GhostRender12111.rendering;
    }

    @ModifyArg(method = SUBMIT, at = @At(value = "INVOKE", target = "Lnet/minecraft/util/ARGB;multiply(II)I"), index = 0)
    private int lads$ghostColor(int baseColor) {
        return GhostRender12111.rendering ? 0x88A8C8FF : baseColor;
    }
}
