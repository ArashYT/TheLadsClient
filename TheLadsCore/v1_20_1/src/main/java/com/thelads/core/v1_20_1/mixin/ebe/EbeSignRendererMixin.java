package com.thelads.core.v1_20_1.mixin.ebe;

import com.mojang.blaze3d.vertex.PoseStack;
import com.thelads.core.v1_20_1.feature.ebe.BakedBlockEntities1201;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.SignRenderer;
import net.minecraft.world.level.block.SignBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.WoodType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Signs (and hanging signs, a subclass): the board is in the chunk mesh, the renderer keeps drawing only the text. */
@Mixin(SignRenderer.class)
public abstract class EbeSignRendererMixin {
    @Inject(method = "renderSignWithText", at = @At("HEAD"))
    private void lads$ebeSignStart(SignBlockEntity sign, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay, BlockState state,
                                   SignBlock block, WoodType wood, Model model, CallbackInfo ci) {
        BakedBlockEntities1201.signBoardBaked = BakedBlockEntities1201.signBoardBaked(sign);
    }

    @Inject(method = "renderSign", at = @At("HEAD"), cancellable = true)
    private void lads$ebeSkipBoard(PoseStack poseStack, MultiBufferSource buffers, int light, int overlay, WoodType wood, Model model, CallbackInfo ci) {
        if (BakedBlockEntities1201.signBoardBaked) ci.cancel();
    }

    @Inject(method = "renderSignWithText", at = @At("RETURN"))
    private void lads$ebeSignEnd(SignBlockEntity sign, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay, BlockState state,
                                 SignBlock block, WoodType wood, Model model, CallbackInfo ci) {
        BakedBlockEntities1201.signBoardBaked = false;
    }
}
