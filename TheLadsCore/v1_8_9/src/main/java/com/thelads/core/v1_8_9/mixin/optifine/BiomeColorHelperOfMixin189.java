package com.thelads.core.v1_8_9.mixin.optifine;

import com.thelads.core.modules.OptiFine189Module;
import net.minecraft.util.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.biome.BiomeColorHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * OptiFine Smooth Biomes setting: when disabled, samples single-point biome color
 * rather than blending across 9-block neighborhood, improving chunk meshing performance.
 * Cancels at HEAD so the vanilla 3x3 blend (9 biome lookups) never runs when disabled; enabled, the method runs unchanged.
 *
 * Smooth Biomes ON (default): the cancellable HEAD hook allocates one CallbackInfoReturnable per call unless C2 inlines the
 * handler and scalar-replaces it. That is expected for a handler this small but NOT measured (no JFR/JMH artifact exists);
 * profile allocation in a real chunk-rebuild before relying on it.
 * Smooth Biomes OFF: cancels at HEAD, bypassing the 9-lookup blend.
 */
@Mixin(BiomeColorHelper.class)
public abstract class BiomeColorHelperOfMixin189 {
    @Inject(method = "getGrassColorAtPos", at = @At("HEAD"), cancellable = true)
    private static void lads$grassColor(IBlockAccess blockAccess, BlockPos pos, CallbackInfoReturnable<Integer> cir) {
        if (!OptiFine189Module.sOfSmoothBiomes) {
            cir.setReturnValue(blockAccess.getBiomeGenForCoords(pos).getGrassColorAtPos(pos));
        }
    }

    @Inject(method = "getFoliageColorAtPos", at = @At("HEAD"), cancellable = true)
    private static void lads$foliageColor(IBlockAccess blockAccess, BlockPos pos, CallbackInfoReturnable<Integer> cir) {
        if (!OptiFine189Module.sOfSmoothBiomes) {
            cir.setReturnValue(blockAccess.getBiomeGenForCoords(pos).getFoliageColorAtPos(pos));
        }
    }

    @Inject(method = "getWaterColorAtPos", at = @At("HEAD"), cancellable = true)
    private static void lads$waterColor(IBlockAccess blockAccess, BlockPos pos, CallbackInfoReturnable<Integer> cir) {
        if (!OptiFine189Module.sOfSmoothBiomes) {
            cir.setReturnValue(blockAccess.getBiomeGenForCoords(pos).getWaterColorMultiplier());
        }
    }
}
