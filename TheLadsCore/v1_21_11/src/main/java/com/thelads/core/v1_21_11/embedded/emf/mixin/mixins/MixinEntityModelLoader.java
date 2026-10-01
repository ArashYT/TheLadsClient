package com.thelads.core.v1_21_11.embedded.emf.mixin.mixins;


import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import com.thelads.core.v1_21_11.embedded.emf.EMF;
import com.thelads.core.v1_21_11.embedded.emf.EMFManager;


@Mixin(EntityModelSet.class)
public class MixinEntityModelLoader {
    @Inject(method = "bakeLayer", at = @At(value = "RETURN"), cancellable = true)
    private void emf$injectModelLoad(ModelLayerLocation layer, CallbackInfoReturnable<ModelPart> cir) {
        if (EMF.testForForgeLoadingError()) return;

        cir.setReturnValue(EMFManager.getInstance().injectIntoModelRootGetter(layer, cir.getReturnValue()));

    }
}
