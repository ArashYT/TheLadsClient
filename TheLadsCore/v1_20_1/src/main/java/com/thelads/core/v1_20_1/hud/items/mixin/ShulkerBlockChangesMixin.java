package com.thelads.core.v1_20_1.hud.items.mixin;

import com.thelads.core.v1_20_1.hud.items.ShulkerContents1201;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Any accepted client block change invalidates that location's prior observation, including replacement boxes. Level, not
 * ClientLevel: 1.20.1's server-verified block updates (ClientLevel.setServerVerifiedBlockState) call Level.setBlock directly.
 */
@Mixin(Level.class)
abstract class ShulkerBlockChangesMixin {
    @Shadow @Final public boolean isClientSide;

    @Inject(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z", at = @At("RETURN"))
    private void lads$changed(BlockPos pos, BlockState state, int flags, int recursion, CallbackInfoReturnable<Boolean> callback) {
        if (isClientSide && callback.getReturnValueZ()) ShulkerContents1201.invalidated(pos);
    }
}
