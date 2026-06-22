package com.thelads.core.mixin.auto.farblockentityrendering21;

import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockEntityRenderer.class)
public interface BlockEntityRendererMixin {

    @Inject(method = "shouldRender", at = @At("HEAD"), require = 0)
    default void shouldRender(BlockEntity blockEntity, CallbackInfoReturnable<Boolean> cir) {
        // Minimal safe injection to ensure the method is called
    }
}
