package com.thelads.core.v1_8_9.feature.fastquit.mixin;

import com.thelads.core.v1_8_9.feature.fastquit.FastQuit189;
import net.minecraft.client.multiplayer.GuiConnecting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A server or LAN connection starts once background saves are done (Forge's registries and server state are back to normal). */
@Mixin(GuiConnecting.class)
public abstract class GuiConnectingFastQuitMixin {
    @Inject(method = "connect", at = @At("HEAD"))
    private void lads$awaitSaves(String ip, int port, CallbackInfo ci) {
        FastQuit189.awaitSaves();
    }
}
