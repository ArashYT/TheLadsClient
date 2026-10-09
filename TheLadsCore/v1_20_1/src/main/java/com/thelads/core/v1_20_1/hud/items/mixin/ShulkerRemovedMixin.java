package com.thelads.core.v1_20_1.hud.items.mixin;

import com.thelads.core.v1_20_1.hud.items.ShulkerContents1201;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A removed or unloaded box cannot show cached contents. */
@Mixin(BlockEntity.class)
abstract class ShulkerRemovedMixin {
    @Inject(method = "setRemoved", at = @At("HEAD"))
    private void lads$removed(CallbackInfo callback) {
        if ((Object) this instanceof ShulkerBoxBlockEntity box) ShulkerContents1201.removed(box);
    }
}
