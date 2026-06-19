package com.thelads.core.mixin;

import net.minecraft.network.Connection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mixin(Connection.class)
public class ConnectionSignalLossMixin {
    private static final Logger THELADS_SIGNALLOSS_LOG = LoggerFactory.getLogger("SignalLoss");

    @Inject(method = "handleDisconnection", at = @At("HEAD"), require = 0)
    private void thelads$onConnectionLost(CallbackInfo ci) {
        THELADS_SIGNALLOSS_LOG.warn("[SignalLoss] Network connection interrupted.");
    }
}
