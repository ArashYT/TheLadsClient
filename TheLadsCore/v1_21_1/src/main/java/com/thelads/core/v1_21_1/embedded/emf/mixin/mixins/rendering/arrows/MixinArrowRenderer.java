package com.thelads.core.v1_21_1.embedded.emf.mixin.mixins.rendering.arrows;

import org.spongepowered.asm.mixin.Mixin;

import com.thelads.core.v1_21_1.embedded.emf.utils.IEMFCustomModelHolder;
import net.minecraft.client.renderer.entity.TippableArrowRenderer;
import com.thelads.core.v1_21_1.embedded.emf.utils.EMFUtils;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.thelads.core.v1_21_1.embedded.emf.models.parts.EMFModelPartRoot;
import net.minecraft.client.model.geom.ModelLayerLocation;
@Mixin(TippableArrowRenderer.class)
public abstract class MixinArrowRenderer implements IEMFCustomModelHolder {
    @Unique
    private EMFModelPartRoot emf$model = null;

    @Inject(method = "<init>", at = @At(value = "TAIL"))
    private void emf$findModel(CallbackInfo ci) {
        ModelLayerLocation layer = new ModelLayerLocation(EMFUtils.res("minecraft", "arrow"), "main");
        emf$setModel(EMFUtils.getArrowOrNull(layer));
    }

    @Override
    public EMFModelPartRoot emf$getModel() {
        return emf$model;
    }

    @Override
    public void emf$setModel(final EMFModelPartRoot model) {
        emf$model = model;
    }
}
