package com.thelads.smp.features.arise.nb9.mixin;

import com.thelads.smp.features.arise.nb9.AriseNB9Config;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LilyPadBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin on LilyPadBlock to prevent unwanted destruction from boats when betterLilypads is enabled.
 */
@Mixin(LilyPadBlock.class)
public abstract class MixinLilyPadBlock {

    @Inject(method = "entityInside", at = @At("HEAD"), cancellable = true)
    private void ladssmp$protectLilyPads(
            BlockState state,
            Level level,
            BlockPos pos,
            Entity entity,
            InsideBlockEffectApplier effectApplier,
            boolean isInside,
            CallbackInfo ci
    ) {
        if (AriseNB9Config.get().betterLilypads) {
            ci.cancel();
        }
    }
}
