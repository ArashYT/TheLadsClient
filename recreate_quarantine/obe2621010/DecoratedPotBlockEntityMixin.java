package com.thelads.core.mixin.auto.obe2621010;

import net.minecraft.client.renderer.blockentity.DecoratedPotBlockEntityRenderer;
import net.minecraft.world.level.block.entity.DecoratedPotBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(net.minecraft.client.renderer.blockentity.DecoratedPotBlockEntityRenderer.class)
public class DecoratedPotBlockEntityMixin {

    @Inject(method = "render", at = @At("HEAD"), require = 0)
    private void render(DecoratedPotBlockEntity decoratedPotBlockEntity, float partialTicks, CallbackInfo ci) {
        // Custom logic here
    }
}
