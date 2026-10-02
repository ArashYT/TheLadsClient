package com.thelads.core.v26_2.embedded.etf.mixin.mixins.entity.renderer.feature;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import com.thelads.core.v26_2.embedded.etf.features.state.ETFState;
import com.thelads.core.v26_2.embedded.etf.utils.ETFRenderLayerWithTexture;
import com.thelads.core.v26_2.embedded.etf.utils.ETFUtils2;

import static com.thelads.core.v26_2.embedded.etf.ETF.EYES_FEATURE_LIGHT_VALUE;

@Mixin(EyesLayer.class)
public abstract class MixinEyeFeatureRenderer {

    @ModifyExpressionValue(method = "submit", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/layers/EyesLayer;renderType()Lnet/minecraft/client/renderer/rendertype/RenderType;"))
    private RenderType etf$allowModifiableEyes(RenderType layer) {
        //the eye texture render layers are hard coded in vanilla and do not recalculate each time
        if (layer instanceof ETFRenderLayerWithTexture etf && etf.etf$getId().isPresent()) {
            Identifier id = etf.etf$getId().get();
            Identifier variant = ETFUtils2.getETFVariantNotNullForInjector(id);
            if (!id.equals(variant)) {
                //if there is a variant then lets send a layer with it
                ETFState.pushRenderLayerModifyState(false);

                RenderType layer2 =
                        net.minecraft.client.renderer.rendertype.RenderTypes
                                .eyes(variant);

                ETFState.popRenderLayerModifyState();

                return layer2;
            }
        }
        //no need to variate so lets just send the hard coded final layer
        return layer;
    }

    @SuppressWarnings("SameReturnValue")
    @ModifyVariable(method =
            "submit"
            , at = @At(value = "HEAD"), argsOnly = true, ordinal = 0)
    private int emf$markEyeLight(int i) {
        return EYES_FEATURE_LIGHT_VALUE;
    }

}


