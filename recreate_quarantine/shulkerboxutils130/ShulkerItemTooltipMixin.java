package com.thelads.core.mixin.auto.shulkerboxutils130;

import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemStack.class)
public abstract class ShulkerItemTooltipMixin {

    @Inject(method = "getDisplayName", at = @At("HEAD"), require = 0, cancellable = true)
    public void onGetDisplayName(CallbackInfoReturnable<Component> cir) {
        // Example of a safe no-op injection
    }
}
