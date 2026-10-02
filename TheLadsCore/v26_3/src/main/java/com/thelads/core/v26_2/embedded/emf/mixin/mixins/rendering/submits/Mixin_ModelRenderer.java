package com.thelads.core.v26_2.embedded.emf.mixin.mixins.rendering.submits;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;

import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.thelads.core.v26_2.embedded.emf.EMF;
import com.thelads.core.v26_2.embedded.emf.EMFManager;
import com.thelads.core.v26_2.embedded.emf.models.IEMFModel;
import com.thelads.core.v26_2.embedded.emf.models.animation.state.EMFBipedPose;
import com.thelads.core.v26_2.embedded.emf.models.animation.state.EMFEntityRenderState;
import com.thelads.core.v26_2.embedded.emf.models.animation.state.EMFState;
import com.thelads.core.v26_2.embedded.emf.models.parts.EMFModelPartRoot;
import com.thelads.core.v26_2.embedded.emf.models.parts.EMFModelPartVanilla;
import com.thelads.core.v26_2.embedded.etf.ETF;
import com.thelads.core.v26_2.embedded.etf.features.state.ETFEntityRenderState;
import com.thelads.core.v26_2.embedded.etf.features.state.ETFState;
import com.thelads.core.v26_2.embedded.etf.features.state.ETFSubmitData;
import com.thelads.core.v26_2.embedded.etf.features.state.HoldsETFRenderState;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import com.thelads.core.v26_2.embedded.etf.utils.ETFEntity;

// Priority set for emf$endRender to go before the same target in ETF
@Mixin(value = ModelFeatureRenderer.class, priority = 900)
public class Mixin_ModelRenderer {


    @Inject(method =
            "prepareModel",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/Model;setupAnim(Ljava/lang/Object;)V",
            shift = At.Shift.AFTER))
    private <S> void emf$animate(final CallbackInfo ci,
                                 @Local(argsOnly = true)
                                 net.minecraft.client.renderer.feature.ModelFeatureRenderer.Submit<S> modelSubmit

    ) {
        // Apply a simple pose copy to armor if required
        applyArmorBipedPose(modelSubmit);
    }

    @Unique
    private <S> void applyArmorBipedPose(
            net.minecraft.client.renderer.feature.ModelFeatureRenderer.Submit<S> modelSubmit

    ) {

        ETFSubmitData data = ETFSubmitData.from(modelSubmit);
        if (data == null) return;
        EMFBipedPose pose = (EMFBipedPose) data.data.get("bipedPose");
        if (pose != null
                && modelSubmit.model() instanceof HumanoidModel<?> humanoidModel
                && !(modelSubmit.model().root() instanceof EMFModelPartRoot root && root.hasAnimation())
        ) {
            pose.applyTo(humanoidModel);
        }
    }

}