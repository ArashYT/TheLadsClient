package com.thelads.core.v1_20_1.feature.fastquit.mixin;

import com.thelads.core.v1_20_1.feature.fastquit.FastQuit1201;
import net.minecraft.client.gui.screens.ConnectScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A server, LAN or Realms connection starts once background saves are done (the last server's mods have stopped). */
@Mixin(ConnectScreen.class)
abstract class ConnectScreenFastQuitMixin {
    @Inject(method = "startConnecting", at = @At("HEAD"))
    private static void lads$awaitSaves(CallbackInfo ci) {
        FastQuit1201.awaitSaves(true);
    }
}
