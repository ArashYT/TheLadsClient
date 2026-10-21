package com.thelads.core.v1_8_9.mixin.optifine;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.thelads.core.modules.OptiFine189Module;
import net.minecraft.util.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.biome.BiomeColorHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * OptiFine Smooth Biomes setting: when disabled, samples single-point biome color
 * rather than blending across 9-block neighborhood, improving chunk meshing performance.
 * Uses MixinExtras ModifyReturnValue to avoid per-block CallbackInfo allocations in hot paths.
 */
@Mixin(BiomeColorHelper.class)
public abstract class BiomeColorHelperOfMixin189 {
    @ModifyReturnValue(method = "getGrassColorAtPos", at = @At("RETURN"))
    private static int lads$grassColor(int original, IBlockAccess blockAccess, BlockPos pos) {
        if (!OptiFine189Module.sOfSmoothBiomes) {
            return blockAccess.getBiomeGenForCoords(pos).getGrassColorAtPos(pos);
        }
        return original;
    }

    @ModifyReturnValue(method = "getFoliageColorAtPos", at = @At("RETURN"))
    private static int lads$foliageColor(int original, IBlockAccess blockAccess, BlockPos pos) {
        if (!OptiFine189Module.sOfSmoothBiomes) {
            return blockAccess.getBiomeGenForCoords(pos).getFoliageColorAtPos(pos);
        }
        return original;
    }

    @ModifyReturnValue(method = "getWaterColorAtPos", at = @At("RETURN"))
    private static int lads$waterColor(int original, IBlockAccess blockAccess, BlockPos pos) {
        if (!OptiFine189Module.sOfSmoothBiomes) {
            return blockAccess.getBiomeGenForCoords(pos).getWaterColorMultiplier();
        }
        return original;
    }
}
