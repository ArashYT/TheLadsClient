package com.thelads.core.v1_21_11.embedded.emf.mixin.mixins.rendering.model;


import org.spongepowered.asm.mixin.Mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.client.renderer.entity.state.ArmadilloRenderState;
import net.minecraft.client.model.animal.armadillo.ArmadilloModel;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import com.thelads.core.v1_21_11.embedded.emf.models.parts.EMFModelPartVanilla;

@Mixin(ArmadilloModel.class)
public abstract class MixinArmadilloModel extends EntityModel<ArmadilloRenderState> {

    @Shadow
    @Final
    private ModelPart body;

    protected MixinArmadilloModel(final ModelPart modelPart) {
        super(modelPart);
    }

    @Inject(method =
            "setupAnim(Lnet/minecraft/client/renderer/entity/state/ArmadilloRenderState;)V"
            , at = @At(value = "TAIL"))
    private void emf$assertLayerFactory(final CallbackInfo ci) {
        if (body instanceof EMFModelPartVanilla emfPart){
            boolean set = !body.skipDraw;
            for (ModelPart sub : emfPart.getAllEMFCustomChildren()) {
                sub.visible = set;
            }
        }
    }
}