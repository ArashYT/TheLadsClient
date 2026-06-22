package com.thelads.core.mixin.auto.signalloss121262;

import net.minecraft.client.Minecraft;
import net.minecraft.network.Connection;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Connection.class)
public class ClientConnectionMixin {

    @Shadow @Final private Minecraft minecraft;

    @Inject(method = "sendPacket", at = @At("HEAD"), require = 0)
    public void onSendPacket(CallbackInfo ci) {
        // Minimal safe injection
    }
}
