package com.thelads.core.v1_21_1.embedded.etf.mixin.mixins.entity.misc;

import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.thelads.core.v1_21_1.embedded.etf.features.state.ETFEntityRenderState;
import com.thelads.core.v1_21_1.embedded.etf.features.state.ETFState;
import com.thelads.core.v1_21_1.embedded.etf.features.state.HoldsETFRenderState;
import com.thelads.core.v1_21_1.embedded.etf.utils.ETFEntity;

@Mixin(EntityRenderDispatcher.class)
public class MixinEntityRenderDispatcher {
    @Inject(method = "render",
        at = @At(value = "HEAD"))
    private <E extends net.minecraft.world.entity.Entity> void etf$grabContext(CallbackInfo ci, @Local(argsOnly = true) E entity, @Share("state_etf") LocalRef<ETFEntityRenderState> stateRef) {
        var etf = ETFEntityRenderState.forEntity((ETFEntity) entity);
        if (etf != null) {
            ETFState.mount(etf);
        }
        stateRef.set(etf);
    }

    @Inject(method =
                "render",
            at = @At(value = "RETURN"))
    private void etf$clearContext(CallbackInfo ci, @Share("state_etf") LocalRef<ETFEntityRenderState> stateRef) {
        if (stateRef.get() != null) {
            ETFState.stackVerify(stateRef.get());
            ETFState.unMount();
        }
    }

}
