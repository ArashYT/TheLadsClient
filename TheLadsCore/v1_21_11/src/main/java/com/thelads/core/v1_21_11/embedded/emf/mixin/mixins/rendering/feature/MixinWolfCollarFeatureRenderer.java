package com.thelads.core.v1_21_11.embedded.emf.mixin.mixins.rendering.feature;


import net.minecraft.client.model.animal.wolf.WolfModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.layers.WolfCollarLayer;
import net.minecraft.client.renderer.entity.AgeableMobRenderer;
import net.minecraft.client.renderer.entity.state.WolfRenderState;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.thelads.core.v1_21_11.embedded.emf.EMF;
import com.thelads.core.v1_21_11.embedded.emf.mixin.mixins.accessor.AgeableMobRendererAccessor;
import com.thelads.core.v1_21_11.embedded.emf.models.parts.EMFModelPart;
import com.thelads.core.v1_21_11.embedded.emf.models.parts.EMFModelPartRoot;
import com.thelads.core.v1_21_11.embedded.emf.models.IEMFModel;
import com.thelads.core.v1_21_11.embedded.emf.EMFManager;
import com.thelads.core.v1_21_11.embedded.emf.utils.EMFUtils;
import com.thelads.core.v1_21_11.embedded.emf.utils.IEMFWolfCollarHolder;

@Mixin(WolfCollarLayer.class)
public abstract class MixinWolfCollarFeatureRenderer extends RenderLayer<
WolfRenderState, WolfModel
> {

    @Unique
    private static final ModelLayerLocation emf$collar_layer = new ModelLayerLocation(EMFUtils.res("minecraft", "wolf"), "collar");
    @Unique
    private static final ModelLayerLocation emf$collar_layer_baby = new ModelLayerLocation(EMFUtils.res("minecraft", "wolf_baby"), "collar");

    public MixinWolfCollarFeatureRenderer() { super(null); }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void setEmf$Model(RenderLayerParent<?, ?> featureRendererContext, CallbackInfo ci) {
        if (EMF.testForForgeLoadingError()) return;

        ModelPart collarModel = EMFManager.getInstance().injectIntoModelRootGetter(emf$collar_layer,
                WolfModel
                        .createMeshDefinition(CubeDeformation.NONE).getRoot().bake(64,32)
        );

        //separate the collar model, if it has a custom jem model or the base wolf has a custom jem model
        if (collarModel instanceof EMFModelPartRoot || ((IEMFModel) featureRendererContext.getModel()).emf$isEMFModel()) {
            try {
                if (featureRendererContext.getModel() instanceof
                    IEMFWolfCollarHolder
                        holder) {
                    holder.emf$setCollarModel(new
                            WolfModel
                            (collarModel));
                }
            } catch (Exception ignored) {
            }
        }


        ModelPart collarModelBaby = EMFManager.getInstance().injectIntoModelRootGetter(emf$collar_layer_baby,
                LayerDefinition.create(WolfModel.createMeshDefinition(CubeDeformation.NONE), 64, 32).apply(WolfModel.BABY_TRANSFORMER).bakeRoot()
        );



        //separate the collar model, if it has a custom jem model or the base wolf has a custom jem model
        if (collarModelBaby instanceof EMFModelPartRoot
                || // base model is custom
                EMFManager.getInstance().injectIntoModelRootGetter(new ModelLayerLocation(EMFUtils.res("minecraft", "wolf_baby"), "main"),
                        LayerDefinition.create(WolfModel.createMeshDefinition(CubeDeformation.NONE), 64, 32).bakeRoot()
                    ) instanceof EMFModelPart) {
            try {
                // store in primary model
                if (featureRendererContext instanceof AgeableMobRendererAccessor ageModelsAccessor
                        && ageModelsAccessor.getBabyModel() instanceof
                        IEMFWolfCollarHolder
                                holder) {
                    holder.emf$setCollarModel(new
                            WolfModel
                            (collarModelBaby));
                }
            } catch (Exception ignored) {}
        }

    }

    @Override
    public @NotNull
        WolfModel
    getParentModel() {
        var base = super.getParentModel(); // already either adult or baby model

        if (base instanceof
                IEMFWolfCollarHolder
                        holder
                && holder.emf$hasCollarModel()) {
            //noinspection unchecked
            var model = (
                    WolfModel
                    ) holder.emf$getCollarModel();

            return model;
        }
        return base;
    }
}
