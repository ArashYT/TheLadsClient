package com.thelads.core.v26_2.world.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.thelads.core.v26_2.world.World26;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.CapeLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Wavy Capes: the cape mesh instead of the cape model, once vanilla has decided the cape shows (World26.cape). */
@Mixin(CapeLayer.class)
public class CapeLayerWavyMixin {
    @WrapOperation(method = "submit*", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;III)V"))
    private void lads$wavyCape(SubmitNodeCollector collector, Model<?> model, Object state, PoseStack pose, RenderType type, int light, int overlay,
                               int outline, Operation<Void> original) {
        if (!(state instanceof AvatarRenderState avatar) || !World26.cape(collector, avatar, pose, type, light, overlay))
            original.call(collector, model, state, pose, type, light, overlay, outline);
    }
}
