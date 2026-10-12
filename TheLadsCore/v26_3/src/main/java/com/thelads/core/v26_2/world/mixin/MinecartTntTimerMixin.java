package com.thelads.core.v26_2.world.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.thelads.core.v26_2.world.World26;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.AbstractMinecartRenderer;
import net.minecraft.client.renderer.entity.state.MinecartRenderState;
import net.minecraft.client.renderer.entity.state.MinecartTntRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * TNT Timer: the time left above a lit TNT minecart. The state's fuseRemainingInTicks is fuse - partial + 1 (-1: not lit); the fuse
 * counts down to 0 and the cart explodes on the tick after (MinecartTNT.tick), so that is the time left.
 */
@Mixin(AbstractMinecartRenderer.class)
public class MinecartTntTimerMixin {
    @Inject(method = "submit(Lnet/minecraft/client/renderer/entity/state/MinecartRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V", at = @At("TAIL"))
    private void lads$tntTimer(MinecartRenderState state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera, CallbackInfo callback) {
        if (state instanceof MinecartTntRenderState tnt && tnt.fuseRemainingInTicks >= 0)
            World26.tnt(state, tnt.fuseRemainingInTicks, true, pose, collector, camera);
    }
}
