package com.thelads.core.v1_20_1.game.misc.mixin;

import com.thelads.core.v1_20_1.game.misc.NativeDynamicLights1201;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Dynamic Lights in the world's light: vanilla's chunk meshing, block entities, particles and fluids all ask this (the BlockPos overload calls it). */
@Mixin(LevelRenderer.class)
abstract class DynamicLightsBlockMixin {
    @Inject(method = "getLightColor(Lnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/world/level/block/state/BlockState;"
        + "Lnet/minecraft/core/BlockPos;)I", at = @At("RETURN"), cancellable = true, require = 1)
    private static void lads$dynamicLight(BlockAndTintGetter level, BlockState state, BlockPos pos, CallbackInfoReturnable<Integer> callback) {
        int coords = callback.getReturnValueI(), lit = NativeDynamicLights1201.coords(coords, pos);
        if (lit != coords) callback.setReturnValue(lit);
    }
}
