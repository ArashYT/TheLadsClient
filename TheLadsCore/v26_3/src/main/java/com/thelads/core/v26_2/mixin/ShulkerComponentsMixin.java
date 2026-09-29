package com.thelads.core.v26_2.mixin;

import com.thelads.core.v26_2.feature.ShulkerContents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockEntity.class)
public class ShulkerComponentsMixin {
    @Inject(method = "applyComponentsFromItemStack", at = @At("RETURN"), require = 1)
    private void lads$placed(ItemStack stack, CallbackInfo callback) {
        if ((Object) this instanceof ShulkerBoxBlockEntity box) ShulkerContents.placed(box, stack);
    }

    @Inject(method = "setRemoved", at = @At("HEAD"), require = 1)
    private void lads$removed(CallbackInfo callback) {
        if ((Object) this instanceof ShulkerBoxBlockEntity box) ShulkerContents.removed(box);
    }
}
