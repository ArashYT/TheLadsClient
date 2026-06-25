package com.thelads.core.mixin.auto.safemod2;

import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public class MixinCamera {
    @Inject(method = "update", at = @At("HEAD"), require = 0)
    private void onUpdate(net.minecraft.client.player.LocalPlayer player, net.minecraft.world.level.Level worldIn) {
        // Custom logic here
    }
}
