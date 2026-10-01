package com.thelads.core.v26_2.embedded.etf.mixin.mixins.vertexconsumers;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.feature.RenderTypeFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import com.thelads.core.v26_2.embedded.etf.compat.SodiumGetBufferInjector;
import com.thelads.core.v26_2.embedded.etf.features.state.ETFState;
import com.thelads.core.v26_2.embedded.etf.utils.URenderTypeToVertexConsumer;

@Mixin(value = RenderTypeFeatureRenderer.class, priority = 800)
public class MixinVertexConsumerProvider$Immediate {


    @ModifyVariable(
            method = "getVertexBuilder",
            at = @At(value = "HEAD"),
            index = 1, argsOnly = true)
    private RenderType etf$modifyRenderLayer(RenderType value) {
        return ETFState.modifyRenderLayerIfRequired(value);
    }


    @Inject(
            method = "getVertexBuilder",
            at = @At(value = "RETURN"))
    private void etf$injectIntoGetBufferReturn(RenderType renderLayer, CallbackInfoReturnable<VertexConsumer> cir) {
        var returned = cir.getReturnValue();
        var uSource = new URenderTypeToVertexConsumer((RenderTypeFeatureRenderer) (Object) this);
        ETFState.insertETFDataIntoVertexConsumer(uSource, renderLayer, returned);
        //quarantined class to contain all sodium interaction
        //sodium ExtendedBufferBuilder classes contain a delegate that must instead have the above data passed into
        SodiumGetBufferInjector.inject(uSource, renderLayer, returned);
    }

}
