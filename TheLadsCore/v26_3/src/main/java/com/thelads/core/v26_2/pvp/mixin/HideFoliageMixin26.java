package com.thelads.core.v26_2.pvp.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.thelads.core.v26_2.pvp.PvpVisuals26;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockBehaviour;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Hide Foliage: a hidden block's state reports no model, which both vanilla's chunk builder and Sodium's skip (they test the render
 * shape before meshing a block). Allocation-free: one array scan, empty while the module is off.
 */
@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class HideFoliageMixin26 {
    @Shadow public abstract Block getBlock();

    @ModifyReturnValue(method = "getRenderShape", at = @At("RETURN"))
    private RenderShape lads$hideFoliage(RenderShape shape) {
        return PvpVisuals26.hidden(getBlock()) ? RenderShape.INVISIBLE : shape;
    }
}
