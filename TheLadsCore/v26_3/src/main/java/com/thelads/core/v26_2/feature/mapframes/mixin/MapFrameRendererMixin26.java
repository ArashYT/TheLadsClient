package com.thelads.core.v26_2.feature.mapframes.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import com.thelads.core.v26_2.feature.mapframes.MapFrameCache26;
import com.thelads.core.v26_2.feature.mapframes.MapFrameState26;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.entity.ItemFrameRenderer;
import net.minecraft.client.renderer.entity.state.ItemFrameRenderState;
import net.minecraft.world.entity.decoration.ItemFrame;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Map frames: the hidden test runs at extraction (cached on the frame), the draw only reads the flag. The map itself is still drawn. */
@Mixin(ItemFrameRenderer.class)
abstract class MapFrameRendererMixin26 {
    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/decoration/ItemFrame;Lnet/minecraft/client/renderer/entity/state/ItemFrameRenderState;F)V", at = @At("RETURN"))
    private void lads$frameExtract(ItemFrame frame, ItemFrameRenderState state, float partial, CallbackInfo ci) {
        ((MapFrameState26) state).lads$mapFrameHidden(MapFrameCache26.hidden(frame));
    }

    @WrapWithCondition(method = "submit(Lnet/minecraft/client/renderer/entity/state/ItemFrameRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/block/BlockModelRenderState;submitWithZOffset(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V"))
    private boolean lads$frameModel(BlockModelRenderState model, PoseStack pose, SubmitNodeCollector collector, int light, int outline, int z,
                                    @Local(argsOnly = true) ItemFrameRenderState state) {
        return !((MapFrameState26) state).lads$mapFrameHidden();
    }
}
