package com.thelads.core.v26_2.embedded.emf.mixin.mixins.rendering;


import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import com.thelads.core.v26_2.embedded.etf.features.state.ETFState;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.thelads.core.v26_2.embedded.emf.models.animation.state.EMFBipedPose;
import com.thelads.core.v26_2.embedded.emf.models.animation.state.EMFEntityRenderState;
import com.thelads.core.v26_2.embedded.emf.models.animation.state.EMFState;
import com.thelads.core.v26_2.embedded.emf.models.parts.EMFModelPartRoot;
import com.thelads.core.v26_2.embedded.emf.models.IEMFModel;
import com.thelads.core.v26_2.embedded.emf.EMFManager;
import com.thelads.core.v26_2.embedded.emf.utils.EMFAnimationPauseHandler;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import com.thelads.core.v26_2.embedded.etf.features.state.HoldsETFRenderState;

@Mixin(LivingEntityRenderer.class)
public abstract class MixinLivingEntityRenderer<T extends LivingEntity, S extends LivingEntityRenderState, M extends EntityModel<? super S>> extends EntityRenderer<T, S> implements RenderLayerParent<S, M> {

    @Shadow
    public abstract Identifier getTextureLocation(final S livingEntityRenderState);



    @Shadow
    protected M model;

    @Shadow
    public abstract M getModel();

    @SuppressWarnings("unused")
    protected MixinLivingEntityRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Inject(method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/EntityModel;setupAnim(Ljava/lang/Object;)V",
                    shift = At.Shift.AFTER))
    private void falseAnimation(CallbackInfo ci, @Local(argsOnly = true) S renderState) {
        // animate so that dependant layers can read the positions (only applies if they set their matrix prior to submission)
        IEMFModel model = (IEMFModel) this.model;
        if (model.emf$isEMFModel() && model.emf$getEMFRootModel().hasAnimation()) {

            if (EMFAnimationPauseHandler.shouldAnimationsPause(EMFEntityRenderState.from(renderState)))
                return;

            model.emf$getEMFRootModel().animateNoPause();

            // Store the biped pose in the render state for use by layers that need it.
            if (getModel() instanceof HumanoidModel<?> humanoidModel) {
                var state = (EMFEntityRenderState) ((HoldsETFRenderState) renderState).etf$getState();
                if (state != null) state.setBipedPose(new EMFBipedPose(humanoidModel));
            }

        }
    }


    @ModifyExpressionValue(method = "getRenderType", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/entity/LivingEntityRenderer;getTextureLocation(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;)Lnet/minecraft/resources/Identifier;"
    ))
    private Identifier emf$getTextureRedirect(final Identifier original){
        // Internal texture overrides are really hacky, if it can be set once here, do that instead
        if (((IEMFModel) model).emf$isEMFModel()) {
            EMFModelPartRoot root = ((IEMFModel) model).emf$getEMFRootModel();
            if (root != null) {
                // Will strip away redundant internal texture overrides to simplify for applying here only
                Identifier texture = root.getTopLevelJemTexture();
                if (texture != null)
                    return texture;
            }
        }

        return original;

    }

    private static final String RENDER = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V";


    @Inject(method = RENDER, at = @At(value = "INVOKE", target = "Ljava/util/List;iterator()Ljava/util/Iterator;"))
    private void emf$grabEntity(CallbackInfo ci, @Share("stateCaptureStatic") LocalRef<EMFState.EMFStateStaticSnapshot> stateCaptureStatic) {
        EMFState.isLayerPhase = true;
        EMFState.isMainPhase = false;

        // Backup just in case state doesn't change but the statics do and get cancelled without reset
        stateCaptureStatic.set(EMFState.captureStatics());
    }

    @Inject(method = RENDER, at = @At(value = "INVOKE", target = "Ljava/util/Iterator;next()Ljava/lang/Object;"))
    private void emf$eachFeatureLoop(CallbackInfo ci, @Share("stateCaptureStatic") LocalRef<EMFState.EMFStateStaticSnapshot> stateCaptureStatic) {
        //todo needed for stray bogged drowned outer layers in 1.21.2+
        //check its needed for 1.21.1
        EMFManager.getInstance().entityRenderCount++;
        EMFState.isLayerPhase = true;

        // Assert state each call in case things got cancelled and couldn't be undone
        if (stateCaptureStatic.get() != null) {
            stateCaptureStatic.get().restoreStatics();
        }
    }

    @Inject(method = RENDER, at = @At("HEAD"))
    private void emf$preRender(CallbackInfo ci) {
        EMFState.isLayerPhase = false;
        EMFState.isMainPhase = true;
    }

    @Inject(method = RENDER, at = @At("TAIL"))
    private void emf$postRender(CallbackInfo ci, @Share("stateCaptureStatic") LocalRef<EMFState.EMFStateStaticSnapshot> stateCaptureStatic) {
        EMFState.isLayerPhase = false;
        EMFState.isMainPhase = false;
        if (stateCaptureStatic.get() != null) {
            stateCaptureStatic.get().restoreStatics();
        }
    }

}
