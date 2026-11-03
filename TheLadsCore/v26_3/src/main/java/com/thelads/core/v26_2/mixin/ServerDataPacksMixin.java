package com.thelads.core.v26_2.mixin;

import net.minecraft.client.multiplayer.ServerData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Server resource packs default to Enabled, for every server entry made (the Network button, server discovery, direct connect) and
 * every saved one with no stored choice ("acceptTextures"); an entry set to Disabled stays so. Prompt saves no key, so it comes back Enabled.
 */
@Mixin(ServerData.class)
public abstract class ServerDataPacksMixin {
    @Inject(method = "<init>", at = @At("RETURN"))
    private void ladsPacksEnabled(CallbackInfo ci) {
        ((ServerData) (Object) this).setResourcePackStatus(ServerData.ServerPackStatus.ENABLED);
    }

    @ModifyArg(method = "read", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ServerData;setResourcePackStatus(Lnet/minecraft/client/multiplayer/ServerData$ServerPackStatus;)V"))
    private static ServerData.ServerPackStatus ladsNoChoiceIsEnabled(ServerData.ServerPackStatus status) {
        return status == ServerData.ServerPackStatus.PROMPT ? ServerData.ServerPackStatus.ENABLED : status;
    }
}
