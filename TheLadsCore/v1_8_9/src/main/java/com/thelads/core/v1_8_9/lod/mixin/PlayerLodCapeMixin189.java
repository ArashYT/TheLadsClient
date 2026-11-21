package com.thelads.core.v1_8_9.lod.mixin;

import com.thelads.core.perf.PlayerLod;
import com.thelads.core.v1_8_9.lod.PlayerLod189;
import net.minecraft.client.renderer.entity.layers.LayerCape;
import net.minecraft.client.entity.AbstractClientPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Player LOD: capes beyond the cape distance. */
@Mixin(LayerCape.class)
public abstract class PlayerLodCapeMixin189 {
    @Inject(method = "doRenderLayer(Lnet/minecraft/client/entity/AbstractClientPlayer;FFFFFFF)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void lads$lod(AbstractClientPlayer entity, float a, float b, float c, float d, float e, float f, float g, CallbackInfo ci) {
        if (PlayerLod189.has(PlayerLod.CAPE)) ci.cancel();
    }
}
