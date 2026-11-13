package com.thelads.core.v1_20_1.mixin.menus;

import com.thelads.core.client.LadsNetwork;
import net.minecraft.client.multiplayer.ServerData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Server resource packs default to Enabled for The Lads' own network entry only (the Network button, a saved or copied one with no stored
 * choice, "acceptTextures"); every other server keeps vanilla's prompt, and an entry set to Disabled stays so.
 */
@Mixin(ServerData.class)
public abstract class ServerDataPacksMixin {
    @Inject(method = "<init>", at = @At("RETURN"))
    private void ladsPacksEnabled(CallbackInfo ci) {
        ServerData self = (ServerData) (Object) this;
        if (LadsNetwork.isAddress(self.ip)) self.setResourcePackStatus(ServerData.ServerPackStatus.ENABLED);
    }

    @Inject(method = "read", at = @At("RETURN"))
    private static void ladsNoChoiceIsEnabled(CallbackInfoReturnable<ServerData> cir) {
        ServerData data = cir.getReturnValue();
        if (data != null && LadsNetwork.isAddress(data.ip) && data.getResourcePackStatus() == ServerData.ServerPackStatus.PROMPT) {
            data.setResourcePackStatus(ServerData.ServerPackStatus.ENABLED);
        }
    }

    @Inject(method = "copyFrom", at = @At("RETURN"))
    private void ladsCopyPacksEnabled(ServerData source, CallbackInfo ci) {
        ServerData self = (ServerData) (Object) this;
        if (LadsNetwork.isAddress(self.ip) && self.getResourcePackStatus() == ServerData.ServerPackStatus.PROMPT) {
            self.setResourcePackStatus(ServerData.ServerPackStatus.ENABLED);
        }
    }
}
