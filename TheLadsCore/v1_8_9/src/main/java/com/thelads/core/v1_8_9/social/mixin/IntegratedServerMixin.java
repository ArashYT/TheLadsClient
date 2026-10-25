package com.thelads.core.v1_8_9.social.mixin;

import com.thelads.core.v1_8_9.social.SocialBridge189;
import net.minecraft.client.multiplayer.ThreadLanServerPing;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.world.WorldSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Suppresses LAN server broadcast ping when hosting on loopback for Friends/Invite scope.
 */
@Mixin(IntegratedServer.class)
public abstract class IntegratedServerMixin {
    /** A world that is already open (Friends hosting on loopback, or LAN) is never published again: /publish then says it failed. */
    @Inject(method = "shareToLAN", at = @At("HEAD"), cancellable = true, require = 1)
    private void ladsRefuseSecondPublish(WorldSettings.GameType type, boolean allowCheats, CallbackInfoReturnable<String> cir) {
        if (((IntegratedServer) (Object) this).getPublic()) cir.setReturnValue(null);
    }

    @Redirect(method = "shareToLAN", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ThreadLanServerPing;start()V"), require = 1)
    private void ladsSuppressLanPing(ThreadLanServerPing ping) {
        if (!SocialBridge189.isLoopbackOnly()) {
            ping.start();
        }
    }
}
