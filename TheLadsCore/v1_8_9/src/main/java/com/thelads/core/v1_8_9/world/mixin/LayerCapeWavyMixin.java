package com.thelads.core.v1_8_9.world.mixin;

import com.thelads.core.v1_8_9.world.World189;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.RenderPlayer;
import net.minecraft.client.renderer.entity.layers.LayerCape;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Wavy Capes: the cape mesh instead of the rigid cape (World189.cape decides with the layer's own checks; false leaves it to vanilla). */
@Mixin(LayerCape.class)
public abstract class LayerCapeWavyMixin {
    @Shadow @Final private RenderPlayer playerRenderer;

    @Inject(method = "doRenderLayer(Lnet/minecraft/client/entity/AbstractClientPlayer;FFFFFFF)V", at = @At("HEAD"), cancellable = true)
    private void lads$wavyCape(AbstractClientPlayer player, float swing, float swingAmount, float partialTicks, float age, float headYaw, float headPitch,
                               float scale, CallbackInfo callback) {
        if (World189.cape(player, partialTicks, playerRenderer)) callback.cancel();
    }
}
