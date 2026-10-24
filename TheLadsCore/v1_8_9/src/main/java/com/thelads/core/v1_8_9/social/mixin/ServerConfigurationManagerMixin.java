package com.thelads.core.v1_8_9.social.mixin;

import com.mojang.authlib.GameProfile;
import com.thelads.core.v1_8_9.social.SocialBridge189;
import java.net.SocketAddress;
import net.minecraft.server.management.ServerConfigurationManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Rejects uninvited player logins when a login filter predicate is active.
 */
@Mixin(ServerConfigurationManager.class)
public abstract class ServerConfigurationManagerMixin {
    @Inject(method = "allowUserToConnect", at = @At("HEAD"), cancellable = true, require = 1)
    private void ladsCheckLoginFilter(SocketAddress address, GameProfile profile, CallbackInfoReturnable<String> cir) {
        if (profile != null && SocialBridge189.isLoginFiltered(profile.getId())) {
            cir.setReturnValue("You weren't invited to this world");
        }
    }
}
