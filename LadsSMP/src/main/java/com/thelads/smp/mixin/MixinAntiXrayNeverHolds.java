package com.thelads.smp.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * AntiXray sends a connection's packets strictly in order and holds every later packet behind the first one that is not "ready" (its ConnectionMixin keeps them in
 * antiXray$isActionReady / pendingActions). A chunk packet whose asynchronous obfuscation never finishes (it does not even need to be switched on: with
 * enabled=false the queue still filled to 20,000 packets) therefore stops the whole connection for good. In the integrated server of a game client the player
 * saw the first chunks, could still walk, and nothing else ever arrived (about every second new world). Connectivity and Packet Fixer remove the timeouts, so the
 * client was not even kicked. A client's own server has nothing to hide from its only player: treat every packet as ready there. A dedicated server is not touched.
 */
@Pseudo
@Mixin(targets = "me.drex.antixray.common.util.Util", remap = false)
public class MixinAntiXrayNeverHolds {
    @Inject(method = "isReady", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void ladssmp$neverHold(net.minecraft.network.protocol.Packet<?> packet, CallbackInfoReturnable<Boolean> cir) {
        if (FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT) cir.setReturnValue(true);
    }
}
