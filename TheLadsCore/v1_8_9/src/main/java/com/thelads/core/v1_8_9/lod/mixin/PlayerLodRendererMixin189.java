package com.thelads.core.v1_8_9.lod.mixin;

import com.thelads.core.perf.PlayerLod;
import com.thelads.core.v1_8_9.lod.PlayerLod189;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.model.ModelPlayer;
import net.minecraft.client.renderer.entity.RenderPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Player LOD: picks the cut-offs for the player being drawn and hides the flat outer skin parts (layers and name tags have their own hooks). */
@Mixin(RenderPlayer.class)
public abstract class PlayerLodRendererMixin189 {
    @Inject(method = "doRender(Lnet/minecraft/client/entity/AbstractClientPlayer;DDDFF)V", at = @At("HEAD"), require = 0)
    private void lads$lodBegin(AbstractClientPlayer player, double x, double y, double z, float yaw, float partial, CallbackInfo ci) {
        PlayerLod189.begin(player, x, y, z);
    }

    @Inject(method = "doRender(Lnet/minecraft/client/entity/AbstractClientPlayer;DDDFF)V", at = @At("RETURN"), require = 0)
    private void lads$lodEnd(AbstractClientPlayer player, double x, double y, double z, float yaw, float partial, CallbackInfo ci) {
        PlayerLod189.bits = 0;
    }

    /** setModelVisibilities runs inside doRender and rewrites every part each call, so GUI renders get them back. */
    @Inject(method = "setModelVisibilities", at = @At("RETURN"), require = 0)
    private void lads$lodOuter(AbstractClientPlayer player, CallbackInfo ci) {
        if (!PlayerLod189.has(PlayerLod.OUTER)) return;
        ModelPlayer model = ((RenderPlayer) (Object) this).getMainModel();
        // showModel, not isHidden: vanilla's setModelVisibilities rewrites showModel on every call, isHidden is only ours (skin layers) to reset.
        model.bipedHeadwear.showModel = model.bipedBodyWear.showModel = model.bipedLeftArmwear.showModel = model.bipedRightArmwear.showModel
            = model.bipedLeftLegwear.showModel = model.bipedRightLegwear.showModel = false;
    }
}
