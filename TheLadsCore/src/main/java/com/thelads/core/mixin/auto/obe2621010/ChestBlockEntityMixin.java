package com.thelads.core.mixin.auto.obe2621010;

import com.thelads.core.features.auto.obe.BlockEntityExt;
import com.thelads.core.features.auto.obe.RenderMode;
import com.thelads.core.features.auto.obe.RenderModeManager;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChestBlockEntity.class)
public class ChestBlockEntityMixin {

    @Inject(method = "<init>(Lnet/minecraft/world/level/block/entity/BlockEntityType;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)V", at = @At("TAIL"), require = 0)
    private void thelads$init(BlockEntityType<?> type, BlockPos pos, BlockState state, CallbackInfo ci) {
        BlockEntityExt ext = (BlockEntityExt) this;
        ext.isSupportedBlockEntity(true);
    }

    @Inject(method = "lidAnimateTick(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/entity/ChestBlockEntity;)V", at = @At("HEAD"), require = 0)
    private static void thelads$lidAnimateTick(Level level, BlockPos pos, BlockState state, ChestBlockEntity entity, CallbackInfo ci) {
        BlockEntityExt ext = (BlockEntityExt) entity;
        if (entity.getOpenNess(0.0f) > 0.05f) {
            RenderModeManager.setRenderModeDelayed(ext, RenderMode.ENTITY, pos);
        } else {
            RenderModeManager.setRenderModeDelayed(ext, RenderMode.TERRAIN, pos);
        }
    }
}
