package com.thelads.core.v1_8_9.mixin.visuals;

import com.thelads.core.modules.MobOverlayModule;
import com.thelads.core.modules.MobSizeModule;
import com.thelads.core.v1_8_9.feature.PvpVisuals189;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.entity.RendererLivingEntity;
import net.minecraft.entity.EntityLivingBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mob Size: the scale goes right after the renderer's own preRenderCallback, inside doRender's pushed matrix and about the mob's
 * feet, as vanilla scales giants and slimes; the matrix pop at the end of doRender takes it off again. Mob Overlay: the outline
 * pass colors an entity by setScoreTeamColor's white (no team); a mob the module outlines gets its kind's color instead.
 */
@Mixin(RendererLivingEntity.class)
public abstract class RendererLivingEntityVisualsMixin189 {
    /** What setScoreTeamColor is coloring (the render thread only). */
    @Unique private static int ladsOutlineKind;

    @Inject(method = "doRender(Lnet/minecraft/entity/EntityLivingBase;DDDFF)V", at = @At(value = "INVOKE", shift = At.Shift.AFTER,
        target = "Lnet/minecraft/client/renderer/entity/RendererLivingEntity;preRenderCallback(Lnet/minecraft/entity/EntityLivingBase;F)V"))
    private void ladsMobSize(EntityLivingBase entity, double x, double y, double z, float yaw, float partialTicks, CallbackInfo ci) {
        int kind = PvpVisuals189.mobKind(entity);
        if (kind == MobOverlayModule.OTHER) return;
        float scale = MobSizeModule.scale(kind == MobOverlayModule.HOSTILE);
        if (scale != 1) GlStateManager.scale(scale, scale, scale);
    }

    @Inject(method = "setScoreTeamColor(Lnet/minecraft/entity/EntityLivingBase;)Z", at = @At("HEAD"))
    private void ladsOutlineEntity(EntityLivingBase entity, CallbackInfoReturnable<Boolean> cir) {
        ladsOutlineKind = PvpVisuals189.mobKind(entity);
    }

    /**
     * Mob Overlay without the outline pass (Fast Render keeps the framebuffer off): the mob is tinted in its kind's color, through the
     * same overlay as a creeper's flash; a hurt flash still wins (setBrightness).
     */
    @Inject(method = "getColorMultiplier(Lnet/minecraft/entity/EntityLivingBase;FF)I", at = @At("HEAD"), cancellable = true)
    private void ladsMobTint(EntityLivingBase entity, float brightness, float partialTicks, CallbackInfoReturnable<Integer> cir) {
        if (!MobOverlayModule.active() || OpenGlHelper.isFramebufferEnabled()) return;
        int kind = PvpVisuals189.mobKind(entity);
        if (MobOverlayModule.outlined(kind)) cir.setReturnValue(0x60000000 | MobOverlayModule.color(kind));
    }

    @ModifyConstant(method = "setScoreTeamColor(Lnet/minecraft/entity/EntityLivingBase;)Z", constant = @Constant(intValue = 16777215))
    private int ladsOutlineColor(int white) {
        return MobOverlayModule.outlined(ladsOutlineKind) ? MobOverlayModule.color(ladsOutlineKind) : white;
    }
}
