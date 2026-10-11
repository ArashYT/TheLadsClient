package com.thelads.core.v1_8_9.mixin;

import com.thelads.core.client.util.LaunchToken;
import net.minecraft.util.Session;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Swaps the launcher's placeholder access token for the one it wrote to stdin (see LaunchToken). */
@Mixin(Session.class)
public abstract class SessionTokenMixin {
    @Shadow @Final @Mutable private String token;

    @Inject(method = "<init>", at = @At("RETURN"), require = 1)
    private void ladsLaunchToken(CallbackInfo ci) {
        token = LaunchToken.resolve(token);
    }
}
