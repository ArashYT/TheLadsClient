package com.thelads.smp.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Simply Tents 3.5 registers an unused simplytents:my_geo_block (no item) whose newBlockEntity builds a TentBlockEntity, a type
 * that does not accept that block: 26.3 throws "Invalid block entity ... state" the moment it is placed (/setblock,
 * WorldEdit), crashing the server tick. The block has no other use, so it gets no block entity.
 */
@Pseudo
@Mixin(targets = "net.sappyeddie.simplytents.tent.block.MyGeoBlock", remap = false)
public class MixinSimplyTentsGeoBlock {
    @Inject(method = "newBlockEntity", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void ladssmp$noTentEntity(BlockPos pos, BlockState state, CallbackInfoReturnable<BlockEntity> cir) {
        cir.setReturnValue(null);
    }
}
