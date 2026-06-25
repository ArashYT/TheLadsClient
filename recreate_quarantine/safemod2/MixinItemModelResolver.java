package com.thelads.core.mixin.auto.safemod2;

import net.minecraft.client.renderer.item.ItemModelResolver;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemModelResolver.class)
public class MixinItemModelResolver {
    @Inject(method = "resolve", at = @At("HEAD"), require = 0)
    private void onResolve(net.minecraft.world.item.ItemStack stack, net.minecraft.client.renderer.RenderType renderType) {
        // Custom logic here
    }
}
