package com.thelads.core.v1_20_1.game.survival.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.thelads.core.modules.SurvivalTweaksModule;
import com.thelads.core.v1_20_1.game.survival.NativeSurvivalTweaks1201;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** SurvivalTweaks: Stable block drops (1/2). A dropped block item appears at the centre of the block, not at a random offset. */
@Mixin(Block.class)
public abstract class SurvivalStableBlockDropsMixin1201 {
    @ModifyExpressionValue(
            method = "popResource(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/item/ItemStack;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;nextDouble(Lnet/minecraft/util/RandomSource;DD)D")
    )
    private static double lads$stabilizeBlockDropSpawnOffset(double original) {
        SurvivalTweaksModule mod = SurvivalTweaksModule.get();
        if (NativeSurvivalTweaks1201.active() && mod != null && mod.isStableBlockDropsEnabled()) {
            return 0.0D;
        }
        return original;
    }
}
