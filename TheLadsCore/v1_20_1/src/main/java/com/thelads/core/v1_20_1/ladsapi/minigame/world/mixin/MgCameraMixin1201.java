package com.thelads.core.v1_20_1.ladsapi.minigame.world.mixin;

import com.thelads.core.client.ladsapi.minigame.CameraState;
import com.thelads.core.client.ladsapi.minigame.Minigame;
import com.thelads.core.v1_20_1.ladsapi.minigame.world.CameraRender1201;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Minigame camera shots: after vanilla placed the camera on its entity, a running shot moves the view (position, rotation) and
 * marks it detached so the player's own model draws. Only the render camera moves: picks and what is sent use the entity itself.
 */
@Mixin(value = Camera.class, priority = 1100)
public abstract class MgCameraMixin1201 {
    @Shadow private boolean detached;

    @Shadow protected abstract void setPosition(double x, double y, double z);

    @Shadow protected abstract void setRotation(float yRot, float xRot);

    @Inject(method = "setup", at = @At("RETURN"))
    private void lads$shot(BlockGetter level, Entity entity, boolean detachedIn, boolean mirror, float partial, CallbackInfo ci) {
        CameraState.Pose p = CameraRender1201.evaluate((Camera) (Object) this, Minigame.now());
        if (p == null) return;
        setPosition(p.x(), p.y(), p.z());
        setRotation((float) p.yaw(), (float) p.pitch());
        detached = true;
    }
}
