package com.thelads.core.v1_8_9.lod.mixin;

import com.thelads.core.perf.PlayerLod;
import com.thelads.core.v1_8_9.lod.PlayerLod189;
import net.minecraft.client.renderer.entity.RendererLivingEntity;
import net.minecraft.entity.EntityLivingBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Player LOD: name tags beyond the name distance (bits are only set while another player is drawn). OptiFine patches this class: require = 0. */
@Mixin(RendererLivingEntity.class)
public abstract class PlayerLodNameMixin189 {
    @Inject(method = "renderName(Lnet/minecraft/entity/EntityLivingBase;DDD)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void lads$lodName(EntityLivingBase entity, double x, double y, double z, CallbackInfo ci) {
        if (PlayerLod189.has(PlayerLod.NAME)) ci.cancel();
    }
}
