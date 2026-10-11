package com.thelads.core.v1_20_1.mixin;

import com.thelads.core.client.util.LaunchToken;
import net.minecraft.client.User;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Swaps the launcher's placeholder access token for the one it wrote to stdin (see LaunchToken). */
@Mixin(User.class)
public abstract class SessionTokenMixin {
    @Shadow @Final @Mutable private String accessToken;

    @Inject(method = "<init>", at = @At("RETURN"), require = 1)
    private void ladsLaunchToken(CallbackInfo ci) {
        accessToken = LaunchToken.resolve(accessToken);
    }
}
