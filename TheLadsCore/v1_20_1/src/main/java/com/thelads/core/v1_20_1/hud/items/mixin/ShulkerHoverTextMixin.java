package com.thelads.core.v1_20_1.hud.items.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.thelads.core.v1_20_1.hud.items.ShulkerInventory1201;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** While the grid shows a box's contents, vanilla's "Item xN ... and N more" list is skipped (its "Items" check reads false). */
@Mixin(ShulkerBoxBlock.class)
abstract class ShulkerHoverTextMixin {
    @WrapOperation(method = "appendHoverText", at = @At(value = "INVOKE", target = "Lnet/minecraft/nbt/CompoundTag;contains(Ljava/lang/String;I)Z"))
    private boolean lads$items(CompoundTag tag, String key, int type, Operation<Boolean> original, @Local(argsOnly = true) ItemStack stack) {
        return original.call(tag, key, type) && !("Items".equals(key) && ShulkerInventory1201.preview(stack));
    }
}
