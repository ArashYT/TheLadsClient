// Adapted from Capes 1.5.10+1.21.11 by Cael (LGPL-2.1-only); modified by The Lads: remapped to Mojang mappings and repackaged into Lads Core.
package com.thelads.core.v1_21_11.embedded.capes.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.renderer.entity.layers.CapeLayer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(CapeLayer.class)
public class MixinCapeFeatureRenderer {

    // Upstream "render*", pinned to the method the shipped jar resolves it to.
    @WrapOperation(method = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/AvatarRenderState;FF)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/rendertype/RenderTypes;entitySolid(Lnet/minecraft/resources/Identifier;)Lnet/minecraft/client/renderer/rendertype/RenderType;"))
    private RenderType fixCapeTransparency(Identifier texture, Operation<RenderType> original) {
        return RenderTypes.armorCutoutNoCull(texture);
    }
}
