package com.thelads.core.v1_8_9.mixin.optifine;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.thelads.core.modules.OptiFine189Module;
import net.minecraft.block.BlockLeaves;
import net.minecraft.util.EnumWorldBlockLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * OptiFine Trees setting: controls leaves opacity and rendering pass independently of global Graphics.
 * Values: 0=Default, 1=Fast (opaque), 2=Fancy (cutout), 3=Smart (cutout).
 * Uses MixinExtras ModifyReturnValue to avoid per-block CallbackInfo allocations in hot paths.
 */
@Mixin(BlockLeaves.class)
public abstract class BlockLeavesMixin189 {
    @ModifyReturnValue(method = "isOpaqueCube", at = @At("RETURN"))
    private boolean lads$treesOpaque(boolean original) {
        int trees = OptiFine189Module.sOfTrees;
        if (trees == 0) return original;
        return trees == 1;
    }

    @ModifyReturnValue(method = "getBlockLayer", at = @At("RETURN"))
    private EnumWorldBlockLayer lads$treesBlockLayer(EnumWorldBlockLayer original) {
        int trees = OptiFine189Module.sOfTrees;
        if (trees == 0) return original;
        return trees == 1 ? EnumWorldBlockLayer.SOLID : EnumWorldBlockLayer.CUTOUT_MIPPED;
    }
}
