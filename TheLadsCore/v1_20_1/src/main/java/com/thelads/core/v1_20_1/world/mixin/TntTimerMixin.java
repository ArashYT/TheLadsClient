package com.thelads.core.v1_20_1.world.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.thelads.core.v1_20_1.world.World1201;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.TntRenderer;
import net.minecraft.world.entity.item.PrimedTnt;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** TNT Timer: the time left above primed TNT. It explodes on the tick its fuse reaches 0 (PrimedTnt.tick), so fuse - partial ticks are left. */
@Mixin(TntRenderer.class)
public class TntTimerMixin {
    @Inject(method = "render(Lnet/minecraft/world/entity/item/PrimedTnt;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", at = @At("TAIL"))
    private void lads$tntTimer(PrimedTnt tnt, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light, CallbackInfo callback) {
        World1201.tnt(tnt, tnt.getFuse() - partial, false, pose, buffers);
    }
}
