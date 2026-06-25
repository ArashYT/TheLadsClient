package com.thelads.core.mixin.auto.obe2621010;

import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(net.minecraft.client.renderer.blockentity.BlockEntityRenderers.class)
public class BlockEntityRenderersMixin {

    @Inject(method = "getRenderer", at = @At("HEAD"), require = 0)
    private static void getRenderer(BlockEntity blockEntity, CallbackInfoReturnable<BlockEntityRenderer> cir) {
        // Custom logic here
    }
}
