package com.thelads.core.v1_20_1.embedded.etf.mixin.mixins.entity.renderer;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.thelads.core.v1_20_1.embedded.etf.ETF;
import com.thelads.core.v1_20_1.embedded.etf.features.state.ETFState;
import com.thelads.core.v1_20_1.embedded.etf.features.state.HoldsETFRenderState;
import com.thelads.core.v1_20_1.embedded.etf.utils.ETFEntity;

@Mixin(EntityRenderer.class)
public abstract class MixinEntityRenderer<T extends Entity
       > {

    @ModifyReturnValue(method = "getPackedLightCoords", at = @At(value = "RETURN"))
    private int etf$vanillaLightOverrideCancel(int light, @Local(argsOnly = true) T entity, @Local(argsOnly = true) float tickDelta) {
        //if need to override vanilla brightness behaviour
        //change return with overridden light value still respecting higher block and sky lights
        return ETF.config().getConfig().getLightOverride(
                entity,
                tickDelta,
                light);
    }

    private static final String RENDER = "render";

    @Inject(method = RENDER, at = @At(value = "HEAD"))
    private void etf$protectPostRenderersLikeNametag(final CallbackInfo ci) {
        ETFState.pushRenderLayerModifyState(false);
    }

    @Inject(method = RENDER, at = @At(value = "TAIL"))
    private void etf$revertForRenderersThatCallSuperFirst(final CallbackInfo ci) {
        ETFState.popRenderLayerModifyState(); // see minecart rendering
    }

}