package com.thelads.core.v1_21_11.embedded.emf.mixin.mixins;


import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import com.thelads.core.v1_21_11.embedded.emf.EMF;
import com.thelads.core.v1_21_11.embedded.emf.models.animation.state.EMFState;
import com.thelads.core.v1_21_11.embedded.emf.models.parts.EMFModelPart;
import com.thelads.core.v1_21_11.embedded.emf.models.parts.EMFModelPartRoot;
import com.thelads.core.v1_21_11.embedded.emf.models.IEMFModel;
import com.thelads.core.v1_21_11.embedded.emf.EMFManager;

import java.util.function.Function;


@Mixin(Model.class)
public class MixinModel implements IEMFModel {
    @Unique
    private EMFModelPartRoot emf$thisEMFModelRoot = null;


    @Inject(method = "<init>",
            at = @At(value = "TAIL"))
    private void emf$discoverEMFModel(final ModelPart modelPart, final Function<?,?> function, final CallbackInfo ci) {
        if (EMF.testForForgeLoadingError()) return;
        if (modelPart instanceof EMFModelPartRoot root) {
            emf$thisEMFModelRoot = root;
        } else if (modelPart instanceof EMFModelPart nonRoot) {
            emf$thisEMFModelRoot = nonRoot.getRoot(); // See vex model having a non-root root part
        }
        EMFManager.lastCreatedRootModelPart = null;
    }


    @Override
    public boolean emf$isEMFModel() {
        return emf$thisEMFModelRoot != null;
    }

    @Override
    public EMFModelPartRoot emf$getEMFRootModel() {
        return emf$thisEMFModelRoot;
    }


    @Inject(method = "renderType",
            at = @At(value = "HEAD"))
    private void emf$discoverEMFModel(CallbackInfoReturnable<RenderType> cir) {
        var state = EMFState.state();
        if (state == null) return;
        state.setLayerFactory(((Model) ((Object) this)).renderType);
    }
}
