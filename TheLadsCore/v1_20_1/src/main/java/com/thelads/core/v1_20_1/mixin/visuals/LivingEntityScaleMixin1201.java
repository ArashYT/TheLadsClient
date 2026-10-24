package com.thelads.core.v1_20_1.mixin.visuals;

import com.mojang.blaze3d.vertex.PoseStack;
import com.thelads.core.modules.MobSizeModule;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mob Size on 1.20.1: the scale goes right after the renderer's own scale(), inside render's pushed pose and about the mob's feet,
 * as vanilla sizes slimes and giants; render's popPose takes it off again. Players and armor stands are not mobs.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityScaleMixin1201 {
    // require = 0: a modpack mod that wraps this same call (Zero SMP, the SMP pack) leaves the hook out instead of crashing the game.
    @Inject(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
        at = @At(value = "INVOKE", shift = At.Shift.AFTER,
            target = "Lnet/minecraft/client/renderer/entity/LivingEntityRenderer;scale(Lnet/minecraft/world/entity/LivingEntity;Lcom/mojang/blaze3d/vertex/PoseStack;F)V"), require = 0)
    private void ladsMobSize(LivingEntity entity, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light, CallbackInfo ci) {
        if (!(entity instanceof Mob)) return;
        float scale = MobSizeModule.scale(entity instanceof Enemy);
        if (scale != 1) pose.scale(scale, scale, scale);
    }
}
