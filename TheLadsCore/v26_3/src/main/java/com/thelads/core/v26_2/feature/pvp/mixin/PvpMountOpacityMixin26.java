package com.thelads.core.v26_2.feature.pvp.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.thelads.core.v26_2.feature.pvp.PvpVisuals26;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mount Opacity: the player's mount takes vanilla's see-through path (getRenderType's forceTransparent: the translucent render type
 * an invisible mob shows a spectator through) in white at the module's opacity instead of 15%.
 */
@Mixin(LivingEntityRenderer.class)
public class PvpMountOpacityMixin26 {
    private static final String SUBMIT = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;"
        + "Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V";

    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V",
        at = @At("TAIL"))
    private void lads$markMount(LivingEntity entity, LivingEntityRenderState state, float partial, CallbackInfo ci) {
        ((PvpVisuals26.MountState) state).lads$mount(PvpVisuals26.isMount(entity));
    }

    @ModifyArg(method = SUBMIT, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/LivingEntityRenderer;getRenderType("
        + "Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;ZZZ)Lnet/minecraft/client/renderer/rendertype/RenderType;"), index = 2)
    private boolean lads$seeThrough(boolean forceTransparent, @Local(argsOnly = true) LivingEntityRenderState state) {
        return forceTransparent || ((PvpVisuals26.MountState) state).lads$mount();
    }

    /** The model colour: ARGB.multiply(base colour, model tint); a mount's base is white at the module's opacity. */
    @ModifyArg(method = SUBMIT, at = @At(value = "INVOKE", target = "Lnet/minecraft/util/ARGB;multiply(II)I"), index = 0)
    private int lads$opacity(int baseColor, @Local(argsOnly = true) LivingEntityRenderState state) {
        return ((PvpVisuals26.MountState) state).lads$mount() ? PvpVisuals26.mountColor() : baseColor;
    }
}
