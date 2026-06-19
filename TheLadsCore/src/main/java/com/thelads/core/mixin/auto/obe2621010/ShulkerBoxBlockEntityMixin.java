package com.thelads.core.mixin.auto.obe2621010;

import com.thelads.core.features.auto.obe.BlockEntityExt;
import com.thelads.core.features.auto.obe.RenderMode;
import com.thelads.core.features.auto.obe.RenderModeManager;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ShulkerBoxBlockEntity.class)
public class ShulkerBoxBlockEntityMixin {

    @Inject(method = "<init>", at = @At("TAIL"), require = 0)
    private void thelads$init(CallbackInfo ci) {
        BlockEntityExt ext = (BlockEntityExt) (Object) this;
        ext.isSupportedBlockEntity(true);
    }

    @Inject(method = "updateAnimation", at = @At("HEAD"), require = 0)
    public void thelads$updateAnimation(Level level, BlockPos pos, BlockState blockState, CallbackInfo ci) {
        ShulkerBoxBlockEntity be = (ShulkerBoxBlockEntity) (Object) this;
        if (be.getAnimationStatus() != ShulkerBoxBlockEntity.AnimationStatus.CLOSED) {
            RenderModeManager.setRenderModeDelayed((BlockEntityExt) be, RenderMode.ENTITY, be.getBlockPos());
        } else {
            RenderModeManager.setRenderModeDelayed((BlockEntityExt) be, RenderMode.TERRAIN, be.getBlockPos());
        }
    }
}
