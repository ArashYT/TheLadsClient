package com.thelads.core.v1_8_9.world.mixin;

import com.thelads.core.v1_8_9.world.World189;
import net.minecraft.client.renderer.entity.RenderTNTPrimed;
import net.minecraft.entity.item.EntityTNTPrimed;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * TNT Timer: the time left above primed TNT. EntityTNTPrimed.onUpdate explodes when a fuse already at 0 counts down, so fuse + 1 -
 * partial ticks are left. The 1.8 protocol never sends the fuse: the client counts from 80 when the TNT appears, so a server that
 * shortens fuses is ahead of the timer.
 */
@Mixin(RenderTNTPrimed.class)
public abstract class TntTimerMixin {
    @Inject(method = "doRender(Lnet/minecraft/entity/item/EntityTNTPrimed;DDDFF)V", at = @At("TAIL"))
    private void lads$tntTimer(EntityTNTPrimed tnt, double x, double y, double z, float yaw, float partialTicks, CallbackInfo callback) {
        World189.tnt(tnt, x, y, z, tnt.fuse + 1 - partialTicks, false);
    }
}
