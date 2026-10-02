package com.thelads.core.v1_21_11.embedded.emf.mixin.mixins.exporting;


import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import com.thelads.core.v1_21_11.embedded.emf.EMF;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.MeshTransformer;

@Mixin(value = MeshTransformer.class, priority = 1001)
public interface MixinMeshTransformer {

    @Inject(method =
            "method_62140"
            , at = @At(value = "HEAD"),cancellable = true)
    private static void emf$cancel(final float f, final float g, final MeshDefinition meshDefinition, final CallbackInfoReturnable<MeshDefinition> cir) {
        if (EMF.tempDisableModelModifications){
            cir.setReturnValue(meshDefinition);
        }
    }
}
