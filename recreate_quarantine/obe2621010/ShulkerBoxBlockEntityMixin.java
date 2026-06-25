package com.thelads.core.mixin.auto.obe2621010;

import net.minecraft.client.renderer.blockentity.ShulkerBoxBlockEntityRenderer;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(net.minecraft.client.renderer.blockentity.ShulkerBoxBlockEntityRenderer.class)
public class ShulkerBoxBlockEntityMixin {

    @Inject(method = "render", at = @At("HEAD"), require = 0)
    private void render(ShulkerBoxBlockEntity shulkerBoxBlockEntity, float partialTicks, CallbackInfo ci) {
        // Custom logic here
    }
}
