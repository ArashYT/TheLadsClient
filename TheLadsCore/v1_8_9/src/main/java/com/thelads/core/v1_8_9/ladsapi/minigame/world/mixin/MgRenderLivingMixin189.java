package com.thelads.core.v1_8_9.ladsapi.minigame.world.mixin;

import com.thelads.core.v1_8_9.ladsapi.minigame.world.GhostRender189;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.RendererLivingEntity;
import net.minecraft.entity.EntityLivingBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Ghosts are drawn by the normal player renderer as a visible dummy; while one is being drawn its model is blended at half opacity
 * (vanilla's own 15 % invisible look is only for spectators and teammates) and its name tag is left out (the ghost's label is
 * drawn by GhostRender189).
 */
@Mixin(RendererLivingEntity.class)
public abstract class MgRenderLivingMixin189 {
    @Inject(method = "renderModel", at = @At("HEAD"))
    private void lads$ghostBegin(EntityLivingBase entity, float a, float b, float c, float d, float e, float scale, CallbackInfo ci) {
        if (!GhostRender189.rendering) return;
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.color(1f, 1f, 1f, 0.5f);
    }

    @Inject(method = "renderModel", at = @At("RETURN"))
    private void lads$ghostEnd(EntityLivingBase entity, float a, float b, float c, float d, float e, float scale, CallbackInfo ci) {
        if (!GhostRender189.rendering) return;
        GlStateManager.disableBlend();
        GlStateManager.color(1f, 1f, 1f, 1f);
    }

    @Inject(method = "canRenderName(Lnet/minecraft/entity/EntityLivingBase;)Z", at = @At("HEAD"), cancellable = true)
    private void lads$ghostNoName(EntityLivingBase entity, CallbackInfoReturnable<Boolean> cir) {
        if (GhostRender189.rendering) cir.setReturnValue(false);
    }
}
