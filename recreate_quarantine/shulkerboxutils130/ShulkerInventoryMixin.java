package com.thelads.core.mixin.auto.shulkerboxutils130;

import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ShulkerBoxBlockEntity.class)
public abstract class ShulkerInventoryMixin {

    @Inject(method = "openContainer", at = @At("HEAD"), require = 0)
    public void onOpenContainer(CallbackInfo ci) {
        // Example of a safe no-op injection
    }
}
