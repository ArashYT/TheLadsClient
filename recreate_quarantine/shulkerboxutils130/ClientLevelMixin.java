package com.thelads.core.mixin.auto.shulkerboxutils130;

import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Level.class)
public abstract class ClientLevelMixin {

    @Shadow @Final private Minecraft minecraftClient;

    @Inject(method = "tick", at = @At("HEAD"), require = 0)
    public void onTick(CallbackInfo ci) {
        // Example of a safe no-op injection
    }
}
