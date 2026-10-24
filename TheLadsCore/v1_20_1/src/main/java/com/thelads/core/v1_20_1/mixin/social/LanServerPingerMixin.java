package com.thelads.core.v1_20_1.mixin.social;

import com.thelads.core.v1_20_1.social.SocialBridge1201;
import net.minecraft.client.server.LanServerPinger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to suppress LAN multicast ping packets when publishing in Friends/Invite scope.
 */
@Mixin(LanServerPinger.class)
public abstract class LanServerPingerMixin {
    @Inject(method = "run()V", at = @At("HEAD"), cancellable = true)
    private void ladsSuppressLanBroadcast(CallbackInfo ci) {
        if (SocialBridge1201.isSuppressLanPinger()) {
            ci.cancel();
        }
    }
}
