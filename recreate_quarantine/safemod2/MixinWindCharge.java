package com.thelads.core.mixin.auto.safemod2;

import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemStack.class)
public class MixinWindCharge {
    @Inject(method = "use", at = @At("HEAD"), require = 0)
    private void onUse(net.minecraft.world.level.Level worldIn, net.minecraft.core.BlockPos pos) {
        // Custom logic here
    }
}
