package com.thelads.core.v1_8_9.lod.mixin;

import com.thelads.core.perf.PlayerLod;
import com.thelads.core.v1_8_9.lod.PlayerLod189;
import net.minecraft.client.renderer.entity.layers.LayerCustomHead;
import net.minecraft.entity.EntityLivingBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Player LOD: head items beyond the item distance. */
@Mixin(LayerCustomHead.class)
public abstract class PlayerLodHeadItemMixin189 {
    @Inject(method = "doRenderLayer(Lnet/minecraft/entity/EntityLivingBase;FFFFFFF)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void lads$lod(EntityLivingBase entity, float a, float b, float c, float d, float e, float f, float g, CallbackInfo ci) {
        if (PlayerLod189.has(PlayerLod.ITEMS)) ci.cancel();
    }
}
