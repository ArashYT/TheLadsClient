package com.thelads.core.v26_2.embedded.emf.mixin.mixins;


import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.animal.parrot.ParrotModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.thelads.core.v26_2.embedded.emf.EMF;
import com.thelads.core.v26_2.embedded.emf.EMFManager;
import com.thelads.core.v26_2.embedded.emf.models.animation.state.EMFEntityRenderState;
import com.thelads.core.v26_2.embedded.emf.models.animation.state.EMFState;
import com.thelads.core.v26_2.embedded.emf.utils.EMFEntity;

import net.minecraft.client.renderer.entity.layers.ParrotOnShoulderLayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.client.Minecraft;
import com.thelads.core.v26_2.embedded.emf.utils.EMFUtils;
import com.thelads.core.v26_2.embedded.etf.features.state.ETFEntityRenderState;
import com.thelads.core.v26_2.embedded.etf.utils.UEntityTypes;

@Mixin(ParrotOnShoulderLayer.class)
public class MixinParrotEntityModel {

    @Unique
    private static final ModelLayerLocation emf$parrot_shoulder =
            new ModelLayerLocation(EMFUtils.res("minecraft", "parrot"), "shoulder");

    @ModifyExpressionValue(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/geom/EntityModelSet;bakeLayer(Lnet/minecraft/client/model/geom/ModelLayerLocation;)Lnet/minecraft/client/model/geom/ModelPart;"))
    private ModelPart emf$injectParrotShoulderLayer(ModelPart original) {
        if (EMF.testForForgeLoadingError()) return original;

        return EMFManager.getInstance().injectIntoModelRootGetter(emf$parrot_shoulder, ParrotModel.createBodyLayer().bakeRoot());
    }


    private static final String RENDER_METHOD = "submitOnShoulder";

    @Inject(method = RENDER_METHOD, at = @At("HEAD"))
    private void emf$parrot1(final CallbackInfo ci, @Local(argsOnly = true) boolean isLeft) {
        EMFState.isInShoulderMethod = true;
        EMFState.isLeftShoulder = isLeft;
    }

    @Inject(method = RENDER_METHOD, at = @At("TAIL"))
    private void emf$parrot2(final CallbackInfo ci) {
        EMFState.isInShoulderMethod = false;
    }
}




