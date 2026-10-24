package com.thelads.core.v1_8_9.pvp.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.thelads.core.v1_8_9.pvp.PvpVisuals189;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.BlockRendererDispatcher;
import net.minecraft.util.BlockPos;
import net.minecraft.world.IBlockAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Hide Foliage: a hidden plant reads as render type -1 (nothing to draw), so renderBlock draws nothing for it; vanilla's, OptiFine's
 * and Ionium's chunk builders all go through renderBlock. require 0: OptiFine rewrites this class at runtime; QA checks both paths.
 */
@Mixin(BlockRendererDispatcher.class)
public abstract class HideFoliageMixin189 {
    @ModifyExpressionValue(method = "renderBlock", at = @At(value = "INVOKE", target = "Lnet/minecraft/block/Block;getRenderType()I"), require = 0)
    private int lads$hideFoliage(int type, @Local(argsOnly = true) IBlockState state, @Local(argsOnly = true) BlockPos pos,
            @Local(argsOnly = true) IBlockAccess world) {
        return type != -1 && PvpVisuals189.hidden(state, pos, world) ? -1 : type;
    }
}
