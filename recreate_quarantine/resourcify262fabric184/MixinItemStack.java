package com.thelads.core.mixin.auto.resourcify262fabric184;

import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemStack.class)
public class MixinItemStack {

    @Inject(method = "use", at = @At("HEAD"), require = 0)
    private void onUse(Level level, LivingEntity livingEntity, InteractionHand interactionHand, CallbackInfo ci) {
        // Minimal safe injection point
    }
}
