package com.thelads.core.v26_2.ladsapi.minigame.world.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.thelads.core.client.ladsapi.minigame.CameraState;
import com.thelads.core.client.ladsapi.minigame.Minigame;
import com.thelads.core.v26_2.ladsapi.minigame.world.CameraRender26;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Minigame camera shots: after vanilla placed the camera on its entity, a running shot moves the view (position, rotation, fov) and
 * marks it detached so the player's own model draws. Frustum and projection are built after both calls, so culling follows the shot.
 */
@Mixin(Camera.class)
public abstract class MgCameraMixin26 {
    @Shadow private boolean detached;

    @Shadow protected abstract void setPosition(double x, double y, double z);

    @Shadow protected abstract void setRotation(float yRot, float xRot);

    @WrapOperation(method = "update(Lnet/minecraft/client/DeltaTracker;)V", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/Camera;alignWithEntity(F)V"))
    private void lads$shot(Camera self, float partial, Operation<Void> original) {
        original.call(self, partial);
        CameraState.Pose p = CameraRender26.evaluate(self, Minigame.now());
        if (p == null) return;
        setPosition(p.x(), p.y(), p.z());
        setRotation((float) p.yaw(), (float) p.pitch());
        detached = true;
    }

    @WrapOperation(method = "update(Lnet/minecraft/client/DeltaTracker;)V", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/Camera;calculateFov(F)F"))
    private float lads$shotFov(Camera self, float partial, Operation<Float> original) {
        float vanilla = original.call(self, partial);
        CameraState.Pose p = CameraRender26.current();
        return p == null ? vanilla : (float) p.fov();
    }
}
