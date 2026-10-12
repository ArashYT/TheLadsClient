package com.thelads.core.v1_8_9.world.mixin;

import com.thelads.core.v1_8_9.world.World189;
import net.minecraft.client.renderer.entity.RenderMinecart;
import net.minecraft.entity.item.EntityMinecart;
import net.minecraft.entity.item.EntityMinecartTNT;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** TNT Timer: the time left above a lit TNT minecart (fuse -1: not lit); it counts down to 0 and explodes on the tick after. */
@Mixin(RenderMinecart.class)
public abstract class MinecartTntTimerMixin {
    @Inject(method = "doRender(Lnet/minecraft/entity/item/EntityMinecart;DDDFF)V", at = @At("TAIL"))
    private void lads$tntTimer(EntityMinecart cart, double x, double y, double z, float yaw, float partialTicks, CallbackInfo callback) {
        if (cart instanceof EntityMinecartTNT && ((EntityMinecartTNT) cart).getFuseTicks() > -1)
            World189.tnt(cart, x, y, z, ((EntityMinecartTNT) cart).getFuseTicks() + 1 - partialTicks, true);
    }
}
