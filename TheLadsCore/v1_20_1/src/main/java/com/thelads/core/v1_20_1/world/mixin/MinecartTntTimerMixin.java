package com.thelads.core.v1_20_1.world.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.thelads.core.v1_20_1.world.World1201;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.MinecartRenderer;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.entity.vehicle.MinecartTNT;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * TNT Timer: the time left above a lit TNT minecart (fuse -1 = not lit). It counts down to 0 and explodes on the tick after
 * (MinecartTNT.tick), so fuse + 1 - partial ticks are left.
 */
@Mixin(MinecartRenderer.class)
public class MinecartTntTimerMixin {
    @Inject(method = "render(Lnet/minecraft/world/entity/vehicle/AbstractMinecart;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", at = @At("TAIL"))
    private void lads$tntTimer(AbstractMinecart cart, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light, CallbackInfo callback) {
        if (cart instanceof MinecartTNT tnt && tnt.getFuse() > -1) World1201.tnt(cart, tnt.getFuse() + 1 - partial, true, pose, buffers);
    }
}
