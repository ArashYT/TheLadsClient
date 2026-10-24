package com.thelads.core.v1_20_1.game.mixin;

import com.thelads.core.modules.FreelookModule;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Freelook on 1.20.1: Camera.setup turns the camera to Freelook's yaw and pitch instead of the entity's view; the third-person
 * distance check, frustum, name tags and particles all follow the camera. The front view's mirror still applies afterwards.
 */
@Mixin(Camera.class)
public abstract class FreelookCameraMixin {
    @Redirect(method = "setup", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getViewYRot(F)F"))
    private float lads$freelookYaw(Entity entity, float partialTick) {
        FreelookModule look = FreelookModule.get();
        return look.detached() ? look.yaw() : entity.getViewYRot(partialTick);
    }

    @Redirect(method = "setup", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getViewXRot(F)F"))
    private float lads$freelookPitch(Entity entity, float partialTick) {
        FreelookModule look = FreelookModule.get();
        return look.detached() ? look.pitch() : entity.getViewXRot(partialTick);
    }
}
