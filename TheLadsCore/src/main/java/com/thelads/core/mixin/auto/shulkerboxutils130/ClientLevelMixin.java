package com.thelads.core.mixin.auto.shulkerboxutils130;

import com.thelads.core.features.auto.shulkerboxutils.ShulkerBoxUtilsCache;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Level.class)
public class ClientLevelMixin {

    @Inject(method = "removeBlockEntity", at = @At("HEAD"), require = 0)
    private void thelads$onRemoveBlockEntity(BlockPos pos, CallbackInfo ci) {
        Level level = (Level) (Object) this;
        if (!level.isClientSide()) {
            return;
        }
        if (level.getBlockEntity(pos) instanceof ShulkerBoxBlockEntity) {
            ShulkerBoxUtilsCache.ITEMS.remove(pos);
            ShulkerBoxUtilsCache.SCREEN_AUTHORITATIVE.remove(pos);
        }
    }
}
