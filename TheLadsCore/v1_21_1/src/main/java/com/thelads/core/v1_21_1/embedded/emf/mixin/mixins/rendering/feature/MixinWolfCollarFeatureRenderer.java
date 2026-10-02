package com.thelads.core.v1_21_1.embedded.emf.mixin.mixins.rendering.feature;


import net.minecraft.client.model.WolfModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.layers.WolfCollarLayer;
import net.minecraft.world.entity.animal.Wolf;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.thelads.core.v1_21_1.embedded.emf.EMF;
import com.thelads.core.v1_21_1.embedded.emf.mixin.mixins.accessor.AgeableMobRendererAccessor;
import com.thelads.core.v1_21_1.embedded.emf.models.parts.EMFModelPart;
import com.thelads.core.v1_21_1.embedded.emf.models.parts.EMFModelPartRoot;
import com.thelads.core.v1_21_1.embedded.emf.models.IEMFModel;
import com.thelads.core.v1_21_1.embedded.emf.EMFManager;
import com.thelads.core.v1_21_1.embedded.emf.utils.EMFUtils;
import com.thelads.core.v1_21_1.embedded.emf.utils.IEMFWolfCollarHolder;

@Mixin(WolfCollarLayer.class)
public abstract class MixinWolfCollarFeatureRenderer extends RenderLayer<
Wolf, WolfModel<Wolf>
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
                    IEMFWolfCollarHolder<?>
                        holder) {
                    holder.emf$setCollarModel(new
                            WolfModel<>
                            (collarModel));
                }
            } catch (Exception ignored) {
            }
        }


    }

    @Override
    public @NotNull
        WolfModel<Wolf>
    getParentModel() {
        var base = super.getParentModel(); // already either adult or baby model

        if (base instanceof
                        IEMFWolfCollarHolder<?>
                        holder
                && holder.emf$hasCollarModel()) {
            //noinspection unchecked
            var model = (
                    WolfModel<Wolf>
                    ) holder.emf$getCollarModel();

            model.attackTime = base.attackTime;
            model.riding = base.riding;
            model.young = base.young;
            return model;
        }
        return base;
    }
}
