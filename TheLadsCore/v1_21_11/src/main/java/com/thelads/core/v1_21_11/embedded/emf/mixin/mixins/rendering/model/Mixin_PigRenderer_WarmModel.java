package com.thelads.core.v1_21_11.embedded.emf.mixin.mixins.rendering.model;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.model.AdultAndBabyModelPair;
import net.minecraft.client.model.animal.pig.PigModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.PigRenderer;
import net.minecraft.client.renderer.entity.state.PigRenderState;
import net.minecraft.world.entity.animal.pig.Pig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.thelads.core.v1_21_11.embedded.emf.EMF;
import com.thelads.core.v1_21_11.embedded.emf.EMFManager;

import com.thelads.core.v1_21_11.embedded.emf.utils.EMFUtils;
import com.thelads.core.v1_21_11.embedded.etf.features.state.HoldsETFRenderState;

@Mixin(PigRenderer.class)
public abstract class Mixin_PigRenderer_WarmModel extends MobRenderer<Pig, PigRenderState, PigModel> {

    @Unique
    private static final ModelLayerLocation emf$warm =
            new ModelLayerLocation(EMFUtils.res("minecraft", "warm_pig"), "main");
    @Unique
    private static final ModelLayerLocation emf$warm_baby =
            new ModelLayerLocation(EMFUtils.res("minecraft", "warm_pig_baby"), "main");

    @Unique
    private static final ModelLayerLocation emf$cold_baby =
            new ModelLayerLocation(EMFUtils.res("minecraft", "cold_pig_baby"), "main");

    @Unique
    private AdultAndBabyModelPair<PigModel> models = null;

    @Unique
    private PigModel coldBabyModel = null;

    @Inject(method = "<init>", at = @At(value = "TAIL"))
    private void emf$createWarmModels(EntityRendererProvider.Context context, CallbackInfo ci) {
        if (EMF.testForForgeLoadingError()) return;


        models = new AdultAndBabyModelPair<>(
                new PigModel(EMFManager.getInstance().injectIntoModelRootGetter(emf$warm,
                        PigModel.createBodyLayer(CubeDeformation.NONE).bakeRoot())),
                new PigModel(EMFManager.getInstance().injectIntoModelRootGetter(emf$warm_baby,
                        PigModel.createBodyLayer(CubeDeformation.NONE).apply(PigModel.BABY_TRANSFORMER).bakeRoot()))
        );
    }


    private static final String RENDER_METHOD = "submit(Lnet/minecraft/client/renderer/entity/state/PigRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/CameraRenderState;)V";

    @Inject(method = RENDER_METHOD, at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/renderer/entity/MobRenderer;submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/CameraRenderState;)V"
    ))
    private void emf$warmModel(final CallbackInfo ci, @Local(argsOnly = true) PigRenderState pigRenderState) {
        var state = ((HoldsETFRenderState) pigRenderState).etf$getState();
        if (state != null && state.entity() != null && state.entity() instanceof Pig oink
                && "minecraft:warm".equals(oink.getVariant().getRegisteredName())) {
            this.model = models.getModel(oink.isBaby());
        }
    }


    @SuppressWarnings("DataFlowIssue")
    public Mixin_PigRenderer_WarmModel() { super(null, null, 0); }
}



