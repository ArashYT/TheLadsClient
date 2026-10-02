package com.thelads.core.v1_21_11.embedded.etf.mixin.mixins.mods.imediatelyfast;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;
import com.thelads.core.v1_21_11.embedded.immediatelyfast.feature.core.BatchableBufferSource; // The Lads: ImmediatelyFast is embedded too
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import com.thelads.core.v1_21_11.embedded.etf.compat.SodiumGetBufferInjector;
import com.thelads.core.v1_21_11.embedded.etf.features.state.ETFState;
import com.thelads.core.v1_21_11.embedded.etf.utils.URenderTypeToVertexConsumer;

@Pseudo
@Mixin(value = BatchableBufferSource.class, priority = 800)
public class MixinBatchableBufferSource {

    @ModifyVariable(
            method = "getBuffer",
            at = @At(value = "HEAD"),
            ordinal = 0, argsOnly = true, require = 0)
    private RenderType etf$modifyRenderLayer2(RenderType value) {
        return ETFState.modifyRenderLayerIfRequired(value);
    }

    @Inject(
            method = "getBuffer",
            at = @At(value = "RETURN"), require = 0)
    private void etf$injectIntoGetBufferReturn(RenderType renderLayer, CallbackInfoReturnable<VertexConsumer> cir) {

        var returned = cir.getReturnValue();
        ETFState.insertETFDataIntoVertexConsumer(new URenderTypeToVertexConsumer((MultiBufferSource) this), renderLayer, returned);

        //todo is this required with immedeately fast?
        // quarantined class to contain all sodium interaction
        // sodium ExtendedBufferBuilder classes contain a delegate that must instead have the above data passed into
        SodiumGetBufferInjector.inject(new URenderTypeToVertexConsumer((MultiBufferSource) this), renderLayer, returned);
    }

}