package com.thelads.core.v1_8_9.mixin;

import com.thelads.core.v1_8_9.feature.OldAnimations189;
import net.minecraft.client.renderer.entity.RenderFish;
import net.minecraft.entity.projectile.EntityFishHook;
import net.minecraft.util.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 1.7 Animations, 1.7 fishing rod position: the first-person line starts at 1.7's rod tip. doRender turns its line origin by the
 * angler's pitch first (OptiFine leaves RenderFish alone); that first turn starts from 1.7's origin instead.
 */
@Mixin(RenderFish.class)
public abstract class RenderFishMixin {
    @Redirect(method = "doRender(Lnet/minecraft/entity/projectile/EntityFishHook;DDDFF)V", at = @At(value = "INVOKE", ordinal = 0,
        target = "Lnet/minecraft/util/Vec3;rotatePitch(F)Lnet/minecraft/util/Vec3;"), require = 1, allow = 1)
    private Vec3 ladsLineOrigin(Vec3 origin, float pitch, EntityFishHook hook, double x, double y, double z, float yaw, float partialTicks) {
        return OldAnimations189.rodLine(origin, hook.angler).rotatePitch(pitch);
    }
}
