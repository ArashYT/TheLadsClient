package com.thelads.core.v26_2.world.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.thelads.core.v26_2.world.World26;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.TntRenderer;
import net.minecraft.client.renderer.entity.state.TntRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * TNT Timer: the time left above primed TNT. The state's fuseRemainingInTicks is fuse - partial + 1, and the TNT explodes on the tick
 * its fuse reaches 0 (PrimedTnt.tick), so one tick less is left.
 */
@Mixin(TntRenderer.class)
public class TntTimerMixin {
    @Inject(method = "submit(Lnet/minecraft/client/renderer/entity/state/TntRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V", at = @At("TAIL"))
    private void lads$tntTimer(TntRenderState state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera, CallbackInfo callback) {
        World26.tnt(state, state.fuseRemainingInTicks - 1, false, pose, collector, camera);
    }
}
