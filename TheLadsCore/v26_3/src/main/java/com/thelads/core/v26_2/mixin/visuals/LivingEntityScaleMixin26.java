package com.thelads.core.v26_2.mixin.visuals;

import com.mojang.blaze3d.vertex.PoseStack;
import com.thelads.core.modules.MobOverlayModule;
import com.thelads.core.modules.MobSizeModule;
import com.thelads.core.v26_2.feature.PvpVisuals26;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mob Size on 26.3: the scale goes right after the renderer's own scale(), inside submit's pushed pose and about the mob's feet, as
 * vanilla sizes slimes and giants; submit's popPose takes it off again. The mob's kind comes from its render state (players and armor
 * stands are not mobs).
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityScaleMixin26 {
    @Inject(method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;"
        + "Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
        at = @At(value = "INVOKE", shift = At.Shift.AFTER,
            target = "Lnet/minecraft/client/renderer/entity/LivingEntityRenderer;scale(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;)V"))
    private void ladsMobSize(LivingEntityRenderState state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera, CallbackInfo ci) {
        int kind = ((PvpVisuals26.Visual) state).lads$kind();
        if (kind != MobOverlayModule.HOSTILE && kind != MobOverlayModule.PASSIVE) return;
        float scale = MobSizeModule.scale(kind == MobOverlayModule.HOSTILE);
        if (scale != 1) pose.scale(scale, scale, scale);
    }
}
