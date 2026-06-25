package com.thelads.core.mixin.auto.obe2621010;

import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.resources.model.ModelManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(net.minecraft.client.renderer.block.model.BlockStateModelSet.class)
public class BlockStateModelSetMixin {

    @Inject(method = "reload", at = @At("HEAD"), require = 0)
    private void reload(ModelManager modelManager, CallbackInfo ci) {
        // Custom logic here
    }
}
