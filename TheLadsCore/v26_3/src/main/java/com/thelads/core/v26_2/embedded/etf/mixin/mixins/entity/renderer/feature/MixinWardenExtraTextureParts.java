package com.thelads.core.v26_2.embedded.etf.mixin.mixins.entity.renderer.feature;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.model.monster.warden.WardenModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import net.minecraft.resources.Identifier;
import net.minecraft.client.renderer.entity.layers.RenderLayer;

import net.minecraft.client.model.geom.builders.MeshTransformer;
import net.minecraft.client.renderer.entity.state.WardenRenderState;
import net.minecraft.client.model.EntityModel;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import com.thelads.core.v26_2.embedded.etf.ETF;

import java.util.List;

@Mixin(WardenModel.class)
public abstract class MixinWardenExtraTextureParts extends EntityModel<WardenRenderState> {

    @SuppressWarnings("unused")
    protected MixinWardenExtraTextureParts() {
        super(null);
    }

    @WrapOperation(method = {
            "createTendrilsLayer",
            "createHeartLayer",
            "createBioluminescentLayer",
            "createPulsatingSpotsLayer"
    }, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/geom/builders/LayerDefinition;apply(Lnet/minecraft/client/model/geom/builders/MeshTransformer;)Lnet/minecraft/client/model/geom/builders/LayerDefinition;"))
    private static LayerDefinition etf$modifyParts1(final LayerDefinition instance, final MeshTransformer meshTransformer, final Operation<LayerDefinition> original) {
        if (ETF.config().getConfig().enableFullBodyWardenTextures) {
            return instance; // cancel call and return original unmodified instance
        }
        return original.call(instance, meshTransformer);
    }

}





