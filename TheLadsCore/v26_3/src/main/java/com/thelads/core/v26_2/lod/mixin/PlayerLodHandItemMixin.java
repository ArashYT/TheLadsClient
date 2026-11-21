package com.thelads.core.v26_2.lod.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.thelads.core.perf.PlayerLod;
import com.thelads.core.v26_2.lod.LodFlags26;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Player LOD: held items beyond the item distance. */
@Mixin(ItemInHandLayer.class)
abstract class PlayerLodHandItemMixin {
    @Inject(method = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/ArmedEntityRenderState;FF)V", at = @At("HEAD"), cancellable = true)
    private void lads$lod(PoseStack pose, SubmitNodeCollector collector, int light, ArmedEntityRenderState state, float a, float b, CallbackInfo ci) {
        if (LodFlags26.has(state, PlayerLod.ITEMS)) ci.cancel();
    }
}
