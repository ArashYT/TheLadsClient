package com.thelads.core.v26_2.mixin;

import com.thelads.core.v26_2.feature.ShulkerContents;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientLevel.class)
public class ShulkerBlockChangesMixin {
    @Inject(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",
        at = @At("RETURN"), require = 1)
    private void lads$changed(BlockPos pos, BlockState state, int flags, int recursion,
                              CallbackInfoReturnable<Boolean> callback) {
        // Any accepted block-state change invalidates that location's prior observation, including replacement boxes.
        if (callback.getReturnValueZ()) ShulkerContents.invalidated(pos);
    }
}
