package com.thelads.core.v26_2.embedded.emf.mixin.mixins.rendering.model;


import net.minecraft.client.model.animal.wolf.WolfModel;
import net.minecraft.client.renderer.entity.state.WolfRenderState;

import net.minecraft.world.entity.animal.wolf.Wolf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.thelads.core.v26_2.embedded.emf.utils.IEMFWolfCollarHolder;

@Mixin(WolfModel.class)
public class MixinWolfEntityModel<T extends Wolf> implements
IEMFWolfCollarHolder
{

    @Unique
            WolfModel
            emf$collarModel = null;

    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/WolfRenderState;)V", at = @At(value = "HEAD"))
    private void smf$setAngles(final WolfRenderState wolfRenderState, final CallbackInfo ci) {
        if (emf$hasCollarModel()) emf$collarModel.setupAnim(wolfRenderState);
    }

    @Override
    public
        WolfModel
    emf$getCollarModel() {
        return emf$collarModel;
    }

    @Override
    public void emf$setCollarModel(
                    WolfModel
                    model) {
        emf$collarModel = model;
    }


}
